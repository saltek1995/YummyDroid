package me.yummydroid.app.data

import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.roundToLong
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

internal data class HlsSubtitleSegment(
    val url: String,
    val offsetMs: Long,
    val durationMs: Long,
)

internal data class MaterializedSubtitleSegment(
    val body: String,
    val offsetMs: Long,
    val durationMs: Long,
    val localMapMs: Long? = null,
    val topLevelBlocks: List<String> = emptyList(),
)

internal data class PlayableSubtitleBody(
    val text: String,
    val mimeType: String,
    val fileExtension: String = mimeType.subtitleFileExtension(),
)

internal data class WebVttCueBody(
    val text: String,
    val localMapMs: Long? = null,
    val topLevelBlocks: List<String> = emptyList(),
)

private data class ParsedWebVttCue(
    val blockIndex: Int,
    val timingLineIndex: Int,
    val startMs: Long,
    val endMs: Long,
    val settings: String,
    val text: String,
) {
    val hasExplicitPlacement: Boolean
        get() = settings.webVttCueSettings().any { setting ->
            setting.startsWith("line:", ignoreCase = true) ||
                setting.startsWith("position:", ignoreCase = true) ||
                setting.startsWith("region:", ignoreCase = true) ||
                setting.startsWith("vertical:", ignoreCase = true)
        }

    val visibleLineCount: Int
        get() = text
            .lineSequence()
            .count { line -> line.visibleSubtitleText().isNotBlank() }
            .coerceAtLeast(1)

    val isSignLike: Boolean
        get() {
            val visible = text.visibleSubtitleText()
            if (visible.isBlank()) return false
            if (Regex("""(?i)<\s*b\b""").containsMatchIn(text)) return true
            val letters = visible.filter { it.isLetter() }
            if (letters.length < 3) return false
            val uppercase = letters.count { it.isUpperCase() }
            return uppercase.toFloat() / letters.length >= 0.75f
        }
}

internal fun String.isHlsPlaylistUrl(): Boolean {
    val lower = substringBefore('?').substringBefore('#').lowercase()
    return lower.endsWith(".m3u8") || "mpegurl" in lower
}

internal fun String.hlsSubtitleSegments(baseUrl: String): List<HlsSubtitleSegment> {
    val segments = mutableListOf<HlsSubtitleSegment>()
    var offsetMs = 0L
    var pendingDurationMs = 0L

    lineSequence().forEach { rawLine ->
        val line = rawLine.trim()
        when {
            line.startsWith("#EXTINF", ignoreCase = true) -> {
                pendingDurationMs = line.substringAfter(':')
                    .substringBefore(',')
                    .toDoubleOrNull()
                    ?.let { (it * 1000.0).toLong() }
                    ?: 0L
            }
            line.isNotBlank() && !line.startsWith("#") -> {
                segments += HlsSubtitleSegment(
                    url = line.resolveUrlAgainst(baseUrl),
                    offsetMs = offsetMs,
                    durationMs = pendingDurationMs,
                )
                offsetMs += pendingDurationMs
                pendingDurationMs = 0L
            }
        }
    }

    return segments
}

internal fun String.webVttCueBody(): WebVttCueBody {
    val lines = replace("\uFEFF", "")
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .lines()
    if (lines.isEmpty()) return WebVttCueBody(text = "")

    var index = 0
    var localMapMs: Long? = null
    if (lines[index].trim().startsWith("WEBVTT", ignoreCase = true)) {
        index++
        while (index < lines.size && lines[index].isNotBlank()) {
            val line = lines[index].trim()
            if (line.isWebVttTopLevelBlockStart()) break
            if (line.startsWith("X-TIMESTAMP-MAP", ignoreCase = true)) {
                localMapMs = line.webVttTimestampMapLocalMs()
            }
            index++
        }
        while (index < lines.size && lines[index].isBlank()) {
            index++
        }
    }

    val topLevelBlocks = mutableListOf<String>()
    val cueBlocks = mutableListOf<String>()
    lines.drop(index)
        .joinToString("\n")
        .splitWebVttBlocks()
        .forEach { block ->
            val normalizedBlock = block
                .lineSequence()
                .filterNot { line -> line.trim().startsWith("X-TIMESTAMP-MAP", ignoreCase = true) }
                .joinToString("\n")
                .trim()
            if (normalizedBlock.isBlank()) return@forEach
            if (normalizedBlock.isWebVttTopLevelBlock()) {
                topLevelBlocks += normalizedBlock
            } else {
                cueBlocks += normalizedBlock
            }
        }

    return WebVttCueBody(
        text = cueBlocks.joinToString("\n\n").trim(),
        localMapMs = localMapMs,
        topLevelBlocks = topLevelBlocks.distinct(),
    )
}

