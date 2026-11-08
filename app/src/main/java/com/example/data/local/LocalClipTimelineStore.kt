package com.example.data.local

import android.content.Context
import com.example.data.model.ClipCandidate
import com.example.data.model.ClipExtendedConfig
import com.example.data.model.DetectedSpeakerRegion
import com.example.data.model.ExportQualityConfig
import com.example.data.model.HookCaptionConfig
import com.example.data.model.LayoutSegment
import com.example.data.model.LogoPlacementConfig
import com.example.data.model.NormalizedRect
import com.example.data.model.VerticalLayoutType
import com.example.data.model.VideoProject
import com.example.data.model.WhopCampaign
import com.example.domain.AutoFramingEngine
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persists [ClipExtendedConfig] (dynamic layout timelines, manual segment overrides,
 * Logo ON/OFF toggle, Hook Caption ON/OFF toggle, export settings, Gallery save paths,
 * and processing checkpoints) in local JSON storage so users can reopen and edit timelines
 * at any time without violating Firestore schema rules.
 */
object LocalClipTimelineStore {

    private const val TIMELINES_FILE = "clipforge_clip_timelines_v2.json"
    private const val DEFAULTS_FILE = "clipforge_toggle_defaults.json"

    fun loadExtendedConfigForClip(
        context: Context?,
        clip: ClipCandidate,
        project: VideoProject? = null,
        campaign: WhopCampaign? = null
    ): ClipExtendedConfig {
        clip.extendedConfig?.let { existing ->
            if (existing.segments.isNotEmpty()) return existing
        }
        if (context != null) {
            val all = loadAllConfigs(context)
            val saved = all[clip.id]
            if (saved != null && saved.segments.isNotEmpty()) {
                return saved
            }
        }
        val (defaultLogoOn, defaultHookOn) = if (context != null) {
            loadDefaultToggleStates(context)
        } else {
            null to null
        }
        val generated = AutoFramingEngine.generateDefaultExtendedConfig(
            clip = clip,
            project = project,
            campaign = campaign,
            defaultLogoEnabled = defaultLogoOn,
            defaultHookEnabled = defaultHookOn
        )
        if (context != null && clip.id.isNotBlank()) {
            saveExtendedConfig(context, generated)
        }
        return generated
    }

    fun saveExtendedConfig(context: Context, config: ClipExtendedConfig) {
        if (config.clipId.isBlank()) return
        val all = loadAllConfigs(context).toMutableMap()
        all[config.clipId] = config
        writeAllConfigs(context, all)
        if (config.logoConfig.saveAsDefault) {
            saveDefaultToggleStates(
                context = context,
                logoEnabled = config.logoConfig.enabled,
                hookEnabled = config.hookConfig.enabled
            )
        }
    }

    fun deleteExtendedConfig(context: Context, clipId: String) {
        val all = loadAllConfigs(context).toMutableMap()
        if (all.remove(clipId) != null) {
            writeAllConfigs(context, all)
        }
    }

    fun saveDefaultToggleStates(
        context: Context,
        logoEnabled: Boolean,
        hookEnabled: Boolean
    ) {
        try {
            val obj = JSONObject().apply {
                put("logoEnabled", logoEnabled)
                put("hookEnabled", hookEnabled)
            }
            File(context.filesDir, DEFAULTS_FILE).writeText(obj.toString(2))
        } catch (_: Exception) {
        }
    }

    fun loadDefaultToggleStates(context: Context): Pair<Boolean?, Boolean?> {
        return try {
            val file = File(context.filesDir, DEFAULTS_FILE)
            if (!file.exists()) return null to null
            val obj = JSONObject(file.readText())
            val logo = if (obj.has("logoEnabled")) obj.optBoolean("logoEnabled", true) else null
            val hook = if (obj.has("hookEnabled")) obj.optBoolean("hookEnabled", true) else null
            logo to hook
        } catch (_: Exception) {
            null to null
        }
    }

