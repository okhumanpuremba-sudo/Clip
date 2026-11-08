package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class VideoProject(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val sourceFileName: String = "",
    val sourceUri: String = "",
    val durationSec: Long = 60L,
    val width: Long = 1920L,
    val height: Long = 1080L,
    val transcriptText: String = "",
    val campaignId: String = "",
    val status: String = "READY",
    val clipCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class WhopCampaign(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val brandName: String = "",
    val payoutPer1kViews: String = "$1.50 / 1K views",
    val targetPlatform: String = "TIKTOK",
    val preferredAspectRatio: String = "9:16",
    val minDurationSec: Long = 22L,
    val maxDurationSec: Long = 60L,
    val logoPlacement: String = "TOP_RIGHT",
    val requiredLogoAsset: String = "brand_watermark.png",
    @get:Exclude val customLogoLocalPath: String = "",
    val safeZoneTopPct: Long = 12L,
    val safeZoneBottomPct: Long = 20L,
    val requiredHashtags: String = "#whop #clips",
    val mandatoryCaptionCta: String = "Check link in bio",
    val seoGuidance: String = "Front-load primary keyword in first 40 chars",
    val minConfidenceScore: Long = 75L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ClipCandidate(
    @DocumentId val id: String = "",
    val userId: String = "",
    val projectId: String = "",
    val campaignId: String = "",
    val title: String = "",
    val category: String = "GAMING_HIGHLIGHT",
    val reasonForSelection: String = "",
    val confidenceScore: Long = 85L,
    val startSec: Long = 0L,
    val endSec: Long = 28L,
    val aspectRatio: String = "9:16",
    val reframeFocusX: Long = 50L,
    val subtitlesEnabled: Boolean = true,
    val subtitleText: String = "",
    val captionText: String = "",
    val hashtags: String = "",
    val seoTitle: String = "",
    val seoKeywords: String = "",
    val logoPlacement: String = "TOP_RIGHT",
    val reviewStatus: String = "PROPOSED",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
    @get:Exclude val extendedConfig: ClipExtendedConfig? = null
) {
    val durationSec: Long
        get() = (endSec - startSec).coerceAtLeast(1L)
}

enum class VerticalLayoutType(
    val id: String,
    val shortBadge: String,
    val title: String,
    val description: String,
    val topSectionWeight: Float,
    val bottomSectionWeight: Float
) {
    LAYOUT_A_STREAMER_AND_CONTENT(
        id = "LAYOUT_A",
        shortBadge = "Layout A • Split Cam + Content",
        title = "Layout A — Streamer & Watched Content",
        description = "Streamer/webcam in top section (38%), primary game or watched video in bottom section (62%). Keeps face uncut without black bars.",
        topSectionWeight = 0.38f,
        bottomSectionWeight = 0.62f
    ),
    LAYOUT_B_MULTIPLE_SPEAKERS(
        id = "LAYOUT_B",
        shortBadge = "Layout B • Multi-Speaker Split",
        title = "Layout B — Multiple Speakers + Content",
        description = "2+ speakers arranged in top horizontal split (42%), main gameplay or watched content in bottom section (58%).",
        topSectionWeight = 0.42f,
        bottomSectionWeight = 0.58f
    ),
    LAYOUT_C_SPEAKER_ONLY(
        id = "LAYOUT_C",
        shortBadge = "Layout C • Full-Frame Speaker",
        title = "Layout C — Speaker-Only Full Frame",
        description = "Full-frame 9:16 crop on speaker/streamer when no gameplay or watched screen is relevant.",
        topSectionWeight = 1.0f,
        bottomSectionWeight = 0.0f
    ),
    LAYOUT_D_FULLSCREEN_CONTENT_SMALL_WEBCAM(
        id = "LAYOUT_D",
        shortBadge = "Layout D • Small Cam Boost",
        title = "Layout D — Full-Screen Content + Enlarged Corner Cam",
        description = "Independently detects small corner webcam (enlarged into top 35%) and full-screen gameplay/HUD (bottom 65%).",
        topSectionWeight = 0.35f,
        bottomSectionWeight = 0.65f
    );

    companion object {
        fun fromId(id: String): VerticalLayoutType =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) || it.name.equals(id, ignoreCase = true) }
                ?: LAYOUT_A_STREAMER_AND_CONTENT
    }
}

