package me.yummydroid.app.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SearchQueryTest {
    @Test
    fun unrelatedServerFallbacksAreRemovedButAlternateTitlesAreKept() = runBlocking {
        val candidates = listOf(
            AnimeDto(animeId = 1, title = "Death Note"),
            AnimeDto(animeId = 2, title = "Тетрадь смерти"),
            AnimeDto(animeId = 3, title = "Death Parade"),
        )
        val result = candidates.matchingSearchTitles("Death Note") { id ->
            when (id) {
                2L -> candidates[1].copy(otherTitles = listOf("Death Note"))
                3L -> candidates[2].copy(otherTitles = listOf("Death", "Note"))
                else -> error("Matching visible titles must not need details")
            }
        }
        assertEquals(listOf(1L, 2L), result.map { it.animeId })
    }

    @Test
    fun unknownWordsCannotBeSilentlyIgnoredByTheServer() = runBlocking {
        val candidate = AnimeDto(animeId = 1, title = "Тетрадь смерти")
        assertEquals(emptyList(), listOf(candidate).matchingSearchTitles("Тетрадь несуществующийтекст") { candidate })
    }

    @Test
    fun casePunctuationWhitespaceAndYoDoNotPreventTitleMatches() = runBlocking {
        val candidate = AnimeDto(title = "Звёздный путь: Финал")
        assertEquals(listOf(candidate), listOf(candidate).matchingSearchTitles("  ЗВЕЗДНЫЙ\tпуть — финал ") {
            error("Normalized title already matches")
        })
    }

    @Test
    fun singleWordSearchKeepsExistingServerMatching() = runBlocking {
        val candidates = listOf(AnimeDto(title = "Наруто"))
        assertEquals(candidates, candidates.matchingSearchTitles("нар") { error("No detail requests") })
        assertEquals(candidates, candidates.matchingSearchTitles("Re:Zero") { error("No detail requests") })
    }

    @Test
    fun cancellationDuringAlternateTitleLookupPropagates() = runBlocking {
        assertFailsWith<CancellationException> {
            listOf(AnimeDto(title = "Тетрадь смерти")).matchingSearchTitles("Death Note") {
                throw CancellationException("Query changed")
            }
        }
        Unit
    }
}