private fun String.splitWebVttBlocks(): List<String> {
    val blocks = mutableListOf<String>()
    val current = mutableListOf<String>()

    lineSequence().forEach { line ->
        if (line.isBlank()) {
            if (current.isNotEmpty()) {
                blocks += current.joinToString("\n")
                current.clear()
            }
        } else {
            current += line
        }
    }
    if (current.isNotEmpty()) {
        blocks += current.joinToString("\n")
    }

    return blocks
}

internal fun String.withNonOverlappingWebVttCueSettings(): String {
    val normalized = replace("\uFEFF", "")
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .trim()
    if (!normalized.startsWith("WEBVTT", ignoreCase = true)) return this

    val blocks = normalized.splitWebVttBlocks().toMutableList()
    val cues = blocks.mapIndexedNotNull { index, block -> block.parseWebVttCue(index) }
    val overlappingCueIndexes = cues.overlappingUnplacedCueIndexes()
    if (overlappingCueIndexes.isEmpty()) return normalized

    val assignedSettings = cues.assignedWebVttCueSettings(overlappingCueIndexes)
    if (assignedSettings.isEmpty()) return normalized

    assignedSettings.forEach { (cueIndex, automaticSettings) ->
        val cue = cues[cueIndex]
        val lines = blocks[cue.blockIndex].lines().toMutableList()
        val timingLine = lines.getOrNull(cue.timingLineIndex) ?: return@forEach
        val match = SubtitleParsingPatterns.webVttTiming.find(timingLine.trim()) ?: return@forEach
        val existingSettings = match.groupValues.getOrNull(3).orEmpty().trim()
        val mergedSettings = existingSettings.withAdditionalWebVttCueSettings(automaticSettings)
        lines[cue.timingLineIndex] = buildString {
            append(match.groupValues[1])
            append(" --> ")
            append(match.groupValues[2])
            if (mergedSettings.isNotBlank()) {
                append(' ')
                append(mergedSettings)
            }
        }
        blocks[cue.blockIndex] = lines.joinToString("\n")
    }

    return buildString {
        append(blocks.joinToString("\n\n"))
        append('\n')
    }
}

private fun String.parseWebVttCue(blockIndex: Int): ParsedWebVttCue? {
    if (isWebVttTopLevelBlock()) return null
    val lines = lines()
    val timingLineIndex = lines.indexOfFirst { line ->
        SubtitleParsingPatterns.webVttTiming.matches(line.trim())
    }
    if (timingLineIndex < 0) return null
    val timing = lines[timingLineIndex].trim()
    val match = SubtitleParsingPatterns.webVttTiming.find(timing) ?: return null
    val startMs = match.groupValues.getOrNull(1)?.webVttTimestampMs() ?: return null
    val endMs = match.groupValues.getOrNull(2)?.webVttTimestampMs() ?: return null
    if (endMs <= startMs) return null
    return ParsedWebVttCue(
        blockIndex = blockIndex,
        timingLineIndex = timingLineIndex,
        startMs = startMs,
        endMs = endMs,
        settings = match.groupValues.getOrNull(3).orEmpty().trim(),
        text = lines.drop(timingLineIndex + 1).joinToString("\n"),
    )
}

private fun List<ParsedWebVttCue>.overlappingUnplacedCueIndexes(): Set<Int> {
    val result = mutableSetOf<Int>()
    forEachIndexed { index, cue ->
        if (cue.hasExplicitPlacement) return@forEachIndexed
        val overlaps = any { other ->
            other.blockIndex != cue.blockIndex &&
                other.startMs < cue.endMs &&
                cue.startMs < other.endMs
        }
        if (overlaps) result += index
    }
    return result
}

private fun List<ParsedWebVttCue>.assignedWebVttCueSettings(cueIndexes: Set<Int>): Map<Int, String> {
    val assignments = linkedMapOf<Int, String>()
    val activeSignPlacements = mutableListOf<ActiveSignPlacement>()
    val activeDialoguePlacements = mutableListOf<ActiveDialoguePlacement>()
    val sortedCueIndexes = cueIndexes.sortedWith(
        compareBy<Int> { this[it].startMs }
            .thenBy { if (this[it].isSignLike) 1 else 0 }
            .thenBy { this[it].blockIndex },
    )

    sortedCueIndexes.forEach { cueIndex ->
        val cue = this[cueIndex]
        assignments[cueIndex] = if (cue.isSignLike) {
            activeSignPlacements.removeAll { placement -> placement.endMs <= cue.startMs }
            val height = cue.signFootprintPercent()
            val line = generateSequence(SIGN_CUE_START_PERCENT) { it + 1 }
                .firstOrNull { candidate ->
                    val candidateEnd = candidate + height
                    candidateEnd <= SIGN_CUE_MAX_END_PERCENT &&
                        activeSignPlacements.none { placement ->
                            rangesOverlap(
                                firstStart = candidate,
                                firstEnd = candidateEnd,
                                secondStart = placement.startPercent,
                                secondEnd = placement.endPercent,
                            )
                        }
                }
                ?: (SIGN_CUE_MAX_END_PERCENT - height).coerceAtLeast(SIGN_CUE_START_PERCENT)
            activeSignPlacements += ActiveSignPlacement(
                startPercent = line,
                endPercent = line + height + SIGN_CUE_GAP_PERCENT,
                endMs = cue.endMs,
            )
            "line:$line% position:50% align:center"
        } else {
            activeDialoguePlacements.removeAll { placement -> placement.endMs <= cue.startMs }
            val height = cue.visibleLineCount + DIALOGUE_CUE_GAP_LINES
            val firstLine = generateSequence(0) { it + 1 }
                .first { candidate ->
                    val candidateEnd = candidate + height
                    activeDialoguePlacements.none { placement ->
                        rangesOverlap(
                            firstStart = candidate,
                            firstEnd = candidateEnd,
                            secondStart = placement.firstLine,
                            secondEnd = placement.lastLineExclusive,
                        )
                    }
                }
            activeDialoguePlacements += ActiveDialoguePlacement(
                firstLine = firstLine,
                lineCount = height,
                endMs = cue.endMs,
            )
            "line:${-1 - firstLine} position:50% align:center"
        }
    }

    return assignments
}

