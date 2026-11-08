package com.example.data.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.provider.OpenableColumns
import com.example.data.model.CampaignPreset
import com.example.data.model.WhopCampaign
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class CustomLogoSelectionResult(
    val fileName: String,
    val localFilePath: String,
    val width: Int,
    val height: Int,
    val sizeBytes: Long,
    val previewBitmap: Bitmap?
)

/**
 * Local on-device persistence store for Whop campaign requirements, reusable Campaign Presets,
 * and custom logo files. Ensures campaign rules, caption styles, and hashtags are saved locally
 * in app storage for deterministic validation and 1-tap reuse across projects.
 */
object LocalWhopCampaignStore {

    private const val LOCAL_CAMPAIGNS_FILE = "whop_campaigns_local.json"
    private const val LOCAL_PRESETS_FILE = "whop_campaign_presets.json"
    private const val LOGOS_DIR = "whop_logos"

    fun loadLocalCampaigns(context: Context, userId: String): List<WhopCampaign> {
        return try {
            val file = File(context.filesDir, LOCAL_CAMPAIGNS_FILE)
            if (!file.exists()) return emptyList()
            val raw = file.readText()
            if (raw.isBlank()) return emptyList()

            val array = JSONArray(raw)
            val results = mutableListOf<WhopCampaign>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val owner = obj.optString("userId", "")
                if (owner.isBlank() || owner == userId) {
                    results.add(
                        WhopCampaign(
                            id = obj.optString("id", "local_${i + 1}"),
                            userId = owner.ifBlank { userId },
                            name = obj.optString("name", "Whop Campaign"),
                            brandName = obj.optString("brandName", "Creator Brand"),
                            payoutPer1kViews = obj.optString("payoutPer1kViews", "$1.50 / 1K views"),
                            targetPlatform = obj.optString("targetPlatform", "TIKTOK"),
                            preferredAspectRatio = obj.optString("preferredAspectRatio", "9:16"),
                            minDurationSec = obj.optLong("minDurationSec", 22L).coerceAtLeast(22L),
                            maxDurationSec = obj.optLong("maxDurationSec", 60L).coerceAtLeast(30L),
                            logoPlacement = obj.optString("logoPlacement", "TOP_RIGHT"),
                            requiredLogoAsset = obj.optString("requiredLogoAsset", "brand_watermark.png"),
                            customLogoLocalPath = obj.optString("customLogoLocalPath", ""),
                            safeZoneTopPct = obj.optLong("safeZoneTopPct", 12L),
                            safeZoneBottomPct = obj.optLong("safeZoneBottomPct", 20L),
                            requiredHashtags = obj.optString("requiredHashtags", "#whop #clips"),
                            mandatoryCaptionCta = obj.optString("mandatoryCaptionCta", "Check link in bio"),
                            seoGuidance = obj.optString("seoGuidance", "Front-load primary keyword in first 40 chars"),
                            minConfidenceScore = obj.optLong("minConfidenceScore", 75L)
                        )
                    )
                }
            }
            results
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveCampaignLocally(context: Context, userId: String, campaign: WhopCampaign): WhopCampaign {
        val currentAll = loadAllUsersLocalCampaigns(context).toMutableList()
        val resolvedId = campaign.id.ifBlank { "local_camp_${UUID.randomUUID().toString().take(8)}" }
        val normalized = campaign.copy(
            id = resolvedId,
            userId = userId,
            preferredAspectRatio = campaign.preferredAspectRatio.ifBlank { "9:16" }
        )

        val existingIdx = currentAll.indexOfFirst {
            it.id == resolvedId || (it.userId == userId && it.name.equals(normalized.name, ignoreCase = true))
        }
        if (existingIdx >= 0) {
            currentAll[existingIdx] = normalized
        } else {
            currentAll.add(0, normalized)
        }
        writeCampaignsToDisk(context, currentAll)
        return normalized
    }

    fun deleteCampaignLocally(context: Context, campaignId: String) {
        val currentAll = loadAllUsersLocalCampaigns(context).filterNot { it.id == campaignId }
        writeCampaignsToDisk(context, currentAll)
    }

    /**
     * Merges Firestore-synced campaigns with locally persisted campaign rules
     * so `preferredAspectRatio`, `customLogoLocalPath`, and any offline-saved campaigns
     * are always available for validation.
     */
    fun mergeWithRemoteCampaigns(
        context: Context,
        userId: String,
        remoteCampaigns: List<WhopCampaign>
    ): List<WhopCampaign> {
        val localList = loadLocalCampaigns(context, userId)
        if (localList.isEmpty()) return remoteCampaigns

        val mergedRemote = remoteCampaigns.map { remote ->
            val matchingLocal = localList.firstOrNull {
                it.id == remote.id || it.name.equals(remote.name, ignoreCase = true)
            }
            if (matchingLocal != null) {
                remote.copy(
                    preferredAspectRatio = matchingLocal.preferredAspectRatio.ifBlank { "9:16" },
                    customLogoLocalPath = matchingLocal.customLogoLocalPath
                )
            } else {
                remote
            }
        }

        val remoteIdsAndNames = mergedRemote.map { it.id to it.name.lowercase() }
        val localOnly = localList.filter { loc ->
            remoteIdsAndNames.none { (rId, rName) ->
                rId == loc.id || rName == loc.name.lowercase()
            }
        }
        return mergedRemote + localOnly
    }

    /**
     * Copies a user-selected custom logo image from the Android Photo Picker into
     * local app storage (`filesDir/whop_logos/`) and decodes a thumbnail preview.
     */
    suspend fun copyCustomLogoToLocalStorage(
        context: Context,
        sourceUri: Uri
    ): Result<CustomLogoSelectionResult> = withContext(Dispatchers.IO) {
        runCatching {
            val rawName = queryDisplayName(context, sourceUri) ?: "custom_campaign_logo.png"
            val sanitizedName = rawName.replace(Regex("[^a-zA-Z0-9._\\-]"), "_").take(80)
            val logosDir = File(context.filesDir, LOGOS_DIR).apply { mkdirs() }
            val destFile = File(logosDir, "${System.currentTimeMillis()}_$sanitizedName")

            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            } ?: throw IllegalStateException("Unable to read selected logo file.")

            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(destFile.absolutePath, boundsOptions)
            val width = boundsOptions.outWidth.coerceAtLeast(1)
            val height = boundsOptions.outHeight.coerceAtLeast(1)

            val preview = decodeScaledLogoPreview(destFile.absolutePath, 160)

            CustomLogoSelectionResult(
                fileName = sanitizedName,
                localFilePath = destFile.absolutePath,
                width = width,
                height = height,
                sizeBytes = destFile.length(),
                previewBitmap = preview
            )
        }
    }

