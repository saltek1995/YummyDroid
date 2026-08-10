package me.yummydroid.app.data

import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.xml.sax.InputSource

internal class SubtitleDashMetadataParser {
    fun extractTracks(body: String): List<ResolvedEmbeddedSubtitleTrack> {
        val document = runCatching {
            secureDocumentBuilderFactory()
                .newDocumentBuilder()
                .parse(InputSource(StringReader(body)))
        }.getOrNull() ?: return emptyList()
        val adaptationSets = document.getElementsByTagNameNS("*", "AdaptationSet")
        return (0 until adaptationSets.length)
            .asSequence()
            .mapNotNull { index -> adaptationSets.item(index) as? Element }
            .filter { element -> element.isDashSubtitleAdaptationSet() }
            .map { element -> element.dashEmbeddedSubtitleTrack() }
            .toList()
            .normalizedEmbeddedSubtitleTracks()
    }

    private fun secureDocumentBuilderFactory(): DocumentBuilderFactory {
        return DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
            runCatching { setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
            runCatching { setXIncludeAware(false) }
            runCatching { setExpandEntityReferences(false) }
        }
    }

    private fun Element.isDashSubtitleAdaptationSet(): Boolean {
        val contentType = attributeOrBlank("contentType").lowercase()
        val mimeType = attributeOrBlank("mimeType").lowercase()
        val codecs = (
            listOf(attributeOrBlank("codecs")) +
                childElements("Representation").map { it.attributeOrBlank("codecs") }
            )
            .joinToString(",")
            .lowercase()
        val roles = childElements("Role")
            .map { role -> role.attributeOrBlank("value").lowercase() }
        return contentType == "text" ||
            mimeType.startsWith("text/") ||
            "ttml" in mimeType ||
            "vtt" in mimeType ||
            "wvtt" in codecs ||
            "stpp" in codecs ||
            roles.any { role -> role == "subtitle" || role == "caption" }
    }

    private fun Element.dashEmbeddedSubtitleTrack(): ResolvedEmbeddedSubtitleTrack {
        val representations = childElements("Representation")
        val language = attributeOrBlank("lang")
            .ifBlank { getAttributeNS("http://www.w3.org/XML/1998/namespace", "lang") }
            .ifBlank { null }
        val label = childText("Label")
            .ifBlank { attributeOrBlank("label") }
            .ifBlank { attributeOrBlank("name") }
            .ifBlank {
                representations.firstNotNullOfOrNull {
                    it.childText("Label").takeIf(String::isNotBlank)
                }.orEmpty()
            }
            .ifBlank {
                representations.firstNotNullOfOrNull {
                    it.attributeOrBlank("label").takeIf(String::isNotBlank)
                }.orEmpty()
            }
        val id = attributeOrBlank("id")
            .ifBlank {
                representations.firstNotNullOfOrNull {
                    it.attributeOrBlank("id").takeIf(String::isNotBlank)
                }.orEmpty()
            }
            .ifBlank { label }
        return ResolvedEmbeddedSubtitleTrack(id = id, label = label, language = language)
    }

    private fun Element.childElements(name: String): List<Element> {
        val nodes = getElementsByTagNameNS("*", name)
        return (0 until nodes.length).mapNotNull { index -> nodes.item(index) as? Element }
    }

    private fun Element.childText(name: String): String {
        return childElements(name).firstOrNull()?.textContent?.trim().orEmpty()
    }

    private fun Element.attributeOrBlank(name: String): String = getAttribute(name).trim()
}