data class NormalizedRect(
    val leftPct: Float = 0f,
    val topPct: Float = 0f,
    val widthPct: Float = 100f,
    val heightPct: Float = 100f
) {
    fun clamped(): NormalizedRect {
        val w = widthPct.coerceIn(8f, 100f)
        val h = heightPct.coerceIn(8f, 100f)
        val l = leftPct.coerceIn(0f, (100f - w).coerceAtLeast(0f))
        val t = topPct.coerceIn(0f, (100f - h).coerceAtLeast(0f))
        return NormalizedRect(l, t, w, h)
    }
}

data class DetectedSpeakerRegion(
    val speakerId: Int,
    val label: String,
    val rect: NormalizedRect,
    val isTalking: Boolean = true,
    val audioEnergyDb: Float = -12f,
    val faceConfidence: Float = 0.92f
)

data class LayoutSegment(
    val id: String = "",
    val startSec: Long = 0L,
    val endSec: Long = 10L,
    val layoutType: VerticalLayoutType = VerticalLayoutType.LAYOUT_A_STREAMER_AND_CONTENT,
    val sceneType: String = "GAMEPLAY_WITH_WEBCAM",
    val sceneDescription: String = "Streamer webcam + primary screen content",
    val detectedSpeakers: List<DetectedSpeakerRegion> = emptyList(),
    val activeSpeakerIds: List<Int> = listOf(1),
    val primaryContentRect: NormalizedRect? = NormalizedRect(0f, 0f, 100f, 100f),
    val webcamCropRect: NormalizedRect = NormalizedRect(72f, 4f, 24f, 24f),
    val zoomLevel: Float = 1.0f,
    val confidence: Float = 0.90f,
    val needsManualReview: Boolean = false,
    val manualReviewReason: String = "",
    val isManuallyOverridden: Boolean = false,
    val transitionDurationMs: Int = 250,
    val audioContinuityVerified: Boolean = true
) {
    val durationSec: Long
        get() = (endSec - startSec).coerceAtLeast(1L)
}

data class LogoPlacementConfig(
    val enabled: Boolean = true,
    val position: String = "TOP_RIGHT",
    val sizePct: Int = 14,
    val marginDp: Int = 16,
    val opacityPct: Int = 95,
    val adaptivePlacement: Boolean = false,
    val saveAsDefault: Boolean = true
)

data class HookCaptionConfig(
    val enabled: Boolean = true,
    val hookText: String = "",
    val position: String = "TOP_SAFE",
    val startSec: Float = 0f,
    val durationSec: Float = 3.5f,
    val fontStyle: String = "HORMOZI_IMPACT",
    val fontSizeSp: Int = 22,
    val textColorHex: String = "#F59E0B",
    val outlineColorHex: String = "#000000",
    val backgroundAlphaPct: Int = 85,
    val alignment: String = "CENTER",
    val adaptiveAvoidFaces: Boolean = true
)

data class ExportQualityConfig(
    val resolution: String = "1080x1920",
    val fps: Int = 30,
    val qualityPreset: String = "HIGH_QUALITY",
    val overwriteBehavior: String = "VERSIONED"
)

data class ScoreWeightsConfig(
    val hookStrengthWeight: Int = 20,
    val entertainmentImpactWeight: Int = 20,
    val storyClarityPayoffWeight: Int = 15,
    val curiositySurpriseWeight: Int = 15,
    val audioVisualQualityWeight: Int = 10,
    val captionPresentationWeight: Int = 10,
    val replayPotentialWeight: Int = 10
) {
    val totalWeight: Int
        get() = (hookStrengthWeight + entertainmentImpactWeight + storyClarityPayoffWeight +
            curiositySurpriseWeight + audioVisualQualityWeight + captionPresentationWeight +
            replayPotentialWeight).coerceAtLeast(1)
}

data class HistoricalPerformanceCalibration(
    val sourceLabel: String = "Authorized Creator Analytics",
    val retentionRatePct: Float = 0f,
    val avgWatchTimeSec: Float = 0f,
    val completionRatePct: Float = 0f,
    val shareRatePct: Float = 0f,
    val calibrationAdjustmentPts: Int = 0
)

