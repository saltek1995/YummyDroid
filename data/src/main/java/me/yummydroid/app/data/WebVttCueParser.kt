package me.yummydroid.app.data

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
    return lineSequence()
        .firstOrNull { it.isNotBlank() }
        ?.trim()
        ?.isWebVttTopLevelBlockStart() == true
}

private fun String.isWebVttTopLevelBlockStart(): Boolean {
    return equals("STYLE", ignoreCase = true) ||
        equals("REGION", ignoreCase = true) ||
        startsWith("NOTE", ignoreCase = true)
}

private fun String.webVttTimestampMapLocalMs(): Long? {
    return SubtitleParsingPatterns.webVttTimestampMapLocal
        .find(this)
        ?.groupValues
        ?.getOrNull(1)
        ?.subtitleTimestampMs()
}

private const val SIGN_CUE_START_PERCENT = 8
private const val SIGN_CUE_MAX_END_PERCENT = 84
private const val SIGN_CUE_LINE_HEIGHT_PERCENT = 10
private const val SIGN_CUE_INNER_GAP_PERCENT = 1
private const val SIGN_CUE_GAP_PERCENT = 4
private const val SIGN_CUE_MIN_HEIGHT_PERCENT = 10
private const val DIALOGUE_CUE_GAP_LINES = 1