private data class ActiveSignPlacement(
    val startPercent: Int,
    val endPercent: Int,
    val endMs: Long,
)

private data class ActiveDialoguePlacement(
    val firstLine: Int,
    val lineCount: Int,
    val endMs: Long,
) {
    val lastLineExclusive: Int
        get() = firstLine + lineCount
}

private fun ParsedWebVttCue.signFootprintPercent(): Int {
    val textHeight = visibleLineCount * SIGN_CUE_LINE_HEIGHT_PERCENT
    val innerGap = (visibleLineCount - 1).coerceAtLeast(0) * SIGN_CUE_INNER_GAP_PERCENT
    return (textHeight + innerGap).coerceAtLeast(SIGN_CUE_MIN_HEIGHT_PERCENT)
}

private fun rangesOverlap(
    firstStart: Int,
    firstEnd: Int,
    secondStart: Int,
    secondEnd: Int,
): Boolean {
    return firstStart < secondEnd && secondStart < firstEnd
}

private const val SIGN_CUE_START_PERCENT = 8
private const val SIGN_CUE_MAX_END_PERCENT = 84
private const val SIGN_CUE_LINE_HEIGHT_PERCENT = 10
private const val SIGN_CUE_INNER_GAP_PERCENT = 1
private const val SIGN_CUE_GAP_PERCENT = 4
private const val SIGN_CUE_MIN_HEIGHT_PERCENT = 10
private const val DIALOGUE_CUE_GAP_LINES = 1
private fun String.withAdditionalWebVttCueSettings(additionalSettings: String): String {
    val existing = webVttCueSettings()
    val existingNames = existing.mapTo(mutableSetOf()) { setting ->
        setting.substringBefore(':').lowercase()
    }
    val additional = additionalSettings.webVttCueSettings()
        .filter { setting -> setting.substringBefore(':').lowercase() !in existingNames }
    return (existing + additional).joinToString(" ")
}

private fun String.webVttCueSettings(): List<String> {
    return trim()
        .split(Regex("""\s+"""))
        .filter { it.isNotBlank() && ':' in it }
}

private fun String.isWebVttTopLevelBlock(): Boolean {
    return (lineSequence()
        .firstOrNull { it.isNotBlank() }
        ?.trim()
        ?.isWebVttTopLevelBlockStart() == true)
}

private fun String.isWebVttTopLevelBlockStart(): Boolean {
    return equals("STYLE", ignoreCase = true) ||
        equals("REGION", ignoreCase = true) ||
        startsWith("NOTE", ignoreCase = true)
}

internal fun String.looksLikeStandaloneHlsWebVttSegment(): Boolean {
    val normalized = replace("\uFEFF", "")
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .trimStart()
    if (!normalized.startsWith("WEBVTT", ignoreCase = true)) return false

    return normalized
        .lineSequence()
        .drop(1)
        .takeWhile { it.isNotBlank() }
        .any { line -> line.trim().startsWith("X-TIMESTAMP-MAP", ignoreCase = true) }
}

private fun String.subtitleFileExtension(): String {
    return when {
        this == "text/x-ssa" -> "ass"
        this == "application/ttml+xml" -> "ttml"
        this == "application/x-subrip" -> "srt"
        else -> "vtt"
    }
}

internal fun String.looksLikeAssSubtitle(): Boolean {
    return lineSequence()
        .map { line -> line.trim() }
        .any { line ->
            if (!line.startsWith("Dialogue:", ignoreCase = true)) return@any false
            val fields = line.substringAfter(':').split(',', limit = 10)
            fields.size >= 10 &&
                fields.getOrNull(1)?.trim()?.subtitleTimestampMs() != null &&
                fields.getOrNull(2)?.trim()?.subtitleTimestampMs() != null
        }
}