    fun loadAllConfigs(context: Context): Map<String, ClipExtendedConfig> {
        return try {
            val file = File(context.filesDir, TIMELINES_FILE)
            if (!file.exists()) return emptyMap()
            val raw = file.readText()
            if (raw.isBlank()) return emptyMap()
            val array = JSONArray(raw)
            val map = mutableMapOf<String, ClipExtendedConfig>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val parsed = parseConfigJson(obj)
                if (parsed.clipId.isNotBlank()) {
                    map[parsed.clipId] = parsed
                }
            }
            map
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun writeAllConfigs(context: Context, configs: Map<String, ClipExtendedConfig>) {
        try {
            val array = JSONArray()
            configs.values.forEach { cfg ->
                array.put(serializeConfigJson(cfg))
            }
            File(context.filesDir, TIMELINES_FILE).writeText(array.toString(2))
        } catch (_: Exception) {
        }
    }

    fun serializeConfigJson(cfg: ClipExtendedConfig): JSONObject {
        return JSONObject().apply {
            put("clipId", cfg.clipId)
            put("projectId", cfg.projectId)
            put("hysteresisMinSegmentSec", cfg.hysteresisMinSegmentSec)
            put("transitionDurationMs", cfg.transitionDurationMs)
            put("lightweightModeAndroid", cfg.lightweightModeAndroid)
            put("savedGalleryUri", cfg.savedGalleryUri)
            put("savedGalleryPath", cfg.savedGalleryPath)
            put("localRenderedMp4Path", cfg.localRenderedMp4Path)
            put("lastRenderError", cfg.lastRenderError)
            put("checkpointStage", cfg.checkpointStage)

            put("logoConfig", JSONObject().apply {
                put("enabled", cfg.logoConfig.enabled)
                put("position", cfg.logoConfig.position)
                put("sizePct", cfg.logoConfig.sizePct)
                put("marginDp", cfg.logoConfig.marginDp)
                put("opacityPct", cfg.logoConfig.opacityPct)
                put("adaptivePlacement", cfg.logoConfig.adaptivePlacement)
                put("saveAsDefault", cfg.logoConfig.saveAsDefault)
            })

            put("hookConfig", JSONObject().apply {
                put("enabled", cfg.hookConfig.enabled)
                put("hookText", cfg.hookConfig.hookText)
                put("position", cfg.hookConfig.position)
                put("startSec", cfg.hookConfig.startSec.toDouble())
                put("durationSec", cfg.hookConfig.durationSec.toDouble())
                put("fontStyle", cfg.hookConfig.fontStyle)
                put("fontSizeSp", cfg.hookConfig.fontSizeSp)
                put("textColorHex", cfg.hookConfig.textColorHex)
                put("outlineColorHex", cfg.hookConfig.outlineColorHex)
                put("backgroundAlphaPct", cfg.hookConfig.backgroundAlphaPct)
                put("alignment", cfg.hookConfig.alignment)
                put("adaptiveAvoidFaces", cfg.hookConfig.adaptiveAvoidFaces)
            })

            put("exportConfig", JSONObject().apply {
                put("resolution", cfg.exportConfig.resolution)
                put("fps", cfg.exportConfig.fps)
                put("qualityPreset", cfg.exportConfig.qualityPreset)
                put("overwriteBehavior", cfg.exportConfig.overwriteBehavior)
            })

            val segArray = JSONArray()
            cfg.segments.forEach { seg ->
                segArray.put(JSONObject().apply {
                    put("id", seg.id)
                    put("startSec", seg.startSec)
                    put("endSec", seg.endSec)
                    put("layoutType", seg.layoutType.id)
                    put("sceneType", seg.sceneType)
                    put("sceneDescription", seg.sceneDescription)
                    put("zoomLevel", seg.zoomLevel.toDouble())
                    put("confidence", seg.confidence.toDouble())
                    put("needsManualReview", seg.needsManualReview)
                    put("manualReviewReason", seg.manualReviewReason)
                    put("isManuallyOverridden", seg.isManuallyOverridden)
                    put("transitionDurationMs", seg.transitionDurationMs)
                    put("audioContinuityVerified", seg.audioContinuityVerified)
                    put("webcamCropRect", rectToJson(seg.webcamCropRect))
                    if (seg.primaryContentRect != null) {
                        put("primaryContentRect", rectToJson(seg.primaryContentRect))
                    }
                    val activeArr = JSONArray()
                    seg.activeSpeakerIds.forEach { activeArr.put(it) }
                    put("activeSpeakerIds", activeArr)

                    val spkArr = JSONArray()
                    seg.detectedSpeakers.forEach { spk ->
                        spkArr.put(JSONObject().apply {
                            put("speakerId", spk.speakerId)
                            put("label", spk.label)
                            put("isTalking", spk.isTalking)
                            put("audioEnergyDb", spk.audioEnergyDb.toDouble())
                            put("faceConfidence", spk.faceConfidence.toDouble())
                            put("rect", rectToJson(spk.rect))
                        })
                    }
                    put("detectedSpeakers", spkArr)
                })
            }
            put("segments", segArray)
        }
    }

    fun parseConfigJson(obj: JSONObject): ClipExtendedConfig {
        val logoObj = obj.optJSONObject("logoConfig")
        val hookObj = obj.optJSONObject("hookConfig")
        val exportObj = obj.optJSONObject("exportConfig")
        val segArray = obj.optJSONArray("segments") ?: JSONArray()

        val segments = mutableListOf<LayoutSegment>()
        for (i in 0 until segArray.length()) {
            val s = segArray.getJSONObject(i)
            val activeIds = mutableListOf<Int>()
            val activeArr = s.optJSONArray("activeSpeakerIds")
            if (activeArr != null) {
                for (j in 0 until activeArr.length()) {
                    activeIds.add(activeArr.optInt(j, 1))
                }
            }
            val speakers = mutableListOf<DetectedSpeakerRegion>()
            val spkArr = s.optJSONArray("detectedSpeakers")
            if (spkArr != null) {
                for (j in 0 until spkArr.length()) {
                    val spk = spkArr.getJSONObject(j)
                    speakers.add(
                        DetectedSpeakerRegion(
                            speakerId = spk.optInt("speakerId", j + 1),
                            label = spk.optString("label", "Speaker #${j + 1}"),
                            rect = jsonToRect(spk.optJSONObject("rect")),
                            isTalking = spk.optBoolean("isTalking", true),
                            audioEnergyDb = spk.optDouble("audioEnergyDb", -10.0).toFloat(),
                            faceConfidence = spk.optDouble("faceConfidence", 0.90).toFloat()
                        )
                    )
                }
            }
            segments.add(
                LayoutSegment(
                    id = s.optString("id", "seg_$i"),
                    startSec = s.optLong("startSec", 0L),
                    endSec = s.optLong("endSec", 10L),
                    layoutType = VerticalLayoutType.fromId(s.optString("layoutType", "LAYOUT_A")),
                    sceneType = s.optString("sceneType", "GAMEPLAY_WITH_WEBCAM"),
                    sceneDescription = s.optString("sceneDescription", ""),
                    detectedSpeakers = speakers,
                    activeSpeakerIds = activeIds.ifEmpty { listOf(1) },
                    primaryContentRect = s.optJSONObject("primaryContentRect")?.let { jsonToRect(it) },
                    webcamCropRect = jsonToRect(s.optJSONObject("webcamCropRect")),
                    zoomLevel = s.optDouble("zoomLevel", 1.0).toFloat(),
                    confidence = s.optDouble("confidence", 0.9).toFloat(),
                    needsManualReview = s.optBoolean("needsManualReview", false),
                    manualReviewReason = s.optString("manualReviewReason", ""),
                    isManuallyOverridden = s.optBoolean("isManuallyOverridden", false),
                    transitionDurationMs = s.optInt("transitionDurationMs", 250),
                    audioContinuityVerified = s.optBoolean("audioContinuityVerified", true)
                )
            )
        }

        return ClipExtendedConfig(
            clipId = obj.optString("clipId", ""),
            projectId = obj.optString("projectId", ""),
            segments = segments,
            logoConfig = if (logoObj != null) {
                LogoPlacementConfig(
                    enabled = logoObj.optBoolean("enabled", true),
                    position = logoObj.optString("position", "TOP_RIGHT"),
                    sizePct = logoObj.optInt("sizePct", 14),
                    marginDp = logoObj.optInt("marginDp", 16),
                    opacityPct = logoObj.optInt("opacityPct", 95),
                    adaptivePlacement = logoObj.optBoolean("adaptivePlacement", false),
                    saveAsDefault = logoObj.optBoolean("saveAsDefault", true)
                )
            } else LogoPlacementConfig(),
            hookConfig = if (hookObj != null) {
                HookCaptionConfig(
                    enabled = hookObj.optBoolean("enabled", true),
                    hookText = hookObj.optString("hookText", ""),
                    position = hookObj.optString("position", "TOP_SAFE"),
                    startSec = hookObj.optDouble("startSec", 0.0).toFloat(),
                    durationSec = hookObj.optDouble("durationSec", 3.5).toFloat(),
                    fontStyle = hookObj.optString("fontStyle", "HORMOZI_IMPACT"),
                    fontSizeSp = hookObj.optInt("fontSizeSp", 22),
                    textColorHex = hookObj.optString("textColorHex", "#F59E0B"),
                    outlineColorHex = hookObj.optString("outlineColorHex", "#000000"),
                    backgroundAlphaPct = hookObj.optInt("backgroundAlphaPct", 85),
                    alignment = hookObj.optString("alignment", "CENTER"),
                    adaptiveAvoidFaces = hookObj.optBoolean("adaptiveAvoidFaces", true)
                )
            } else HookCaptionConfig(),
            exportConfig = if (exportObj != null) {
                ExportQualityConfig(
                    resolution = exportObj.optString("resolution", "1080x1920"),
                    fps = exportObj.optInt("fps", 30),
                    qualityPreset = exportObj.optString("qualityPreset", "HIGH_QUALITY"),
                    overwriteBehavior = exportObj.optString("overwriteBehavior", "VERSIONED")
                )
            } else ExportQualityConfig(),
            hysteresisMinSegmentSec = obj.optLong("hysteresisMinSegmentSec", 3L),
            transitionDurationMs = obj.optInt("transitionDurationMs", 250),
            lightweightModeAndroid = obj.optBoolean("lightweightModeAndroid", true),
            savedGalleryUri = obj.optString("savedGalleryUri", ""),
            savedGalleryPath = obj.optString("savedGalleryPath", ""),
            localRenderedMp4Path = obj.optString("localRenderedMp4Path", ""),
            lastRenderError = obj.optString("lastRenderError", ""),
            checkpointStage = obj.optString("checkpointStage", "ANALYZED")
        )
    }

    private fun rectToJson(rect: NormalizedRect): JSONObject = JSONObject().apply {
        put("leftPct", rect.leftPct.toDouble())
        put("topPct", rect.topPct.toDouble())
        put("widthPct", rect.widthPct.toDouble())
        put("heightPct", rect.heightPct.toDouble())
    }

    private fun jsonToRect(obj: JSONObject?): NormalizedRect {
        if (obj == null) return NormalizedRect(70f, 4f, 26f, 28f)
        return NormalizedRect(
            leftPct = obj.optDouble("leftPct", 70.0).toFloat(),
            topPct = obj.optDouble("topPct", 4.0).toFloat(),
            widthPct = obj.optDouble("widthPct", 26.0).toFloat(),
            heightPct = obj.optDouble("heightPct", 28.0).toFloat()
        )
    }
}