    /**
     * Generates a real local PNG brand watermark file in `filesDir/whop_logos/`
     * so users can immediately test local logo file validation even on an emulator with no gallery photos.
     */
    suspend fun generateLocalBrandWatermarkFile(
        context: Context,
        brandName: String
    ): Result<CustomLogoSelectionResult> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanBrand = brandName.trim().ifBlank { "WHOP PARTNER" }.uppercase().take(16)
            val safeFileSlug = cleanBrand.lowercase().replace(Regex("[^a-z0-9]"), "_").ifBlank { "whop_logo" }
            val fileName = "${safeFileSlug}_watermark.png"
            val logosDir = File(context.filesDir, LOGOS_DIR).apply { mkdirs() }
            val destFile = File(logosDir, fileName)

            val width = 320
            val height = 96
            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)

            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(230, 18, 22, 31)
                style = Paint.Style.FILL
            }
            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(245, 158, 11)
                style = Paint.Style.STROKE
                strokeWidth = 4f
            }
            val rect = RectF(4f, 4f, width - 4f, height - 4f)
            canvas.drawRoundRect(rect, 18f, 18f, bgPaint)
            canvas.drawRoundRect(rect, 18f, 18f, borderPaint)

            val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(245, 158, 11)
                textSize = 20f
                isFakeBoldText = true
            }
            val brandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 26f
                isFakeBoldText = true
            }
            canvas.drawText("WHOP VERIFIED", 22f, 36f, badgePaint)
            canvas.drawText(cleanBrand, 22f, 70f, brandPaint)

            FileOutputStream(destFile).use { out ->
                bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
            }

            CustomLogoSelectionResult(
                fileName = fileName,
                localFilePath = destFile.absolutePath,
                width = width,
                height = height,
                sizeBytes = destFile.length(),
                previewBitmap = bmp
            )
        }
    }

    fun loadLogoPreviewFromPath(localPath: String): Bitmap? {
        if (localPath.isBlank()) return null
        val file = File(localPath)
        if (!file.exists()) return null
        return decodeScaledLogoPreview(file.absolutePath, 160)
    }

    private fun decodeScaledLogoPreview(path: String, maxDim: Int): Bitmap? {
        return try {
            val raw = BitmapFactory.decodeFile(path) ?: return null
            val ratio = raw.width.toFloat() / raw.height.coerceAtLeast(1).toFloat()
            val targetW = if (ratio >= 1f) maxDim else (maxDim * ratio).toInt().coerceAtLeast(32)
            val targetH = if (ratio >= 1f) (maxDim / ratio).toInt().coerceAtLeast(32) else maxDim
            Bitmap.createScaledBitmap(raw, targetW, targetH, true)
        } catch (_: Exception) {
            null
        }
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) cursor.getString(idx) else null
                    } else null
                }
        } catch (_: Exception) {
            null
        }
    }

    private fun loadAllUsersLocalCampaigns(context: Context): List<WhopCampaign> {
        return loadLocalCampaigns(context, "")
    }

    private fun writeCampaignsToDisk(context: Context, campaigns: List<WhopCampaign>) {
        try {
            val array = JSONArray()
            campaigns.forEach { camp ->
                val obj = JSONObject().apply {
                    put("id", camp.id)
                    put("userId", camp.userId)
                    put("name", camp.name)
                    put("brandName", camp.brandName)
                    put("payoutPer1kViews", camp.payoutPer1kViews)
                    put("targetPlatform", camp.targetPlatform)
                    put("preferredAspectRatio", camp.preferredAspectRatio.ifBlank { "9:16" })
                    put("minDurationSec", camp.minDurationSec)
                    put("maxDurationSec", camp.maxDurationSec)
                    put("logoPlacement", camp.logoPlacement)
                    put("requiredLogoAsset", camp.requiredLogoAsset)
                    put("customLogoLocalPath", camp.customLogoLocalPath)
                    put("safeZoneTopPct", camp.safeZoneTopPct)
                    put("safeZoneBottomPct", camp.safeZoneBottomPct)
                    put("requiredHashtags", camp.requiredHashtags)
                    put("mandatoryCaptionCta", camp.mandatoryCaptionCta)
                    put("seoGuidance", camp.seoGuidance)
                    put("minConfidenceScore", camp.minConfidenceScore)
                }
                array.put(obj)
            }
            File(context.filesDir, LOCAL_CAMPAIGNS_FILE).writeText(array.toString(2))
        } catch (_: Exception) {
        }
    }

    fun loadCampaignPresets(context: Context, userId: String): List<CampaignPreset> {
        val file = File(context.filesDir, LOCAL_PRESETS_FILE)
        if (!file.exists() || file.readText().isBlank()) {
            val defaults = defaultStarterPresets(userId)
            writePresetsToDisk(context, defaults)
            return defaults
        }
        return try {
            val array = JSONArray(file.readText())
            val results = mutableListOf<CampaignPreset>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val owner = obj.optString("userId", "")
                if (owner.isBlank() || owner == userId) {
                    results.add(
                        CampaignPreset(
                            id = obj.optString("id", "preset_${i + 1}"),
                            userId = owner.ifBlank { userId },
                            presetName = obj.optString("presetName", "Custom Preset #${i + 1}"),
                            campaignName = obj.optString("campaignName", "Whop Creator Bounty"),
                            brandName = obj.optString("brandName", "Creator Brand"),
                            payoutPer1kViews = obj.optString("payoutPer1kViews", "$2.00 / 1K views"),
                            targetPlatform = obj.optString("targetPlatform", "TIKTOK"),
                            preferredAspectRatio = obj.optString("preferredAspectRatio", "9:16"),
                            minDurationSec = obj.optLong("minDurationSec", 22L).coerceAtLeast(22L),
                            maxDurationSec = obj.optLong("maxDurationSec", 55L).coerceAtLeast(30L),
                            logoPlacement = obj.optString("logoPlacement", "TOP_RIGHT"),
                            requiredLogoAsset = obj.optString("requiredLogoAsset", "whop_partner_watermark.png"),
                            customLogoLocalPath = obj.optString("customLogoLocalPath", ""),
                            safeZoneTopPct = obj.optLong("safeZoneTopPct", 12L),
                            safeZoneBottomPct = obj.optLong("safeZoneBottomPct", 20L),
                            requiredHashtags = obj.optString("requiredHashtags", "#whop #clipping #viral"),
                            mandatoryCaptionCta = obj.optString("mandatoryCaptionCta", "Check link in bio"),
                            seoGuidance = obj.optString("seoGuidance", "Front-load primary keyword in first 40 chars"),
                            minConfidenceScore = obj.optLong("minConfidenceScore", 78L),
                            viralCaptionStyle = obj.optString("viralCaptionStyle", "HORMOZI_BOLD"),
                            includeVideoCaptions = obj.optBoolean("includeVideoCaptions", true),
                            defaultClipCount = obj.optInt("defaultClipCount", 3).coerceIn(1, 10)
                        )
                    )
                }
            }
            if (results.isEmpty()) defaultStarterPresets(userId) else results
        } catch (_: Exception) {
            defaultStarterPresets(userId)
        }
    }

    fun saveCampaignPreset(context: Context, userId: String, preset: CampaignPreset): List<CampaignPreset> {
        val current = loadCampaignPresets(context, userId).toMutableList()
        val resolvedId = preset.id.ifBlank { "preset_${UUID.randomUUID().toString().take(8)}" }
        val cleanName = preset.presetName.trim().ifBlank { "${preset.brandName} (${preset.preferredAspectRatio})" }
        val normalized = preset.copy(
            id = resolvedId,
            userId = userId,
            presetName = cleanName,
            defaultClipCount = preset.defaultClipCount.coerceIn(1, 10)
        )
        val existingIdx = current.indexOfFirst {
            it.id == resolvedId || it.presetName.equals(cleanName, ignoreCase = true)
        }
        if (existingIdx >= 0) {
            current[existingIdx] = normalized
        } else {
            current.add(0, normalized)
        }
        writePresetsToDisk(context, current)
        return current
    }

    fun deleteCampaignPreset(context: Context, userId: String, presetId: String): List<CampaignPreset> {
        val updated = loadCampaignPresets(context, userId).filterNot { it.id == presetId }
        writePresetsToDisk(context, updated)
        return updated
    }

    private fun defaultStarterPresets(userId: String): List<CampaignPreset> = listOf(
        CampaignPreset(
            id = "preset_hormozi_bounty",
            userId = userId,
            presetName = "Hormozi Bold 9:16 Bounty",
            campaignName = "Whop Creator Growth Bounty",
            brandName = "Whop Creators Pro",
            payoutPer1kViews = "$2.50 / 1K views",
            targetPlatform = "TIKTOK",
            preferredAspectRatio = "9:16",
            minDurationSec = 22L,
            maxDurationSec = 50L,
            logoPlacement = "TOP_RIGHT",
            requiredLogoAsset = "whop_partner_badge.png",
            safeZoneTopPct = 12L,
            safeZoneBottomPct = 22L,
            requiredHashtags = "#whop #clipping #viral #shorts",
            mandatoryCaptionCta = "Join the bounty via link in bio",
            seoGuidance = "Front-load bold retention hook in first 40 chars",
            minConfidenceScore = 80L,
            viralCaptionStyle = "HORMOZI_BOLD",
            includeVideoCaptions = true,
            defaultClipCount = 3
        ),
        CampaignPreset(
            id = "preset_gaming_hype",
            userId = userId,
            presetName = "Gaming Clutch Hype Pack",
            campaignName = "Pro Esports & Stream Highlights",
            brandName = "ApexGaming",
            payoutPer1kViews = "$2.00 / 1K views",
            targetPlatform = "SHORTS",
            preferredAspectRatio = "9:16",
            minDurationSec = 22L,
            maxDurationSec = 55L,
            logoPlacement = "TOP_LEFT",
            requiredLogoAsset = "apex_gaming_watermark.png",
            safeZoneTopPct = 12L,
            safeZoneBottomPct = 20L,
            requiredHashtags = "#gaming #clutch #twitchclips #whop",
            mandatoryCaptionCta = "Watch full stream via link in bio",
            seoGuidance = "Include game title + clutch keyword in SEO title",
            minConfidenceScore = 78L,
            viralCaptionStyle = "GAMING_HYPE",
            includeVideoCaptions = true,
            defaultClipCount = 5
        ),
        CampaignPreset(
            id = "preset_podcast_story",
            userId = userId,
            presetName = "Podcast Storyteller Reels",
            campaignName = "Podcast & Interview Viral Clips",
            brandName = "StudioTalks",
            payoutPer1kViews = "$1.80 / 1K views",
            targetPlatform = "REELS",
            preferredAspectRatio = "9:16",
            minDurationSec = 24L,
            maxDurationSec = 60L,
            logoPlacement = "BOTTOM_RIGHT",
            requiredLogoAsset = "studiotalks_logo.png",
            safeZoneTopPct = 10L,
            safeZoneBottomPct = 22L,
            requiredHashtags = "#podcastclips #storytime #reels #viral",
            mandatoryCaptionCta = "Full episode linked in bio",
            seoGuidance = "Use curiosity-gap question in SEO title",
            minConfidenceScore = 75L,
            viralCaptionStyle = "STORYTELLER",
            includeVideoCaptions = true,
            defaultClipCount = 4
        )
    )

    private fun writePresetsToDisk(context: Context, presets: List<CampaignPreset>) {
        try {
            val array = JSONArray()
            presets.forEach { p ->
                val obj = JSONObject().apply {
                    put("id", p.id)
                    put("userId", p.userId)
                    put("presetName", p.presetName)
                    put("campaignName", p.campaignName)
                    put("brandName", p.brandName)
                    put("payoutPer1kViews", p.payoutPer1kViews)
                    put("targetPlatform", p.targetPlatform)
                    put("preferredAspectRatio", p.preferredAspectRatio)
                    put("minDurationSec", p.minDurationSec)
                    put("maxDurationSec", p.maxDurationSec)
                    put("logoPlacement", p.logoPlacement)
                    put("requiredLogoAsset", p.requiredLogoAsset)
                    put("customLogoLocalPath", p.customLogoLocalPath)
                    put("safeZoneTopPct", p.safeZoneTopPct)
                    put("safeZoneBottomPct", p.safeZoneBottomPct)
                    put("requiredHashtags", p.requiredHashtags)
                    put("mandatoryCaptionCta", p.mandatoryCaptionCta)
                    put("seoGuidance", p.seoGuidance)
                    put("minConfidenceScore", p.minConfidenceScore)
                    put("viralCaptionStyle", p.viralCaptionStyle)
                    put("includeVideoCaptions", p.includeVideoCaptions)
                    put("defaultClipCount", p.defaultClipCount)
                }
                array.put(obj)
            }
            File(context.filesDir, LOCAL_PRESETS_FILE).writeText(array.toString(2))
        } catch (_: Exception) {
        }
    }
}

