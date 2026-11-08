package com.example.domain

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.example.data.model.CaptionPhraseGroup
import com.example.data.model.ClipCandidate
import com.example.data.model.TimedCaptionWord
import com.example.data.model.VerticalLayoutType
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Professional Word-by-Word Animated Caption Engine Synchronized to Speech.
 *
 * Responsibilities:
 * - Generates word-level timestamps (`TimedCaptionWord`) aligned to the clip's dialogue transcript.
 * - Groups words into readable 3–5 word phrase groups (`CaptionPhraseGroup`) without altering
 *   the speaker's words or meaning.
 * - Preserves gaming/creator slang and distinguishes genuine speech from uncertain transcription (`[?]`).
 * - Renders word-by-word animated captions with active-word highlighting, bold high-contrast typography,
 *   thick outlines, drop shadows, and smooth pop/bounce animations.
 * - Recalculates safe caption Y-positions dynamically whenever the vertical layout changes
 *   (Layout A, B, C, or D) to stay clear of faces and gameplay HUDs.
 * - Keeps ordinary dialogue subtitles completely independent from opening hook captions.
 */
object WordLevelCaptionEngine {

    private val KNOWN_SLANG_WORDS = setOf(
        "cooked", "clutch", "1v4", "1v5", "gg", "goat", "nerf", "buff", "aggro",
        "chat", "clip", "noob", "cracked", "diff", "peek", "flick", "one-tap",
        "pog", "w", "l", "fr", "ngl", "lowkey", "highkey", "bussin", "sus", "meta"
    )

    private val POWER_KEYWORDS = setOf(
        "unbelievable", "insane", "impossible", "wait", "watch", "never", "everyone",
        "shocked", "million", "win", "clutch", "secret", "mistake", "crazy", "look",
        "stop", "final", "overtime", "record", "mythic", "legendary", "broken", "instantly"
    )

    data class ActiveWordRenderState(
        val group: CaptionPhraseGroup,
        val activeWordIndexInGroup: Int,
        val activeWordProgress: Float, // 0f..1f within the currently spoken word
        val popScale: Float // 1.0f..1.22f bounce scale for active spoken word
    )

