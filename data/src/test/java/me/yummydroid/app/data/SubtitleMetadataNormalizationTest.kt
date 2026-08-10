package me.yummydroid.app.data

import kotlin.test.Test
import kotlin.test.assertEquals

class SubtitleMetadataNormalizationTest {
    @Test
    fun escapedPathAndQuerySeparatorsAreDecoded() {
        val metadata = "https:\\/\\/cdn.example.test\\/subs.vtt?one=1&amp;two=2\\u0026three=3"

        assertEquals(
            "https://cdn.example.test/subs.vtt?one=1&two=2&three=3",
            metadata.normalizeSubtitleMetadataBody(),
        )
    }
}
