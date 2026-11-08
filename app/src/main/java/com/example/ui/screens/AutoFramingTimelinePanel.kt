package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMerge
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.filled.AutoAwesomeMotion
import androidx.compose.material.icons.filled.BrandingWatermark
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Title
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.ClipCandidate
import com.example.data.model.ClipExtendedConfig
import com.example.data.model.HookCaptionConfig
import com.example.data.model.LayoutSegment
import com.example.data.model.LogoPlacementConfig
import com.example.data.model.VerticalLayoutType
import com.example.data.model.WhopCampaign
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
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Editable Visual Detection & Dynamic Layout Switching Timeline Panel.
 * Displays:
 * - Multi-segment layout timeline with start/end timestamps (Layout A, B, C, D)
 * - Detected speakers, audio energy, and scene boundaries
 * - Manual segment override (Layout A/B/C/D, Zoom, Split, Merge, Speaker selection, Low-Confidence review)
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditableDynamicLayoutTimelineCard(
    clip: ClipCandidate,
    extendedConfig: ClipExtendedConfig,
    currentPlayheadSec: Long,
    onSeekPlayhead: (Long) -> Unit,
    onOverrideSegment: (
        segmentId: String,
        newLayout: VerticalLayoutType?,
        newZoom: Float?,
        activeSpeakers: List<Int>?
    ) -> Unit,
    onSplitSegment: (String) -> Unit,
    onMergeSegmentWithNext: (String) -> Unit
) {
    val segments = extendedConfig.segments
    var selectedSegmentId by remember(clip.id, segments.size) {
        mutableStateOf(segments.firstOrNull()?.id.orEmpty())
    }
    val activeSegment = segments.firstOrNull { it.id == selectedSegmentId }
        ?: segments.firstOrNull { currentPlayheadSec >= it.startSec && currentPlayheadSec < it.endSec }
        ?: segments.firstOrNull()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("dynamic_layout_timeline_card"),
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
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesomeMotion,
                        contentDescription = null,
                        tint = ForgeAmber,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = "AI AUTO-FRAMING & DYNAMIC LAYOUT TIMELINE",
                            style = MaterialTheme.typography.labelSmall,
                            color = ForgeAmber,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${segments.size} Scene Segments • Hysteresis ≥${extendedConfig.hysteresisMinSegmentSec}s • Smooth ${extendedConfig.transitionDurationMs}ms Transitions",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
                StatusPillBadge(
                    text = if (extendedConfig.hasMultipleDynamicLayouts) "DYNAMIC SWITCHING" else "SINGLE LAYOUT",
                    color = TechnicalCyan
                )
            }

            // Visual Multi-Segment Timeline Bar
            if (segments.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Tap any segment on the timeline to inspect or override its 9:16 layout, speaker, or zoom:",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(StudioObsidian)
                            .padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        segments.forEachIndexed { idx, seg ->
                            val isSelected = seg.id == activeSegment?.id
                            val segColor = layoutBadgeColor(seg.layoutType)
                            val weight = seg.durationSec.toFloat().coerceAtLeast(1f)
                            Box(
                                modifier = Modifier
                                    .weight(weight)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(segColor.copy(alpha = if (isSelected) 0.35f else 0.16f))
                                    .border(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (seg.needsManualReview) WarningCoral else if (isSelected) segColor else segColor.copy(alpha = 0.5f),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable {
                                        selectedSegmentId = seg.id
                                        onSeekPlayhead(seg.startSec)
                                    }
                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                                    .testTag("timeline_segment_bar_$idx")
                            ) {
                                Column(
                                    verticalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxHeight()
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = seg.layoutType.id.replace("_", " "),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = segColor,
                                            fontWeight = FontWeight.Bold
                                        )
                                        if (seg.needsManualReview) {
                                            Icon(
                                                imageVector = Icons.Default.Warning,
                                                contentDescription = "Needs Manual Review",
                                                tint = WarningCoral,
                                                modifier = Modifier.size(13.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = "${seg.startSec}s–${seg.endSec}s",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextPrimary
                                    )
                                }
                            }
                        }
                    }

                    // Overlay Timing Indicators (Captions, Hook, Logo)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Hook Overlay: ${if (extendedConfig.hookConfig.enabled) "ON (0s–${String.format(Locale.US, "%.1fs", extendedConfig.hookConfig.durationSec)})" else "OFF"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (extendedConfig.hookConfig.enabled) ForgeAmber else TextMuted
                        )
                        Text(
                            text = "Subtitles: ${if (clip.subtitlesEnabled) "SYNCED (${clip.startSec}s–${clip.endSec}s)" else "OFF"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (clip.subtitlesEnabled) TechnicalCyan else TextMuted
                        )
                        Text(
                            text = "Logo: ${if (extendedConfig.logoConfig.enabled) extendedConfig.logoConfig.position else "OFF"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (extendedConfig.logoConfig.enabled) VerifiedEmerald else TextMuted
                        )
                    }
                }
            }

            // Selected Segment Inspector & Manual Override Controls
            if (activeSegment != null) {
                Surface(
                    color = StudioObsidian,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(
                        1.dp,
                        if (activeSegment.needsManualReview) WarningCoral else StudioBorder
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "SEGMENT (${activeSegment.startSec}s–${activeSegment.endSec}s) • ${activeSegment.sceneType}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = layoutBadgeColor(activeSegment.layoutType),
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = activeSegment.sceneDescription,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextPrimary
                                )
                            }
                            StatusPillBadge(
                                text = if (activeSegment.isManuallyOverridden) {
                                    "MANUAL OVERRIDE"
                                } else {
                                    "CONF ${(activeSegment.confidence * 100).roundToInt()}%"
                                },
                                color = if (activeSegment.needsManualReview) WarningCoral else VerifiedEmerald
                            )
                        }

                        if (activeSegment.needsManualReview) {
                            Surface(
                                color = WarningCoral.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, WarningCoral),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = WarningCoral,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = activeSegment.manualReviewReason.ifBlank {
                                            "Low confidence detection — safe fallback crop applied. Select a layout below to confirm."
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = WarningCoral,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }

                        // 4 Automatic Vertical Layouts Selector (Manual Override)
                        Text(
                            text = "Override Segment Vertical 9:16 Layout:",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            VerticalLayoutType.entries.forEach { layoutOption ->
                                val isCurrent = activeSegment.layoutType == layoutOption
                                FilterChip(
                                    selected = isCurrent,
                                    onClick = {
                                        onOverrideSegment(
                                            activeSegment.id,
                                            layoutOption,
                                            activeSegment.zoomLevel,
                                            activeSegment.activeSpeakerIds
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = layoutOption.shortBadge,
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = layoutBadgeColor(layoutOption).copy(alpha = 0.24f),
                                        selectedLabelColor = layoutBadgeColor(layoutOption)
                                    ),
                                    modifier = Modifier.testTag("override_segment_${layoutOption.id}")
                                )
                            }
                        }

                        // Detected Speakers & Active Speaker Selection
                        if (activeSegment.detectedSpeakers.isNotEmpty()) {
                            Text(
                                text = "Detected Speakers & Audio Activity (${activeSegment.detectedSpeakers.size} visible):",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                activeSegment.detectedSpeakers.forEach { spk ->
                                    val isSelectedSpeaker = spk.speakerId in activeSegment.activeSpeakerIds
                                    FilterChip(
                                        selected = isSelectedSpeaker,
                                        onClick = {
                                            val updatedIds = if (isSelectedSpeaker && activeSegment.activeSpeakerIds.size > 1) {
                                                activeSegment.activeSpeakerIds - spk.speakerId
                                            } else {
                                                (activeSegment.activeSpeakerIds + spk.speakerId).distinct()
                                            }
                                            onOverrideSegment(
                                                activeSegment.id,
                                                activeSegment.layoutType,
                                                activeSegment.zoomLevel,
                                                updatedIds
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.Person,
                                                contentDescription = null,
                                                modifier = Modifier.size(15.dp)
                                            )
                                        },
                                        label = {
                                            Text(
                                                text = "${spk.label} (${spk.audioEnergyDb.roundToInt()}dB)",
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        }
                                    )
                                }
                            }
                        }

                        // Segment Zoom Override Slider
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Speaker / Webcam Crop Zoom: ${String.format(Locale.US, "%.1fx", activeSegment.zoomLevel)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextPrimary
                            )
                            Text(
                                text = "Webcam Region: (${activeSegment.webcamCropRect.leftPct.roundToInt()}%, ${activeSegment.webcamCropRect.topPct.roundToInt()}%)",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                        }
                        Slider(
                            value = activeSegment.zoomLevel,
                            onValueChange = { newZoom ->
                                onOverrideSegment(
                                    activeSegment.id,
                                    activeSegment.layoutType,
                                    newZoom,
                                    activeSegment.activeSpeakerIds
                                )
                            },
                            valueRange = 1.0f..2.2f,
                            modifier = Modifier.testTag("segment_zoom_slider")
                        )

                        // Split / Merge Segment Actions
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { onSplitSegment(activeSegment.id) },
                                enabled = activeSegment.durationSec >= 2L,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("split_segment_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.CallSplit,
                                    contentDescription = null,
                                    tint = TechnicalCyan,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Split Segment", color = TechnicalCyan, style = MaterialTheme.typography.labelMedium)
                            }

                            val segIndex = segments.indexOfFirst { it.id == activeSegment.id }
                            val canMergeNext = segIndex >= 0 && segIndex < segments.lastIndex
                            OutlinedButton(
                                onClick = { onMergeSegmentWithNext(activeSegment.id) },
                                enabled = canMergeNext,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("merge_segment_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.CallMerge,
                                    contentDescription = null,
                                    tint = ForgeAmber,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Merge Next", color = ForgeAmber, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Requirement 7: Dedicated ON/OFF Toggle for Logo Placement with Position, Size, Margin,
 * Opacity, Default Persistence, and Whop Campaign Conflict Warnings.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LogoPlacementToggleCard(
    logoConfig: LogoPlacementConfig,
    activeCampaign: WhopCampaign?,
    onUpdateLogoConfig: (LogoPlacementConfig) -> Unit
) {
    val campaignRequiresLogo = activeCampaign != null && activeCampaign.logoPlacement != "NONE"
    val campaignProhibitsLogo = activeCampaign != null && activeCampaign.logoPlacement == "NONE"
    val hasConflict = (campaignRequiresLogo && (!logoConfig.enabled || logoConfig.position != activeCampaign?.logoPlacement)) ||
        (campaignProhibitsLogo && logoConfig.enabled)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("logo_placement_card"),
        colors = CardDefaults.cardColors(containerColor = StudioSurface),
        border = BorderStroke(
            width = if (hasConflict) 1.5.dp else 1.dp,
            color = if (hasConflict) WarningCoral else StudioBorder
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                color = StudioObsidian,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(
                    1.dp,
                    if (logoConfig.enabled) VerifiedEmerald else StudioBorder
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
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.BrandingWatermark,
                            contentDescription = null,
                            tint = if (logoConfig.enabled) VerifiedEmerald else TextMuted
                        )
                        Column {
                            Text(
                                text = if (logoConfig.enabled) {
                                    "LOGO PLACEMENT: ON (${logoConfig.position})"
                                } else {
                                    "LOGO PLACEMENT: OFF (NO WATERMARK)"
                                },
                                style = MaterialTheme.typography.titleSmall,
                                color = if (logoConfig.enabled) VerifiedEmerald else TextSecondary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (logoConfig.enabled) {
                                    "Brand logo overlaid on 9:16 preview & rendered video"
                                } else {
                                    "Logo removed from preview & FFmpeg render — full frame space"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                        }
                    }
                    Switch(
                        checked = logoConfig.enabled,
                        onCheckedChange = { enabled ->
                            val resolvedPos = if (enabled && logoConfig.position == "NONE") {
                                activeCampaign?.logoPlacement?.takeIf { it != "NONE" } ?: "TOP_RIGHT"
                            } else {
                                logoConfig.position
                            }
                            onUpdateLogoConfig(
                                logoConfig.copy(
                                    enabled = enabled,
                                    position = resolvedPos
                                )
                            )
                        },
                        modifier = Modifier.testTag("logo_placement_toggle_switch")
                    )
                }
            }

            if (hasConflict && activeCampaign != null) {
                Surface(
                    color = WarningCoral.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, WarningCoral),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = WarningCoral,
                            modifier = Modifier.size(18.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "WHOP CAMPAIGN LOGO RULE CONFLICT",
                                style = MaterialTheme.typography.labelSmall,
                                color = WarningCoral,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (campaignProhibitsLogo) {
                                    "Campaign '${activeCampaign.name}' prohibits brand logos (NONE), but Logo Placement is ON."
                                } else {
                                    "Campaign '${activeCampaign.name}' requires '${activeCampaign.requiredLogoAsset}' at ${activeCampaign.logoPlacement}."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = TextPrimary
                            )
                        }
                        Button(
                            onClick = {
                                onUpdateLogoConfig(
                                    logoConfig.copy(
                                        enabled = campaignRequiresLogo,
                                        position = activeCampaign.logoPlacement
                                    )
                                )
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = WarningCoral,
                                contentColor = StudioObsidian
                            )
                        ) {
                            Text("Sync Rule", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            if (logoConfig.enabled) {
                Text(
                    text = "Logo Safe-Zone Position:",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("TOP_LEFT", "TOP_RIGHT", "TOP_CENTER", "BOTTOM_LEFT", "BOTTOM_RIGHT").forEach { pos ->
                        FilterChip(
                            selected = logoConfig.position == pos,
                            onClick = { onUpdateLogoConfig(logoConfig.copy(position = pos)) },
                            label = { Text(pos.replace("_", " "), style = MaterialTheme.typography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = VerifiedEmerald.copy(alpha = 0.22f),
                                selectedLabelColor = VerifiedEmerald
                            ),
                            modifier = Modifier.testTag("logo_pos_chip_$pos")
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Size: ${logoConfig.sizePct}% of width",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextPrimary
                    )
                    Text(
                        text = "Margin: ${logoConfig.marginDp}dp • Opacity: ${logoConfig.opacityPct}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
                Slider(
                    value = logoConfig.sizePct.toFloat(),
                    onValueChange = { onUpdateLogoConfig(logoConfig.copy(sizePct = it.roundToInt().coerceIn(8, 30))) },
                    valueRange = 8f..30f
                )
            }
        }
    }
}

/**
 * Requirement 8: Dedicated ON/OFF Toggle for Hook Caption Placement
 * (Strictly independent of ordinary dialogue subtitles).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HookCaptionPlacementToggleCard(
    hookConfig: HookCaptionConfig,
    subtitlesEnabled: Boolean,
    onUpdateHookConfig: (HookCaptionConfig) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("hook_caption_card"),
        colors = CardDefaults.cardColors(containerColor = StudioSurface),
        border = BorderStroke(1.dp, StudioBorder)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                color = StudioObsidian,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(
                    1.dp,
                    if (hookConfig.enabled) ForgeAmber else StudioBorder
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
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Title,
                            contentDescription = null,
                            tint = if (hookConfig.enabled) ForgeAmber else TextMuted
                        )
                        Column {
                            Text(
                                text = if (hookConfig.enabled) {
                                    "HOOK CAPTION PLACEMENT: ON (${hookConfig.position})"
                                } else {
                                    "HOOK CAPTION PLACEMENT: OFF"
                                },
                                style = MaterialTheme.typography.titleSmall,
                                color = if (hookConfig.enabled) ForgeAmber else TextSecondary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Independent of Dialogue Subtitles (Subtitles are currently ${if (subtitlesEnabled) "ON" else "OFF"})",
                                style = MaterialTheme.typography.labelSmall,
                                color = TechnicalCyan
                            )
                        }
                    }
                    Switch(
                        checked = hookConfig.enabled,
                        onCheckedChange = { enabled ->
                            onUpdateHookConfig(hookConfig.copy(enabled = enabled))
                        },
                        modifier = Modifier.testTag("hook_caption_toggle_switch")
                    )
                }
            }

            if (hookConfig.enabled) {
                OutlinedTextField(
                    value = hookConfig.hookText,
                    onValueChange = { onUpdateHookConfig(hookConfig.copy(hookText = it)) },
                    label = { Text("Opening Viral Hook Headline Banner") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("hook_caption_text_input")
                )

                Text(
                    text = "Hook Safe-Zone Placement (Avoids Speaker Face & HUD):",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val positions = listOf(
                        "TOP_SAFE" to "Top Safe Zone",
                        "UPPER_MIDDLE" to "Upper Middle",
                        "ABOVE_SPLIT_LINE" to "Split Divider",
                        "CENTER_BANNER" to "Center Banner"
                    )
                    positions.forEach { (posId, label) ->
                        FilterChip(
                            selected = hookConfig.position == posId,
                            onClick = { onUpdateHookConfig(hookConfig.copy(position = posId)) },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ForgeAmber.copy(alpha = 0.24f),
                                selectedLabelColor = ForgeAmber
                            ),
                            modifier = Modifier.testTag("hook_pos_chip_$posId")
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Hook Display Duration: ${String.format(Locale.US, "%.1fs", hookConfig.durationSec)} (from 0.0s)",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextPrimary
                    )
                    Text(
                        text = "Font: ${hookConfig.fontStyle.replace("_", " ")} (${hookConfig.fontSizeSp}sp)",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
                Slider(
                    value = hookConfig.durationSec,
                    onValueChange = { onUpdateHookConfig(hookConfig.copy(durationSec = it.coerceIn(1.5f, 10.0f))) },
                    valueRange = 1.5f..10.0f
                )
            }
        }
    }
}

fun layoutBadgeColor(layoutType: VerticalLayoutType): Color = when (layoutType) {
    VerticalLayoutType.LAYOUT_A_STREAMER_AND_CONTENT -> ForgeAmber
    VerticalLayoutType.LAYOUT_B_MULTIPLE_SPEAKERS -> Color(0xFFA855F7)
    VerticalLayoutType.LAYOUT_C_SPEAKER_ONLY -> VerifiedEmerald
    VerticalLayoutType.LAYOUT_D_FULLSCREEN_CONTENT_SMALL_WEBCAM -> TechnicalCyan
}
