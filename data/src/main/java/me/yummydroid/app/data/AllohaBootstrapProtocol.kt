package me.yummydroid.app.data

import java.io.IOException
import java.security.MessageDigest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.booleanOrNull
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal const val ALLOHA_BOOTSTRAP_NORMALIZED_SHA256 =
    "e746808552ea65f95f83b8b3bd1c01a5c8ad58b5159a4dc26912fff4e0ef2440"

internal class UnsupportedAllohaBootstrap : IOException("Alloha bootstrap version is unsupported")

internal data class AllohaBootstrapPage(
    val userParam: JsonObject,
    val fileList: JsonObject,
    val movie: JsonObject,
    val viewportSeed: String,
    val bundleUrl: String,
)

/** The metadata response, not array order, identifies the provider-selected translation. */
internal fun JsonObject.withAllohaBootstrapSelection(): JsonObject {
    val sources = (this["hlsSource"] as? JsonArray)?.filterIsInstance<JsonObject>().orEmpty()
    val selected = sources.firstOrNull { (it["default"] as? JsonPrimitive)?.booleanOrNull == true }
        ?: sources.firstOrNull() ?: return this
    // Quality selection must stay inside the selected translation. Other audio sources
    // are not mirrors, even when they happen to offer a higher resolution.
    return JsonObject(this + mapOf("currentSource" to selected, "hlsSource" to JsonArray(listOf(selected))))
}

internal fun parseAllohaBootstrapPage(html: String, pageUrl: String): AllohaBootstrapPage {
    val user = assignment(html, "userParam")?.let(::scalarObject) ?: unsupported()
    val token = user["token"]?.trim().orEmpty()
    val domain = user["domain"]?.trim().orEmpty()
    if (token.isEmpty() || domain.isEmpty()) unsupported()
    val movie = assignment(html, "movie")?.let(::scalarObject) ?: unsupported()
    if (movie["type"]?.trim().isNullOrEmpty()) unsupported()
    val fileExpression = assignment(html, "fileList") ?: unsupported()
    val fileArgument = jsonParseArgument(fileExpression) ?: unsupported()
    val fileList = runCatching { VIDEO_RESOLVER_JSON.parseToJsonElement(fileArgument) as? JsonObject }.getOrNull() ?: unsupported()
    val active = fileList["active"] as? JsonObject ?: unsupported()
    if ((active["id"] as? JsonPrimitive)?.contentOrNull.isNullOrBlank()) unsupported()
    val viewport = viewportContent(html) ?: unsupported()
    val bundle = bundleUrl(html, pageUrl) ?: unsupported()
    return AllohaBootstrapPage(
        userParam = JsonObject(user.mapValues { JsonPrimitive(it.value) }),
        fileList = fileList,
        movie = JsonObject(movie.mapValues { JsonPrimitive(it.value) }),
        viewportSeed = viewport,
        bundleUrl = bundle,
    )
}

internal fun extractAllohaBootstrapGuard(
    bundle: String,
    expectedNormalizedSha256: String = ALLOHA_BOOTSTRAP_NORMALIZED_SHA256,
): String {
    val body = functionBody(bundle, "Xk") ?: unsupported()
    val candidates = RETURN_GUARD.findAll(body).mapNotNull { match ->
        val raw = match.groups[3]?.value ?: return@mapNotNull null
        val value = decodeJsString(raw) ?: return@mapNotNull null
        val start = bundle.indexOf(body) + match.groups[3]!!.range.first
        val normalized = bundle.replaceRange(start, start + raw.length, "\"__PROVIDER_GUARD__\"")
        value.takeIf { it.isNotBlank() && sha256(normalized) == expectedNormalizedSha256 }
    }.toList()
    return candidates.singleOrNull() ?: unsupported()
}

private val RETURN_GUARD = Regex("""return\s+([A-Za-z_$][\w$]*)\s*\[\s*(['"])a\2\s*]\s*\(\s*[-+0-9xa-fA-F\s()]+\s*,\s*((?:'(?:\\.|[^'\\])*')|(?:"(?:\\.|[^"\\])*"))\s*\)""")

