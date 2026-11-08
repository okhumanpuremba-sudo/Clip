package com.example.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CropPortrait
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.mutableLongStateOf
import com.example.data.model.ClipCandidate
import com.example.data.model.ExportQualityConfig
import com.example.data.model.HighlightCategory
import com.example.data.model.HookCaptionConfig
import com.example.data.model.LogoPlacementConfig
import com.example.data.model.VerticalLayoutType
import com.example.data.model.VideoProject
import com.example.data.model.WhopCampaign
import com.example.domain.AutoFramingEngine
import com.example.domain.WhopRuleValidator
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
import kotlin.math.roundToLong

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ClipReviewStudioScreen(
    clips: List<ClipCandidate>,
    projects: List<VideoProject>,
    campaigns: List<WhopCampaign>,
    selectedClipId: String?,
    selectedProjectId: String?,
    selectedCampaignId: String?,
    viralCaptionStyle: String = "HORMOZI_BOLD",
    onSetViralCaptionStyle: (String) -> Unit = {},
    singlePreviewFrame: Bitmap?,
    projectPreviewFramesMap: Map<String, List<Bitmap>> = emptyMap(),
    lastExportSummary: String?,
    lastExportFfmpegCommand: String?,
    lastExportSrtPreview: String?,
    lastGallerySaveMessage: String? = null,
    isSavingToGallery: Boolean = false,
    onSelectClip: (String, String, Long) -> Unit,
    onUpdateClip: (ClipCandidate) -> Unit,
    onAutoFixForCampaign: (ClipCandidate, WhopCampaign) -> Unit,
    onExportClip: (ClipCandidate, WhopCampaign?, VideoProject?) -> Unit,
    onOverrideSegment: (ClipCandidate, String, VerticalLayoutType?, Float?, List<Int>?) -> Unit = { _, _, _, _, _ -> },
    onSplitSegment: (ClipCandidate, String) -> Unit = { _, _ -> },
    onMergeSegmentWithNext: (ClipCandidate, String) -> Unit = { _, _ -> },
    onUpdateLogoToggle: (ClipCandidate, LogoPlacementConfig, WhopCampaign?) -> Unit = { _, _, _ -> },
    onUpdateHookToggle: (ClipCandidate, HookCaptionConfig) -> Unit = { _, _ -> },
    onUpdateExportConfig: (ClipCandidate, ExportQualityConfig) -> Unit = { _, _ -> },
    onSaveToGallery: (ClipCandidate, WhopCampaign?, VideoProject?) -> Unit = { _, _, _ -> },
    onRenameClip: (ClipCandidate, String) -> Unit = { _, _ -> },
    onDeleteClip: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val textToolbar = LocalTextToolbar.current
    var statusFilter by rememberSaveable { mutableStateOf("ALL") }
    var seoCopyFeedback by remember { mutableStateOf<String?>(null) }
    var approvalBlockedFeedback by remember { mutableStateOf<String?>(null) }

    val filteredClips = remember(clips, selectedProjectId, statusFilter) {
        clips.filter { clip ->
            (selectedProjectId == null || clip.projectId == selectedProjectId || clips.none { it.projectId == selectedProjectId }) &&
                (statusFilter == "ALL" || clip.reviewStatus == statusFilter)
        }
    }

    val activeClip = filteredClips.firstOrNull { it.id == selectedClipId } ?: filteredClips.firstOrNull()
    val parentProject = projects.firstOrNull { it.id == activeClip?.projectId } ?: projects.firstOrNull()
    val activeCampaign = campaigns.firstOrNull { it.id == activeClip?.campaignId?.ifBlank { selectedCampaignId } }
        ?: campaigns.firstOrNull { it.id == selectedCampaignId }
        ?: campaigns.firstOrNull()

    var editTitle by remember(activeClip?.id) { mutableStateOf(activeClip?.title.orEmpty()) }
    var editStartSec by remember(activeClip?.id) { mutableFloatStateOf((activeClip?.startSec ?: 0L).toFloat()) }
    var editEndSec by remember(activeClip?.id) {
        val rawStart = activeClip?.startSec ?: 0L
        val rawEnd = (activeClip?.endSec ?: 28L).coerceAtLeast(rawStart + 22L)
        mutableFloatStateOf(rawEnd.toFloat())
    }
    var editReframeX by remember(activeClip?.id) { mutableFloatStateOf((activeClip?.reframeFocusX ?: 50L).toFloat()) }
    var editSubtitlesOn by remember(activeClip?.id) { mutableStateOf(activeClip?.subtitlesEnabled ?: true) }
    var editSubtitleText by remember(activeClip?.id) { mutableStateOf(activeClip?.subtitleText.orEmpty()) }
    var editCaptionText by remember(activeClip?.id) { mutableStateOf(activeClip?.captionText.orEmpty()) }
    var editHashtags by remember(activeClip?.id) { mutableStateOf(activeClip?.hashtags.orEmpty()) }
    var editSeoTitle by remember(activeClip?.id) { mutableStateOf(activeClip?.seoTitle.orEmpty()) }
    var editSeoKeywords by remember(activeClip?.id) { mutableStateOf(activeClip?.seoKeywords.orEmpty()) }
    var editLogoPos by remember(activeClip?.id) { mutableStateOf(activeClip?.logoPlacement ?: "TOP_RIGHT") }
    var currentPlayheadSec by remember(activeClip?.id) { mutableLongStateOf(activeClip?.startSec ?: 0L) }

    LaunchedEffect(
        activeClip?.title,
        activeClip?.startSec,
        activeClip?.endSec,
        activeClip?.subtitlesEnabled,
        activeClip?.subtitleText,
        activeClip?.captionText,
        activeClip?.hashtags,
        activeClip?.seoTitle,
        activeClip?.seoKeywords,
        activeClip?.logoPlacement
    ) {
        if (activeClip != null) {
            editTitle = activeClip.title
            editStartSec = activeClip.startSec.toFloat()
            editEndSec = activeClip.endSec.coerceAtLeast(activeClip.startSec + 22L).toFloat()
            editReframeX = activeClip.reframeFocusX.toFloat()
            editSubtitlesOn = activeClip.subtitlesEnabled
            editSubtitleText = activeClip.subtitleText
            editCaptionText = activeClip.captionText
            editHashtags = activeClip.hashtags
            editSeoTitle = activeClip.seoTitle
            editSeoKeywords = activeClip.seoKeywords
            editLogoPos = activeClip.logoPlacement
            if (currentPlayheadSec !in activeClip.startSec..activeClip.endSec) {
                currentPlayheadSec = activeClip.startSec
            }
            approvalBlockedFeedback = null
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Filter Chips & Clip Selector Strip
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "CLIP REVIEW, 9:16 REFRAME & EXPORT STUDIO",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                    StatusPillBadge(text = "${filteredClips.size} CLIPS", color = ForgeAmber)
                }

                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("ALL", "PROPOSED", "APPROVED", "REJECTED", "EXPORTED").forEach { st ->
                        FilterChip(
                            selected = statusFilter == st,
                            onClick = { statusFilter = st },
                            label = { Text(st) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ForgeAmber.copy(alpha = 0.2f),
                                selectedLabelColor = ForgeAmber
                            )
                        )
                    }
                }

                if (filteredClips.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(filteredClips, key = { it.id }) { clip ->
                            val isSelected = clip.id == activeClip?.id
                            val cat = HighlightCategory.fromId(clip.category)
                            Card(
                                modifier = Modifier
                                    .width(235.dp)
                                    .clickable {
                                        onSelectClip(
                                            clip.id,
                                            parentProject?.sourceUri.orEmpty(),
                                            clip.startSec
                                        )
                                    }
                                    .testTag("clip_selector_${clip.id}"),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) StudioSurfaceElevated else StudioSurface
                                ),
                                border = BorderStroke(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) ForgeAmber else StudioBorder
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        StatusPillBadge(
                                            text = cat.label.uppercase(),
                                            color = Color(cat.badgeHex)
                                        )
                                        Text(
                                            text = "${clip.confidenceScore}%",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = VerifiedEmerald
                                        )
                                    }
                                    Text(
                                        text = clip.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = TextPrimary,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = "${clip.startSec}s–${clip.endSec}s (${clip.durationSec}s) • ${if (clip.subtitlesEnabled) "Captions ON" else "Captions OFF"}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (activeClip == null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = StudioSurface),
                    border = BorderStroke(1.dp, StudioBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CropPortrait,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(38.dp)
                        )
                        Text(
                            text = "No clips match the current filter. Run 'Process' on a YouTube/Twitch link or 'AI Clip' from the Projects tab.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            val activeExtendedConfig = activeClip.extendedConfig
                ?: AutoFramingEngine.generateDefaultExtendedConfig(
                    clip = activeClip,
                    project = parentProject,
                    campaign = activeCampaign
                )

            val activeProjectFrames = projectPreviewFramesMap[activeClip.projectId].orEmpty()
                .ifEmpty { listOfNotNull(singlePreviewFrame) }
            val resolvedSingleFrame = activeProjectFrames.firstOrNull() ?: singlePreviewFrame

            val liveEditedClip = activeClip.copy(
                title = editTitle,
                startSec = editStartSec.roundToLong(),
                endSec = editEndSec.roundToLong().coerceAtLeast(editStartSec.roundToLong() + 22L),
                reframeFocusX = editReframeX.roundToLong(),
                subtitlesEnabled = editSubtitlesOn,
                subtitleText = editSubtitleText,
                captionText = editCaptionText,
                hashtags = editHashtags,
                seoTitle = editSeoTitle,
                seoKeywords = editSeoKeywords,
                logoPlacement = if (activeExtendedConfig.logoConfig.enabled) editLogoPos else "NONE",
                extendedConfig = activeExtendedConfig
            )

            // 2A. In-App Vertical Video Player & Original vs. 9:16 Split Compare
            item {
                InAppVerticalVideoPlayerCard(
                    clip = liveEditedClip,
                    extendedConfig = activeExtendedConfig,
                    campaign = activeCampaign,
                    sourcePreviewFrame = resolvedSingleFrame,
                    sourcePreviewFrames = activeProjectFrames,
                    currentPlayheadSec = currentPlayheadSec,
                    onPlayheadChanged = { currentPlayheadSec = it }
                )
            }

            // 2B. AI Auto-Framing & Dynamic Layout Switching Timeline Editor
            item {
                EditableDynamicLayoutTimelineCard(
                    clip = liveEditedClip,
                    extendedConfig = activeExtendedConfig,
                    currentPlayheadSec = currentPlayheadSec,
                    onSeekPlayhead = { currentPlayheadSec = it },
                    onOverrideSegment = { segId, newLayout, newZoom, activeSpk ->
                        onOverrideSegment(liveEditedClip, segId, newLayout, newZoom, activeSpk)
                    },
                    onSplitSegment = { segId ->
                        onSplitSegment(liveEditedClip, segId)
                    },
                    onMergeSegmentWithNext = { segId ->
                        onMergeSegmentWithNext(liveEditedClip, segId)
                    }
                )
            }

            // 2C. Dedicated Logo Placement ON/OFF Toggle Card
            item {
                LogoPlacementToggleCard(
                    logoConfig = activeExtendedConfig.logoConfig,
                    activeCampaign = activeCampaign,
                    onUpdateLogoConfig = { updatedLogo ->
                        editLogoPos = if (updatedLogo.enabled) updatedLogo.position else "NONE"
                        onUpdateLogoToggle(liveEditedClip, updatedLogo, activeCampaign)
                    }
                )
            }

            // 2D. Dedicated Hook Caption Placement ON/OFF Toggle Card (Independent of Subtitles)
            item {
                HookCaptionPlacementToggleCard(
                    hookConfig = activeExtendedConfig.hookConfig,
                    subtitlesEnabled = editSubtitlesOn,
                    onUpdateHookConfig = { updatedHook ->
                        onUpdateHookToggle(liveEditedClip, updatedHook)
                    }
                )
            }

            // 2E. Save Finished Video to Android Gallery Card
            item {
                SaveToAndroidGalleryCard(
                    clip = liveEditedClip,
                    extendedConfig = activeExtendedConfig,
                    isSavingToGallery = isSavingToGallery,
                    lastGallerySaveMessage = lastGallerySaveMessage,
                    onUpdateExportConfig = { updatedExport ->
                        onUpdateExportConfig(liveEditedClip, updatedExport)
                    },
                    onSaveToGallery = {
                        onSaveToGallery(liveEditedClip, activeCampaign, parentProject)
                    }
                )
            }

            // 2. Interactive 9:16 Vertical Reframe & Safe-Zone Canvas Preview
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
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
                                    text = "9:16 VERTICAL AUTO-REFRAME & SAFE-ZONE VIEWPORT",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ForgeAmber
                                )
                                Text(
                                    text = liveEditedClip.title,
                                    style = MaterialTheme.typography.titleLarge,
                                    color = TextPrimary
                                )
                            }
                            StatusPillBadge(text = liveEditedClip.reviewStatus, color = TechnicalCyan)
                        }

                        Text(
                            text = "AI Selection Reason: ${liveEditedClip.reasonForSelection}",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )

                        // 16:9 Source with 9:16 Crop Window + Safe Zone Overlay
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(250.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(StudioObsidian),
                            contentAlignment = Alignment.Center
                        ) {
                            if (singlePreviewFrame != null) {
                                Image(
                                    bitmap = singlePreviewFrame.asImageBitmap(),
                                    contentDescription = "Source Video Frame Preview",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            val safeTopRatio = (activeCampaign?.safeZoneTopPct ?: 12L) / 100f
                            val safeBottomRatio = (activeCampaign?.safeZoneBottomPct ?: 20L) / 100f
                            val focusNorm = (editReframeX / 100f).coerceIn(0f, 1f)

                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val canvasW = size.width
                                val canvasH = size.height
                                val cropW = (canvasH * (9f / 16f)).coerceAtMost(canvasW * 0.65f)
                                val maxOffsetX = (canvasW - cropW).coerceAtLeast(0f)
                                val cropLeft = maxOffsetX * focusNorm

                                // Dim outside 9:16 crop window
                                drawRect(
                                    color = Color.Black.copy(alpha = 0.68f),
                                    topLeft = Offset(0f, 0f),
                                    size = Size(cropLeft, canvasH)
                                )
                                drawRect(
                                    color = Color.Black.copy(alpha = 0.68f),
                                    topLeft = Offset(cropLeft + cropW, 0f),
                                    size = Size(canvasW - (cropLeft + cropW), canvasH)
                                )

                                // Top & Bottom Platform Dead/Safe Zones inside 9:16 crop
                                val topSafeH = canvasH * safeTopRatio
                                val bottomSafeH = canvasH * safeBottomRatio
                                drawRect(
                                    color = WarningCoral.copy(alpha = 0.22f),
                                    topLeft = Offset(cropLeft, 0f),
                                    size = Size(cropW, topSafeH)
                                )
                                drawRect(
                                    color = WarningCoral.copy(alpha = 0.22f),
                                    topLeft = Offset(cropLeft, canvasH - bottomSafeH),
                                    size = Size(cropW, bottomSafeH)
                                )

                                // Dashed Safe Zone Guide Lines
                                val dash = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)
                                drawLine(
                                    color = TechnicalCyan,
                                    start = Offset(cropLeft, topSafeH),
                                    end = Offset(cropLeft + cropW, topSafeH),
                                    strokeWidth = 2f,
                                    pathEffect = dash
                                )
                                drawLine(
                                    color = TechnicalCyan,
                                    start = Offset(cropLeft, canvasH - bottomSafeH),
                                    end = Offset(cropLeft + cropW, canvasH - bottomSafeH),
                                    strokeWidth = 2f,
                                    pathEffect = dash
                                )

                                // 9:16 Active Viewport Frame
                                drawRect(
                                    color = ForgeAmber,
                                    topLeft = Offset(cropLeft, 0f),
                                    size = Size(cropW, canvasH),
                                    style = Stroke(width = 3.5f)
                                )
                            }

                            // Watermark Logo Badge Preview
                            if (editLogoPos != "NONE") {
                                val badgeAlignment = when (editLogoPos) {
                                    "TOP_LEFT" -> Alignment.TopStart
                                    "TOP_RIGHT" -> Alignment.TopEnd
                                    "BOTTOM_LEFT" -> Alignment.BottomStart
                                    else -> Alignment.BottomEnd
                                }
                                Surface(
                                    color = ForgeAmber.copy(alpha = 0.9f),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier
                                        .align(badgeAlignment)
                                        .padding(horizontal = 24.dp, vertical = 28.dp)
                                ) {
                                    Text(
                                        text = "LOGO: ${activeCampaign?.requiredLogoAsset ?: editLogoPos}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = StudioObsidian,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                    )
                                }
                            }

                            // Burned-in Viral Video Caption Overlay Preview (Toggled ON/OFF)
                            if (editSubtitlesOn) {
                                val (pillBg, borderCol, textCol) = when (viralCaptionStyle) {
                                    "GAMING_HYPE" -> Triple(Color(0xEB0E1726), TechnicalCyan, TechnicalCyan)
                                    "STORYTELLER" -> Triple(Color(0xEB18181B), VerifiedEmerald, Color.White)
                                    "CLEAN_MINIMAL" -> Triple(Color.Black.copy(alpha = 0.65f), Color.White.copy(alpha = 0.4f), Color.White)
                                    else -> Triple(Color.Black.copy(alpha = 0.88f), ForgeAmber, ForgeAmber)
                                }
                                Surface(
                                    color = pillBg,
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.5.dp, borderCol),
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(bottom = 54.dp, start = 28.dp, end = 28.dp)
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = viralCaptionStyle.replace("_", " "),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = borderCol
                                        )
                                        Text(
                                            text = editSubtitleText.ifBlank { editTitle.uppercase() },
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = textCol,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            } else {
                                Surface(
                                    color = Color.Black.copy(alpha = 0.6f),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(bottom = 54.dp)
                                ) {
                                    Text(
                                        text = "VIDEO CAPTIONS OFF (CLEAN FRAME)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextSecondary,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        // Reframe & Trim Sliders
                        val maxProjDuration = (parentProject?.durationSec ?: 300L).toFloat().coerceAtLeast(60f)
                        Text(
                            text = "9:16 Horizontal Subject Focus X: ${editReframeX.roundToLong()}% (Auto-Reframe Tracking)",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextPrimary
                        )
                        Slider(
                            value = editReframeX,
                            onValueChange = { editReframeX = it },
                            valueRange = 0f..100f
                        )

                        Text(
                            text = "Adjustable Viral Clip Trim (>20s Enforced): ${editStartSec.roundToLong()}s to ${editEndSec.roundToLong()}s (Duration: ${(editEndSec.roundToLong() - editStartSec.roundToLong()).coerceAtLeast(22L)}s)",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextPrimary
                        )
                        Slider(
                            value = editStartSec,
                            onValueChange = {
                                editStartSec = it
                                if (editEndSec < editStartSec + 22f) {
                                    editEndSec = (editStartSec + 26f).coerceAtMost(maxProjDuration)
                                }
                            },
                            valueRange = 0f..(maxProjDuration - 22f).coerceAtLeast(10f)
                        )
                        Slider(
                            value = editEndSec,
                            onValueChange = {
                                editEndSec = it.coerceAtLeast(editStartSec + 22f)
                            },
                            valueRange = 22f..maxProjDuration.coerceAtLeast(30f)
                        )
                    }
                }
            }

            // 3. Whop Campaign Compliance Audit Card
            if (activeCampaign != null) {
                item {
                    val report = WhopRuleValidator.validateClip(liveEditedClip, activeCampaign)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = StudioSurfaceElevated),
                        border = BorderStroke(
                            width = 1.5.dp,
                            color = if (report.allPassed) VerifiedEmerald else WarningCoral
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "WHOP CAMPAIGN RULE VALIDATION",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (report.allPassed) VerifiedEmerald else WarningCoral
                                    )
                                    Text(
                                        text = report.campaignName,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = TextPrimary
                                    )
                                }
                                StatusPillBadge(
                                    text = "${report.passedCount}/${report.checks.size} PASSED",
                                    color = if (report.allPassed) VerifiedEmerald else WarningCoral
                                )
                            }

                            report.checks.forEach { check ->
                                Row(
                                    verticalAlignment = Alignment.Top,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = if (check.passed) Icons.Default.CheckCircle else Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = if (check.passed) VerifiedEmerald else WarningCoral,
                                        modifier = Modifier
                                            .padding(top = 2.dp)
                                            .size(16.dp)
                                    )
                                    Column {
                                        Text(
                                            text = check.ruleName,
                                            style = MaterialTheme.typography.labelLarge,
                                            color = TextPrimary
                                        )
                                        Text(
                                            text = check.detail,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = TextSecondary
                                        )
                                    }
                                }
                            }

                            if (!report.allPassed) {
                                Button(
                                    onClick = { onAutoFixForCampaign(liveEditedClip, activeCampaign) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = TechnicalCyan,
                                        contentColor = StudioObsidian
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("autofix_whop_rules_button")
                                ) {
                                    Icon(Icons.Default.Build, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Auto-Fix Clip to Pass All Whop Campaign Rules", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            // 4. Viral Video Captions (ON/OFF Toggle + Presets), Hashtags & Copy SEOs Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = StudioSurface),
                    border = BorderStroke(1.dp, StudioBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Prominent Video Captions ON/OFF Toggle Header
                        Surface(
                            color = StudioObsidian,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(
                                1.dp,
                                if (editSubtitlesOn) VerifiedEmerald else StudioBorder
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Subtitles,
                                        contentDescription = null,
                                        tint = if (editSubtitlesOn) VerifiedEmerald else TextMuted
                                    )
                                    Column {
                                        Text(
                                            text = if (editSubtitlesOn) {
                                                "ADD CAPTIONS IN VIDEO: ON"
                                            } else {
                                                "ADD CAPTIONS IN VIDEO: OFF"
                                            },
                                            style = MaterialTheme.typography.titleSmall,
                                            color = if (editSubtitlesOn) VerifiedEmerald else TextSecondary,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = if (editSubtitlesOn) {
                                                "Viral burned-in video captions enabled on 9:16 export"
                                            } else {
                                                "Video captions disabled — exports clean video frame"
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TextSecondary
                                        )
                                    }
                                }
                                Switch(
                                    checked = editSubtitlesOn,
                                    onCheckedChange = { enabled ->
                                        editSubtitlesOn = enabled
                                        onUpdateClip(liveEditedClip.copy(subtitlesEnabled = enabled))
                                    },
                                    modifier = Modifier.testTag("review_video_captions_switch")
                                )
                            }
                        }

                        // Cool Viral Video Caption Style Generator Bar
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Whatshot,
                                    contentDescription = null,
                                    tint = ForgeAmber,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "1-TAP COOL VIRAL VIDEO CAPTION STYLES:",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ForgeAmber,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val presets = listOf(
                                    "HORMOZI_BOLD" to "HORMOZI BOLD",
                                    "GAMING_HYPE" to "GAMING HYPE",
                                    "STORYTELLER" to "STORYTELLER HOOK",
                                    "CLEAN_MINIMAL" to "CLEAN MINIMAL"
                                )
                                presets.forEach { (styleId, label) ->
                                    FilterChip(
                                        selected = viralCaptionStyle == styleId,
                                        onClick = {
                                            onSetViralCaptionStyle(styleId)
                                            val styledSub = WhopRuleValidator.formatViralVideoCaption(
                                                styleId = styleId,
                                                title = editTitle,
                                                currentSubtitle = editSubtitleText
                                            )
                                            val styledPost = WhopRuleValidator.formatViralPostCaption(
                                                styleId = styleId,
                                                title = editTitle,
                                                cta = activeCampaign?.mandatoryCaptionCta.orEmpty(),
                                                hashtags = editHashtags
                                            )
                                            editSubtitleText = styledSub
                                            editCaptionText = styledPost
                                            editSubtitlesOn = true
                                            onUpdateClip(
                                                liveEditedClip.copy(
                                                    subtitlesEnabled = true,
                                                    subtitleText = styledSub,
                                                    captionText = styledPost
                                                )
                                            )
                                        },
                                        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = ForgeAmber.copy(alpha = 0.25f),
                                            selectedLabelColor = ForgeAmber
                                        ),
                                        modifier = Modifier.testTag("review_viral_style_$styleId")
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = editTitle,
                            onValueChange = { editTitle = it },
                            label = { Text("Clip Hook Title") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (editSubtitlesOn) {
                            OutlinedTextField(
                                value = editSubtitleText,
                                onValueChange = { editSubtitleText = it },
                                label = { Text("Viral Burned-In Video Captions (.SRT & 9:16 Overlay)") },
                                minLines = 2,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("video_caption_input")
                            )
                        }

                        OutlinedTextField(
                            value = editCaptionText,
                            onValueChange = { editCaptionText = it },
                            label = { Text("Viral Social Post Caption & Mandatory CTA") },
                            minLines = 2,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = editHashtags,
                                onValueChange = { editHashtags = it },
                                label = { Text("Hashtags") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = editLogoPos,
                                onValueChange = { editLogoPos = it },
                                label = { Text("Logo Zone") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // SEO Metadata Section + Copy SEO Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "VIRAL SEO METADATA (SHORTS / TIKTOK / REELS)",
                                style = MaterialTheme.typography.labelSmall,
                                color = TechnicalCyan,
                                fontWeight = FontWeight.Bold
                            )
                            if (seoCopyFeedback != null) {
                                StatusPillBadge(text = seoCopyFeedback.orEmpty(), color = VerifiedEmerald)
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = editSeoTitle,
                                onValueChange = { editSeoTitle = it },
                                label = { Text("SEO Search Title") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = editSeoKeywords,
                                onValueChange = { editSeoKeywords = it },
                                label = { Text("SEO Keywords") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // Dedicated Copy SEOs Action Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    textToolbar.hide()
                                    val seoBundle = buildString {
                                        appendLine("SEO Title: ${editSeoTitle.ifBlank { editTitle }}")
                                        appendLine("SEO Keywords: $editSeoKeywords")
                                        appendLine("Viral Caption: $editCaptionText")
                                        append("Hashtags: $editHashtags")
                                    }
                                    clipboardManager.setText(AnnotatedString(seoBundle))
                                    seoCopyFeedback = "COPIED ALL SEOS!"
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = TechnicalCyan,
                                    contentColor = StudioObsidian
                                ),
                                modifier = Modifier
                                    .weight(1.3f)
                                    .height(48.dp)
                                    .testTag("copy_seo_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy all SEO metadata",
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Copy SEOs", fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = {
                                    textToolbar.hide()
                                    clipboardManager.setText(AnnotatedString(editSeoKeywords))
                                    seoCopyFeedback = "KEYWORDS COPIED!"
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("copy_seo_keywords_button")
                            ) {
                                Text("Copy Keywords", color = TechnicalCyan)
                            }

                            OutlinedButton(
                                onClick = {
                                    textToolbar.hide()
                                    val postBundle = "${editCaptionText.trim()} ${editHashtags.trim()}".trim()
                                    clipboardManager.setText(AnnotatedString(postBundle))
                                    seoCopyFeedback = "CAPTION COPIED!"
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("copy_viral_caption_button")
                            ) {
                                Text("Copy Caption", color = ForgeAmber)
                            }
                        }

                        // Editorial Review Actions: Save / Approve / Reject / Export
                        if (approvalBlockedFeedback != null) {
                            Surface(
                                color = WarningCoral.copy(alpha = 0.16f),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, WarningCoral),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("approval_blocked_warning")
                            ) {
                                Text(
                                    text = approvalBlockedFeedback.orEmpty(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = WarningCoral,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    approvalBlockedFeedback = null
                                    onUpdateClip(liveEditedClip.copy(reviewStatus = "REJECTED"))
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("reject_clip_button")
                            ) {
                                Icon(Icons.Default.Close, contentDescription = null, tint = WarningCoral, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Reject", color = WarningCoral)
                            }

                            Button(
                                onClick = {
                                    val report = activeCampaign?.let { WhopRuleValidator.validateClip(liveEditedClip, it) }
                                    if (report != null && report.blockingConflicts.isNotEmpty()) {
                                        val firstConflict = report.blockingConflicts.first()
                                        approvalBlockedFeedback = "Final Approval Blocked by Campaign Rule: ${firstConflict.ruleName} — ${firstConflict.detail} Tap 'Auto-Fix Clip' above or resolve conflict."
                                    } else {
                                        approvalBlockedFeedback = null
                                        onUpdateClip(liveEditedClip.copy(reviewStatus = "APPROVED"))
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = VerifiedEmerald,
                                    contentColor = StudioObsidian
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("approve_clip_button")
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Approve", fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = {
                                    onExportClip(liveEditedClip, activeCampaign, parentProject)
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ForgeAmber,
                                    contentColor = StudioObsidian
                                ),
                                modifier = Modifier
                                    .weight(1.2f)
                                    .height(48.dp)
                                    .testTag("export_clip_button")
                            ) {
                                Icon(Icons.Default.IosShare, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Export Package", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // 5. Export Output & Pluggable FFmpeg Worker Inspector
            if (lastExportSummary != null || lastExportFfmpegCommand != null) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = StudioSurfaceElevated),
                        border = BorderStroke(1.5.dp, TechnicalCyan)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "EXPORT ARTIFACTS & PLUGGABLE BACKEND WORKER SPEC",
                                style = MaterialTheme.typography.labelSmall,
                                color = TechnicalCyan
                            )
                            if (lastExportSummary != null) {
                                Text(
                                    text = lastExportSummary,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = VerifiedEmerald
                                )
                            }
                            if (lastExportFfmpegCommand != null) {
                                Text(
                                    text = "Generated 9:16 Dynamic FFmpeg Filtergraph Command (Termux & Backend Render Worker):",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary
                                )
                                Surface(
                                    color = StudioObsidian,
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, StudioBorder),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = lastExportFfmpegCommand,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = ForgeAmber,
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }
                            }
                            if (!lastExportSrtPreview.isNullOrBlank()) {
                                Text(
                                    text = "Generated .SRT Subtitle File Preview:",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary
                                )
                                Surface(
                                    color = StudioObsidian,
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, StudioBorder),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = lastExportSrtPreview,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextPrimary,
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 6. Full Clip Library Explorer & Manager
            item {
                ClipLibraryExplorerCard(
                    clips = clips,
                    projects = projects,
                    campaigns = campaigns,
                    activeClipId = activeClip.id,
                    onSelectAndReopenClip = { chosenClip, chosenProj ->
                        onSelectClip(
                            chosenClip.id,
                            chosenProj?.sourceUri.orEmpty(),
                            chosenClip.startSec
                        )
                    },
                    onRenameClip = onRenameClip,
                    onDeleteClipConfirmed = onDeleteClip,
                    onSaveClipToGallery = onSaveToGallery
                )
            }
        }
    }
}
