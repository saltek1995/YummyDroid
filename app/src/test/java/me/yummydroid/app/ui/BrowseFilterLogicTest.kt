package me.yummydroid.app.ui

import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import me.yummydroid.app.data.BrowseFilters
import me.yummydroid.app.data.FilterOption

class BrowseFilterLogicTest {
    @Test
    fun advancedCountIncludesAuthorizedMarksAndAllRangeEndpoints() {
        val filters = BrowseFilters(
            fromYear = 2000,
            toYear = 2026,
            minRating = 5.0,
            maxRating = 10.0,
            episodeFrom = 1,
            episodeTo = 12,
            excludedGenres = setOf("drama"),
            seasons = setOf("spring"),
            types = setOf("tv"),
            studios = setOf("studio"),
            creators = setOf("creator"),
            translates = setOf("dubbing"),
            ageRatings = setOf("2"),
            userMarks = setOf("0"),
            excludedUserMarks = setOf("3"),
            offlineOnly = true,
        )

        assertEquals(16, filters.advancedFilterCount(isAuthorized = true))
        assertEquals(14, filters.advancedFilterCount(isAuthorized = false))
    }

    @Test
    fun numericInputSanitizersPreserveOnlySupportedSyntaxAndLength() {
        assertEquals("12345", integerInput("a12-34567"))
        assertEquals("1.23", decimalInput("a1,2.3"))
    }

    @Test
    fun rangeValuesRejectOutOfDomainNumbers() {
        assertEquals(1900, "1900".yearFilterValue())
        assertEquals(2100, "2100".yearFilterValue())
        assertNull("1899".yearFilterValue())
        assertNull("2101".yearFilterValue())

        assertEquals(0, "0".episodeFilterValue())
        assertEquals(10000, "10000".episodeFilterValue())
        assertNull("10001".episodeFilterValue())

        assertEquals(0.0, "0".ratingFilterValue())
        assertEquals(10.0, "10".ratingFilterValue())
        assertNull("10.1".ratingFilterValue())
    }

    @Test
    fun optionsUseLocalizedTitleOrderingAndValueAsTieBreaker() {
        val options = listOf(
            FilterOption(title = "beta", value = "2"),
            FilterOption(title = "Alpha", value = "3"),
            FilterOption(title = "alpha", value = "1"),
        )

        assertEquals(
            listOf("1", "3", "2"),
            options.sortedByTitle(Locale.ENGLISH).map(FilterOption::value),
        )
    }

    @Test
    fun mergedOptionsKeepSelectedValuesMissingFromCatalog() {
        val result = mergedFilterOptions(
            catalogOptions = listOf(FilterOption(title = "Known", value = "known")),
            selectedValues = setOf("missing"),
            selectedTitles = mapOf("missing" to "Remembered"),
        )

        assertEquals(setOf("known", "missing"), result.map(FilterOption::value).toSet())
        assertEquals("Remembered", result.single { it.value == "missing" }.title)
    }

    @Test
    fun studioAndCreatorTogglesKeepTitlesInSyncWithSelections() {
        val selected = BrowseFilters()
            .toggleStudioFilter("studio-id", "Studio title")
            .toggleCreatorFilter("creator-id", "Creator title")

        assertEquals(setOf("studio-id"), selected.studios)
        assertEquals(mapOf("studio-id" to "Studio title"), selected.studioTitles)
        assertEquals(setOf("creator-id"), selected.creators)
        assertEquals(mapOf("creator-id" to "Creator title"), selected.creatorTitles)

        val cleared = selected
            .toggleStudioFilter("studio-id", null)
            .toggleCreatorFilter("creator-id", null)
        assertEquals(BrowseFilters(), cleared)
    }

    @Test
    fun singleChoiceToggleReplacesPreviousSelectionAndCanClearIt() {
        assertEquals(setOf("ongoing"), setOf("released", "announcement").toggleSingle("ongoing"))
        assertEquals(emptySet(), setOf("ongoing").toggleSingle("ongoing"))
    }

