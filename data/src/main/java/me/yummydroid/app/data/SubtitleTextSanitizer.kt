package me.yummydroid.app.data

internal fun String.visibleSubtitleText(): String {
    return replace(SubtitleParsingPatterns.htmlTag, "")
        .replace(SubtitleParsingPatterns.htmlSpaceEntity, " ")
        .replace('\u00A0', ' ')
        .trim()
}

internal fun String.stripAssOverrideTags(): String {
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

internal fun String.visibleAssSubtitleText(): String {
    return stripAssOverrideTags()
        .replace(SubtitleParsingPatterns.assBlankEscape, "")
        .visibleSubtitleText()
}
