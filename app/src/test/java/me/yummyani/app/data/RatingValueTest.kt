package me.yummyani.app.data

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
}