data class ViewerAppealScoreBreakdown(
    val rank: Int = 1,
    val estimatedViewerAppealScore: Int = 88,
    val hookStrengthScore: Int = 90,
    val entertainmentImpactScore: Int = 88,
    val storyClarityPayoffScore: Int = 86,
    val curiositySurpriseScore: Int = 85,
    val audioVisualQualityScore: Int = 90,
    val captionPotentialScore: Int = 92,
    val replayPotentialScore: Int = 84,
    val openingSecondStrengthScore: Int = 91,
    val contextDependencyLevel: String = "LOW", // LOW, MEDIUM, HIGH
    val missingContextWarning: String = "",
    val recommendedPlatform: String = "YouTube Shorts",
    val recommendedDurationSec: Long = 28L,
    val whySelectedExplanation: String = "",
    val setupContextBefore: String = "",
    val climaxMomentDuring: String = "",
    val payoffContextAfter: String = "",
    val weightsUsed: ScoreWeightsConfig = ScoreWeightsConfig(),
    val historicalCalibration: HistoricalPerformanceCalibration? = null
)

data class TimedCaptionWord(
    val word: String,
    val startSec: Float,
    val endSec: Float,
    val confidence: Float = 0.96f,
    val isKeyword: Boolean = false,
    val isSlang: Boolean = false,
    val isUncertain: Boolean = false
)

data class CaptionPhraseGroup(
    val groupIndex: Int,
    val startSec: Float,
    val endSec: Float,
    val words: List<TimedCaptionWord>
)

data class VideoStreamProbeReport(
    val pathOrUri: String = "",
    val exists: Boolean = false,
    val sizeBytes: Long = 0L,
    val durationMs: Long = 0L,
    val width: Int = 1080,
    val height: Int = 1920,
    val videoCodec: String = "video/avc",
    val frameRateFps: Float = 30f,
    val isVariableFrameRate: Boolean = false,
    val vfrNote: String = "Constant Frame Rate (CFR) verified",
    val lowFpsLimitationNote: String = "",
    val hasAudioTrack: Boolean = true,
    val audioCodec: String = "audio/mp4a-latm",
    val audioSampleRateHz: Int = 44100,
    val audioChannels: Int = 2,
    val uniqueMotionFramesCount: Int = 24,
    val repeatedFrameRatio: Float = 0.0f,
    val hasFrozenOrRepeatedSequence: Boolean = false,
    val frozenDiagnosticMessage: String = "Continuous motion verified across clip timeline",
    val audioVideoSyncVerified: Boolean = true,
    val comparedWithSourcePassed: Boolean = true,
    val passesIntegrityCheck: Boolean = true
)

data class ClipExtendedConfig(
    val clipId: String = "",
    val projectId: String = "",
    val segments: List<LayoutSegment> = emptyList(),
    val logoConfig: LogoPlacementConfig = LogoPlacementConfig(),
    val hookConfig: HookCaptionConfig = HookCaptionConfig(),
    val exportConfig: ExportQualityConfig = ExportQualityConfig(),
    val hysteresisMinSegmentSec: Long = 3L,
    val transitionDurationMs: Int = 250,
    val lightweightModeAndroid: Boolean = true,
    val savedGalleryUri: String = "",
    val savedGalleryPath: String = "",
    val localRenderedMp4Path: String = "",
    val lastRenderError: String = "",
    val checkpointStage: String = "ANALYZED",
    val viewerAppealScore: ViewerAppealScoreBreakdown? = null,
    val wordTimestamps: List<TimedCaptionWord> = emptyList(),
    val integrityReport: VideoStreamProbeReport? = null
) {
    val primaryLayoutType: VerticalLayoutType
        get() = segments.maxByOrNull { it.durationSec }?.layoutType
            ?: VerticalLayoutType.LAYOUT_A_STREAMER_AND_CONTENT

    val hasMultipleDynamicLayouts: Boolean
        get() = segments.map { it.layoutType }.distinct().size > 1

    val hasLowConfidenceSegments: Boolean
        get() = segments.any { it.needsManualReview }
}

