package me.yummydroid.app.data

import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.jsonObject

class AllohaBootstrapProtocolTest {
    @Test fun choosesDeclaredDefaultTranslationAheadOfArrayOrder() {
        val body = VIDEO_RESOLVER_JSON.parseToJsonElement("""{"hlsSource":[
            {"audioId":"first","quality":{"1080":"https://media.example/first.m3u8"}},
            {"audioId":"selected","default":true,"quality":{"720":"https://media.example/selected.m3u8"}}
        ]}""").jsonObject.withAllohaBootstrapSelection()
        val streams = body.toString().extractAllohaRuntimeStreams("https://player.example/")
        assertTrue(streams.isNotEmpty())
        assertTrue(streams.all { it.providerAudioId == "selected" })
    }
    @Test fun keepsFirstTranslationWhenTheProviderDoesNotDeclareADefault() {
        val body = VIDEO_RESOLVER_JSON.parseToJsonElement("""{"hlsSource":[
            {"audioId":"first","quality":{"720":"https://media.example/first.m3u8"}},
            {"audioId":"other","quality":{"1080":"https://media.example/other.m3u8"}}
        ]}""").jsonObject.withAllohaBootstrapSelection()
        val streams = body.toString().extractAllohaRuntimeStreams("https://player.example/")
        assertTrue(streams.isNotEmpty())
        assertTrue(streams.all { it.providerAudioId == "first" })
    }
    @Test fun archivedBootstrapIsCompatibleWhenExplicitlySupplied() {
        val path = System.getenv("YUMMY_ALLOHA_BOOTSTRAP_ARCHIVE")
        org.junit.Assume.assumeTrue("Opt-in offline provider archive", path != null)
        val directory = java.io.File(requireNotNull(path))
        val page = parseAllohaBootstrapPage(directory.resolve("alloha.html").readText(), "https://alloha.yani.tv/")
        assertTrue(page.viewportSeed.isNotBlank())
        assertTrue(page.bundleUrl.startsWith("https://alloha.yani.tv/build/app."))
        // No assertion renders either the provider guard or page tokens on failure.
        assertTrue(extractAllohaBootstrapGuard(directory.resolve("alloha-app.js").readText()).isNotBlank())
    }
    @Test fun parsesStrictBootstrap() {
        val html = """<meta name='viewporti' content='width=device-width&amp;seed=x'><script src='/build/app.abc-12.js'></script><script>const userParam={token:'token',domain:"example.org",device:0,hidden:JSON.parse('{"x":1}'),autoplay:0,start:'',audio:'a',subtitle:'s'}; const movie={id:'1',type:'movie'}; const fileList=JSON.parse('{"active":{"id":"42"}}');</script>"""
        val page = parseAllohaBootstrapPage(html, "https://example.org/embed/1")
        assertEquals("token", page.userParam["token"]!!.jsonPrimitive.content)
        assertEquals("width=device-width&seed=x", page.viewportSeed)
        assertEquals("https://example.org/build/app.abc-12.js", page.bundleUrl)
    }

    @Test fun rejectsMalformedAndCrossOriginBootstrap() {
        val base = """<meta name=viewporti content=x><script>const userParam={token:'x',domain:'d'};const movie={type:'m'};const fileList=JSON.parse('{"active":{"id":"1"}}');</script>"""
        assertEquals("https://example.org/build/app.a.js", parseAllohaBootstrapPage(
            base + "<script src='/build/app.a.js'>", "https://example.org/x").bundleUrl)
        assertFailsWith<UnsupportedAllohaBootstrap> { parseAllohaBootstrapPage(base, "https://example.org/x") }
        assertFailsWith<UnsupportedAllohaBootstrap> { parseAllohaBootstrapPage(base + "<script src='https://other.org/build/app.a.js'>", "https://example.org/x") }
    }

    @Test fun extractsOnlyHashPinnedGuardAndDecodesEscapes() {
        val bundle = "function Xk(){return zK['a'](1 + 2, 'g\\x75\\u0061rd');}"
        val normalized = bundle.replace("'g\\x75\\u0061rd'", "\"__PROVIDER_GUARD__\"")
        val hash = MessageDigest.getInstance("SHA-256").digest(normalized.toByteArray()).joinToString("") { "%02x".format(it) }
        assertEquals("guard", extractAllohaBootstrapGuard(bundle, hash))
        assertEquals("rotated", extractAllohaBootstrapGuard(bundle.replace("'g\\x75\\u0061rd'", "'rotated'"), hash))
        assertFailsWith<UnsupportedAllohaBootstrap> { extractAllohaBootstrapGuard(bundle.replace("1 + 2", "1 + 3"), hash) }
    }
}
