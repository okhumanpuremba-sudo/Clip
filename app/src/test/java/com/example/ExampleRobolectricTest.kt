package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.LocalClipTimelineStore
import com.example.data.model.ClipCandidate
import com.example.data.model.HookCaptionConfig
import com.example.data.model.LayoutSegment
import com.example.data.model.LogoPlacementConfig
import com.example.data.model.VerticalLayoutType
import com.example.data.model.VideoProject
import com.example.data.model.WhopCampaign
import com.example.domain.AutoFramingEngine
import com.example.domain.VideoProcessingPipeline
import com.example.domain.WhopRuleValidator
import com.example.util.GallerySaveResult
import com.example.util.GalleryVideoSaver
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ExampleRobolectricTest {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `test 1 to 4 - automatic vertical layouts A B C D detection`() {
        // Test 1: Streamer watching a video -> Layout A (streamer top, watched content bottom)
        val reactionClip = ClipCandidate(
            id = "clip_react_1",
            title = "Streamer reacting to viral video",
            category = "REACTION",
            startSec = 0L,
            endSec = 9L
        )
        val segA = AutoFramingEngine.analyzeClipTimelineSegments(reactionClip).first()
        assertEquals(VerticalLayoutType.LAYOUT_A_STREAMER_AND_CONTENT, segA.layoutType)

        // Test 2: Two visible speakers (podcast/guest/interview) -> Layout B (multi-speaker split)
        val multiSpeakerClip = ClipCandidate(
            id = "clip_multi_2",
            title = "Host and Guest podcast interview debate",
            category = "FUNNY_MOMENT",
            startSec = 0L,
            endSec = 9L
        )
        val segB = AutoFramingEngine.analyzeClipTimelineSegments(multiSpeakerClip).first()
        assertEquals(VerticalLayoutType.LAYOUT_B_MULTIPLE_SPEAKERS, segB.layoutType)
        assertTrue(segB.detectedSpeakers.size >= 2)

        // Test 3: Speaker-only chatting with no gameplay -> Layout C (full-frame speaker)
        val speakerOnlyClip = ClipCandidate(
            id = "clip_speaker_3",
            title = "Streamer talking directly to viewers story",
            category = "MEMORABLE_DIALOGUE",
            startSec = 0L,
            endSec = 9L
        )
        val segC = AutoFramingEngine.analyzeClipTimelineSegments(speakerOnlyClip).first()
        assertEquals(VerticalLayoutType.LAYOUT_C_SPEAKER_ONLY, segC.layoutType)

        // Test 4: Full-screen clutch gameplay with small corner webcam -> Layout D
        val clutchClip = ClipCandidate(
            id = "clip_clutch_4",
            title = "Unbelievable 1v4 overtime clutch play",
            category = "CLUTCH_PLAY",
            startSec = 0L,
            endSec = 30L
        )
        val segmentsD = AutoFramingEngine.analyzeClipTimelineSegments(clutchClip)
        assertTrue(segmentsD.any { it.layoutType == VerticalLayoutType.LAYOUT_D_FULLSCREEN_CONTENT_SMALL_WEBCAM })
    }

    @Test
    fun `test 5 and 6 - dynamic layout switching within a 30s clip and hysteresis anti-flicker`() {
        val project = VideoProject(
            id = "proj_30s",
            title = "Streamer Reaction & Direct Chat",
            durationSec = 120L,
            transcriptText = """
                [00:00] Streamer watching funny gameplay video
                [00:10] Streamer stops video and talks directly to viewers about the quote
                [00:20] Watching reaction video payoff on screen
            """.trimIndent()
        )
        val clip = ClipCandidate(
            id = "clip_dynamic_30s",
            projectId = project.id,
            title = "Streamer watching video then talking",
            category = "REACTION",
            startSec = 0L,
            endSec = 30L
        )

        val ext = AutoFramingEngine.generateDefaultExtendedConfig(clip, project)
        assertEquals(3, ext.segments.size)
        // Segment 1 (0-10s): Layout A (Streamer + Watched Content)
        assertEquals(0L, ext.segments[0].startSec)
        assertEquals(10L, ext.segments[0].endSec)
        assertEquals(VerticalLayoutType.LAYOUT_A_STREAMER_AND_CONTENT, ext.segments[0].layoutType)
        // Segment 2 (10-20s): Layout C (Speaker-Only)
        assertEquals(10L, ext.segments[1].startSec)
        assertEquals(20L, ext.segments[1].endSec)
        assertEquals(VerticalLayoutType.LAYOUT_C_SPEAKER_ONLY, ext.segments[1].layoutType)
        // Segment 3 (20-30s): Layout A (Streamer + Watched Content)
        assertEquals(20L, ext.segments[2].startSec)
        assertEquals(30L, ext.segments[2].endSec)
        assertEquals(VerticalLayoutType.LAYOUT_A_STREAMER_AND_CONTENT, ext.segments[2].layoutType)

        // Test 6: Hysteresis absorbs brief 1-second false detection flicker
        val flickering = listOf(
            LayoutSegment(id = "s1", startSec = 0L, endSec = 10L, layoutType = VerticalLayoutType.LAYOUT_A_STREAMER_AND_CONTENT),
            LayoutSegment(id = "s_flicker", startSec = 10L, endSec = 11L, layoutType = VerticalLayoutType.LAYOUT_C_SPEAKER_ONLY),
            LayoutSegment(id = "s2", startSec = 11L, endSec = 30L, layoutType = VerticalLayoutType.LAYOUT_A_STREAMER_AND_CONTENT)
        )
        val smoothed = AutoFramingEngine.applyHysteresisAndSmoothing(
            segments = flickering,
            minSegmentDurationSec = 3L,
            clipStartSec = 0L,
            clipEndSec = 30L
        )
        assertEquals(1, smoothed.size)
        assertEquals(0L, smoothed.first().startSec)
        assertEquals(30L, smoothed.first().endSec)
        assertEquals(VerticalLayoutType.LAYOUT_A_STREAMER_AND_CONTENT, smoothed.first().layoutType)
    }

    @Test
    fun `test 7 - manual segment override split merge and timeline persistence`() {
        val clip = ClipCandidate(id = "clip_override_7", projectId = "proj_7", startSec = 0L, endSec = 30L)
        val initialExt = LocalClipTimelineStore.loadExtendedConfigForClip(context, clip)
        val firstSegId = initialExt.segments.first().id

        // Override first segment to LAYOUT_B_MULTIPLE_SPEAKERS with 1.6x zoom
        val overridden = AutoFramingEngine.overrideSegment(
            segments = initialExt.segments,
            segmentId = firstSegId,
            newLayoutType = VerticalLayoutType.LAYOUT_B_MULTIPLE_SPEAKERS,
            newZoomLevel = 1.6f
        )
        assertEquals(VerticalLayoutType.LAYOUT_B_MULTIPLE_SPEAKERS, overridden.first().layoutType)
        assertEquals(1.6f, overridden.first().zoomLevel, 0.01f)
        assertTrue(overridden.first().isManuallyOverridden)

        // Split first segment and then merge back
        val afterSplit = AutoFramingEngine.splitSegmentAt(overridden, firstSegId, 5L)
        assertEquals(overridden.size + 1, afterSplit.size)
        val afterMerge = AutoFramingEngine.mergeSegmentWithNext(afterSplit, afterSplit.first().id)
        assertEquals(overridden.size, afterMerge.size)

        // Save and reload from LocalClipTimelineStore
        val savedExt = initialExt.copy(segments = overridden)
        LocalClipTimelineStore.saveExtendedConfig(context, savedExt)
        val reloaded = LocalClipTimelineStore.loadExtendedConfigForClip(context, clip)
        assertEquals(VerticalLayoutType.LAYOUT_B_MULTIPLE_SPEAKERS, reloaded.segments.first().layoutType)
    }

    @Test
    fun `test 8 and 9 - in-app preview rendering and Save to Android Gallery verification`() = runBlocking {
        val clip = ClipCandidate(
            id = "clip_gallery_9",
            projectId = "proj_9",
            title = "Viral Clutch Save Test",
            startSec = 10L,
            endSec = 25L,
            subtitlesEnabled = true,
            subtitleText = "[00:00] UNBELIEVABLE CLUTCH!"
        )
        val ext = AutoFramingEngine.generateDefaultExtendedConfig(clip)

        // Test 8: Render composite vertical frame bitmap for in-app preview
        val previewBmp = GalleryVideoSaver.renderCompositeVerticalFrameBitmap(
            clip = clip,
            extendedConfig = ext,
            campaign = null,
            sourceFrame = null,
            timestampSec = 12L
        )
        assertNotNull(previewBmp)
        assertEquals(540, previewBmp.width)
        assertEquals(960, previewBmp.height)

        // Test 9: Save to Android Gallery & verify file exists, size > 0, and valid duration
        val saveResult = GalleryVideoSaver.saveClipToAndroidGallery(
            context = context,
            clip = clip,
            extendedConfig = ext,
            campaign = null
        )
        assertTrue("Expected GallerySaveResult.Success but got $saveResult", saveResult is GallerySaveResult.Success)
        val success = saveResult as GallerySaveResult.Success
        assertTrue(success.localMp4File.exists())
        assertTrue(success.sizeBytes > 0L)
        assertTrue(success.durationSec > 0L)
        assertTrue(success.verification.isValid)
        assertTrue(success.folderPath.contains("ClipForgeAI"))
    }

    @Test
    fun `test 10 11 12 and 13 - Logo ON OFF toggle and Hook Caption ON OFF independent of subtitles`() {
        val campaign = WhopCampaign(
            id = "camp_rules",
            name = "Pro Whop Bounty",
            brandName = "WhopPro",
            logoPlacement = "TOP_LEFT",
            requiredLogoAsset = "whop_pro_logo.png"
        )
        val clip = ClipCandidate(
            id = "clip_toggles_10",
            title = "WAIT FOR THE ENDING",
            startSec = 0L,
            endSec = 20L,
            subtitlesEnabled = true,
            subtitleText = "[00:00] Dialogue subtitle line one",
            logoPlacement = "TOP_LEFT"
        )
        val baseExt = AutoFramingEngine.generateDefaultExtendedConfig(clip, campaign = campaign)

        // Test 10: Turning Logo Placement OFF removes logo from FFmpeg command & triggers campaign conflict warning
        val extLogoOff = baseExt.copy(logoConfig = LogoPlacementConfig(enabled = false, position = "NONE"))
        val cmdLogoOff = AutoFramingEngine.buildDynamicFfmpegCommand(
            clip = clip.copy(logoPlacement = "NONE", extendedConfig = extLogoOff),
            extendedConfig = extLogoOff,
            campaign = campaign,
            sourceFileName = "input.mp4"
        )
        assertFalse("Logo input should be omitted when Logo Placement is OFF", cmdLogoOff.contains("whop_pro_logo.png"))
        assertFalse("Logo overlay filter should be omitted when Logo Placement is OFF", cmdLogoOff.contains("[logo_scaled]overlay="))
        val validationLogoOff = WhopRuleValidator.validateClip(clip.copy(logoPlacement = "NONE", extendedConfig = extLogoOff), campaign)
        assertTrue("Turning logo OFF when campaign requires logo must flag blocking conflict", validationLogoOff.blockingConflicts.isNotEmpty())

        // Test 11: Turning Logo Placement ON places logo in the selected position in FFmpeg command
        val extLogoOn = baseExt.copy(logoConfig = LogoPlacementConfig(enabled = true, position = "TOP_LEFT", sizePct = 15))
        val cmdLogoOn = AutoFramingEngine.buildDynamicFfmpegCommand(
            clip = clip.copy(logoPlacement = "TOP_LEFT", extendedConfig = extLogoOn),
            extendedConfig = extLogoOn,
            campaign = campaign,
            sourceFileName = "input.mp4"
        )
        assertTrue("Logo asset must be included when Logo Placement is ON", cmdLogoOn.contains("whop_pro_logo.png"))
        assertTrue("Logo overlay must be present when Logo Placement is ON", cmdLogoOn.contains("[logo_scaled]overlay="))

        // Test 12: Turning Hook Caption Placement OFF removes only the hook caption while leaving ordinary subtitles ON
        val extHookOff = baseExt.copy(hookConfig = HookCaptionConfig(enabled = false, hookText = "HOOK TEXT"))
        val cmdHookOffSubOn = AutoFramingEngine.buildDynamicFfmpegCommand(
            clip = clip.copy(subtitlesEnabled = true, extendedConfig = extHookOff),
            extendedConfig = extHookOff,
            campaign = campaign,
            sourceFileName = "input.mp4"
        )
        assertFalse("Hook drawtext must be omitted when Hook Caption is OFF", cmdHookOffSubOn.contains("drawtext="))
        assertTrue("Dialogue SRT subtitles must remain active when Hook Caption is OFF", cmdHookOffSubOn.contains("subtitles="))

        // Test 13: Turning Hook Caption Placement ON renders hook caption at selected position & duration even if subtitles are OFF
        val extHookOn = baseExt.copy(
            hookConfig = HookCaptionConfig(
                enabled = true,
                hookText = "STOP SCROLLING NOW",
                position = "TOP_SAFE",
                startSec = 0f,
                durationSec = 4.0f
            )
        )
        val cmdHookOnSubOff = AutoFramingEngine.buildDynamicFfmpegCommand(
            clip = clip.copy(subtitlesEnabled = false, extendedConfig = extHookOn),
            extendedConfig = extHookOn,
            campaign = campaign,
            sourceFileName = "input.mp4"
        )
        assertTrue("Hook drawtext must be present when Hook Caption is ON", cmdHookOnSubOff.contains("drawtext=text='STOP SCROLLING NOW'"))
        assertTrue("Hook duration window must be enforced", cmdHookOnSubOff.contains("between(t,0.0,4.0)"))
        assertFalse("Dialogue subtitles must be omitted when subtitlesEnabled is false", cmdHookOnSubOff.contains("subtitles="))
    }

    @Test
    fun `test 14 - every generated and exported viral clip is strictly over 20 seconds`() = runBlocking {
        val project = VideoProject(
            id = "proj_yt_viral",
            title = "IShowSpeed & Cole Palmer Meet After Chelsea Match",
            sourceFileName = "YouTube • IShowSpeed (abc123xyz)",
            durationSec = 240L,
            transcriptText = """
                [00:12] Speed: Yo Cole Palmer!! Cold Palmer celebration live right now, chat clip that!
                [00:48] Cole Palmer & Speed (Funny Moment): Wait no way you actually did the shiver celebration in the tunnel!
                [01:35] Speed (High Reaction): UNBELIEVABLE MOMENT! Look at that pass and reaction!
                [02:18] Closing reaction and breakdown for the stream!
            """.trimIndent()
        )
        val campaign = WhopCampaign(
            id = "camp_viral_20s",
            name = "Viral Clipping Bounty",
            minDurationSec = 22L,
            maxDurationSec = 55L
        )

        val generatedClips = WhopRuleValidator.extractClipsWithoutAiTokens(
            project = project,
            campaign = campaign,
            desiredClipCount = 3,
            includeVideoCaptions = true,
            viralCaptionStyle = "HORMOZI_BOLD"
        )

        assertEquals(3, generatedClips.size)
        generatedClips.forEach { clip ->
            assertTrue(
                "Expected every viral clip duration to be > 20s (>= 22s), but got ${clip.durationSec}s for ${clip.title}",
                clip.durationSec > 20L
            )
            // Verify multi-timestamp subtitle lines exist for dynamic progression across the clip
            assertTrue(
                "Expected multi-timestamp subtitle lines in ${clip.subtitleText}",
                clip.subtitleText.lines().size >= 3
            )
            val phraseStart = GalleryVideoSaver.resolveTimeSyncedSubtitlePhrase(clip, clip.startSec + 1L)
            val phraseLate = GalleryVideoSaver.resolveTimeSyncedSubtitlePhrase(clip, clip.startSec + 18L)
            assertTrue(phraseStart.isNotBlank())
            assertTrue(phraseLate.isNotBlank())
        }

        // Also verify GalleryVideoSaver enforces >20s duration on exported MP4
        val firstClip = generatedClips.first()
        val ext = firstClip.extendedConfig ?: AutoFramingEngine.generateDefaultExtendedConfig(firstClip, project, campaign)
        val saveRes = GalleryVideoSaver.saveClipToAndroidGallery(
            context = context,
            clip = firstClip,
            extendedConfig = ext,
            campaign = campaign
        )
        assertTrue("Expected GallerySaveResult.Success but got $saveRes", saveRes is GallerySaveResult.Success)
        val success = saveRes as GallerySaveResult.Success
        assertTrue("Expected saved MP4 duration > 20s, got ${success.durationSec}s", success.durationSec > 20L)
    }
}
