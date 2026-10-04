package me.yummydroid.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FilterOptionsTest {
    @Test
    fun sortApiContractsRemainStable() {
        assertEquals("rating", AnimeSort.Rating.apiValue)
        assertFalse(AnimeSort.Rating.forward)
        assertEquals("title", AnimeSort.Title.apiValue)
        assertFalse(AnimeSort.Title.forward)
        assertTrue(AnimeSort.Top.forward)
        assertEquals("id", AnimeSort.Id.apiValue)
        assertEquals("random", AnimeSort.Random.apiValue)
        assertTrue(AnimeSort.Random.forward)
    }

    @Test
    fun optionValuesMatchRemoteApiContracts() {
        assertEquals(listOf("released", "ongoing", "announcement"), statusFilterOptions.map { it.value })
        assertEquals(listOf("winter", "spring", "summer", "fall"), seasonFilterOptions.map { it.value })
        assertEquals(listOf("dubbing", "multivoice", "duet", "onevoice", "subtitles"), translateFilterOptions.map { it.value })
        assertEquals(listOf("G" to "1", "PG" to "2", "PG-13" to "3", "R-17+" to "4", "R+" to "5"), ageRatingFilterOptions.map { it.title to it.value })
        assertEquals(listOf("0", "1", "2", "3", "5", "4"), userMarkFilterOptions.map { it.value })
    }
}
