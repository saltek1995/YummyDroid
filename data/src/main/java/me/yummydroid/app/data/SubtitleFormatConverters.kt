package me.yummydroid.app.data

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

private fun String.toWebVttTimingLine(): String? {
    val match = SubtitleParsingPatterns.timingLine.find(trim()) ?: return null
    val startMs = match.groupValues.getOrNull(1)?.subtitleTimestampMs() ?: return null
    val endMs = match.groupValues.getOrNull(2)?.subtitleTimestampMs() ?: return null
    if (endMs <= startMs) return null
    val settings = match.groupValues.getOrNull(3).orEmpty()
    return "${startMs.toWebVttTimestamp()} --> ${endMs.toWebVttTimestamp()}$settings"
}

private fun String.xmlTimeAttribute(name: String): Long? {
    return SubtitleParsingPatterns.xmlTimeAttribute
        .findAll(this)
        .firstOrNull { match -> match.groupValues.getOrNull(1).equals(name, ignoreCase = true) }
        ?.groupValues
        ?.getOrNull(2)
        ?.subtitleTimestampMs()
}