@IgnoreExtraProperties
data class ApiUsageLog(
    @DocumentId val id: String = "",
    val userId: String = "",
    val providerId: String = "",
    val modelName: String = "",
    val operation: String = "",
    val latencyMs: Long = 0L,
    val estimatedTokens: Long = 0L,
    val retryCount: Long = 0L,
    val success: Boolean = true,
    val errorMessage: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

enum class HighlightCategory(val id: String, val label: String, val badgeHex: Long) {
    FUNNY_MOMENT("FUNNY_MOMENT", "Funny Moment", 0xFFF59E0B),
    REACTION("REACTION", "High Reaction", 0xFFEC4899),
    GAMING_HIGHLIGHT("GAMING_HIGHLIGHT", "Gaming Highlight", 0xFF38BDF8),
    CLUTCH_PLAY("CLUTCH_PLAY", "Clutch Play", 0xFF10B981),
    EXCITING_MOMENT("EXCITING_MOMENT", "Exciting Peak", 0xFFA855F7),
    MEMORABLE_DIALOGUE("MEMORABLE_DIALOGUE", "Memorable Quote", 0xFF6366F1);

    companion object {
        fun fromId(id: String): HighlightCategory =
            entries.firstOrNull { it.id == id } ?: GAMING_HIGHLIGHT
    }
}

data class CampaignValidationCheck(
    val ruleName: String,
    val passed: Boolean,
    val detail: String,
    val isBlockingConflict: Boolean = false
)

data class CampaignValidationReport(
    val campaignName: String,
    val checks: List<CampaignValidationCheck>
) {
    val allPassed: Boolean get() = checks.all { it.passed }
    val passedCount: Int get() = checks.count { it.passed }
    val blockingConflicts: List<CampaignValidationCheck> get() = checks.filter { !it.passed && it.isBlockingConflict }
}

enum class ProviderVerificationState(val label: String) {
    VERIFIED_WORKING("VERIFIED WORKING (LIVE TEST PASSED)"),
    UNTESTED("UNTESTED — Run Live Test Before Relying on Backup"),
    FAILED("TEST FAILED — Check Endpoint or Quota")
}

data class AiProviderConfig(
    val id: String,
    val name: String,
    val modelOrEndpoint: String,
    val roleLabel: String,
    val costNotes: String,
    val isBuiltInFirebase: Boolean,
    val verificationState: ProviderVerificationState = ProviderVerificationState.UNTESTED,
    val lastTestedLatencyMs: Long? = null,
    val lastTestMessage: String = "Not tested in this session yet."
)

/**
 * Reusable Campaign Preset combining Whop campaign validation rules, hashtags,
 * aspect ratio preferences, viral video caption style, captions ON/OFF state,
 * logo ON/OFF state, hook caption ON/OFF state, and default clip count (1–10).
 */
data class CampaignPreset(
    val id: String = "",
    val userId: String = "",
    val presetName: String = "",
    val campaignName: String = "",
    val brandName: String = "",
    val payoutPer1kViews: String = "$2.00 / 1K views",
    val targetPlatform: String = "TIKTOK",
    val preferredAspectRatio: String = "9:16",
    val minDurationSec: Long = 22L,
    val maxDurationSec: Long = 55L,
    val logoPlacement: String = "TOP_RIGHT",
    val logoEnabled: Boolean = true,
    val requiredLogoAsset: String = "whop_partner_watermark.png",
    val customLogoLocalPath: String = "",
    val safeZoneTopPct: Long = 12L,
    val safeZoneBottomPct: Long = 20L,
    val requiredHashtags: String = "#whop #clipping #viral",
    val mandatoryCaptionCta: String = "Check link in bio",
    val seoGuidance: String = "Front-load primary keyword in first 40 chars",
    val minConfidenceScore: Long = 78L,
    val viralCaptionStyle: String = "HORMOZI_BOLD",
    val includeVideoCaptions: Boolean = true,
    val includeHookCaption: Boolean = true,
    val defaultHookPosition: String = "TOP_SAFE",
    val defaultClipCount: Int = 3
) {
    fun toWhopCampaign(existingCampaignId: String = ""): WhopCampaign = WhopCampaign(
        id = existingCampaignId,
        userId = userId,
        name = campaignName.ifBlank { presetName },
        brandName = brandName.ifBlank { "Creator Brand" },
        payoutPer1kViews = payoutPer1kViews,
        targetPlatform = targetPlatform,
        preferredAspectRatio = preferredAspectRatio.ifBlank { "9:16" },
        minDurationSec = minDurationSec.coerceAtLeast(22L),
        maxDurationSec = maxDurationSec.coerceAtLeast(30L),
        logoPlacement = if (logoEnabled) logoPlacement else "NONE",
        requiredLogoAsset = requiredLogoAsset,
        customLogoLocalPath = customLogoLocalPath,
        safeZoneTopPct = safeZoneTopPct,
        safeZoneBottomPct = safeZoneBottomPct,
        requiredHashtags = requiredHashtags,
        mandatoryCaptionCta = mandatoryCaptionCta,
        seoGuidance = seoGuidance,
        minConfidenceScore = minConfidenceScore
    )
}