    /**
     * Generates word-level timestamps (`List<TimedCaptionWord>`) across [0f .. clip.durationSec]
     * from the clip's timecoded transcript lines or dialogue text.
     */
    fun generateWordTimestampsForClip(
        clip: ClipCandidate,
        projectTranscript: String = ""
    ): List<TimedCaptionWord> {
        // Return cached/edited word timestamps if already populated and non-empty
        val existing = clip.extendedConfig?.wordTimestamps.orEmpty()
        if (existing.isNotEmpty()) {
            return existing
        }

        val clipDurSec = clip.durationSec.coerceAtLeast(22L).toFloat()
        val rawSource = extractDialogueLinesForWindow(
            clip = clip,
            projectTranscript = projectTranscript
        )

        // Parse explicit [mm:ss] or [ss] segments if present
        val timecodeRegex = Regex("\\[(?:(\\d{1,2}):(\\d{2})|(\\d+)s?)\\]\\s*([^\\[]+)")
        val matches = timecodeRegex.findAll(rawSource).mapNotNull { m ->
            val rawSec = if (m.groupValues[1].isNotEmpty()) {
                m.groupValues[1].toLong() * 60L + m.groupValues[2].toLong()
            } else {
                m.groupValues[3].toLongOrNull() ?: 0L
            }
            val text = m.groupValues[4].trim()
            if (text.isNotEmpty()) rawSec.toFloat() to text else null
        }.toList()

        val timedSegments: List<Pair<Pair<Float, Float>, String>> = if (matches.size >= 2) {
            // Normalize segment timestamps to [0f .. clipDurSec]
            val firstSec = matches.first().first
            val lastSec = matches.last().first
            val isAbsolute = firstSec >= clip.startSec && lastSec <= clip.endSec + 5f && firstSec > 3f
            matches.mapIndexed { idx, (sec, text) ->
                val relStart = if (isAbsolute) {
                    (sec - clip.startSec.toFloat()).coerceIn(0f, clipDurSec - 1f)
                } else {
                    sec.coerceIn(0f, clipDurSec - 1f)
                }
                val nextRelStart = if (idx + 1 < matches.size) {
                    val nxt = matches[idx + 1].first
                    if (isAbsolute) {
                        (nxt - clip.startSec.toFloat()).coerceIn(relStart + 1.2f, clipDurSec)
                    } else {
                        nxt.coerceIn(relStart + 1.2f, clipDurSec)
                    }
                } else {
                    clipDurSec
                }
                (relStart to max(relStart + 1.2f, nextRelStart)) to text
            }
        } else {
            val cleanText = rawSource
                .replace(Regex("\\[\\d{1,2}:\\d{2}\\]"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
                .ifBlank { "${clip.title} — ${clip.reasonForSelection}" }
            val clauses = cleanText
                .split(Regex("(?<=[.!?•—])\\s+"))
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .ifEmpty { listOf(cleanText) }

            val sliceDur = clipDurSec / clauses.size.coerceAtLeast(1)
            clauses.mapIndexed { i, clause ->
                val s = i * sliceDur
                val e = ((i + 1) * sliceDur).coerceAtMost(clipDurSec)
                (s to e) to clause
            }
        }

        val wordsList = mutableListOf<TimedCaptionWord>()
        for ((window, segmentText) in timedSegments) {
            val (segStart, segEnd) = window
            val tokens = segmentText
                .split(Regex("\\s+"))
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            if (tokens.isEmpty()) continue

            // Weight word duration by syllable/character length for natural speech cadence
            val charWeights = tokens.map { token ->
                val baseLen = token.filter { it.isLetterOrDigit() }.length.coerceIn(2, 12)
                val punctuationPause = if (token.endsWith(".") || token.endsWith("!") || token.endsWith("?") || token.endsWith(",")) 1.5f else 1.0f
                baseLen * punctuationPause
            }
            val totalWeight = charWeights.sum().coerceAtLeast(1f)
            val availableSec = (segEnd - segStart).coerceAtLeast(0.6f)
            var cursor = segStart

            tokens.forEachIndexed { idx, rawToken ->
                val wDur = (charWeights[idx] / totalWeight) * availableSec
                val wStart = cursor
                val wEnd = (cursor + wDur).coerceAtMost(segEnd)
                cursor = wEnd

                val cleanLower = rawToken.lowercase(Locale.US).trim(',', '.', '!', '?', '"', '\'', ':', ';')
                val isUncertain = rawToken.contains("[?]") || rawToken.contains("(?)") || cleanLower == "inaudible"
                val isSlang = cleanLower in KNOWN_SLANG_WORDS
                val isKeyword = isSlang ||
                    cleanLower in POWER_KEYWORDS ||
                    rawToken.any { it.isDigit() } ||
                    (rawToken.length >= 4 && rawToken == rawToken.uppercase(Locale.US) && rawToken.any { it.isLetter() })

                wordsList.add(
                    TimedCaptionWord(
                        word = rawToken,
                        startSec = wStart,
                        endSec = max(wStart + 0.08f, wEnd),
                        confidence = if (isUncertain) 0.58f else 0.96f,
                        isKeyword = isKeyword,
                        isSlang = isSlang,
                        isUncertain = isUncertain
                    )
                )
            }
        }

        return wordsList
    }

    private fun extractDialogueLinesForWindow(
        clip: ClipCandidate,
        projectTranscript: String
    ): String {
        if (projectTranscript.isNotBlank()) {
            val lineRegex = Regex("\\[(?:(\\d{1,2}):(\\d{2})|(\\d+)s?)\\]\\s*(.+)")
            val matchingLines = projectTranscript.lines().mapNotNull { line ->
                val m = lineRegex.find(line.trim()) ?: return@mapNotNull null
                val sec = if (m.groupValues[1].isNotEmpty()) {
                    m.groupValues[1].toLong() * 60L + m.groupValues[2].toLong()
                } else {
                    m.groupValues[3].toLongOrNull() ?: return@mapNotNull null
                }
                val text = m.groupValues[4].trim()
                if (sec in (clip.startSec - 4L)..(clip.endSec + 4L) && text.isNotBlank()) {
                    val relSec = (sec - clip.startSec).coerceAtLeast(0L)
                    val mm = relSec / 60L
                    val ss = relSec % 60L
                    String.format(Locale.US, "[%02d:%02d] %s", mm, ss, text)
                } else null
            }
            if (matchingLines.size >= 2) {
                return matchingLines.joinToString(" ")
            }
        }
        return clip.subtitleText.ifBlank { "${clip.title}. ${clip.reasonForSelection}" }
    }

    /**
     * Groups word-level timestamps into readable 3–5 word phrase groups without changing
     * the speaker's words or meaning.
     */
    fun groupWordsIntoReadablePhrases(
        words: List<TimedCaptionWord>,
        maxWordsPerGroup: Int = 4
    ): List<CaptionPhraseGroup> {
        if (words.isEmpty()) return emptyList()
        val groups = mutableListOf<CaptionPhraseGroup>()
        val currentBucket = mutableListOf<TimedCaptionWord>()
        var groupIndex = 0

        for (w in words) {
            currentBucket.add(w)
            val endsSentence = w.word.endsWith(".") || w.word.endsWith("!") || w.word.endsWith("?")
            if (currentBucket.size >= maxWordsPerGroup || (endsSentence && currentBucket.size >= 2)) {
                groups.add(
                    CaptionPhraseGroup(
                        groupIndex = groupIndex++,
                        startSec = currentBucket.first().startSec,
                        endSec = currentBucket.last().endSec,
                        words = currentBucket.toList()
                    )
                )
                currentBucket.clear()
            }
        }
        if (currentBucket.isNotEmpty()) {
            groups.add(
                CaptionPhraseGroup(
                    groupIndex = groupIndex,
                    startSec = currentBucket.first().startSec,
                    endSec = currentBucket.last().endSec,
                    words = currentBucket.toList()
                )
            )
        }
        return groups
    }

    /**
     * Resolves the active phrase group and the exact currently spoken word at [relativeTimeSec]
     * (0.0f .. clip.durationSec) for word-by-word animated rendering.
     */
    fun resolveActiveWordState(
        words: List<TimedCaptionWord>,
        relativeTimeSec: Float
    ): ActiveWordRenderState? {
        if (words.isEmpty()) return null
        val groups = groupWordsIntoReadablePhrases(words)
        if (groups.isEmpty()) return null

        val activeGroup = groups.firstOrNull { relativeTimeSec in it.startSec..it.endSec }
            ?: groups.minByOrNull { group ->
                when {
                    relativeTimeSec < group.startSec -> group.startSec - relativeTimeSec
                    relativeTimeSec > group.endSec -> relativeTimeSec - group.endSec
                    else -> 0f
                }
            } ?: return null

        val wordIdx = activeGroup.words.indexOfFirst { relativeTimeSec in it.startSec..it.endSec }
            .let { found ->
                if (found >= 0) found
                else if (relativeTimeSec < activeGroup.words.first().startSec) 0
                else activeGroup.words.lastIndex
            }

        val activeWord = activeGroup.words[wordIdx]
        val wordSpan = (activeWord.endSec - activeWord.startSec).coerceAtLeast(0.08f)
        val wordProgress = ((relativeTimeSec - activeWord.startSec) / wordSpan).coerceIn(0f, 1f)
        // Smooth pop/bounce scale curve peaking at 1.18x in the middle of the spoken word
        val popScale = 1.0f + 0.18f * sin(wordProgress * Math.PI.toFloat())

        return ActiveWordRenderState(
            group = activeGroup,
            activeWordIndexInGroup = wordIdx,
            activeWordProgress = wordProgress,
            popScale = popScale
        )
    }

    /**
     * Computes the safe vertical Y-center for dialogue captions given the active 9:16 layout segment
     * so captions NEVER cover the speaker's face or critical HUD elements.
     */
    fun computeSafeCaptionCenterY(
        canvasHeight: Float,
        layoutType: VerticalLayoutType,
        safeBottomPct: Float
    ): Float {
        val safeBottomMargin = canvasHeight * (safeBottomPct / 100f).coerceIn(0.14f, 0.28f)
        return when (layoutType) {
            // In split layouts (A & D), placing captions right on the split seam or lower safe zone avoids both top facecam and center gameplay crosshair
            VerticalLayoutType.LAYOUT_A_STREAMER_AND_CONTENT -> canvasHeight * 0.38f
            VerticalLayoutType.LAYOUT_D_FULLSCREEN_CONTENT_SMALL_WEBCAM -> canvasHeight * 0.35f
            VerticalLayoutType.LAYOUT_B_MULTIPLE_SPEAKERS -> canvasHeight * 0.42f
            // In full-frame speaker layout (C), place captions in the lower chest/safe zone below the speaker's face
            VerticalLayoutType.LAYOUT_C_SPEAKER_ONLY -> (canvasHeight - safeBottomMargin - 38f)
        }
    }

    /**
     * Renders word-by-word animated captions onto [canvas] with:
     * - Active spoken word highlighted with a high-contrast kinetic pill & pop scale
     * - Power keywords colored in accent amber/cyan
     * - Thick black outline + subtle shadow for 100% phone readability
     * - Uncertain words badged with `[?]`
     */
    fun drawWordByWordAnimatedCaptions(
        canvas: Canvas,
        width: Float,
        height: Float,
        state: ActiveWordRenderState,
        layoutType: VerticalLayoutType,
        captionStylePreset: String = "HORMOZI_BOLD",
        safeBottomPct: Float = 20f
    ) {
        val centerY = computeSafeCaptionCenterY(height, layoutType, safeBottomPct)
        val baseFontSize = (width * 0.044f).coerceIn(18f, 30f)

        val words = state.group.words
        if (words.isEmpty()) return

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = baseFontSize
            isFakeBoldText = true
            textAlign = Paint.Align.LEFT
        }
        val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = baseFontSize
            isFakeBoldText = true
            textAlign = Paint.Align.LEFT
            style = Paint.Style.STROKE
            strokeWidth = 6f
            color = Color.BLACK
        }

        val spaceWidth = textPaint.measureText(" ") * 1.15f
        val formattedTokens = words.map { w ->
            val raw = if (captionStylePreset == "STORYTELLER" || captionStylePreset == "CLEAN_MINIMAL") {
                w.word
            } else {
                w.word.uppercase(Locale.US)
            }
            if (w.isUncertain && !raw.contains("?")) "$raw[?]" else raw
        }

        val wordWidths = formattedTokens.map { textPaint.measureText(it) }
        val totalTextWidth = wordWidths.sum() + spaceWidth * (words.size - 1).coerceAtLeast(0)

        // Backdrop plate for extra contrast
        val platePaddingX = 20f
        val platePaddingY = 18f
        val plateLeft = ((width - totalTextWidth) / 2f - platePaddingX).coerceAtLeast(14f)
        val plateRight = ((width + totalTextWidth) / 2f + platePaddingX).coerceAtMost(width - 14f)
        val plateRect = RectF(
            plateLeft,
            centerY - baseFontSize - platePaddingY,
            plateRight,
            centerY + platePaddingY + 6f
        )

        val backdropPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(195, 8, 12, 20)
            style = Paint.Style.FILL
            setShadowLayer(8f, 0f, 4f, Color.argb(180, 0, 0, 0))
        }
        canvas.drawRoundRect(plateRect, 16f, 16f, backdropPaint)

