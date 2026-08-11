package me.yummydroid.app.data

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
        "x-ssa" in typeHint || typeHint.endsWith(".ass") || typeHint.endsWith(".ssa") ->
            normalized.hasAssDialogueCue()
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
