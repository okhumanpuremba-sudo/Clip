package com.example.ui.screens

import android.graphics.Bitmap
import android.widget.VideoView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.ClipCandidate
import com.example.data.model.ClipExtendedConfig
import com.example.data.model.ExportQualityConfig
import com.example.data.model.HighlightCategory
import com.example.data.model.VideoProject
import com.example.data.model.WhopCampaign
import com.example.domain.AutoFramingEngine
import com.example.ui.theme.ForgeAmber
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioObsidian
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.TechnicalCyan
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VerifiedEmerald
import com.example.ui.theme.WarningCoral
import com.example.util.GalleryVideoSaver
import java.io.File
import kotlinx.coroutines.delay
import kotlin.math.roundToLong

/**
 * Requirement 5: In-App Video Player with Play, Pause, Seek, Replay, Full-Screen view,
 * and Original 16:9 vs. Edited 9:16 Vertical Split Comparison.
 */
@Composable
fun InAppVerticalVideoPlayerCard(
    clip: ClipCandidate,
    extendedConfig: ClipExtendedConfig,
    campaign: WhopCampaign?,
    sourcePreviewFrame: Bitmap?,
    sourcePreviewFrames: List<Bitmap> = emptyList(),
    currentPlayheadSec: Long,
    onPlayheadChanged: (Long) -> Unit
) {
    var isPlaying by rememberSaveable(clip.id) { mutableStateOf(false) }
    var compareOriginalMode by rememberSaveable(clip.id) { mutableStateOf(false) }
    var isFullScreen by rememberSaveable(clip.id) { mutableStateOf(false) }

    val startSec = clip.startSec
    val endSec = clip.endSec.coerceAtLeast(startSec + 22L)
    val activeSegment = AutoFramingEngine.segmentAtTimestamp(extendedConfig.segments, currentPlayheadSec)

    val activeFrameForPlayhead = remember(sourcePreviewFrames, sourcePreviewFrame, currentPlayheadSec, startSec, endSec) {
        if (sourcePreviewFrames.size > 1) {
            val span = (endSec - startSec).coerceAtLeast(1L).toFloat()
            val norm = ((currentPlayheadSec - startSec).coerceAtLeast(0L).toFloat() / span).coerceIn(0f, 0.999f)
            val idx = (norm * sourcePreviewFrames.size).toInt().coerceIn(0, sourcePreviewFrames.lastIndex)
            sourcePreviewFrames[idx]
        } else {
            sourcePreviewFrames.firstOrNull() ?: sourcePreviewFrame
        }
    }

    // Advance playhead automatically while playing
    LaunchedEffect(isPlaying, currentPlayheadSec, startSec, endSec) {
        if (isPlaying) {
            delay(1000L)
            val next = currentPlayheadSec + 1L
            if (next >= endSec) {
                isPlaying = false
                onPlayheadChanged(startSec)
            } else {
                onPlayheadChanged(next)
            }
        }
    }

    val renderedVerticalBitmap = remember(
        clip.id,
        clip.title,
        clip.subtitlesEnabled,
        clip.subtitleText,
        extendedConfig,
        currentPlayheadSec,
        activeFrameForPlayhead
    ) {
        GalleryVideoSaver.renderCompositeVerticalFrameBitmap(
            clip = clip,
            extendedConfig = extendedConfig,
            campaign = campaign,
            sourceFrame = activeFrameForPlayhead,
            timestampSec = currentPlayheadSec,
            outWidth = 480,
            outHeight = 854
        )
    }

    val localMp4File = remember(extendedConfig.localRenderedMp4Path) {
        extendedConfig.localRenderedMp4Path.takeIf { it.isNotBlank() }?.let { File(it) }?.takeIf { it.exists() && it.length() > 256L }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("in_app_video_player_card"),
        colors = CardDefaults.cardColors(containerColor = StudioSurface),
        border = BorderStroke(1.dp, StudioBorder)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "IN-APP VERTICAL VIDEO PLAYER & LAYOUT PREVIEW",
                        style = MaterialTheme.typography.labelSmall,
                        color = ForgeAmber,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = activeSegment?.let {
                            "Active @ ${currentPlayheadSec}s: ${it.layoutType.shortBadge}"
                        } ?: "9:16 Vertical Preview",
                        style = MaterialTheme.typography.bodySmall,
                        color = TechnicalCyan
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = { compareOriginalMode = !compareOriginalMode },
                        modifier = Modifier.testTag("compare_original_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Compare,
                            contentDescription = "Compare Original vs Edited",
                            tint = if (compareOriginalMode) ForgeAmber else TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (compareOriginalMode) "Comparing 16:9 vs 9:16" else "Compare Original",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (compareOriginalMode) ForgeAmber else TextPrimary
                        )
                    }

                    IconButton(
                        onClick = { isFullScreen = true },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("fullscreen_preview_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Fullscreen,
                            contentDescription = "Full Screen Preview",
                            tint = TechnicalCyan
                        )
                    }
                }
            }

            // Video Viewport (Supports Single 9:16 or Side-by-Side Compare 16:9 Original + 9:16 Edited)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(290.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(StudioObsidian)
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (compareOriginalMode) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "ORIGINAL 16:9 SOURCE",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(150.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF141B29)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (sourcePreviewFrame != null) {
                                Image(
                                    bitmap = sourcePreviewFrame.asImageBitmap(),
                                    contentDescription = "Original 16:9 Source Frame",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Text(
                                    text = "16:9 Source Frame\n(${clip.startSec}s–${clip.endSec}s)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = renderedVerticalBitmap.asImageBitmap(),
                        contentDescription = "Finished 9:16 Vertical Video Preview",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(10.dp))
                    )
                }
            }

            // Transport Controls: Play/Pause, Replay, Seek Slider & Timestamp Readout
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { isPlaying = !isPlaying },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isPlaying) TechnicalCyan else ForgeAmber,
                        contentColor = StudioObsidian
                    ),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("player_play_pause_button")
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play"
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isPlaying) "Pause" else "Play",
                        fontWeight = FontWeight.Bold
                    )
                }

                OutlinedButton(
                    onClick = {
                        isPlaying = false
                        onPlayheadChanged(startSec)
                    },
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("player_replay_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Replay,
                        contentDescription = "Replay",
                        tint = TextPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Replay", color = TextPrimary)
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${currentPlayheadSec}s / ${endSec}s",
                            style = MaterialTheme.typography.labelSmall,
                            color = ForgeAmber,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = activeSegment?.layoutType?.id ?: "LAYOUT_A",
                            style = MaterialTheme.typography.labelSmall,
                            color = TechnicalCyan
                        )
                    }
                    Slider(
                        value = currentPlayheadSec.toFloat().coerceIn(startSec.toFloat(), endSec.toFloat()),
                        onValueChange = {
                            isPlaying = false
                            onPlayheadChanged(it.roundToLong().coerceIn(startSec, endSec))
                        },
                        valueRange = startSec.toFloat()..endSec.toFloat().coerceAtLeast(startSec + 1f),
                        modifier = Modifier.testTag("player_seek_slider")
                    )
                }
            }

            if (localMp4File != null) {
                Text(
                    text = "Verified local MP4 ready for native playback & gallery export: ${localMp4File.name} (${localMp4File.length() / 1024} KB)",
                    style = MaterialTheme.typography.labelSmall,
                    color = VerifiedEmerald
                )
            }
        }
    }

    if (isFullScreen) {
        Dialog(
            onDismissRequest = { isFullScreen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = StudioObsidian
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = clip.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary
                            )
                            Text(
                                text = "Full-Screen 9:16 Vertical Preview • ${activeSegment?.layoutType?.title.orEmpty()}",
                                style = MaterialTheme.typography.labelSmall,
                                color = ForgeAmber
                            )
                        }
                        OutlinedButton(onClick = { isFullScreen = false }) {
                            Text("Exit Full Screen", color = TextPrimary)
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (localMp4File != null) {
                            AndroidView(
                                factory = { ctx ->
                                    VideoView(ctx).apply {
                                        setVideoPath(localMp4File.absolutePath)
                                        setOnPreparedListener { mp ->
                                            mp.isLooping = true
                                            start()
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Image(
                                bitmap = renderedVerticalBitmap.asImageBitmap(),
                                contentDescription = "Full Screen 9:16 Preview",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { isPlaying = !isPlaying },
                            colors = ButtonDefaults.buttonColors(containerColor = ForgeAmber, contentColor = StudioObsidian)
                        ) {
                            Text(if (isPlaying) "Pause" else "Play", fontWeight = FontWeight.Bold)
                        }
                        Slider(
                            value = currentPlayheadSec.toFloat().coerceIn(startSec.toFloat(), endSec.toFloat()),
                            onValueChange = { onPlayheadChanged(it.roundToLong()) },
                            valueRange = startSec.toFloat()..endSec.toFloat().coerceAtLeast(startSec + 1f),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Requirement 6: Save Videos to the Android Gallery Card with Resolution, FPS, Quality Preset,
 * Non-Overwriting File Versioning, and Verified Saved Path Display.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SaveToAndroidGalleryCard(
    clip: ClipCandidate,
    extendedConfig: ClipExtendedConfig,
    isSavingToGallery: Boolean,
    lastGallerySaveMessage: String?,
    onUpdateExportConfig: (ExportQualityConfig) -> Unit,
    onSaveToGallery: () -> Unit
) {
    val exportCfg = extendedConfig.exportConfig

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("save_to_gallery_card"),
        colors = CardDefaults.cardColors(containerColor = StudioSurfaceElevated),
        border = BorderStroke(1.5.dp, VerifiedEmerald)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "SAVE FINISHED 9:16 VIDEO TO ANDROID GALLERY",
                        style = MaterialTheme.typography.labelSmall,
                        color = VerifiedEmerald,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Exports verified MP4 to Movies/ClipForgeAI for TikTok, Instagram Reels, YouTube Shorts & WhatsApp",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
                StatusPillBadge(
                    text = "${exportCfg.resolution} • ${exportCfg.fps}FPS",
                    color = VerifiedEmerald
                )
            }

            // Resolution & Frame Rate Selectors
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("1080x1920" to "1080p Full HD", "720x1280" to "720p Fast").forEach { (res, label) ->
                        FilterChip(
                            selected = exportCfg.resolution == res,
                            onClick = { onUpdateExportConfig(exportCfg.copy(resolution = res)) },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = VerifiedEmerald.copy(alpha = 0.22f),
                                selectedLabelColor = VerifiedEmerald
                            ),
                            modifier = Modifier.testTag("export_res_$res")
                        )
                    }
                    listOf(30, 60).forEach { fps ->
                        FilterChip(
                            selected = exportCfg.fps == fps,
                            onClick = { onUpdateExportConfig(exportCfg.copy(fps = fps)) },
                            label = { Text("${fps}fps", style = MaterialTheme.typography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = TechnicalCyan.copy(alpha = 0.22f),
                                selectedLabelColor = TechnicalCyan
                            ),
                            modifier = Modifier.testTag("export_fps_$fps")
                        )
                    }
                }
            }

            // Quality Preset & Overwrite Protection
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    "HIGH_QUALITY" to "High Quality",
                    "BALANCED_FAST" to "Balanced",
                    "TERMUX_LIGHTWEIGHT" to "Termux Lightweight"
                ).forEach { (presetId, label) ->
                    FilterChip(
                        selected = exportCfg.qualityPreset == presetId,
                        onClick = { onUpdateExportConfig(exportCfg.copy(qualityPreset = presetId)) },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                    )
                }
                FilterChip(
                    selected = exportCfg.overwriteBehavior == "VERSIONED",
                    onClick = {
                        val next = if (exportCfg.overwriteBehavior == "VERSIONED") "OVERWRITE" else "VERSIONED"
                        onUpdateExportConfig(exportCfg.copy(overwriteBehavior = next))
                    },
                    label = {
                        Text(
                            text = if (exportCfg.overwriteBehavior == "VERSIONED") {
                                "Safe Versioning (_v2, _v3)"
                            } else {
                                "Overwrite Existing"
                            },
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                )
            }

            Button(
                onClick = onSaveToGallery,
                enabled = !isSavingToGallery,
                colors = ButtonDefaults.buttonColors(
                    containerColor = VerifiedEmerald,
                    contentColor = StudioObsidian
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("save_to_gallery_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = "Save to Android Gallery",
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isSavingToGallery) {
                        "Rendering & Verifying MP4 for Gallery..."
                    } else {
                        "Download / Save to Android Gallery (.MP4)"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            val savedPathInfo = extendedConfig.savedGalleryPath.ifBlank { lastGallerySaveMessage.orEmpty() }
            if (savedPathInfo.isNotBlank()) {
                Surface(
                    color = StudioObsidian,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, VerifiedEmerald),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("gallery_saved_path_banner")
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "VERIFIED ANDROID GALLERY EXPORT",
                            style = MaterialTheme.typography.labelSmall,
                            color = VerifiedEmerald,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (extendedConfig.savedGalleryPath.isNotBlank()) {
                                "Saved Path: ${extendedConfig.savedGalleryPath}"
                            } else {
                                savedPathInfo
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = TextPrimary
                        )
                    }
                }
            }
        }
    }
}

/**
 * Requirement 5: Functional Clip Library with Filtering by Campaign/Project/Status,
 * Rename Dialog, Delete with Confirmation Dialog, and Reopen for Editing.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ClipLibraryExplorerCard(
    clips: List<ClipCandidate>,
    projects: List<VideoProject>,
    campaigns: List<WhopCampaign>,
    activeClipId: String?,
    onSelectAndReopenClip: (ClipCandidate, VideoProject?) -> Unit,
    onRenameClip: (ClipCandidate, String) -> Unit,
    onDeleteClipConfirmed: (String) -> Unit,
    onSaveClipToGallery: (ClipCandidate, WhopCampaign?, VideoProject?) -> Unit
) {
    var campaignFilterId by rememberSaveable { mutableStateOf("ALL") }
    var clipToRename by remember { mutableStateOf<ClipCandidate?>(null) }
    var renameInput by remember { mutableStateOf("") }
    var clipToDelete by remember { mutableStateOf<ClipCandidate?>(null) }

    val filteredLibraryClips = remember(clips, campaignFilterId) {
        clips.filter { clip ->
            campaignFilterId == "ALL" || clip.campaignId == campaignFilterId
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("clip_library_explorer_card"),
        colors = CardDefaults.cardColors(containerColor = StudioSurface),
        border = BorderStroke(1.dp, StudioBorder)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.VideoLibrary,
                        contentDescription = null,
                        tint = TechnicalCyan
                    )
                    Column {
                        Text(
                            text = "CLIP LIBRARY & GALLERY EXPORT MANAGER",
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Filter by campaign, rename, delete with confirmation, reopen timeline, or save to Gallery",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                }
                StatusPillBadge(text = "${filteredLibraryClips.size} IN LIBRARY", color = TechnicalCyan)
            }

            if (campaigns.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = campaignFilterId == "ALL",
                        onClick = { campaignFilterId = "ALL" },
                        label = { Text("All Campaigns", style = MaterialTheme.typography.labelSmall) }
                    )
                    campaigns.forEach { camp ->
                        FilterChip(
                            selected = campaignFilterId == camp.id,
                            onClick = { campaignFilterId = camp.id },
                            label = { Text(camp.name.take(20), style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }

            filteredLibraryClips.forEach { clip ->
                val proj = projects.firstOrNull { it.id == clip.projectId }
                val camp = campaigns.firstOrNull { it.id == clip.campaignId }
                val ext = clip.extendedConfig
                val cat = HighlightCategory.fromId(clip.category)
                val isSelected = clip.id == activeClipId
                val layoutSummary = ext?.segments?.joinToString(" → ") { it.layoutType.id }
                    ?: "LAYOUT_A"

                Surface(
                    color = if (isSelected) StudioSurfaceElevated else StudioObsidian,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(
                        width = if (isSelected) 1.5.dp else 1.dp,
                        color = if (isSelected) ForgeAmber else StudioBorder
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("library_clip_card_${clip.id}")
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                StatusPillBadge(text = cat.label.uppercase(), color = Color(cat.badgeHex))
                                StatusPillBadge(text = clip.reviewStatus, color = TechnicalCyan)
                            }
                            Text(
                                text = "${clip.startSec}s–${clip.endSec}s (${clip.durationSec}s)",
                                style = MaterialTheme.typography.labelSmall,
                                color = ForgeAmber,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Text(
                            text = clip.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = "Source: ${proj?.title ?: "VOD Project"} (${proj?.sourceFileName ?: "stream.mp4"}) • Campaign: ${camp?.name ?: "Default"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )

                        Text(
                            text = "Dynamic Layouts: $layoutSummary • Logo: ${if (ext?.logoConfig?.enabled != false && clip.logoPlacement != "NONE") clip.logoPlacement else "OFF"} • Hook: ${if (ext?.hookConfig?.enabled != false) "ON" else "OFF"} • Subtitles: ${if (clip.subtitlesEnabled) "ON" else "OFF"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = VerifiedEmerald
                        )

                        if (!ext?.savedGalleryPath.isNullOrBlank()) {
                            Text(
                                text = "Gallery Export: ${ext?.savedGalleryPath}",
                                style = MaterialTheme.typography.labelSmall,
                                color = TechnicalCyan
                            )
                        }
                        if (!ext?.lastRenderError.isNullOrBlank()) {
                            Text(
                                text = "Last Render Error: ${ext?.lastRenderError}",
                                style = MaterialTheme.typography.labelSmall,
                                color = WarningCoral
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OutlinedButton(
                                onClick = { onSelectAndReopenClip(clip, proj) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Reopen", style = MaterialTheme.typography.labelSmall, color = ForgeAmber)
                            }

                            OutlinedButton(
                                onClick = {
                                    clipToRename = clip
                                    renameInput = clip.title
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("rename_clip_button_${clip.id}")
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = "Rename", modifier = Modifier.size(14.dp), tint = TextPrimary)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Rename", style = MaterialTheme.typography.labelSmall, color = TextPrimary)
                            }

                            OutlinedButton(
                                onClick = { clipToDelete = clip },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("delete_clip_button_${clip.id}")
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(14.dp), tint = WarningCoral)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Delete", style = MaterialTheme.typography.labelSmall, color = WarningCoral)
                            }

                            Button(
                                onClick = { onSaveClipToGallery(clip, camp, proj) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = VerifiedEmerald,
                                    contentColor = StudioObsidian
                                ),
                                modifier = Modifier
                                    .weight(1.2f)
                                    .testTag("library_save_gallery_${clip.id}")
                            ) {
                                Text("Gallery", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }

    if (clipToRename != null) {
        AlertDialog(
            onDismissRequest = { clipToRename = null },
            title = { Text("Rename Clip") },
            text = {
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    label = { Text("New Clip Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        clipToRename?.let { onRenameClip(it, renameInput) }
                        clipToRename = null
                    }
                ) {
                    Text("Save Name")
                }
            },
            dismissButton = {
                TextButton(onClick = { clipToRename = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (clipToDelete != null) {
        AlertDialog(
            onDismissRequest = { clipToDelete = null },
            title = { Text("Delete Clip?") },
            text = {
                Text("Are you sure you want to permanently delete '${clipToDelete?.title}' from your Clip Library?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        clipToDelete?.id?.let { onDeleteClipConfirmed(it) }
                        clipToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = WarningCoral, contentColor = StudioObsidian)
                ) {
                    Text("Delete Clip", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { clipToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}
