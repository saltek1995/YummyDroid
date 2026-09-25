package me.yummydroid.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AllohaBrowserHeadersTest {
    @Test fun expandsTheBrowsersLanguagePreferences() {
        assertEquals("en-US,en;q=0.9", allohaBrowserAcceptLanguage(listOf("en-US"), "151.0.7922.199"))
        assertEquals("ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7",
            allohaBrowserAcceptLanguage(listOf("ru-RU", "en-US"), "120.0.6099.211"))
    }

    @Test fun groupsRegionalVariantsAndDeduplicatesPrimaryLanguages() {
        assertEquals("en-US,en-GB;q=0.9,en;q=0.8",
            allohaBrowserAcceptLanguage(listOf("en-US", "en-GB"), "130.0.6723.116"))
        assertEquals("en-US,en;q=0.9,en-GB;q=0.8",
            allohaBrowserAcceptLanguage(listOf("en-US", "en", "en-GB"), "140.0.7289.1"))
    }

    @Test fun rejectsUnverifiedVersionsAndInvalidLanguageData() {
        assertNull(allohaBrowserAcceptLanguage(listOf("en-US"), null))
        assertNull(allohaBrowserAcceptLanguage(listOf("en-US"), "119.0.0.0"))
        assertNull(allohaBrowserAcceptLanguage(listOf("en-US\r\nInjected: true"), "151.0.0.0"))
        assertNull(allohaBrowserAcceptLanguage(emptyList(), "151.0.0.0"))
    }

    @Test fun floorsLongPreferenceListsAtPointOne() {
        val header = allohaBrowserAcceptLanguage(listOf("de-DE", "ja-JP", "ko-KR", "fr-FR", "es-ES", "en-US"), "150.0.0.0")
        assertEquals("de-DE,de;q=0.9,ja-JP;q=0.8,ja;q=0.7,ko-KR;q=0.6,ko;q=0.5,fr-FR;q=0.4,fr;q=0.3,es-ES;q=0.2,es;q=0.1,en-US;q=0.1,en;q=0.1", header)
    }
}