    @Test
    fun genreTogglesMoveValuesBetweenIncludedAndExcludedSelections() {
        val filters = BrowseFilters(
            genres = setOf("action", "drama"),
            excludedGenres = setOf("comedy"),
        )

        val excluded = filters.toggleExcludedGenre("action")
        assertEquals(setOf("drama"), excluded.genres)
        assertEquals(setOf("comedy", "action"), excluded.excludedGenres)

        val included = excluded.toggleIncludedGenre("action")
        assertEquals(setOf("drama", "action"), included.genres)
        assertEquals(setOf("comedy"), included.excludedGenres)

        val cleared = included.toggleIncludedGenre("action")
        assertEquals(setOf("drama"), cleared.genres)
        assertEquals(setOf("comedy"), cleared.excludedGenres)
    }

    @Test
    fun filterOptionLocalizationMapsOnlyKnownApplicableCodes() {
        val markKeys = listOf(
            UiStringKey.Watching,
            UiStringKey.Planned,
            UiStringKey.Watched,
            UiStringKey.Dropped,
            UiStringKey.Favorites,
            UiStringKey.Postponed,
        )
        markKeys.forEachIndexed { value, key ->
            assertEquals(key, FilterOption("Mark", value.toString()).localizedTitleKey("user_marks"))
            assertEquals(key, FilterOption("Mark", value.toString()).localizedTitleKey("excluded_user_marks"))
        }
        (1..5).forEach { value ->
            assertNull(FilterOption("Age", value.toString()).localizedTitleKey("age"))
            assertNull(FilterOption("Studio", value.toString()).localizedTitleKey("studios"))
            assertNull(FilterOption("Creator", value.toString()).localizedTitleKey("creators"))
        }

        mapOf(
            "released" to UiStringKey.Released,
            "ongoing" to UiStringKey.Ongoing,
            "announcement" to UiStringKey.Announcements,
            "announce" to UiStringKey.Announcements,
        ).forEach { (value, key) ->
            assertEquals(key, FilterOption("Status", value).localizedTitleKey("status"))
        }
        mapOf(
            "winter" to UiStringKey.Winter,
            "spring" to UiStringKey.Spring,
            "summer" to UiStringKey.Summer,
            "fall" to UiStringKey.Fall,
        ).forEach { (value, key) ->
            assertEquals(key, FilterOption("Season", value).localizedTitleKey("seasons"))
        }
        mapOf(
            "dubbing" to UiStringKey.FullDubbing,
            "multivoice" to UiStringKey.MultiVoice,
            "duet" to UiStringKey.TwoVoice,
            "onevoice" to UiStringKey.SingleVoice,
            "subtitles" to UiStringKey.Subtitles,
        ).forEach { (value, key) ->
            assertEquals(key, FilterOption("Translation", value).localizedTitleKey("translates"))
        }
    }

    @Test
    fun dialogNormalizationCollapsesSinglesAndRemovesContradictorySelections() {
        val normalized = BrowseFilters(
            statuses = linkedSetOf("released", "ongoing"),
            seasons = linkedSetOf("spring", "fall"),
            types = linkedSetOf("tv", "movie"),
            ageRatings = linkedSetOf("1", "5"),
            genres = setOf("action", "drama"),
            excludedGenres = setOf("action", "comedy"),
            studios = setOf("studio-a", "studio-b"),
            userMarks = setOf("0", "2"),
            excludedUserMarks = setOf("2", "3"),
        ).normalizedForFiltersDialog(isAuthorized = true, forcedOfflineMode = false)

        assertEquals(setOf("released"), normalized.statuses)
        assertEquals(setOf("spring"), normalized.seasons)
        assertEquals(setOf("tv"), normalized.types)
        assertEquals(setOf("1"), normalized.ageRatings)
        assertEquals(setOf("drama"), normalized.genres)
        assertEquals(setOf("action", "comedy"), normalized.excludedGenres)
        assertEquals(setOf("studio-a", "studio-b"), normalized.studios)
        assertEquals(setOf("0"), normalized.userMarks)
        assertEquals(setOf("2", "3"), normalized.excludedUserMarks)

        val anonymous = normalized.normalizedForFiltersDialog(isAuthorized = false, forcedOfflineMode = false)
        assertEquals(emptySet(), anonymous.userMarks)
        assertEquals(emptySet(), anonymous.excludedUserMarks)
    }
}
