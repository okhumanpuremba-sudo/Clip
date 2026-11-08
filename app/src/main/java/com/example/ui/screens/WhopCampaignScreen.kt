package com.example.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AddTask
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.BookmarkAdded
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CropPortrait
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Whatshot
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.LocalWhopCampaignStore
import com.example.data.model.CampaignPreset
import com.example.data.model.ClipCandidate
import com.example.data.model.WhopCampaign
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
import java.io.File
import kotlin.math.roundToLong
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WhopCampaignScreen(
    campaigns: List<WhopCampaign>,
    campaignPresets: List<CampaignPreset> = emptyList(),
    selectedCampaignId: String?,
    activePresetId: String? = null,
    currentViralCaptionStyle: String = "HORMOZI_BOLD",
    currentIncludeVideoCaptions: Boolean = true,
    currentDesiredClipCount: Int = 3,
    onSelectCampaign: (String) -> Unit,
    onSaveCampaign: (WhopCampaign) -> Unit,
    onSaveCampaignPreset: (CampaignPreset) -> Unit = {},
    onApplyCampaignPreset: (CampaignPreset) -> Unit = {},
    onDeleteCampaignPreset: (String) -> Unit = {},
    onCreateStarterCampaign: () -> Unit,
    onDeleteCampaign: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var presetNameInput by rememberSaveable { mutableStateOf("My Viral 9:16 Preset") }
    var presetViralStyle by rememberSaveable(currentViralCaptionStyle) { mutableStateOf(currentViralCaptionStyle) }
    var presetIncludeCaptions by rememberSaveable(currentIncludeVideoCaptions) { mutableStateOf(currentIncludeVideoCaptions) }
    var presetClipCount by rememberSaveable(currentDesiredClipCount) { mutableIntStateOf(currentDesiredClipCount) }

    var name by rememberSaveable { mutableStateOf("Whop Creator Bounty") }
    var brandName by rememberSaveable { mutableStateOf("ApexGaming") }
    var payoutRate by rememberSaveable { mutableStateOf("$2.00 / 1K views") }
    var targetPlatform by rememberSaveable { mutableStateOf("TIKTOK") }
    var preferredAspectRatio by rememberSaveable { mutableStateOf("9:16") }
    var minDuration by rememberSaveable { mutableFloatStateOf(15f) }
    var maxDuration by rememberSaveable { mutableFloatStateOf(55f) }
    var logoPlacement by rememberSaveable { mutableStateOf("TOP_RIGHT") }
    var requiredLogoAsset by rememberSaveable { mutableStateOf("whop_partner_watermark.png") }
    var customLogoLocalPath by rememberSaveable { mutableStateOf("") }
    var customLogoStatusNote by rememberSaveable { mutableStateOf("No local logo file selected yet — pick an image or generate a studio watermark.") }
    var safeTopPct by rememberSaveable { mutableFloatStateOf(12f) }
    var safeBottomPct by rememberSaveable { mutableFloatStateOf(22f) }
    var requiredHashtags by rememberSaveable { mutableStateOf("#whop #clipping #gaming") }
    var customTagDraft by rememberSaveable { mutableStateOf("") }
    var mandatoryCta by rememberSaveable { mutableStateOf("Check link in bio for the bounty") }
    var seoGuidance by rememberSaveable { mutableStateOf("Include primary game/topic keyword in first 40 characters") }
    var minConfidence by rememberSaveable { mutableFloatStateOf(78f) }
    var localSaveFeedback by rememberSaveable { mutableStateOf<String?>(null) }

    var logoPreviewBitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(customLogoLocalPath) {
        if (customLogoLocalPath.isNotBlank() && logoPreviewBitmap == null) {
            logoPreviewBitmap = LocalWhopCampaignStore.loadLogoPreviewFromPath(customLogoLocalPath)
        }
    }

    val logoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val res = LocalWhopCampaignStore.copyCustomLogoToLocalStorage(context, uri)
                res.onSuccess { info ->
                    requiredLogoAsset = info.fileName
                    customLogoLocalPath = info.localFilePath
                    logoPreviewBitmap = info.previewBitmap
                    customLogoStatusNote = "Saved locally: ${info.fileName} (${info.width}x${info.height}, ${info.sizeBytes / 1024} KB)"
                }.onFailure { err ->
                    customLogoStatusNote = "Logo selection failed: ${err.localizedMessage ?: "Unable to copy file"}"
                }
            }
        }
    }

    val platforms = listOf("TIKTOK", "REELS", "SHORTS", "ALL_VERTICAL")
    val aspectOptions = listOf(
        "9:16" to "9:16 Vertical (Shorts/TikTok/Reels)",
        "1:1" to "1:1 Square Feed",
        "16:9" to "16:9 Landscape VOD"
    )
    val logoPositions = listOf("TOP_LEFT", "TOP_RIGHT", "BOTTOM_LEFT", "BOTTOM_RIGHT", "NONE")
    val suggestedTags = listOf("#whop", "#clipping", "#gaming", "#viral", "#shorts", "#bounty", "#ad")

    val parsedHashtagList = remember(requiredHashtags) {
        requiredHashtags
            .split(" ", ",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { if (it.startsWith("#")) it else "#$it" }
            .distinctBy { it.lowercase() }
    }

    // Helper to load a CampaignPreset into all form fields + apply globally
    val loadPresetIntoForm: (CampaignPreset) -> Unit = { preset ->
        presetNameInput = preset.presetName
        name = preset.campaignName
        brandName = preset.brandName
        payoutRate = preset.payoutPer1kViews
        targetPlatform = preset.targetPlatform
        preferredAspectRatio = preset.preferredAspectRatio.ifBlank { "9:16" }
        minDuration = preset.minDurationSec.toFloat()
        maxDuration = preset.maxDurationSec.toFloat()
        logoPlacement = preset.logoPlacement
        requiredLogoAsset = preset.requiredLogoAsset
        customLogoLocalPath = preset.customLogoLocalPath
        safeTopPct = preset.safeZoneTopPct.toFloat()
        safeBottomPct = preset.safeZoneBottomPct.toFloat()
        requiredHashtags = preset.requiredHashtags
        mandatoryCta = preset.mandatoryCaptionCta
        seoGuidance = preset.seoGuidance
        minConfidence = preset.minConfidenceScore.toFloat()
        presetViralStyle = preset.viralCaptionStyle
        presetIncludeCaptions = preset.includeVideoCaptions
        presetClipCount = preset.defaultClipCount
        onApplyCampaignPreset(preset)
        localSaveFeedback = "Loaded preset '${preset.presetName}' (${preset.preferredAspectRatio}, ${preset.viralCaptionStyle.replace("_", " ")}, ${preset.defaultClipCount} clips)."
    }

    // Build a live draft campaign to run a real-time validation preview
    val draftCampaign = remember(
        name, brandName, payoutRate, targetPlatform, preferredAspectRatio,
        minDuration, maxDuration, logoPlacement, requiredLogoAsset, customLogoLocalPath,
        safeTopPct, safeBottomPct, requiredHashtags, mandatoryCta, seoGuidance, minConfidence
    ) {
        WhopCampaign(
            name = name,
            brandName = brandName,
            payoutPer1kViews = payoutRate,
            targetPlatform = targetPlatform,
            preferredAspectRatio = preferredAspectRatio,
            minDurationSec = minDuration.roundToLong(),
            maxDurationSec = maxDuration.roundToLong(),
            logoPlacement = logoPlacement,
            requiredLogoAsset = requiredLogoAsset,
            customLogoLocalPath = customLogoLocalPath,
            safeZoneTopPct = safeTopPct.roundToLong(),
            safeZoneBottomPct = safeBottomPct.roundToLong(),
            requiredHashtags = parsedHashtagList.joinToString(" "),
            mandatoryCaptionCta = mandatoryCta,
            seoGuidance = seoGuidance,
            minConfidenceScore = minConfidence.roundToLong()
        )
    }

    val sampleValidationReport = remember(draftCampaign) {
        val sampleCompliantClip = WhopRuleValidator.autoFixClipForCampaign(
            ClipCandidate(
                title = "Sample Campaign Clip Test",
                startSec = 10L,
                endSec = 35L,
                aspectRatio = preferredAspectRatio,
                logoPlacement = logoPlacement,
                hashtags = parsedHashtagList.joinToString(" ")
            ),
            draftCampaign
        )
        WhopRuleValidator.validateClip(sampleCompliantClip, draftCampaign)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Campaign Presets Manager Card (Save & Load Recurring Rules, Caption Styles & Hashtags)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = StudioSurfaceElevated),
                border = BorderStroke(1.5.dp, TechnicalCyan)
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
                                imageVector = Icons.Default.Bookmarks,
                                contentDescription = null,
                                tint = TechnicalCyan,
                                modifier = Modifier.size(22.dp)
                            )
                            Column {
                                Text(
                                    text = "CAMPAIGN PRESETS (SAVE & LOAD)",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "Quickly apply recurring Whop rules, viral caption styles & hashtags to new projects",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                        }
                        StatusPillBadge(text = "${campaignPresets.size} PRESETS", color = TechnicalCyan)
                    }

                    // Saved Presets List for 1-Tap Load
                    if (campaignPresets.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            campaignPresets.forEach { preset ->
                                val isPresetActive = preset.id == activePresetId
                                Surface(
                                    color = if (isPresetActive) TechnicalCyan.copy(alpha = 0.14f) else StudioObsidian,
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(
                                        width = if (isPresetActive) 1.5.dp else 1.dp,
                                        color = if (isPresetActive) TechnicalCyan else StudioBorder
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { loadPresetIntoForm(preset) }
                                        .testTag("preset_card_${preset.id}")
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(3.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Text(
                                                    text = preset.presetName,
                                                    style = MaterialTheme.typography.titleSmall,
                                                    color = if (isPresetActive) TechnicalCyan else TextPrimary,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                StatusPillBadge(
                                                    text = preset.preferredAspectRatio,
                                                    color = ForgeAmber
                                                )
                                                StatusPillBadge(
                                                    text = preset.viralCaptionStyle.replace("_", " "),
                                                    color = VerifiedEmerald
                                                )
                                            }
                                            Text(
                                                text = "${preset.brandName} • ${preset.defaultClipCount} clips • Captions: ${if (preset.includeVideoCaptions) "ON" else "OFF"} • ${preset.minDurationSec}s–${preset.maxDurationSec}s",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = TextSecondary
                                            )
                                            Text(
                                                text = "Hashtags: ${preset.requiredHashtags}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = TechnicalCyan
                                            )
                                        }

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            OutlinedButton(
                                                onClick = { loadPresetIntoForm(preset) },
                                                modifier = Modifier
                                                    .height(42.dp)
                                                    .testTag("load_preset_button_${preset.id}")
                                            ) {
                                                Text(
                                                    text = if (isPresetActive) "Loaded" else "Load",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = if (isPresetActive) VerifiedEmerald else TechnicalCyan,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            IconButton(
                                                onClick = { onDeleteCampaignPreset(preset.id) },
                                                modifier = Modifier
                                                    .size(42.dp)
                                                    .testTag("delete_preset_${preset.id}")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.DeleteOutline,
                                                    contentDescription = "Delete preset ${preset.presetName}",
                                                    tint = TextMuted
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Save Current Form + Caption Style + Clip Count as a Reusable Campaign Preset
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
                                    text = "PRESET CAPTION STYLE & DEFAULT CLIP COUNT",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ForgeAmber,
                                    fontWeight = FontWeight.Bold
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
                                    FilterChip(
                                        selected = presetViralStyle == styleId,
                                        onClick = { presetViralStyle = styleId },
                                        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = ForgeAmber.copy(alpha = 0.25f),
                                            selectedLabelColor = ForgeAmber
                                        ),
                                        modifier = Modifier.testTag("preset_caption_style_$styleId")
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Include Burned-In Video Captions (${if (presetIncludeCaptions) "ON" else "OFF"}) • Default Clips: $presetClipCount",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary
                                )
                                Switch(
                                    checked = presetIncludeCaptions,
                                    onCheckedChange = { presetIncludeCaptions = it },
                                    modifier = Modifier.testTag("preset_include_captions_switch")
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = presetNameInput,
                                    onValueChange = { presetNameInput = it },
                                    label = { Text("Campaign Preset Name") },
                                    singleLine = true,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("preset_name_input")
                                )
                                Button(
                                    onClick = {
                                        val cleanPresetName = presetNameInput.trim().ifBlank { "$brandName $preferredAspectRatio Preset" }
                                        val savedPreset = CampaignPreset(
                                            presetName = cleanPresetName,
                                            campaignName = name.ifBlank { cleanPresetName },
                                            brandName = brandName.ifBlank { "Creator Brand" },
                                            payoutPer1kViews = payoutRate,
                                            targetPlatform = targetPlatform,
                                            preferredAspectRatio = preferredAspectRatio,
                                            minDurationSec = minDuration.roundToLong(),
                                            maxDurationSec = maxDuration.roundToLong(),
                                            logoPlacement = logoPlacement,
                                            requiredLogoAsset = requiredLogoAsset,
                                            customLogoLocalPath = customLogoLocalPath,
                                            safeZoneTopPct = safeTopPct.roundToLong(),
                                            safeZoneBottomPct = safeBottomPct.roundToLong(),
                                            requiredHashtags = parsedHashtagList.joinToString(" "),
                                            mandatoryCaptionCta = mandatoryCta,
                                            seoGuidance = seoGuidance,
                                            minConfidenceScore = minConfidence.roundToLong(),
                                            viralCaptionStyle = presetViralStyle,
                                            includeVideoCaptions = presetIncludeCaptions,
                                            defaultClipCount = presetClipCount
                                        )
                                        onSaveCampaignPreset(savedPreset)
                                        localSaveFeedback = "Saved Campaign Preset '$cleanPresetName' locally with ${parsedHashtagList.size} hashtags & $presetViralStyle style!"
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = TechnicalCyan,
                                        contentColor = StudioObsidian
                                    ),
                                    modifier = Modifier
                                        .height(52.dp)
                                        .testTag("save_campaign_preset_button")
                                ) {
                                    Icon(Icons.Default.BookmarkAdded, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Save Preset", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Main Whop Campaign Requirements Form
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = StudioSurface),
                border = BorderStroke(1.5.dp, ForgeAmber)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "WHOP CAMPAIGN REQUIREMENTS FORM",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary
                            )
                            Text(
                                text = "Configure rules, aspect ratio, hashtags & custom brand logo for clip validation",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                        OutlinedButton(
                            onClick = onCreateStarterCampaign,
                            modifier = Modifier.testTag("starter_whop_campaign_button")
                        ) {
                            Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("1-Tap Preset")
                        }
                    }

                    // Section 1: Campaign Identity & Payout
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("Campaign Title") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("campaign_name_input")
                        )
                        OutlinedTextField(
                            value = brandName,
                            onValueChange = { brandName = it },
                            label = { Text("Brand / Creator") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("campaign_brand_input")
                        )
                    }

                    OutlinedTextField(
                        value = payoutRate,
                        onValueChange = { payoutRate = it },
                        label = { Text("Payout / Bounty Rate (e.g. $2.00 / 1K views)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("campaign_payout_input")
                    )

                    // Section 2: Aspect Ratio Preference & Target Platform
                    Surface(
                        color = StudioObsidian,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, StudioBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CropPortrait,
                                    contentDescription = null,
                                    tint = ForgeAmber,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "ASPECT RATIO PREFERENCE (ENFORCED ON EXPORT)",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = TextPrimary
                                )
                            }

                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                aspectOptions.forEach { (ratioCode, ratioLabel) ->
                                    FilterChip(
                                        selected = preferredAspectRatio == ratioCode,
                                        onClick = { preferredAspectRatio = ratioCode },
                                        label = { Text(ratioLabel) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = ForgeAmber.copy(alpha = 0.22f),
                                            selectedLabelColor = ForgeAmber
                                        ),
                                        modifier = Modifier.testTag("aspect_ratio_chip_$ratioCode")
                                    )
                                }
                            }

                            Text(
                                text = "Target Distribution Platform:",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                platforms.forEach { plat ->
                                    FilterChip(
                                        selected = targetPlatform == plat,
                                        onClick = { targetPlatform = plat },
                                        label = { Text(plat) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = TechnicalCyan.copy(alpha = 0.2f),
                                            selectedLabelColor = TechnicalCyan
                                        )
                                    )
                                }
                            }
                        }
                    }

                    // Section 3: Custom Logo File Selection & Watermark Placement
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
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FolderSpecial,
                                        contentDescription = null,
                                        tint = TechnicalCyan,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "CUSTOM LOGO FILE SELECTION & WATERMARK",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = TextPrimary
                                    )
                                }
                                if (customLogoLocalPath.isNotBlank() && File(customLogoLocalPath).exists()) {
                                    StatusPillBadge(text = "FILE SAVED LOCALLY", color = VerifiedEmerald)
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        logoPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = TechnicalCyan,
                                        contentColor = StudioObsidian
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("select_custom_logo_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AddPhotoAlternate,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Select Logo Image", fontWeight = FontWeight.Bold)
                                }

                                OutlinedButton(
                                    onClick = {
                                        scope.launch {
                                            val gen = LocalWhopCampaignStore.generateLocalBrandWatermarkFile(
                                                context = context,
                                                brandName = brandName
                                            )
                                            gen.onSuccess { info ->
                                                requiredLogoAsset = info.fileName
                                                customLogoLocalPath = info.localFilePath
                                                logoPreviewBitmap = info.previewBitmap
                                                customLogoStatusNote = "Generated & saved local PNG: ${info.fileName} (${info.width}x${info.height})"
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("generate_local_logo_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AutoFixHigh,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = ForgeAmber
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Generate PNG")
                                }
                            }

                            // Live Custom Logo Preview + Asset Filename Input
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val bmp = logoPreviewBitmap
                                if (bmp != null) {
                                    Image(
                                        bitmap = bmp.asImageBitmap(),
                                        contentDescription = "Selected custom campaign logo preview",
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier
                                            .size(width = 96.dp, height = 56.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(StudioSurfaceElevated)
                                            .padding(4.dp)
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    OutlinedTextField(
                                        value = requiredLogoAsset,
                                        onValueChange = { requiredLogoAsset = it },
                                        label = { Text("Logo Filename / Asset ID") },
                                        singleLine = true,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("campaign_logo_asset_input")
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = customLogoStatusNote,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (customLogoLocalPath.isNotBlank()) VerifiedEmerald else TextSecondary
                                    )
                                }
                            }

                            Text(
                                text = "Logo Watermark Safe Placement:",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                logoPositions.forEach { pos ->
                                    FilterChip(
                                        selected = logoPlacement == pos,
                                        onClick = { logoPlacement = pos },
                                        label = { Text(pos) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = TechnicalCyan.copy(alpha = 0.2f),
                                            selectedLabelColor = TechnicalCyan
                                        )
                                    )
                                }
                            }
                        }
                    }

                    // Section 4: Required Hashtags Input & Tag Chips
                    Surface(
                        color = StudioObsidian,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, StudioBorder),
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
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Tag,
                                        contentDescription = null,
                                        tint = ForgeAmber,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "REQUIRED CAMPAIGN HASHTAGS (${parsedHashtagList.size} ACTIVE)",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = TextPrimary
                                    )
                                }
                            }

                            OutlinedTextField(
                                value = requiredHashtags,
                                onValueChange = { requiredHashtags = it },
                                label = { Text("Required Hashtags (space or comma separated)") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("campaign_hashtags_input")
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = customTagDraft,
                                    onValueChange = { customTagDraft = it },
                                    label = { Text("Add single hashtag (e.g. #creator)") },
                                    singleLine = true,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("add_hashtag_draft_input")
                                )
                                OutlinedButton(
                                    onClick = {
                                        val raw = customTagDraft.trim().replace(" ", "")
                                        if (raw.isNotBlank()) {
                                            val formatted = if (raw.startsWith("#")) raw else "#$raw"
                                            if (parsedHashtagList.none { it.equals(formatted, ignoreCase = true) }) {
                                                requiredHashtags = (parsedHashtagList + formatted).joinToString(" ")
                                            }
                                            customTagDraft = ""
                                        }
                                    },
                                    modifier = Modifier.testTag("add_hashtag_chip_button")
                                ) {
                                    Text("+ Add Tag")
                                }
                            }

                            Text(
                                text = "Tap to toggle popular Whop hashtags:",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                suggestedTags.forEach { tag ->
                                    val isPresent = parsedHashtagList.any { it.equals(tag, ignoreCase = true) }
                                    FilterChip(
                                        selected = isPresent,
                                        onClick = {
                                            requiredHashtags = if (isPresent) {
                                                parsedHashtagList.filterNot { it.equals(tag, ignoreCase = true) }
                                                    .joinToString(" ")
                                            } else {
                                                (parsedHashtagList + tag).joinToString(" ")
                                            }
                                        },
                                        label = { Text(tag) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = ForgeAmber.copy(alpha = 0.22f),
                                            selectedLabelColor = ForgeAmber
                                        )
                                    )
                                }
                            }
                        }
                    }

                    // Section 5: Duration Window, Safe Zones, CTA & SEO Rules
                    Column {
                        Text(
                            text = "Clip Duration Window: ${minDuration.roundToLong()}s to ${maxDuration.roundToLong()}s",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextPrimary
                        )
                        Slider(
                            value = minDuration,
                            onValueChange = {
                                minDuration = it
                                if (maxDuration < minDuration + 5f) maxDuration = (minDuration + 5f).coerceAtMost(180f)
                            },
                            valueRange = 5f..90f
                        )
                        Slider(
                            value = maxDuration,
                            onValueChange = {
                                maxDuration = it.coerceAtLeast(minDuration + 5f)
                            },
                            valueRange = 15f..180f
                        )
                    }

                    Column {
                        Text(
                            text = "Platform UI Safe Zones — Top: ${safeTopPct.roundToLong()}% | Bottom: ${safeBottomPct.roundToLong()}% | Min AI Confidence: ${minConfidence.roundToLong()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextPrimary
                        )
                        Slider(
                            value = safeBottomPct,
                            onValueChange = { safeBottomPct = it },
                            valueRange = 10f..35f
                        )
                        Slider(
                            value = minConfidence,
                            onValueChange = { minConfidence = it },
                            valueRange = 50f..95f
                        )
                    }

                    OutlinedTextField(
                        value = mandatoryCta,
                        onValueChange = { mandatoryCta = it },
                        label = { Text("Mandatory Caption CTA / Disclaimers") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = seoGuidance,
                        onValueChange = { seoGuidance = it },
                        label = { Text("SEO Guidance & Hook Instructions") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Live Local Validation Checklist Preview
                    Surface(
                        color = VerifiedEmerald.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, VerifiedEmerald.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "LOCAL VALIDATION ENGINE PREVIEW (${sampleValidationReport.passedCount}/${sampleValidationReport.checks.size} RULES ACTIVE)",
                                style = MaterialTheme.typography.labelSmall,
                                color = VerifiedEmerald
                            )
                            Text(
                                text = "Enforcing Aspect Ratio: $preferredAspectRatio • Hashtags: ${parsedHashtagList.joinToString(" ")} • Logo: ${requiredLogoAsset.ifBlank { "None" }} ($logoPlacement)",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }

                    Button(
                        onClick = {
                            onSaveCampaign(draftCampaign)
                            localSaveFeedback = "Saved '${draftCampaign.name}' locally to whop_campaigns_local.json (${draftCampaign.preferredAspectRatio}, ${parsedHashtagList.size} hashtags, logo: ${draftCampaign.requiredLogoAsset})."
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ForgeAmber,
                            contentColor = StudioObsidian
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("save_campaign_button")
                    ) {
                        Icon(Icons.Default.AddTask, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Save Campaign Requirements Locally & Sync", fontWeight = FontWeight.Bold)
                    }

                    if (localSaveFeedback != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = VerifiedEmerald,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = localSaveFeedback.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = VerifiedEmerald
                            )
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = "SAVED WHOP CAMPAIGNS (${campaigns.size} AVAILABLE FOR VALIDATION)",
                style = MaterialTheme.typography.labelLarge,
                color = TextSecondary
            )
        }

        items(campaigns, key = { it.id }) { camp ->
            val isSelected = camp.id == selectedCampaignId
            val hasLocalLogo = camp.customLogoLocalPath.isNotBlank() && File(camp.customLogoLocalPath).exists()
            val savedLogoThumb = remember(camp.customLogoLocalPath) {
                LocalWhopCampaignStore.loadLogoPreviewFromPath(camp.customLogoLocalPath)
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onSelectCampaign(camp.id)
                        // Also populate form fields so the user can inspect or edit the selected campaign
                        name = camp.name
                        brandName = camp.brandName
                        payoutRate = camp.payoutPer1kViews
                        targetPlatform = camp.targetPlatform
                        preferredAspectRatio = camp.preferredAspectRatio.ifBlank { "9:16" }
                        minDuration = camp.minDurationSec.toFloat()
                        maxDuration = camp.maxDurationSec.toFloat()
                        logoPlacement = camp.logoPlacement
                        requiredLogoAsset = camp.requiredLogoAsset
                        customLogoLocalPath = camp.customLogoLocalPath
                        safeTopPct = camp.safeZoneTopPct.toFloat()
                        safeBottomPct = camp.safeZoneBottomPct.toFloat()
                        requiredHashtags = camp.requiredHashtags
                        mandatoryCta = camp.mandatoryCaptionCta
                        seoGuidance = camp.seoGuidance
                        minConfidence = camp.minConfidenceScore.toFloat()
                        logoPreviewBitmap = savedLogoThumb
                    }
                    .testTag("campaign_card_${camp.id}"),
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) StudioSurfaceElevated else StudioSurface
                ),
                border = BorderStroke(
                    width = if (isSelected) 1.5.dp else 1.dp,
                    color = if (isSelected) ForgeAmber else StudioBorder
                )
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
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
                            if (savedLogoThumb != null) {
                                Image(
                                    bitmap = savedLogoThumb.asImageBitmap(),
                                    contentDescription = "Saved custom logo for ${camp.name}",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .size(width = 48.dp, height = 32.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(StudioObsidian)
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Verified,
                                    contentDescription = null,
                                    tint = if (isSelected) ForgeAmber else TechnicalCyan,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "${camp.name} • ${camp.brandName}",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary
                                )
                                Text(
                                    text = if (hasLocalLogo) "Local Logo Verified • Saved Locally" else "Saved Locally for Validation",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = VerifiedEmerald
                                )
                            }
                        }
                        StatusPillBadge(text = camp.payoutPer1kViews, color = VerifiedEmerald)
                        IconButton(
                            onClick = { onDeleteCampaign(camp.id) },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "Delete campaign ${camp.name}",
                                tint = TextMuted
                            )
                        }
                    }

                    Text(
                        text = "Platform: ${camp.targetPlatform} (${camp.preferredAspectRatio.ifBlank { "9:16" }}) | Duration: ${camp.minDurationSec}s–${camp.maxDurationSec}s | Logo: ${camp.logoPlacement} (${camp.requiredLogoAsset})",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                    Text(
                        text = "Safe Zones: Top ${camp.safeZoneTopPct}%, Bottom ${camp.safeZoneBottomPct}% | Hashtags: ${camp.requiredHashtags} | CTA: \"${camp.mandatoryCaptionCta}\"",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
        }
    }
}
