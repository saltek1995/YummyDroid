package me.yummydroid.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.serialization.json.Json

class RatingValueTest {
    @Test
    fun parsesObjectRatingAverage() {
        val element = Json.parseToJsonElement("""{"average":8.46,"count":12}""")

        assertEquals(8.46, element.ratingValue())
    }

    @Test
    fun parsesPrimitiveRating() {
        val element = Json.parseToJsonElement("8.46228427310765")

        assertEquals(8.46228427310765, element.ratingValue())
    }

    @Test
    fun ignoresMissingOrZeroRating() {
        assertNull(null.ratingValue())
        assertNull(Json.parseToJsonElement("""{"average":0}""").ratingValue())
        assertNull(Json.parseToJsonElement("0").ratingValue())
    }

    @Test
    fun parsesSkipSegmentObject() {
        val element = Json.parseToJsonElement("""{"time":51,"length":39}""")
        val segment = element.toVideoSkipSegment(VideoSkipKind.Opening)

        assertEquals(VideoSkipKind.Opening, segment?.kind)
        assertEquals(51_000L, segment?.startMs)
        assertEquals(90_000L, segment?.endMs)
    }

    @Test
    fun parsesSkipSegmentArray() {
        val element = Json.parseToJsonElement("""[1399,1423]""")
        val segment = element.toVideoSkipSegment(VideoSkipKind.Ending)

        assertEquals(VideoSkipKind.Ending, segment?.kind)
        assertEquals(1_399_000L, segment?.startMs)
        assertEquals(1_423_000L, segment?.endMs)
    }
}
