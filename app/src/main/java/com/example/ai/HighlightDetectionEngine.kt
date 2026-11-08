package com.example.ai

import android.graphics.Bitmap
import android.util.Log
import com.example.data.model.ClipCandidate
import com.example.data.model.VideoProject
import com.example.data.model.WhopCampaign
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

private const val TAG = "HighlightDetectionEngine"

data class AiDetectionOutcome(
    val clips: List<ClipCandidate>,
    val providerId: String,
    val modelUsed: String,
    val latencyMs: Long,
    val estimatedTokens: Long,
    val retryCount: Int
)

data class ProviderTestOutcome(
    val success: Boolean,
    val latencyMs: Long,
    val message: String
)

class HighlightDetectionEngine {

    companion object {
        const val MIN_VIRAL_CLIP_DURATION_SEC = 22L // Strictly > 20 seconds
    }

    private val clipListSchema = Schema.obj(
        properties = mapOf(
            "clips" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "title" to Schema.string(description = "Punchy viral hook title specific to this video under 70 chars"),
                        "category" to Schema.enumeration(
                            values = listOf(
                                "FUNNY_MOMENT",
                                "REACTION",
                                "GAMING_HIGHLIGHT",
                                "CLUTCH_PLAY",
                                "EXCITING_MOMENT",
                                "MEMORABLE_DIALOGUE"
                            ),
                            description = "Highlight moment classification"
                        ),
                        "reasonForSelection" to Schema.string(description = "Concise 1-sentence viral mechanism & retention reason under 140 chars"),
                        "confidenceScore" to Schema.integer(description = "Confidence score from 75 to 99"),
                        "startSec" to Schema.integer(description = "Clip start timestamp in seconds"),
                        "endSec" to Schema.integer(description = "Clip end timestamp in seconds (endSec - startSec MUST be >= 22 seconds)"),
                        "reframeFocusX" to Schema.integer(description = "Suggested 9:16 horizontal crop focus percentage from 0 to 100"),
                        "subtitleText" to Schema.string(description = "Multi-line timecoded viral subtitles across the >20s clip, e.g. [00:00] Hook [00:06] Build up [00:14] Climax [00:21] Payoff"),
                        "captionText" to Schema.string(description = "Social media post caption with hook and CTA under 140 chars"),
                        "hashtags" to Schema.string(description = "Space-separated hashtags starting with #"),
                        "seoTitle" to Schema.string(description = "Search-optimized title under 80 chars"),
                        "seoKeywords" to Schema.string(description = "Comma-separated SEO keywords")
                    )
                ),
                description = "List of 1 to 10 detected timestamped viral highlight clips (each > 20 seconds)"
            )
        )
    )

    private val structuredConfig = generationConfig {
        responseMimeType = "application/json"
        responseSchema = clipListSchema
        temperature = 0.4f
    }

    // Lazy initialization per firebase-ai-logic-android requirements
    private val primaryModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = structuredConfig
        )
    }

    private val backupLiteModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.5-flash-lite",
            generationConfig = structuredConfig
        )
    }

    private val connectivityPingPrimary by lazy {
        Firebase.ai.generativeModel("gemini-3.8-flash")
    }

    private val connectivityPingBackupLite by lazy {
        Firebase.ai.generativeModel("gemini-3.5-flash-lite")
    }

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    private fun downscaleFrameForInference(source: Bitmap): Bitmap {
        val targetWidth = 240
        val ratio = source.height.toFloat() / source.width.coerceAtLeast(1).toFloat()
        val targetHeight = (targetWidth * ratio).toInt().coerceIn(90, 240)
        return if (source.width <= targetWidth && source.height <= targetHeight) {
            source
        } else {
            Bitmap.createScaledBitmap(source, targetWidth, targetHeight, true)
        }
    }

    /**
     * Runs the AI highlight detection pipeline with streaming response collection,
     * selectable clip count (1–10), enforced >20s clip duration (22s–55s),
     * video captions ON/OFF toggle, viral caption style presets,
     * bounded 35s–45s per-attempt timeouts, and automatic failover to gemini-3.5-flash-lite.
     */
    suspend fun detectHighlights(
        project: VideoProject,
        campaign: WhopCampaign?,
        sampleFrames: List<Bitmap>,
        desiredClipCount: Int = 3,
        includeVideoCaptions: Boolean = true,
        viralCaptionStyle: String = "HORMOZI_BOLD",
        maxRetries: Int = 2,
        allowBackupModelFailover: Boolean = true,
        economyTokenMode: Boolean = false,
        onProgressUpdate: (String, Float) -> Unit = { _, _ -> }
    ): AiDetectionOutcome {
        val startTime = System.currentTimeMillis()
        val targetClipCount = desiredClipCount.coerceIn(1, 10)
        val prompt = buildDetectionPrompt(
            project = project,
            campaign = campaign,
            desiredClipCount = targetClipCount,
            includeVideoCaptions = includeVideoCaptions,
            viralCaptionStyle = viralCaptionStyle,
            economyTokenMode = economyTokenMode
        )
        val totalRetries = maxRetries.coerceAtLeast(1)
        val compactFrame = if (economyTokenMode) {
            null
        } else {
            sampleFrames.firstOrNull()?.let { downscaleFrameForInference(it) }
        }
        var attempt = 0
        var lastException: Exception? = null

        while (attempt <= totalRetries) {
            val useBackup = economyTokenMode || (allowBackupModelFailover && attempt >= 1)
            val activeModel = if (useBackup) backupLiteModel else primaryModel
            val activeModelName = if (useBackup) "gemini-3.5-flash-lite" else "gemini-3.8-flash"
            val activeProviderId = if (useBackup) "firebase_ai_backup_lite" else "firebase_ai_primary"
            val timeoutMs = if (useBackup) 45_000L else 35_000L
            val includeFrameOnThisAttempt = (attempt == 0 && compactFrame != null && !economyTokenMode)

            onProgressUpdate(
                if (attempt == 0) {
                    val modeTag = if (economyTokenMode) " [Token-Saver Lite]" else ""
                    "Generating $targetClipCount viral clip(s) (>20s each) via $activeModelName$modeTag..."
                } else {
                    "Retry $attempt/$totalRetries: Failing over to ultra-fast $activeModelName ($targetClipCount clips)..."
                },
                0.48f
            )

            try {
                val rawJson = withTimeout(timeoutMs) {
                    val stream = if (includeFrameOnThisAttempt && compactFrame != null) {
                        activeModel.generateContentStream(
                            content {
                                image(compactFrame)
                                text(prompt)
                            }
                        )
                    } else {
                        activeModel.generateContentStream(prompt)
                    }

                    val builder = StringBuilder()
                    var chunkCount = 0
                    val expectedChars = (targetClipCount * 380).coerceAtLeast(450)
                    stream.collect { chunk ->
                        val part = chunk.text
                        if (!part.isNullOrEmpty()) {
                            builder.append(part)
                            chunkCount++
                            if (chunkCount % 2 == 0) {
                                val streamFraction = (builder.length.toFloat() / expectedChars.toFloat()).coerceIn(0.05f, 0.95f)
                                val totalProg = (0.48f + streamFraction * 0.40f).coerceIn(0.50f, 0.88f)
                                onProgressUpdate(
                                    "Streaming $targetClipCount viral clip(s) (>20s) from $activeModelName (${builder.length} chars)...",
                                    totalProg
                                )
                            }
                        }
                    }
                    builder.toString().trim()
                }

                if (rawJson.isBlank()) {
                    throw IllegalStateException("Model $activeModelName returned an empty response.")
                }

                onProgressUpdate("Parsing & validating $targetClipCount viral clips (>20s duration)...", 0.92f)
                val parsedClips = parseClipsFromJson(
                    jsonString = rawJson,
                    project = project,
                    campaign = campaign,
                    desiredClipCount = targetClipCount,
                    includeVideoCaptions = includeVideoCaptions,
                    viralCaptionStyle = viralCaptionStyle
                )
                val latency = (System.currentTimeMillis() - startTime).coerceAtLeast(1L)
                val estTokens = ((prompt.length + rawJson.length) / 4L).coerceAtLeast(120L) +
                    (if (includeFrameOnThisAttempt) 258L else 0L)

                return AiDetectionOutcome(
                    clips = parsedClips,
                    providerId = activeProviderId,
                    modelUsed = activeModelName,
                    latencyMs = latency,
                    estimatedTokens = estTokens,
                    retryCount = attempt
                )
            } catch (e: CancellationException) {
                if (e is TimeoutCancellationException) {
                    Log.e(TAG, "Highlight generation timed out on attempt ${attempt + 1} ($activeModelName, ${timeoutMs}ms)", e)
                    lastException = Exception(
                        "AI highlight detection on $activeModelName timed out after ${timeoutMs / 1000} seconds.",
                        e
                    )
                } else {
                    throw e
                }
            } catch (e: Exception) {
                Log.e(TAG, "Highlight generation failed on attempt ${attempt + 1} ($activeModelName)", e)
                lastException = e
                val msg = e.message.orEmpty().lowercase()
                if (msg.contains("app check") || msg.contains("attestation") || msg.contains("403")) {
                    break
                }
            }

            attempt++
            if (attempt <= totalRetries) {
                delay(600L)
            }
        }

        throw lastException ?: IllegalStateException("AI Highlight Detection failed after $totalRetries retries.")
    }

    suspend fun testProviderConnectivity(
        providerId: String,
        customEndpointUrl: String = ""
    ): ProviderTestOutcome {
        val start = System.currentTimeMillis()
        return try {
            when (providerId) {
                "firebase_ai_primary" -> {
                    val response = withTimeout(30_000L) {
                        connectivityPingPrimary.generateContent("Reply with the single word READY.")
                    }
                    val text = response.text?.trim().orEmpty()
                    val latency = (System.currentTimeMillis() - start).coerceAtLeast(1L)
                    if (text.isNotEmpty()) {
                        ProviderTestOutcome(
                            success = true,
                            latencyMs = latency,
                            message = "Live App Check + Gemini 3.8 Flash round-trip succeeded (${latency}ms): \"$text\""
                        )
                    } else {
                        ProviderTestOutcome(false, latency, "Empty response from gemini-3.8-flash.")
                    }
                }

                "firebase_ai_backup_lite" -> {
                    val response = withTimeout(30_000L) {
                        connectivityPingBackupLite.generateContent("Reply with the single word STANDBY_OK.")
                    }
                    val text = response.text?.trim().orEmpty()
                    val latency = (System.currentTimeMillis() - start).coerceAtLeast(1L)
                    if (text.isNotEmpty()) {
                        ProviderTestOutcome(
                            success = true,
                            latencyMs = latency,
                            message = "Live backup test passed on gemini-3.5-flash-lite (${latency}ms): \"$text\""
                        )
                    } else {
                        ProviderTestOutcome(false, latency, "Empty response from gemini-3.5-flash-lite.")
                    }
                }

                "custom_remote_worker" -> {
                    if (customEndpointUrl.isBlank() || !customEndpointUrl.startsWith("https://")) {
                        return ProviderTestOutcome(
                            success = false,
                            latencyMs = 0L,
                            message = "No valid HTTPS worker URL configured. Backup remains UNTESTED."
                        )
                    }
                    withContext(Dispatchers.IO) {
                        val req = Request.Builder().url(customEndpointUrl.trim()).get().build()
                        httpClient.newCall(req).execute().use { resp ->
                            val latency = (System.currentTimeMillis() - start).coerceAtLeast(1L)
                            if (resp.isSuccessful) {
                                ProviderTestOutcome(
                                    success = true,
                                    latencyMs = latency,
                                    message = "HTTP ${resp.code} OK from custom endpoint (${latency}ms)."
                                )
                            } else {
                                ProviderTestOutcome(
                                    success = false,
                                    latencyMs = latency,
                                    message = "Endpoint returned HTTP ${resp.code}."
                                )
                            }
                        }
                    }
                }

                else -> ProviderTestOutcome(false, 0L, "Unknown provider ID: $providerId")
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Provider test timed out", e)
            ProviderTestOutcome(false, System.currentTimeMillis() - start, "Timed out after 30s.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Provider live test failed for $providerId", e)
            ProviderTestOutcome(
                success = false,
                latencyMs = (System.currentTimeMillis() - start).coerceAtLeast(1L),
                message = e.localizedMessage ?: "Connection test failed."
            )
        }
    }

    private fun sampleTranscriptWindow(rawTranscript: String, maxChars: Int = 3200): String {
        val trimmed = rawTranscript.trim()
        if (trimmed.isEmpty()) return ""
        if (trimmed.length <= maxChars) return trimmed

        val sliceSize = maxChars / 3
        val head = trimmed.take(sliceSize)
        val midStart = (trimmed.length / 2 - sliceSize / 2).coerceAtLeast(sliceSize)
        val middle = trimmed.substring(midStart, (midStart + sliceSize).coerceAtMost(trimmed.length))
        val tail = trimmed.takeLast(sliceSize)

        return buildString {
            appendLine(head)
            appendLine("\n[... middle section of stream transcript ...]")
            appendLine(middle)
            appendLine("\n[... final section of stream transcript ...]")
            appendLine(tail)
        }
    }

    private fun buildDetectionPrompt(
        project: VideoProject,
        campaign: WhopCampaign?,
        desiredClipCount: Int = 3,
        includeVideoCaptions: Boolean = true,
        viralCaptionStyle: String = "HORMOZI_BOLD",
        economyTokenMode: Boolean = false
    ): String {
        val targetAspect = campaign?.preferredAspectRatio?.ifBlank { "9:16" } ?: "9:16"
        val effectiveMinDur = max(campaign?.minDurationSec ?: 25L, MIN_VIRAL_CLIP_DURATION_SEC)
        val effectiveMaxDur = max(campaign?.maxDurationSec ?: 55L, effectiveMinDur + 10L)

        val campaignBlock = if (campaign != null) {
            """
            ACTIVE WHOP CAMPAIGN RULES (MUST ENFORCE):
            - Campaign Name: ${campaign.name} (Brand: ${campaign.brandName})
            - Target Platform: ${campaign.targetPlatform} (Aspect Ratio: $targetAspect)
            - MANDATORY Clip Duration: EVERY clip MUST be MORE THAN 20 SECONDS (between $effectiveMinDur seconds and $effectiveMaxDur seconds, i.e., endSec - startSec >= $effectiveMinDur)
            - Required Logo Placement: ${campaign.logoPlacement} (${campaign.requiredLogoAsset})
            - Required Hashtags: ${campaign.requiredHashtags}
            - Mandatory Caption CTA: "${campaign.mandatoryCaptionCta}"
            - SEO Guidance: ${campaign.seoGuidance}
            - Minimum Confidence Score: ${campaign.minConfidenceScore}
            """.trimIndent()
        } else {
            "Standard $targetAspect short-form clipping rules: EVERY clip MUST be MORE THAN 20 SECONDS (between 24 and 48 seconds)."
        }

        val captionStyleInstruction = when (viralCaptionStyle) {
            "GAMING_HYPE" -> "GAMING HYPE style: high-adrenaline multi-timestamp lines across the >20s clip (e.g. '[00:00] WAIT FOR THIS MOMENT!! [00:06] NOBODY SAW THIS COMING [00:13] UNREAL REACTION [00:20] CHAT WENT CRAZY')"
            "STORYTELLER" -> "STORYTELLER HOOK style: curiosity-gap narrative across the >20s clip (e.g. '[00:00] Nobody believed this would happen... [00:07] Watch the reaction closely [00:15] Until THIS moment changed everything [00:22] Legendary payoff')"
            "CLEAN_MINIMAL" -> "CLEAN MINIMAL style: crisp timecoded creator subtitles every 6 seconds across the clip"
            else -> "HORMOZI BOLD style: ultra-punchy 4-6 word kinetic timecoded lines across the >20s clip (e.g. '[00:00] STOP SCROLLING RIGHT NOW [00:06] WATCH WHAT HAPPENS NEXT [00:13] THIS CHANGED EVERYTHING [00:20] UNBELIEVABLE PAYOFF')"
        }

        val maxTranscriptChars = if (economyTokenMode) 1800 else 3200
        val sampledTranscript = sampleTranscriptWindow(project.transcriptText, maxChars = maxTranscriptChars)

        return """
            You are ClipForge AI, an elite viral short-form video producer for YouTube Shorts, TikTok, Instagram Reels, and Whop Creator Bounties.
            Analyze the source video metadata, creator/channel, thumbnail visual cues, heatmap peaks, and timecoded transcript/chapters to find EXACTLY $desiredClipCount clip(s) with the highest probability of going VIRAL.

            CRITICAL VIRAL CLIPPING REQUIREMENTS:
            1. DURATION > 20 SECONDS (MANDATORY): Every single clip MUST have `endSec - startSec >= $effectiveMinDur` (strictly more than 20 seconds, ideally 25 to 45 seconds). Never output a clip shorter than 22 seconds.
            2. TRUE VIRAL MOMENT SELECTION: Pick the exact timestamp windows where the video has the strongest viral potential (2-second scroll-stopper hook, peak reaction, hilarious interaction, shocking twist, or most-replayed climax). Base all titles, subtitles, and captions strictly on '${project.title}' and its actual transcript/creators — NEVER invent unrelated topics.
            3. MULTI-TIMESTAMP SUBTITLES (`subtitleText`):
               - Burned-in Video Captions Mode: ${if (includeVideoCaptions) "ENABLED" else "DISABLED (still provide backup caption text)"}
               - Style Preset: $captionStyleInstruction
               - Include 4 to 5 timecoded lines (`[00:00] ... [00:06] ... [00:13] ... [00:20] ...`) that progress through the >20s viral moment.
            4. Classify each clip into one of: FUNNY_MOMENT, REACTION, GAMING_HIGHLIGHT, CLUTCH_PLAY, EXCITING_MOMENT, MEMORABLE_DIALOGUE.

            SOURCE VIDEO / STREAM METADATA:
            - Project Title: ${project.title}
            - Source Info: ${project.sourceFileName}
            - Total Duration: ${project.durationSec} seconds (All clip startSec and endSec MUST be within 0..${project.durationSec} and endSec - startSec >= $effectiveMinDur)

            $campaignBlock

            SOURCE TRANSCRIPT / CAPTIONS / STREAM CHAPTERS:
            ${sampledTranscript.ifBlank { "Infer high-virality timestamped segments (>22s each) within 0..${project.durationSec}s specifically tailored to '${project.title}'." }}
        """.trimIndent()
    }

    private fun parseClipsFromJson(
        jsonString: String,
        project: VideoProject,
        campaign: WhopCampaign?,
        desiredClipCount: Int = 3,
        includeVideoCaptions: Boolean = true,
        viralCaptionStyle: String = "HORMOZI_BOLD"
    ): List<ClipCandidate> {
        val root = JSONObject(jsonString)
        val array = root.getJSONArray("clips")
        val results = mutableListOf<ClipCandidate>()
        val maxDur = project.durationSec.coerceAtLeast(60L)
        // Strictly enforce > 20 seconds (minimum 22s, default 25s)
        val minClipLen = max(campaign?.minDurationSec ?: 25L, MIN_VIRAL_CLIP_DURATION_SEC)
        val maxClipLen = max(campaign?.maxDurationSec ?: 55L, minClipLen + 10L)
        val validAspect = if (campaign?.preferredAspectRatio in listOf("9:16", "16:9", "1:1")) {
            campaign?.preferredAspectRatio ?: "9:16"
        } else {
            "9:16"
        }

        val limit = array.length().coerceAtMost(desiredClipCount.coerceIn(1, 10))
        for (i in 0 until limit) {
            val item = array.getJSONObject(i)
            val maxAllowedStart = (maxDur - minClipLen).coerceAtLeast(0L)
            val rawStart = item.optLong("startSec", (i * 28L).coerceAtMost(maxAllowedStart))
                .coerceIn(0L, maxAllowedStart)
            val rawEnd = item.optLong("endSec", rawStart + 28L)
            val boundedDuration = (rawEnd - rawStart).coerceIn(minClipLen, maxClipLen)
            val finalEnd = (rawStart + boundedDuration).coerceAtLeast(rawStart + minClipLen)

            val rawCategory = item.optString("category", "EXCITING_MOMENT")
            val validCategory = if (rawCategory in setOf(
                    "FUNNY_MOMENT", "REACTION", "GAMING_HIGHLIGHT",
                    "CLUTCH_PLAY", "EXCITING_MOMENT", "MEMORABLE_DIALOGUE"
                )
            ) rawCategory else "EXCITING_MOMENT"

            val rawTitle = item.optString("title", "${project.title.take(48)} — Viral Clip #${i + 1}").take(150)
            val rawSubtitle = item.optString("subtitleText", "").ifBlank {
                when (viralCaptionStyle) {
                    "GAMING_HYPE" -> "[00:00] WAIT FOR IT... ${rawTitle.uppercase()}!! [00:07] WATCH THIS REACTION [00:14] NOBODY EXPECTED THIS [00:21] UNREAL PAYOFF"
                    "STORYTELLER" -> "[00:00] Watch closely... [00:07] $rawTitle [00:14] Everything changed right here [00:21] Wait for the ending"
                    "CLEAN_MINIMAL" -> "[00:00] $rawTitle [00:08] Peak viral moment [00:16] Full reaction & payoff"
                    else -> "[00:00] STOP SCROLLING: ${rawTitle.uppercase()} [00:07] WATCH WHAT HAPPENS NEXT [00:15] UNBELIEVABLE MOMENT [00:21] FULL REACTION"
                }
            }

            results.add(
                ClipCandidate(
                    projectId = project.id,
                    campaignId = campaign?.id.orEmpty(),
                    title = rawTitle,
                    category = validCategory,
                    reasonForSelection = item.optString("reasonForSelection", "High-retention viral moment (${finalEnd - rawStart}s) with strong opening hook and payoff.").take(600),
                    confidenceScore = item.optLong("confidenceScore", 91L).coerceIn(0L, 100L),
                    startSec = rawStart,
                    endSec = finalEnd,
                    aspectRatio = validAspect,
                    reframeFocusX = item.optLong("reframeFocusX", 50L).coerceIn(0L, 100L),
                    subtitlesEnabled = includeVideoCaptions,
                    subtitleText = rawSubtitle.take(1000),
                    captionText = item.optString("captionText", "").take(1000),
                    hashtags = item.optString("hashtags", campaign?.requiredHashtags ?: "#clips #shorts #viral").take(400),
                    seoTitle = item.optString("seoTitle", rawTitle).take(200),
                    seoKeywords = item.optString("seoKeywords", "viral clips, highlights, shorts").take(300),
                    logoPlacement = campaign?.logoPlacement ?: "TOP_RIGHT",
                    reviewStatus = "PROPOSED"
                )
            )
        }
        return results.sortedByDescending { it.confidenceScore }
    }
}
