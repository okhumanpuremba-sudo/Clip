package com.example.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BookmarkAdded
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.CampaignPreset
import com.example.data.model.ClipCandidate
import com.example.data.model.VideoProject
import com.example.data.model.WhopCampaign
import com.example.domain.StreamPlatform
import com.example.domain.UrlVideoIngestEngine
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DashboardAndProjectsScreen(
    projects: List<VideoProject>,
    campaigns: List<WhopCampaign>,
    campaignPresets: List<CampaignPreset> = emptyList(),
    activePresetId: String? = null,
    clips: List<ClipCandidate>,
    selectedProjectId: String?,
    selectedCampaignId: String?,
    desiredClipCount: Int = 3,
    includeVideoCaptions: Boolean = true,
    viralCaptionStyle: String = "HORMOZI_BOLD",
    isIngestingUrl: Boolean,
    isAnalyzing: Boolean,
    pipelineProgress: Float = 0f,
    pipelineStageLabel: String = "",
    previewFrames: Map<String, List<Bitmap>>,
    onSelectProject: (String) -> Unit,
    onSelectCampaign: (String) -> Unit,
    onApplyCampaignPreset: (CampaignPreset) -> Unit = {},
    onSaveCampaignPreset: (CampaignPreset) -> Unit = {},
    onSetDesiredClipCount: (Int) -> Unit = {},
    onSetIncludeVideoCaptions: (Boolean) -> Unit = {},
    onSetViralCaptionStyle: (String) -> Unit = {},
    onIngestStreamUrl: (String, String, WhopCampaign?, Boolean) -> Unit,
    onUploadVideoUri: (Uri, String, String, String) -> Unit,
    onCreateSampleProject: (String) -> Unit,
    onDeleteProject: (String) -> Unit,
    onRunAiDetection: (VideoProject, WhopCampaign?) -> Unit,
    onRunZeroTokenLocalClipper: (VideoProject, WhopCampaign?) -> Unit = { _, _ -> },
    onNavigateToCampaigns: () -> Unit,
    onNavigateToReview: () -> Unit
) {
    var streamUrlInput by rememberSaveable { mutableStateOf("") }
    var viralFocusNotes by rememberSaveable { mutableStateOf("") }
    var showUrlOptions by rememberSaveable { mutableStateOf(false) }
    var clipCountDropdownExpanded by remember { mutableStateOf(false) }
    var showQuickSavePreset by rememberSaveable { mutableStateOf(false) }
    var quickPresetNameInput by rememberSaveable { mutableStateOf("") }
    var customProjectTitle by rememberSaveable { mutableStateOf("") }
    var transcriptInput by rememberSaveable { mutableStateOf("") }
    var showSpecExplain by rememberSaveable { mutableStateOf(false) }

    val clipboardManager = LocalClipboardManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val textToolbar = LocalTextToolbar.current

    val activeProject = projects.firstOrNull { it.id == selectedProjectId } ?: projects.firstOrNull()
    val activeCampaign = campaigns.firstOrNull { it.id == selectedCampaignId } ?: campaigns.firstOrNull()
    val isBusy = isIngestingUrl || isAnalyzing
    val detectedPlatform = if (streamUrlInput.isNotBlank()) {
        UrlVideoIngestEngine.detectPlatform(streamUrlInput)
    } else null

    val triggerUrlProcess = {
        if (!isBusy && streamUrlInput.isNotBlank()) {
            textToolbar.hide()
            keyboardController?.hide()
            onIngestStreamUrl(
                streamUrlInput.trim(),
                viralFocusNotes.trim(),
                activeCampaign,
                true
            )
        }
    }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            onUploadVideoUri(
                uri,
                customProjectTitle,
                transcriptInput,
                activeCampaign?.id.orEmpty()
            )
            customProjectTitle = ""
            transcriptInput = ""
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Top KPI Summary Row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MetricStatCard(
                    modifier = Modifier.weight(1f),
                    label = "PROJECTS",
                    value = projects.size.toString(),
                    badge = "CLOUD SYNC",
                    badgeColor = VerifiedEmerald
                )
                MetricStatCard(
                    modifier = Modifier.weight(1f),
                    label = "VIRAL CLIPS",
                    value = clips.size.toString(),
                    badge = "${clips.count { it.reviewStatus == "APPROVED" || it.reviewStatus == "EXPORTED" }} APPROVED",
                    badgeColor = ForgeAmber
                )
                MetricStatCard(
                    modifier = Modifier.weight(1f),
                    label = "PRESETS",
                    value = campaignPresets.size.toString(),
                    badge = activeCampaign?.preferredAspectRatio ?: "9:16",
                    badgeColor = TechnicalCyan
                )
            }
        }

        // 2. Clean YouTube & Twitch URL Input + Campaign Presets + 1-10 Clip Count Dropdown + Video Captions ON/OFF + Progress Bar + Process Button
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = StudioSurfaceElevated),
                border = BorderStroke(1.5.dp, ForgeAmber)
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
                                imageVector = Icons.Default.Link,
                                contentDescription = null,
                                tint = ForgeAmber,
                                modifier = Modifier.size(22.dp)
                            )
                            Column {
                                Text(
                                    text = "YOUTUBE OR TWITCH URL CLIPPER",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "Paste URL, load a Campaign Preset, pick 1–10 clips & press Process",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                        }
                        val badgeText = when (detectedPlatform) {
                            StreamPlatform.YOUTUBE -> "YOUTUBE DETECTED"
                            StreamPlatform.TWITCH -> "TWITCH DETECTED"
                            StreamPlatform.GENERIC_URL -> "STREAM URL"
                            null -> "AI READY"
                        }
                        val badgeTone = when (detectedPlatform) {
                            StreamPlatform.YOUTUBE -> ForgeAmber
                            StreamPlatform.TWITCH -> TechnicalCyan
                            else -> VerifiedEmerald
                        }
                        StatusPillBadge(text = badgeText, color = badgeTone)
                    }

                    // Clean URL Input Field
                    OutlinedTextField(
                        value = streamUrlInput,
                        onValueChange = { streamUrlInput = it },
                        placeholder = {
                            Text(
                                text = "https://www.youtube.com/watch?v=... or https://www.twitch.tv/videos/...",
                                color = TextMuted
                            )
                        },
                        label = { Text("YouTube or Twitch URL") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Go
                        ),
                        keyboardActions = KeyboardActions(
                            onGo = { triggerUrlProcess() }
                        ),
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Link,
                                contentDescription = "Stream URL",
                                tint = ForgeAmber
                            )
                        },
                        trailingIcon = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (streamUrlInput.isNotBlank()) {
                                    IconButton(
                                        onClick = {
                                            textToolbar.hide()
                                            streamUrlInput = ""
                                        },
                                        modifier = Modifier
                                            .size(48.dp)
                                            .testTag("clear_url_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Clear,
                                            contentDescription = "Clear URL",
                                            tint = TextSecondary
                                        )
                                    }
                                } else {
                                    IconButton(
                                        onClick = {
                                            textToolbar.hide()
                                            val clipText = clipboardManager.getText()?.text?.trim().orEmpty()
                                            if (clipText.isNotBlank()) {
                                                streamUrlInput = clipText
                                            }
                                        },
                                        modifier = Modifier
                                            .size(48.dp)
                                            .testTag("paste_url_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ContentPaste,
                                            contentDescription = "Paste URL from clipboard",
                                            tint = TechnicalCyan
                                        )
                                    }
                                }
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ForgeAmber,
                            unfocusedBorderColor = StudioBorder,
                            focusedContainerColor = StudioObsidian,
                            unfocusedContainerColor = StudioObsidian
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("stream_url_input")
                    )

                    // Campaign Presets Quick-Load & Save Bar
                    Surface(
                        color = StudioObsidian,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, TechnicalCyan.copy(alpha = 0.6f)),
                        modifier = Modifier.fillMaxWidth()
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
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Bookmarks,
                                        contentDescription = null,
                                        tint = TechnicalCyan,
                                        modifier = Modifier.size(17.dp)
                                    )
                                    Column {
                                        Text(
                                            text = "CAMPAIGN PRESETS (1-TAP RULES, CAPTIONS & HASHTAGS)",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TechnicalCyan,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = activeCampaign?.let {
                                                "Active: ${it.name} • ${it.preferredAspectRatio} • ${it.requiredHashtags}"
                                            } ?: "Select a preset to load Whop rules, caption style & hashtags",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TextSecondary
                                        )
                                    }
                                }
                                TextButton(
                                    onClick = { showQuickSavePreset = !showQuickSavePreset },
                                    modifier = Modifier.testTag("toggle_quick_save_preset_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.BookmarkAdded,
                                        contentDescription = null,
                                        tint = ForgeAmber,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (showQuickSavePreset) "Cancel" else "+ Save Preset",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = ForgeAmber,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            if (campaignPresets.isNotEmpty()) {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    campaignPresets.forEach { preset ->
                                        val isSelected = preset.id == activePresetId
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = { onApplyCampaignPreset(preset) },
                                            label = {
                                                Text(
                                                    text = "${preset.presetName} (${preset.preferredAspectRatio} • ${preset.defaultClipCount}c)",
                                                    style = MaterialTheme.typography.labelSmall
                                                )
                                            },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = TechnicalCyan.copy(alpha = 0.25f),
                                                selectedLabelColor = TechnicalCyan
                                            ),
                                            modifier = Modifier.testTag("dashboard_preset_chip_${preset.id}")
                                        )
                                    }
                                }
                            }

                            if (showQuickSavePreset) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = quickPresetNameInput,
                                        onValueChange = { quickPresetNameInput = it },
                                        placeholder = { Text("e.g. Daily Gaming 9:16 Pack") },
                                        label = { Text("New Campaign Preset Name") },
                                        singleLine = true,
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("dashboard_quick_preset_name_input")
                                    )
                                    Button(
                                        onClick = {
                                            val title = quickPresetNameInput.trim().ifBlank {
                                                "${activeCampaign?.brandName ?: "Creator"} ${viralCaptionStyle.replace("_", " ")} Preset"
                                            }
                                            val newPreset = CampaignPreset(
                                                presetName = title,
                                                campaignName = activeCampaign?.name ?: title,
                                                brandName = activeCampaign?.brandName ?: "ClipForge Studio",
                                                payoutPer1kViews = activeCampaign?.payoutPer1kViews ?: "$2.00 / 1K views",
                                                targetPlatform = activeCampaign?.targetPlatform ?: "TIKTOK",
                                                preferredAspectRatio = activeCampaign?.preferredAspectRatio ?: "9:16",
                                                minDurationSec = activeCampaign?.minDurationSec ?: 15L,
                                                maxDurationSec = activeCampaign?.maxDurationSec ?: 55L,
                                                logoPlacement = activeCampaign?.logoPlacement ?: "TOP_RIGHT",
                                                requiredLogoAsset = activeCampaign?.requiredLogoAsset ?: "whop_partner_watermark.png",
                                                customLogoLocalPath = activeCampaign?.customLogoLocalPath.orEmpty(),
                                                safeZoneTopPct = activeCampaign?.safeZoneTopPct ?: 12L,
                                                safeZoneBottomPct = activeCampaign?.safeZoneBottomPct ?: 22L,
                                                requiredHashtags = activeCampaign?.requiredHashtags ?: "#whop #clipping #viral #shorts",
                                                mandatoryCaptionCta = activeCampaign?.mandatoryCaptionCta ?: "Check link in bio for the full video",
                                                seoGuidance = activeCampaign?.seoGuidance ?: "High-velocity hook in first 40 characters",
                                                minConfidenceScore = activeCampaign?.minConfidenceScore ?: 78L,
                                                viralCaptionStyle = viralCaptionStyle,
                                                includeVideoCaptions = includeVideoCaptions,
                                                defaultClipCount = desiredClipCount
                                            )
                                            onSaveCampaignPreset(newPreset)
                                            quickPresetNameInput = ""
                                            showQuickSavePreset = false
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = VerifiedEmerald,
                                            contentColor = StudioObsidian
                                        ),
                                        modifier = Modifier
                                            .height(48.dp)
                                            .testTag("dashboard_save_preset_button")
                                    ) {
                                        Text("Save", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    // Row: 1 to 10 Clips Dropdown Menu + Add Captions in Video ON/OFF Toggle
                    Surface(
                        color = StudioObsidian,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, StudioBorder),
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
                                        text = "HOW MANY CLIPS TO GENERATE (1–10)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = ForgeAmber,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = when {
                                            desiredClipCount <= 2 -> "Fastest generation & lowest token usage"
                                            desiredClipCount <= 5 -> "Balanced viral multi-clip batch"
                                            else -> "High-volume creator pack ($desiredClipCount clips)"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextSecondary
                                    )
                                }

                                Box {
                                    OutlinedButton(
                                        onClick = { clipCountDropdownExpanded = true },
                                        border = BorderStroke(1.5.dp, ForgeAmber),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier
                                            .height(48.dp)
                                            .testTag("clip_count_dropdown_button")
                                    ) {
                                        Text(
                                            text = "$desiredClipCount ${if (desiredClipCount == 1) "Clip" else "Clips"}",
                                            color = ForgeAmber,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                            imageVector = Icons.Default.ArrowDropDown,
                                            contentDescription = "Select clip count 1 to 10",
                                            tint = ForgeAmber
                                        )
                                    }

                                    DropdownMenu(
                                        expanded = clipCountDropdownExpanded,
                                        onDismissRequest = { clipCountDropdownExpanded = false }
                                    ) {
                                        (1..10).forEach { countOption ->
                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        text = "$countOption ${if (countOption == 1) "Viral Clip" else "Viral Clips"}",
                                                        fontWeight = if (countOption == desiredClipCount) FontWeight.Bold else FontWeight.Normal,
                                                        color = if (countOption == desiredClipCount) ForgeAmber else TextPrimary
                                                    )
                                                },
                                                onClick = {
                                                    onSetDesiredClipCount(countOption)
                                                    clipCountDropdownExpanded = false
                                                },
                                                modifier = Modifier.testTag("clip_count_option_$countOption")
                                            )
                                        }
                                    }
                                }
                            }

                            // Add Captions in Video ON/OFF Toggle Row
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
                                        imageVector = Icons.Default.Subtitles,
                                        contentDescription = null,
                                        tint = if (includeVideoCaptions) VerifiedEmerald else TextMuted,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Column {
                                        Text(
                                            text = if (includeVideoCaptions) {
                                                "ADD CAPTIONS IN VIDEO: ON"
                                            } else {
                                                "ADD CAPTIONS IN VIDEO: OFF"
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (includeVideoCaptions) VerifiedEmerald else TextSecondary,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = if (includeVideoCaptions) {
                                                "Burned-in viral video captions + .SRT enabled"
                                            } else {
                                                "Clean video frame without burned-in subtitles"
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TextSecondary
                                        )
                                    }
                                }
                                Switch(
                                    checked = includeVideoCaptions,
                                    onCheckedChange = { onSetIncludeVideoCaptions(it) },
                                    modifier = Modifier.testTag("dashboard_video_captions_switch")
                                )
                            }

                            // Cool Viral Video Caption Style Selector (shown when Video Captions are ON)
                            if (includeVideoCaptions) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Whatshot,
                                            contentDescription = null,
                                            tint = ForgeAmber,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Text(
                                            text = "COOL VIRAL CAPTION STYLE:",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TextSecondary
                                        )
                                    }
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        val captionStyles = listOf(
                                            "HORMOZI_BOLD" to "HORMOZI BOLD",
                                            "GAMING_HYPE" to "GAMING HYPE",
                                            "STORYTELLER" to "STORYTELLER HOOK",
                                            "CLEAN_MINIMAL" to "CLEAN MINIMAL"
                                        )
                                        captionStyles.forEach { (styleId, label) ->
                                            val isSelected = viralCaptionStyle == styleId
                                            FilterChip(
                                                selected = isSelected,
                                                onClick = { onSetViralCaptionStyle(styleId) },
                                                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                                colors = FilterChipDefaults.filterChipColors(
                                                    selectedContainerColor = ForgeAmber.copy(alpha = 0.25f),
                                                    selectedLabelColor = ForgeAmber
                                                ),
                                                modifier = Modifier.testTag("viral_style_chip_$styleId")
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Live Multi-Stage Progress Bar inside the URL Clipper Card
                    if (isBusy || pipelineProgress > 0f) {
                        val effectiveProgress = if (pipelineProgress > 0f) {
                            pipelineProgress.coerceIn(0.05f, 1f)
                        } else if (isBusy) {
                            0.18f
                        } else {
                            1f
                        }
                        Surface(
                            color = StudioObsidian,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(
                                1.dp,
                                if (effectiveProgress >= 1f) VerifiedEmerald else ForgeAmber
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("pipeline_progress_container")
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
                                    Text(
                                        text = pipelineStageLabel.ifBlank {
                                            if (isIngestingUrl) "Fetching YouTube/Twitch metadata & captions..."
                                            else if (isAnalyzing) "Scoring $desiredClipCount viral clip(s)..."
                                            else "Pipeline complete"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (effectiveProgress >= 1f) VerifiedEmerald else ForgeAmber,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = "${(effectiveProgress * 100).toInt()}%",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (effectiveProgress >= 1f) VerifiedEmerald else TechnicalCyan,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                LinearProgressIndicator(
                                    progress = { effectiveProgress },
                                    color = if (effectiveProgress >= 1f) VerifiedEmerald else ForgeAmber,
                                    trackColor = StudioBorder,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .testTag("pipeline_progress_bar")
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { triggerUrlProcess() },
                            enabled = !isBusy && streamUrlInput.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ForgeAmber,
                                contentColor = StudioObsidian
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .testTag("process_url_button")
                        ) {
                            if (isBusy) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = StudioObsidian,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isIngestingUrl) "Ingesting URL..." else "Processing $desiredClipCount Clip(s)...",
                                    fontWeight = FontWeight.Bold
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Process ($desiredClipCount ${if (desiredClipCount == 1) "Clip" else "Clips"})",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = { showUrlOptions = !showUrlOptions },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .height(50.dp)
                                .testTag("toggle_url_options_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = "Toggle URL options",
                                modifier = Modifier.size(18.dp),
                                tint = TechnicalCyan
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (showUrlOptions) "Hide Options" else "Options")
                        }
                    }

                    // Quick 1-tap test links + optional prompt notes when expanded
                    if (showUrlOptions) {
                        Surface(
                            color = StudioObsidian,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, StudioBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Sample URLs:",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextSecondary
                                    )
                                    FilterChip(
                                        selected = false,
                                        onClick = {
                                            streamUrlInput = "https://www.youtube.com/watch?v=aqz-KE-bpKQ"
                                            viralFocusNotes = "Find funny moments, big reactions, and high-retention 9:16 hooks."
                                        },
                                        label = { Text("YouTube Sample") }
                                    )
                                    FilterChip(
                                        selected = false,
                                        onClick = {
                                            streamUrlInput = "https://www.twitch.tv/videos/2149823410"
                                            viralFocusNotes = "Look for overtime 1v4 clutch plays and loud streamer hype reactions."
                                        },
                                        label = { Text("Twitch VOD Sample") }
                                    )
                                }

                                OutlinedTextField(
                                    value = viralFocusNotes,
                                    onValueChange = { viralFocusNotes = it },
                                    label = { Text("Optional AI Focus Notes (e.g. 'funny moments, clutch plays')") },
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("viral_focus_notes_input")
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    TextButton(
                                        onClick = {
                                            onIngestStreamUrl(
                                                streamUrlInput.trim(),
                                                viralFocusNotes.trim(),
                                                activeCampaign,
                                                false
                                            )
                                        },
                                        enabled = !isBusy && streamUrlInput.isNotBlank(),
                                        modifier = Modifier.testTag("import_url_only_button")
                                    ) {
                                        Text("Import Metadata Only (Skip AI)", color = TechnicalCyan)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. Feature Dependency, Cost & Limitation Disclosure Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = StudioSurface),
                border = BorderStroke(1.dp, StudioBorder)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showSpecExplain = !showSpecExplain },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Architecture Info",
                                tint = TechnicalCyan,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "YOUTUBE/TWITCH & LOCAL PIPELINE: DEPENDENCIES, COSTS & LIMITATIONS",
                                style = MaterialTheme.typography.labelSmall,
                                color = TechnicalCyan
                            )
                        }
                        Text(
                            text = if (showSpecExplain) "HIDE" else "EXPAND",
                            style = MaterialTheme.typography.labelSmall,
                            color = ForgeAmber
                        )
                    }

                    if (showSpecExplain) {
                        Text(
                            text = "• Dependencies: OkHttpClient + XmlPullParser for live YouTube oEmbed, playerResponse metadata, public timedtext captions, thumbnail Bitmap, and Twitch OpenGraph extraction; Firebase AI Logic (gemini-3.8-flash with App Check) for multimodal viral hook scoring; Android Photo Picker + MediaMuxer for local videos.\n" +
                                "• Expected Costs: $0.00 for public YouTube oEmbed/caption & Twitch OpenGraph fetching; $0.00 on Firebase Spark free tier for Gemini 3.8 Flash and Firestore within daily quotas.\n" +
                                "• Limitations: YouTube & Twitch prohibit direct raw MP4 stream ripping inside mobile client sandboxes without a backend worker; therefore, ClipForge AI fetches the real video title, duration, thumbnail image, description chapters, and public captions to identify exact viral timestamp windows, and generates the ready-to-run `yt-dlp --download-sections` + `ffmpeg` 9:16 crop command in the Export Package.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
            }
        }

        // 4. Local Phone Video Upload & Sample VOD Ingestion Card
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
                        Text(
                            text = "OR UPLOAD PHONE VIDEO / PASTE TRANSCRIPT",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                        StatusPillBadge(text = "WORKING: LOCAL MP4 REMUX", color = VerifiedEmerald)
                    }

                    OutlinedTextField(
                        value = customProjectTitle,
                        onValueChange = { customProjectTitle = it },
                        label = { Text("Project Title (Optional — auto-detected from file)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("project_title_input")
                    )

                    OutlinedTextField(
                        value = transcriptInput,
                        onValueChange = { transcriptInput = it },
                        label = { Text("Timecoded Transcript / Context Notes (Optional for AI context)") },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("project_transcript_input")
                    )

                    if (campaigns.isNotEmpty()) {
                        Text(
                            text = "Active Whop Campaign Ruleset:",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            campaigns.forEach { camp ->
                                val selected = camp.id == activeCampaign?.id
                                FilterChip(
                                    selected = selected,
                                    onClick = { onSelectCampaign(camp.id) },
                                    label = { Text("${camp.name} (${camp.targetPlatform})") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = ForgeAmber.copy(alpha = 0.2f),
                                        selectedLabelColor = ForgeAmber
                                    )
                                )
                            }
                        }
                    } else {
                        OutlinedButton(
                            onClick = onNavigateToCampaigns,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.AddCircleOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Configure a Whop Campaign Ruleset First")
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                videoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                )
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = TechnicalCyan,
                                contentColor = StudioObsidian
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("upload_video_button")
                        ) {
                            Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Select Phone Video", fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = { onCreateSampleProject(activeCampaign?.id.orEmpty()) },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("load_sample_project_button")
                        ) {
                            Icon(Icons.Default.MovieCreation, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Load Sample VOD")
                        }
                    }
                }
            }
        }

        // 5. Active Project & AI Highlight Detection Pipeline Trigger
        if (activeProject != null) {
            item {
                val projectFrames = previewFrames[activeProject.id].orEmpty()
                val projectClips = clips.filter { it.projectId == activeProject.id }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = StudioSurfaceElevated),
                    border = BorderStroke(1.5.dp, ForgeAmber.copy(alpha = 0.7f))
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
                                    text = "ACTIVE PROJECT WORKSPACE",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ForgeAmber
                                )
                                Text(
                                    text = activeProject.title,
                                    style = MaterialTheme.typography.titleLarge,
                                    color = TextPrimary
                                )
                            }
                            StatusPillBadge(
                                text = activeProject.status,
                                color = if (activeProject.status == "CLIPS_GENERATED" || activeProject.status == "EXPORTED") {
                                    VerifiedEmerald
                                } else {
                                    TechnicalCyan
                                }
                            )
                        }

                        Text(
                            text = "Source: ${activeProject.sourceFileName} • ${activeProject.width}x${activeProject.height} • Duration: ${activeProject.durationSec}s • Viral Clips: ${projectClips.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )

                        if (activeProject.sourceUri.startsWith("http")) {
                            Text(
                                text = "Stream Link: ${activeProject.sourceUri}",
                                style = MaterialTheme.typography.labelSmall,
                                color = TechnicalCyan
                            )
                        }

                        if (projectFrames.isNotEmpty()) {
                            Text(
                                text = "Extracted Thumbnail / Multimodal Keyframes (Sent to Gemini 3.8 Flash):",
                                style = MaterialTheme.typography.labelSmall,
                                color = VerifiedEmerald
                            )
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(projectFrames) { bmp ->
                                    Image(
                                        bitmap = bmp.asImageBitmap(),
                                        contentDescription = "Extracted Video Keyframe",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(width = 140.dp, height = 78.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                    )
                                }
                            }
                        }

                        if (activeProject.transcriptText.isNotBlank()) {
                            Surface(
                                color = StudioObsidian,
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, StudioBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = activeProject.transcriptText.take(460) +
                                        if (activeProject.transcriptText.length > 460) "..." else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }

                        if (isBusy || pipelineProgress > 0f) {
                            val workspaceProgress = if (pipelineProgress > 0f) {
                                pipelineProgress.coerceIn(0.05f, 1f)
                            } else if (isBusy) {
                                0.18f
                            } else {
                                1f
                            }
                            Surface(
                                color = StudioObsidian,
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(
                                    1.dp,
                                    if (workspaceProgress >= 1f) VerifiedEmerald else ForgeAmber
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("workspace_progress_container")
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = pipelineStageLabel.ifBlank {
                                                if (isAnalyzing) "Processing $desiredClipCount viral clip(s)..." else "Pipeline ready"
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (workspaceProgress >= 1f) VerifiedEmerald else ForgeAmber,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            text = "${(workspaceProgress * 100).toInt()}%",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (workspaceProgress >= 1f) VerifiedEmerald else TechnicalCyan,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    LinearProgressIndicator(
                                        progress = { workspaceProgress },
                                        color = if (workspaceProgress >= 1f) VerifiedEmerald else ForgeAmber,
                                        trackColor = StudioBorder,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(7.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .testTag("workspace_pipeline_progress_bar")
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { onRunAiDetection(activeProject, activeCampaign) },
                                enabled = !isBusy,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ForgeAmber,
                                    contentColor = StudioObsidian
                                ),
                                modifier = Modifier
                                    .weight(1.2f)
                                    .height(50.dp)
                                    .testTag("run_ai_pipeline_button")
                            ) {
                                if (isAnalyzing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = StudioObsidian,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Generating $desiredClipCount Clip(s)...", fontWeight = FontWeight.Bold)
                                } else {
                                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("AI Clip ($desiredClipCount)", fontWeight = FontWeight.Bold)
                                }
                            }

                            OutlinedButton(
                                onClick = { onRunZeroTokenLocalClipper(activeProject, activeCampaign) },
                                enabled = !isBusy,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp)
                                    .testTag("zero_token_local_clipper_button")
                            ) {
                                Text("0-Token Local ($desiredClipCount)", color = TechnicalCyan)
                            }

                            if (projectClips.isNotEmpty()) {
                                OutlinedButton(
                                    onClick = onNavigateToReview,
                                    modifier = Modifier.height(50.dp)
                                ) {
                                    Icon(Icons.Default.PlayCircleOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Review (${projectClips.size})")
                                }
                            }
                        }
                    }
                }
            }
        }

        // 6. Project Library List
        item {
            Text(
                text = "ALL VIDEO & STREAM PROJECTS (${projects.size})",
                style = MaterialTheme.typography.labelLarge,
                color = TextSecondary
            )
        }

        if (projects.isEmpty()) {
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
                            imageVector = Icons.Default.MovieCreation,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(36.dp)
                        )
                        Text(
                            text = "No projects yet. Paste a YouTube/Twitch link above, select a video from your phone, or tap 'Load Sample VOD'.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }
            }
        } else {
            items(projects, key = { it.id }) { proj ->
                val isSelected = proj.id == activeProject?.id
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectProject(proj.id) }
                        .testTag("project_card_${proj.id}"),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) StudioSurfaceElevated else StudioSurface
                    ),
                    border = BorderStroke(
                        width = if (isSelected) 1.5.dp else 1.dp,
                        color = if (isSelected) ForgeAmber else StudioBorder
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = proj.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary
                            )
                            Text(
                                text = "${proj.sourceFileName} • ${proj.durationSec}s • ${proj.width}x${proj.height}",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            StatusPillBadge(text = proj.status, color = TechnicalCyan)
                            IconButton(
                                onClick = { onDeleteProject(proj.id) },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = "Delete project ${proj.title}",
                                    tint = TextMuted
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MetricStatCard(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    badge: String,
    badgeColor: androidx.compose.ui.graphics.Color
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = StudioSurface),
        border = BorderStroke(1.dp, StudioBorder)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary
            )
            Text(
                text = badge,
                style = MaterialTheme.typography.labelSmall,
                color = badgeColor
            )
        }
    }
}

@Composable
fun StatusPillBadge(
    text: String,
    color: androidx.compose.ui.graphics.Color
) {
    Surface(
        color = color.copy(alpha = 0.15f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.6f)),
        shape = RoundedCornerShape(6.dp)
    ) {
        Box(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = color
            )
        }
    }
}