private fun unsupported(): Nothing = throw UnsupportedAllohaBootstrap()

private fun sha256(text: String): String = MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

private fun assignment(source: String, name: String): String? {
    val matches = Regex("""(?:const|let|var)\s+${Regex.escape(name)}\s*=""").findAll(source).mapNotNull {
        expressionAt(source, it.range.last + 1)
    }.toList()
    return matches.singleOrNull()
}

private fun expressionAt(source: String, offset: Int): String? {
    var start = offset
    while (start < source.length && source[start].isWhitespace()) start++
    return when (source.getOrNull(start)) {
        '{' -> balanced(source, start, '{', '}')
        else -> if (source.startsWith("JSON.parse", start)) {
            val open = source.indexOf('(', start)
            if (open < 0) null else balanced(source, open, '(', ')')?.let { "JSON.parse$it" }
        } else null
    }
}

private fun scalarObject(expression: String): Map<String, String>? {
    if (!expression.startsWith('{') || !expression.endsWith('}')) return null
    val result = linkedMapOf<String, String>()
    var index = 1
    while (index < expression.length - 1) {
        index = skipSpaceAndComma(expression, index)
        if (index >= expression.length - 1) break
        val key = readKey(expression, index) ?: return null
        index = key.second
        index = skipSpaceAndComma(expression, index)
        if (expression.getOrNull(index) != ':') return null
        index = skipSpaceAndComma(expression, index + 1)
        val value = readScalar(expression, index)
        if (value != null) {
            result[key.first] = value.first
            index = value.second
        } else {
            index = skipValue(expression, index) ?: return null
        }
    }
    return result
}

private fun readKey(source: String, index: Int): Pair<String, Int>? = when (source.getOrNull(index)) {
    '\'', '"' -> readQuoted(source, index)?.let { decodeJsString(it.first)?.let { value -> value to it.second } }
    else -> Regex("[A-Za-z_$][\\w$]*").find(source, index)?.takeIf { it.range.first == index }?.let { it.value to it.range.last + 1 }
}

private fun readScalar(source: String, index: Int): Pair<String, Int>? = when (source.getOrNull(index)) {
    '\'', '"' -> readQuoted(source, index)?.let { decodeJsString(it.first)?.let { value -> value to it.second } }
    else -> Regex("(?:true|false|null|-?(?:0|[1-9]\\d*)(?:\\.\\d+)?)(?![\\w$])").find(source, index)
        ?.takeIf { it.range.first == index }?.let { it.value to it.range.last + 1 }
}

private fun jsonParseArgument(expression: String): String? {
    val match = Regex("""^JSON\.parse\s*\(""").find(expression) ?: return null
    var start = match.range.last + 1
    while (expression.getOrNull(start)?.isWhitespace() == true) start++
    val quoted = readQuoted(expression, start) ?: return null
    var end = quoted.second
    while (expression.getOrNull(end)?.isWhitespace() == true) end++
    if (expression.getOrNull(end) != ')' || expression.substring(end + 1).trim().isNotEmpty()) return null
    return decodeJsString(quoted.first)
}

private fun skipValue(source: String, index: Int): Int? {
    var i = index
    var parens = 0
    while (i < source.length - 1) {
        when (source[i]) {
            '\'', '"' -> { val quoted = readQuoted(source, i) ?: return null; i = quoted.second; continue }
            '{' -> { val objectValue = balanced(source, i, '{', '}') ?: return null; i += objectValue.length; continue }
            '[' -> { val arrayValue = balanced(source, i, '[', ']') ?: return null; i += arrayValue.length; continue }
            '(' -> parens++
            ')' -> if (parens-- == 0) return null
            ',' -> if (parens == 0) return i
        }
        i++
    }
    return source.length - 1
}

private fun skipSpaceAndComma(source: String, initial: Int): Int { var i = initial; while (source.getOrNull(i)?.let { it.isWhitespace() || it == ',' } == true) i++; return i }