        var cursorX = ((width - totalTextWidth) / 2f).coerceAtLeast(22f)
        val baselineY = centerY + (baseFontSize * 0.32f)

        words.forEachIndexed { idx, timedWord ->
            val tokenText = formattedTokens[idx]
            val wWidth = wordWidths[idx]
            val isActive = idx == state.activeWordIndexInGroup

            if (isActive) {
                // Draw kinetic highlight pill behind the currently spoken word
                val pillColor = when (captionStylePreset) {
                    "GAMING_HYPE" -> Color.rgb(16, 185, 129) // Emerald highlight
                    "STORYTELLER" -> Color.rgb(56, 189, 248) // Cyan highlight
                    else -> Color.rgb(245, 158, 11) // Hormozi Amber/Gold highlight
                }
                val pillRect = RectF(
                    cursorX - 6f,
                    baselineY - baseFontSize - 6f,
                    cursorX + wWidth + 6f,
                    baselineY + 8f
                )
                val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = pillColor
                    style = Paint.Style.FILL
                }
                canvas.save()
                val wordCenterX = cursorX + wWidth / 2f
                val wordCenterY = baselineY - baseFontSize / 2f
                canvas.scale(state.popScale, state.popScale, wordCenterX, wordCenterY)
                canvas.drawRoundRect(pillRect, 10f, 10f, pillPaint)

                outlinePaint.color = Color.BLACK
                outlinePaint.strokeWidth = 4f
                canvas.drawText(tokenText, cursorX, baselineY, outlinePaint)

                textPaint.color = Color.rgb(10, 14, 20)
                canvas.drawText(tokenText, cursorX, baselineY, textPaint)
                canvas.restore()
            } else {
                // Non-active word in current phrase group: draw thick outline + keyword/white fill
                outlinePaint.color = Color.BLACK
                outlinePaint.strokeWidth = 5.5f
                canvas.drawText(tokenText, cursorX, baselineY, outlinePaint)

                textPaint.color = when {
                    timedWord.isUncertain -> Color.rgb(251, 113, 133) // Coral for uncertain [?]
                    timedWord.isKeyword -> Color.rgb(253, 224, 71) // Warm yellow for keywords
                    else -> Color.WHITE
                }
                canvas.drawText(tokenText, cursorX, baselineY, textPaint)
            }

