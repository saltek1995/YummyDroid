package me.yummydroid.app.ui

import androidx.core.text.HtmlCompat

private val releaseNotesComment = Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL)
private val releaseNotesHtmlTag = Regex("</?[A-Za-z][A-Za-z0-9]*(?:\\s+[^<>]*|\\s*/?)>")

internal fun String.releaseNotesDisplayText(): String {
    val content = replace(releaseNotesComment, "").trim()
    if (!releaseNotesHtmlTag.containsMatchIn(content)) return content
    return HtmlCompat.fromHtml(content, HtmlCompat.FROM_HTML_MODE_LEGACY)
        .toString()
        .trim()
}
