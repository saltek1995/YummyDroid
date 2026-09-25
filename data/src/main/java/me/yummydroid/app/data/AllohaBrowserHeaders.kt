package me.yummydroid.app.data

/** Reconstructs Chromium's header from its own navigator.languages, not the app UI language. */
internal fun allohaBrowserAcceptLanguage(languages: List<String>, webViewVersion: String?): String? {
    // ExpandLanguageList / GenerateAcceptLanguageHeader in net/http/http_util.cc,
    // verified against Chromium M120, M130, M140 and M150. Older branches differ.
    val major = webViewVersion?.substringBefore('.')?.toIntOrNull() ?: return null
    if (major < 120 || languages.isEmpty() || languages.size > 20) return null
    val languageTag = Regex("[A-Za-z]{1,8}(?:-[A-Za-z0-9]{1,8})*")
    if (languages.any { !languageTag.matches(it) }) return null
    val expanded = linkedMapOf<String, String>()
    fun add(language: String) { expanded.putIfAbsent(language.lowercase(), language) }
    languages.forEachIndexed { index, language ->
        add(language)
        val primary = language.substringBefore('-')
        val nextPrimary = languages.getOrNull(index + 1)?.substringBefore('-')
        if (!primary.equals(nextPrimary, ignoreCase = true)) add(primary)
    }
    return expanded.values.mapIndexed { index, language ->
        if (index == 0) language else "$language;q=0.${(10 - index).coerceAtLeast(1)}"
    }.joinToString(",")
}