internal fun String.withAssHeaderIfMissing(): String {
    val hasEventsSection = lineSequence()
        .map { line -> line.trim() }
        .any { line -> line.equals("[Events]", ignoreCase = true) }
    val hasEventFormat = lineSequence()
        .map { line -> line.trim() }
        .any { line -> line.startsWith("Format:", ignoreCase = true) }
    if (hasEventsSection && hasEventFormat) return this

    val dialogueLines = lineSequence()
        .map { line -> line.trim() }
        .filter { line -> line.startsWith("Dialogue:", ignoreCase = true) }
        .toList()
    if (dialogueLines.isEmpty()) return this

    return buildString {
        appendLine("[Script Info]")
        appendLine("ScriptType: v4.00+")
        appendLine("Collisions: Normal")
        appendLine("PlayResX: 384")
        appendLine("PlayResY: 288")
        appendLine()
        appendLine("[V4+ Styles]")
        appendLine("Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding")
        appendLine("Style: Default,Arial,24,&H00FFFFFF,&H000000FF,&H00000000,&H64000000,-1,0,0,0,100,100,0,0,1,1.5,0,2,10,10,10,1")
        appendLine()
        appendLine("[Events]")
        appendLine("Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text")
        dialogueLines.forEach(::appendLine)
    }
}

internal fun String.looksLikeTtmlSubtitle(): Boolean {
    return trimStart().startsWith("<tt", ignoreCase = true)
}

internal fun String.timedSubtitleTextToWebVtt(): String? {
    val lines = lines()
    val cues = mutableListOf<String>()
    var index = 0
    if (lines.firstOrNull()?.trim()?.startsWith("WEBVTT", ignoreCase = true) == true) {
        index = 1
        while (index < lines.size && lines[index].isNotBlank()) index++
        while (index < lines.size && lines[index].isBlank()) index++
    }

    while (index < lines.size) {
        val current = lines[index].trim()
        if (current.isBlank()) {
            index++
            continue
        }

        val timingLine = when {
            SubtitleParsingPatterns.timingLine.matches(current) -> current
            index + 1 < lines.size && SubtitleParsingPatterns.timingLine.matches(lines[index + 1].trim()) -> {
                index++
                lines[index].trim()
            }
            else -> {
                index++
                continue
            }
        }
        val webVttTiming = timingLine.toWebVttTimingLine() ?: continue
        index++

        val textLines = mutableListOf<String>()
        while (index < lines.size && lines[index].trim().isNotEmpty()) {
            val textLine = lines[index].trimEnd()
            if (textLine.visibleSubtitleText().isNotBlank()) {
                textLines += textLine
            }
            index++
        }
        if (textLines.isNotEmpty()) {
            cues += buildString {
                append(webVttTiming)
                append('\n')
                append(textLines.joinToString("\n"))
            }
        }
    }

    return cues.toWebVttDocument()
}

internal fun String.assToWebVtt(): String? {
    val cues = lineSequence()
        .map { line -> line.trim() }
        .filter { line -> line.startsWith("Dialogue:", ignoreCase = true) }
        .mapNotNull { line ->
            val fields = line.substringAfter(':').split(',', limit = 10)
            if (fields.size < 10) return@mapNotNull null
            val startMs = fields.getOrNull(1)?.trim()?.subtitleTimestampMs() ?: return@mapNotNull null
            val endMs = fields.getOrNull(2)?.trim()?.subtitleTimestampMs() ?: return@mapNotNull null
            val text = fields.getOrNull(9)
                ?.stripAssOverrideTags()
                ?.replace(Regex("""\\[Nn]"""), "\n")
                ?.replace(Regex("""\\h"""), " ")
                ?.visibleSubtitleText()
                ?: return@mapNotNull null
            if (text.isBlank() || endMs <= startMs) return@mapNotNull null
            "${startMs.toWebVttTimestamp()} --> ${endMs.toWebVttTimestamp()}\n$text"
        }
        .toList()

    return cues.toWebVttDocument()
}

internal fun String.ttmlToWebVtt(): String? {
    val cues = SubtitleParsingPatterns.ttmlParagraphWithAttributes.findAll(this)
        .mapNotNull { match ->
            val attributes = match.groupValues.getOrNull(1).orEmpty()
            val startMs = attributes.xmlTimeAttribute("begin") ?: return@mapNotNull null
            val endMs = attributes.xmlTimeAttribute("end") ?: return@mapNotNull null
            val text = match.groupValues.getOrNull(2)
                ?.visibleSubtitleText()
                ?: return@mapNotNull null
            if (text.isBlank() || endMs <= startMs) return@mapNotNull null
            "${startMs.toWebVttTimestamp()} --> ${endMs.toWebVttTimestamp()}\n$text"
        }
        .toList()

    return cues.toWebVttDocument()
}