private fun readQuoted(source: String, start: Int): Pair<String, Int>? {
    val quote = source.getOrNull(start) ?: return null
    if (quote != '\'' && quote != '"') return null
    var i = start + 1
    while (i < source.length) {
        if (source[i] == '\\') { i += 2; continue }
        if (source[i] == quote) return source.substring(start, i + 1) to i + 1
        i++
    }
    return null
}

private fun decodeJsString(raw: String): String? {
    if (raw.length < 2 || raw.first() != raw.last() || raw.first() !in "'\"") return null
    val out = StringBuilder(); var i = 1
    while (i < raw.length - 1) {
        val c = raw[i++]
        if (c != '\\') { out.append(c); continue }
        when (val escape = raw.getOrNull(i++) ?: return null) {
            '\\' -> out.append('\\'); '\'' -> out.append('\''); '"' -> out.append('"'); '/' -> out.append('/')
            'n' -> out.append('\n'); 'r' -> out.append('\r'); 't' -> out.append('\t'); 'b' -> out.append('\b'); 'f' -> out.append('\u000C')
            'x' -> { val hex = raw.substringOrNull(i, i + 2) ?: return null; out.append(hex.toIntOrNull(16)?.toChar() ?: return null); i += 2 }
            'u' -> { val hex = raw.substringOrNull(i, i + 4) ?: return null; out.append(hex.toIntOrNull(16)?.toChar() ?: return null); i += 4 }
            else -> return null
        }
    }
    return out.toString()
}

private fun String.substringOrNull(start: Int, end: Int): String? = if (start <= end && end <= length) substring(start, end) else null

private fun balanced(source: String, start: Int, open: Char, close: Char): String? {
    var depth = 0; var i = start
    while (i < source.length) {
        when (source[i]) {
            '\'', '"' -> { val quote = readQuoted(source, i) ?: return null; i = quote.second; continue }
            open -> depth++
            close -> if (--depth == 0) return source.substring(start, i + 1)
        }; i++
    }; return null
}

private fun viewportContent(html: String): String? = META.findAll(html).mapNotNull { tag ->
    val attrs = attributes(tag.value)
    attrs["name"]?.equals("viewporti", true)?.takeIf { it }?.let { attrs["content"]?.let(::decodeHtml) }
}.singleOrNull()?.takeIf { it.isNotBlank() }

private fun bundleUrl(html: String, pageUrl: String): String? {
    val page = pageUrl.toHttpUrlOrNull() ?: return null
    val candidates = SCRIPT.findAll(html).mapNotNull { attributes(it.value)["src"] }
        .filter { Regex("""(?:^|/)build/app\.[A-Za-z0-9_-]+\.js(?:[?#].*)?$""").matches(it) }
        .mapNotNull { page.resolve(decodeHtml(it)) }
        .filter { it.scheme == "https" && it.scheme == page.scheme && it.host == page.host && it.port == page.port }
        .map { it.toString() }.toList()
    return candidates.singleOrNull()
}

private val META = Regex("""<meta\b[^>]*>""", RegexOption.IGNORE_CASE)
private val SCRIPT = Regex("""<script\b[^>]*>""", RegexOption.IGNORE_CASE)
private val ATTR = Regex("""([A-Za-z_:][-A-Za-z0-9_:.]*)\s*=\s*(?:\"([^\"]*)\"|'([^']*)'|([^\s>]+))""")
private fun attributes(tag: String) = ATTR.findAll(tag).associate { it.groupValues[1].lowercase() to it.groupValues.drop(2).firstOrNull { value -> value.isNotEmpty() }.orEmpty() }
private fun decodeHtml(value: String) = value.replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace("&lt;", "<").replace("&gt;", ">")

private fun functionBody(source: String, name: String): String? {
    val match = Regex("""function\s+${Regex.escape(name)}\s*\(""").findAll(source).toList().singleOrNull() ?: return null
    val brace = source.indexOf('{', match.range.last + 1); if (brace < 0) return null
    return balanced(source, brace, '{', '}')
}