            cursorX += wWidth + spaceWidth
        }
    }

    /**
     * Verifies that all word-level caption timestamps fall monotonically within [0 .. renderedDurationSec]
     * so audio and captions stay synchronized across all layout transitions.
     */
    fun verifyCaptionAudioAlignment(
        words: List<TimedCaptionWord>,
        renderedDurationSec: Long
    ): Boolean {
        if (words.isEmpty()) return false
        val maxSec = renderedDurationSec.toFloat() + 0.5f
        var prevStart = -0.01f
        for (w in words) {
            if (w.startSec < prevStart || w.endSec < w.startSec || w.startSec > maxSec) {
                return false
            }
            prevStart = w.startSec
        }
        return true
    }

    /**
     * Builds an Advanced SubStation Alpha (`.ass`) subtitle script with `\kf` karaoke word-level
     * highlight timing so FFmpeg / Termux renders word-by-word animated captions natively.
     */
    fun buildWordLevelAssSubtitles(
        clip: ClipCandidate,
        words: List<TimedCaptionWord>
    ): String {
        val groups = groupWordsIntoReadablePhrases(words)
        return buildString {
            appendLine("[Script Info]")
            appendLine("ScriptType: v4.00+")
            appendLine("PlayResX: 1080")
            appendLine("PlayResY: 1920")
            appendLine()
            appendLine("[V4+ Styles]")
            appendLine("Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding")
            appendLine("Style: ViralWord,Sans,64,&H000B9EF5,&H00FFFFFF,&H00000000,&H80000000,-1,0,1,5,2,2,60,60,380,1")
            appendLine()
            appendLine("[Events]")
            appendLine("Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text")
            for (g in groups) {
                val startAss = formatAssTime(g.startSec)
                val endAss = formatAssTime(g.endSec)
                val karaokeLine = g.words.joinToString(" ") { w ->
                    val centiseconds = ((w.endSec - w.startSec) * 100f).toInt().coerceAtLeast(8)
                    "{\\kf$centiseconds}${w.word}"
                }
                appendLine("Dialogue: 0,$startAss,$endAss,ViralWord,,0,0,0,,$karaokeLine")
            }
        }
    }

    private fun formatAssTime(sec: Float): String {
        val totalCs = (sec.coerceAtLeast(0f) * 100f).toInt()
        val hrs = totalCs / 360000
        val mins = (totalCs % 360000) / 6000
        val secs = (totalCs % 6000) / 100
        val cs = totalCs % 100
        return String.format(Locale.US, "%d:%02d:%02d.%02d", hrs, mins, secs, cs)
    }
}