internal fun String.jsonSubtitleToWebVtt(): String? {
    val first = firstOrNull { !it.isWhitespace() } ?: return null
    if (first != '{' && first != '[') return null
    val element = runCatching { VIDEO_RESOLVER_JSON.parseToJsonElement(this) }.getOrNull() ?: return null
    val cues = element.collectJsonSubtitleCues()
        .distinctBy { cue -> "${cue.startMs}:${cue.endMs}:${cue.settings}:${cue.text}" }
        .sortedWith(compareBy<JsonSubtitleCue> { it.startMs }.thenBy { it.endMs })
        .map { cue ->
            buildString {
                append(cue.startMs.toWebVttTimestamp())
                append(" --> ")
                append(cue.endMs.toWebVttTimestamp())
                cue.settings.takeIf { it.isNotBlank() }?.let { settings ->
                    append(' ')
                    append(settings)
                }
                append('\n')
                append(cue.text)
            }
        }
    return cues.toWebVttDocument()
}

private data class JsonSubtitleCue(
    val startMs: Long,
    val endMs: Long,
    val text: String,
    val settings: String = "",
)

private fun JsonElement.collectJsonSubtitleCues(): List<JsonSubtitleCue> {
    return when (this) {
        is JsonArray -> flatMap { element -> element.collectJsonSubtitleCues() }
        is JsonObject -> collectJsonSubtitleCuesFromObject()
        else -> emptyList()
    }
}

private fun JsonObject.collectJsonSubtitleCuesFromObject(): List<JsonSubtitleCue> {
    val start = firstSubtitleTimeMs(jsonSubtitleStartKeys)
    val end = firstSubtitleTimeMs(jsonSubtitleEndKeys)
    val duration = firstSubtitleTimeMs(jsonSubtitleDurationKeys)
    val text = firstSubtitleCueText()
    val settings = subtitleCueSettings()
    val selfCue = if (start != null && text != null) {
        val resolvedEnd = end ?: duration?.let(start::plus)
        resolvedEnd
            ?.takeIf { it > start }
            ?.let { JsonSubtitleCue(startMs = start, endMs = it, text = text, settings = settings) }
    } else {
        null
    }

    return listOfNotNull(selfCue) + values.flatMap { element -> element.collectJsonSubtitleCues() }
}

private fun JsonObject.firstSubtitleTimeMs(keys: Set<String>): Long? {
    return entries.firstNotNullOfOrNull { (key, value) ->
        key.jsonSubtitleKeyIdentity()
            .takeIf(keys::contains)
            ?.let { identity -> (value as? JsonPrimitive)?.subtitleTimeMs(identity) }
    }
}

private fun JsonPrimitive.subtitleTimeMs(keyIdentity: String): Long? {
    val raw = contentOrNull?.trim()?.takeIf { it.isNotBlank() } ?: return null
    raw.subtitleTimestampMs()?.let { return it }
    val numeric = raw.toDoubleOrNull() ?: return null
    val isMilliseconds = "ms" in keyIdentity || numeric >= JSON_SUBTITLE_MILLISECONDS_THRESHOLD
    return if (isMilliseconds) {
        numeric.roundToLong()
    } else {
        (numeric * 1000.0).roundToLong()
    }.coerceAtLeast(0L)
}

private fun JsonObject.firstSubtitleCueText(): String? {
    firstSubtitleCueText(jsonSubtitlePrimaryTextKeys)?.let { return it }
    if (subtitleCueSettings().isNotBlank()) return null
    return firstSubtitleCueText(jsonSubtitleFallbackTextKeys)
}

private fun JsonObject.firstSubtitleCueText(keys: Set<String>): String? {
    return entries.firstNotNullOfOrNull { (key, value) ->
        key.jsonSubtitleKeyIdentity()
            .takeIf(keys::contains)
            ?.let { (value as? JsonPrimitive)?.contentOrNull }
            ?.subtitleCuePlainText()
            ?.takeIf { it.isNotBlank() }
    }
}

private fun JsonObject.subtitleCueSettings(): String {
    val settings = mutableListOf<String>()
    entries.forEach { (key, value) ->
        val identity = key.jsonSubtitleKeyIdentity()
        val content = (value as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
        if (content.isBlank()) return@forEach
        when {
            identity in jsonSubtitleSettingsKeys -> content
                .toWebVttCueSettings()
                ?.let(settings::add)
            identity == "line" -> content
                .toWebVttCueSettingValue()
                ?.let { settings += "line:$it" }
            identity == "position" -> content
                .toWebVttCueSettingValue()
                ?.let { settings += "position:$it" }
            identity == "size" -> content
                .toWebVttCueSettingValue()
                ?.let { settings += "size:$it" }
            identity == "align" -> content
                .toWebVttCueAlign()
                ?.let { settings += "align:$it" }
            identity == "vertical" -> content
                .takeIf { it == "rl" || it == "lr" }
                ?.let { settings += "vertical:$it" }
            identity == "region" -> content
                .takeIf { it.isWebVttCueSettingToken() }
                ?.let { settings += "region:$it" }
        }
    }
    return settings.distinct().joinToString(" ")
}

private fun String.subtitleCuePlainText(): String {
    return replace(Regex("""(?i)<br\s*/?>"""), "\n")
        .replace(Regex("""\\[Nn]"""), "\n")
        .visibleSubtitleText()
}

private fun String.toWebVttCueSettings(): String? {
    val settings = trim()
    if (settings.isBlank()) return null
    return settings
        .split(Regex("""\s+"""))
        .mapNotNull { token ->
            val name = token.substringBefore(':').jsonSubtitleKeyIdentity()
            val value = token.substringAfter(':', "").trim()
            when (name) {
                "line" -> value.toWebVttCueSettingValue()?.let { "line:$it" }
                "position" -> value.toWebVttCueSettingValue()?.let { "position:$it" }
                "size" -> value.toWebVttCueSettingValue()?.let { "size:$it" }
                "align" -> value.lowercase()
                    .toWebVttCueAlign()
                    ?.let { "align:$it" }
                "vertical" -> value.lowercase()
                    .takeIf { it == "rl" || it == "lr" }
                    ?.let { "vertical:$it" }
                "region" -> value
                    .takeIf { it.isWebVttCueSettingToken() }
                    ?.let { "region:$it" }
                else -> null
            }
        }
        .distinct()
        .joinToString(" ")
        .takeIf { it.isNotBlank() }
}

private fun String.toWebVttCueSettingValue(): String? {
    val value = trim().replace(',', '.')
    return value.takeIf { setting ->
        setting.equals("auto", ignoreCase = true) ||
            setting.matches(Regex("""-?\d+(?:\.\d+)?%?(?:,(?:start|center|end|line-left|line-right))?"""))
    }
}

private fun String.isWebVttCueSettingToken(): Boolean {
    return matches(Regex("""[A-Za-z0-9_-]+"""))
}

private fun String.toWebVttCueAlign(): String? {
    return when (lowercase()) {
        "middle" -> "center"
        in webVttCueAlignValues -> lowercase()
        else -> null
    }
}

private fun String.jsonSubtitleKeyIdentity(): String {
    return lowercase().replace(Regex("""[^a-z0-9]"""), "")
}

private val jsonSubtitleStartKeys = setOf(
    "start",
    "starttime",
    "begin",
    "from",
    "time",
    "startms",
)

private val jsonSubtitleEndKeys = setOf(
    "end",
    "endtime",
    "stop",
    "finish",
    "to",
    "endms",
)

private val jsonSubtitleDurationKeys = setOf(
    "duration",
    "dur",
    "length",
    "durationms",
)

private val jsonSubtitlePrimaryTextKeys = setOf(
    "text",
    "content",
    "caption",
    "subtitle",
    "body",
    "value",
)

private val jsonSubtitleFallbackTextKeys = setOf(
    "line",
)

private val jsonSubtitleSettingsKeys = setOf(
    "settings",
    "cuesettings",
    "vttsettings",
    "webvttsettings",
)

private val webVttCueAlignValues = setOf("start", "center", "end", "left", "right")

private const val JSON_SUBTITLE_MILLISECONDS_THRESHOLD = 10_000.0

private fun List<String>.toWebVttDocument(): String? {
    if (isEmpty()) return null
    return buildString {
        append("WEBVTT\n\n")
        append(joinToString("\n\n"))
        append('\n')
    }
}

private fun String.toWebVttTimingLine(): String? {
    val match = SubtitleParsingPatterns.timingLine.find(trim()) ?: return null
    val startMs = match.groupValues.getOrNull(1)?.subtitleTimestampMs() ?: return null
    val endMs = match.groupValues.getOrNull(2)?.subtitleTimestampMs() ?: return null
    if (endMs <= startMs) return null
    val settings = match.groupValues.getOrNull(3).orEmpty()
    return "${startMs.toWebVttTimestamp()} --> ${endMs.toWebVttTimestamp()}$settings"
}

internal fun String.hasSubtitleCues(mimeType: String? = null, uri: String = ""): Boolean {
    val normalized = replace("\uFEFF", "")
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .trim()
    if (normalized.isBlank()) return false

    val typeHint = listOf(mimeType.orEmpty(), uri.substringBefore('?').substringBefore('#'))
        .joinToString(" ")
        .lowercase()
    return when {
        "subrip" in typeHint || typeHint.endsWith(".srt") -> normalized.hasTimedSubtitleCue()
        "x-ssa" in typeHint || typeHint.endsWith(".ass") || typeHint.endsWith(".ssa") -> normalized.hasAssDialogueCue()
        "ttml" in typeHint || "dfxp" in typeHint || typeHint.endsWith(".ttml") || typeHint.endsWith(".dfxp") ->
            normalized.hasTtmlCue()
        else -> normalized.hasTimedSubtitleCue()
    }
}

private fun String.hasTimedSubtitleCue(): Boolean {
    val lines = lines()
    for (index in lines.indices) {
        if (!SubtitleParsingPatterns.timing.containsMatchIn(lines[index].trim())) continue
        var textIndex = index + 1
        while (textIndex < lines.size && lines[textIndex].trim().isNotEmpty()) {
            val cueText = lines[textIndex].visibleSubtitleText()
            if (
                cueText.isNotBlank() &&
                !cueText.startsWith("NOTE", ignoreCase = true) &&
                !cueText.startsWith("STYLE", ignoreCase = true)
            ) {
                return true
            }
            textIndex++
        }
    }
    return false
}

private fun String.hasAssDialogueCue(): Boolean {
    return lineSequence()
        .map { line -> line.trim() }
        .filter { line -> line.startsWith("Dialogue:", ignoreCase = true) }
        .any { line ->
            line.assDialogueText()
                .visibleAssSubtitleText()
                .isNotBlank()
        }
}

private fun String.assDialogueText(): String {
    var commaCount = 0
    for (index in indices) {
        if (this[index] == ',') {
            commaCount++
            if (commaCount == 9) {
                return substring(index + 1)
            }
        }
    }
    return substringAfterLast(',', missingDelimiterValue = "")
}

private fun String.hasTtmlCue(): Boolean {
    return SubtitleParsingPatterns.ttmlParagraph.findAll(this)
        .any { match ->
            match.groupValues.getOrNull(1)
                ?.visibleSubtitleText()
                ?.isNotBlank() == true
        }
}

private fun String.visibleAssSubtitleText(): String {
    return stripAssOverrideTags()
        .replace(SubtitleParsingPatterns.assBlankEscape, "")
        .visibleSubtitleText()
}

private fun String.stripAssOverrideTags(): String {
    val firstOverrideStart = indexOf('{')
    if (firstOverrideStart < 0) return this

    val builder = StringBuilder(length)
    var index = 0
    while (index < length) {
        val overrideStart = indexOf('{', startIndex = index)
        if (overrideStart < 0) {
            builder.append(this, index, length)
            break
        }

        builder.append(this, index, overrideStart)
        val overrideEnd = indexOf('}', startIndex = overrideStart + 1)
        if (overrideEnd < 0) {
            builder.append(this, overrideStart, length)
            break
        }
        index = overrideEnd + 1
    }
    return builder.toString()
}

private fun String.visibleSubtitleText(): String {
    return replace(SubtitleParsingPatterns.htmlTag, "")
        .replace(SubtitleParsingPatterns.htmlSpaceEntity, " ")
        .replace('\u00A0', ' ')
        .trim()
}

internal fun File.subtitleTextOrNull(): String? {
    if (!isFile || length() <= 0L) return null
    return runCatching { readText(Charsets.UTF_8) }.getOrNull()
}

internal fun File.writeVerifiedSubtitleCacheFile(text: String, mimeType: String): Boolean {
    val directory = parentFile ?: return false
    if (!directory.exists() && !directory.mkdirs()) return false
    val bytes = text.toByteArray(Charsets.UTF_8)
    val tempFile = File(directory, "$name.${System.nanoTime()}.tmp")

    return runCatching {
        FileOutputStream(tempFile).use { output ->
            output.write(bytes)
            output.fd.sync()
        }
        check(tempFile.isFile && tempFile.length() == bytes.size.toLong())
        check(tempFile.hasSubtitleCues(mimeType = mimeType))
        if (exists() && !delete()) {
            check(!exists())
        }
        if (!tempFile.renameTo(this)) {
            tempFile.copyTo(this, overwrite = true)
            check(tempFile.delete() || !tempFile.exists())
        }
        isFile &&
            length() == bytes.size.toLong() &&
            readBytes().contentEquals(bytes) &&
            hasSubtitleCues(mimeType = mimeType)
    }.getOrElse {
        runCatching { tempFile.delete() }
        false
    }
}

internal fun File.hasSubtitleCues(mimeType: String? = null): Boolean {
    return subtitleTextOrNull()?.hasSubtitleCues(mimeType = mimeType, uri = name) == true
}

internal fun List<MaterializedSubtitleSegment>.shouldShiftWebVttCueTimes(): Boolean {
    val samples = filter { it.offsetMs > 0L }
        .mapNotNull { segment ->
            val firstCueStartMs = segment.body.firstWebVttCueStartMs() ?: return@mapNotNull null
            segment to firstCueStartMs
        }
    if (samples.isEmpty()) return false

    val localCueCount = samples.count { (segment, firstCueStartMs) ->
        val localWindowMs = maxOf(segment.durationMs + 5_000L, 60_000L)
        firstCueStartMs < localWindowMs && firstCueStartMs + 10_000L < segment.offsetMs
    }
    val absoluteCueCount = samples.count { (segment, firstCueStartMs) ->
        firstCueStartMs + 10_000L >= segment.offsetMs ||
            kotlin.math.abs(firstCueStartMs - segment.offsetMs) <= segment.durationMs + 10_000L
    }

    return localCueCount > absoluteCueCount
}

internal fun MaterializedSubtitleSegment.normalizedWebVttCueBody(shiftBySegmentOffset: Boolean): String {
    val firstCueStartMs = body.firstWebVttCueStartMs()
    val mapLocalMs = localMapMs
    val timestampMapShiftMs = if (mapLocalMs != null && firstCueStartMs != null) {
        val localWindowMs = maxOf(durationMs + 5_000L, 60_000L)
        val cueLooksLocalToMap = abs(firstCueStartMs - mapLocalMs) <= localWindowMs ||
            firstCueStartMs < localWindowMs
        if (cueLooksLocalToMap) offsetMs - mapLocalMs else 0L
    } else {
        0L
    }
    val shiftMs = if (timestampMapShiftMs != 0L || mapLocalMs != null) {
        timestampMapShiftMs
    } else if (shiftBySegmentOffset) {
        offsetMs
    } else {
        0L
    }
    return body.shiftWebVttCueTimes(shiftMs)
}

private fun String.firstWebVttCueStartMs(): Long? {
    return lineSequence()
        .mapNotNull { line ->
            SubtitleParsingPatterns.webVttTiming
                .find(line.trim())
                ?.groupValues
                ?.getOrNull(1)
                ?.webVttTimestampMs()
        }
        .firstOrNull()
}

private fun String.webVttTimestampMapLocalMs(): Long? {
    return SubtitleParsingPatterns.webVttTimestampMapLocal
        .find(this)
        ?.groupValues
        ?.getOrNull(1)
        ?.subtitleTimestampMs()
}

private fun String.shiftWebVttCueTimes(offsetMs: Long): String {
    if (offsetMs == 0L) return this
    return lineSequence().joinToString("\n") { line ->
        val match = SubtitleParsingPatterns.webVttTiming.find(line.trim()) ?: return@joinToString line
        val startMs = match.groupValues.getOrNull(1)?.webVttTimestampMs() ?: return@joinToString line
        val endMs = match.groupValues.getOrNull(2)?.webVttTimestampMs() ?: return@joinToString line
        val settings = match.groupValues.getOrNull(3).orEmpty()
        "${(startMs + offsetMs).toWebVttTimestamp()} --> ${(endMs + offsetMs).toWebVttTimestamp()}$settings"
    }
}

private fun String.xmlTimeAttribute(name: String): Long? {
    return SubtitleParsingPatterns.xmlTimeAttribute
        .findAll(this)
        .firstOrNull { match -> match.groupValues.getOrNull(1).equals(name, ignoreCase = true) }
        ?.groupValues
        ?.getOrNull(2)
        ?.subtitleTimestampMs()
}

private fun String.subtitleTimestampMs(): Long? {
    val normalized = trim().replace(',', '.')
    val pieces = normalized.split(':')
    if (pieces.size !in 2..3) return null
    val secondsParts = pieces.last().split('.')
    if (secondsParts.size !in 1..2) return null

    val hours = if (pieces.size == 3) pieces[0].toLongOrNull() ?: return null else 0L
    val minutes = pieces[pieces.size - 2].toLongOrNull() ?: return null
    val seconds = secondsParts[0].toLongOrNull() ?: return null
    val milliseconds = secondsParts.getOrNull(1)
        ?.padEnd(3, '0')
        ?.take(3)
        ?.toLongOrNull()
        ?: 0L

    return hours * 3_600_000L + minutes * 60_000L + seconds * 1_000L + milliseconds
}

private fun String.webVttTimestampMs(): Long? {
    val pieces = split(':')
    if (pieces.size !in 2..3) return null
    val secondsParts = pieces.last().split('.')
    if (secondsParts.size != 2) return null

    val hours = if (pieces.size == 3) pieces[0].toLongOrNull() ?: return null else 0L
    val minutes = pieces[pieces.size - 2].toLongOrNull() ?: return null
    val seconds = secondsParts[0].toLongOrNull() ?: return null
    val milliseconds = secondsParts[1].padEnd(3, '0').take(3).toLongOrNull() ?: return null

    return hours * 3_600_000L + minutes * 60_000L + seconds * 1_000L + milliseconds
}

private fun Long.toWebVttTimestamp(): String {
    val safeMs = coerceAtLeast(0L)
    val hours = safeMs / 3_600_000L
    val minutes = (safeMs % 3_600_000L) / 60_000L
    val seconds = (safeMs % 60_000L) / 1_000L
    val milliseconds = safeMs % 1_000L
    return "%02d:%02d:%02d.%03d".format(hours, minutes, seconds, milliseconds)
}
