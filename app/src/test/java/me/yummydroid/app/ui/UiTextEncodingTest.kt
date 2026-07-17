package me.yummydroid.app.ui

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import me.yummydroid.app.formatPlaybackTime
import me.yummydroid.app.formatRating
import me.yummydroid.app.formatViews
import me.yummydroid.app.data.Anime
import me.yummydroid.app.data.ScheduleAnime

class UiTextEncodingTest {
    @Test
    fun formatsPlayerTimeAndRatingsConsistently() {
        assertEquals("00:00", formatPlaybackTime(-1_000))
        assertEquals("01:05", formatPlaybackTime(65_000))
        assertEquals("1:01:05", formatPlaybackTime(3_665_000))
        assertEquals("9.6", formatRating(9.56))
    }

    @Test
    fun formatsViewsWithReadableRussianSuffixes() {
        assertEquals("999", formatViews(999))
        assertEquals("1.2 тыс", formatViews(1_234))
        assertEquals("448 тыс", formatViews(448_000))
        assertEquals("1.3 млн", formatViews(1_300_000))
        assertEquals("12 млн", formatViews(12_000_000))
    }

    @Test
    fun scheduleKeepsOnlyFutureEpisodes() {
        val now = 2_000L
        val past = scheduleItem(id = 1L, nextEpisodeAtSeconds = now - 60L)
        val missingDate = scheduleItem(id = 2L, nextEpisodeAtSeconds = 0L)
        val future = scheduleItem(id = 3L, nextEpisodeAtSeconds = now + 60L)

        assertEquals(listOf(future), upcomingScheduleItems(listOf(past, missingDate, future), nowSeconds = now))
    }

    @Test
    fun sourceFilesDoNotContainCommonCp1251Mojibake() {
        val roots = listOf(Path.of("src/main"), Path.of("app/src/main"))
            .filter { Files.exists(it) }
        assertTrue(roots.isNotEmpty(), "Source root was not found")

        val badFiles = roots.flatMap { root ->
            Files.walk(root).use { stream ->
                stream
                    .filter { Files.isRegularFile(it) }
                    .filter { it.toString().endsWith(".kt") || it.toString().endsWith(".xml") }
                    .filter { containsCp1251Mojibake(String(Files.readAllBytes(it), StandardCharsets.UTF_8)) }
                    .map { root.relativize(it).toString() }
                    .toList()
            }
        }

        assertFalse(
            badFiles.isNotEmpty(),
            "Found broken Russian text encoding in: ${badFiles.joinToString()}",
        )
    }

    private fun containsCp1251Mojibake(text: String): Boolean {
        val commonMojibakeFragments = listOf(
            "Рџ",
            "Рђ",
            "Р‘",
            "Р’",
            "Р“",
            "Р”",
            "Р•",
            "Р–",
            "Р—",
            "Рќ",
            "Рћ",
            "Р°",
            "Р±",
            "Рµ",
            "Р¶",
            "Р·",
            "Рґ",
            "Рє",
            "Р»",
            "Рј",
            "РЅ",
            "Рѕ",
            "СЃ",
            "С‚",
            "С‡",
            "С€",
            "С‰",
            "СЊ",
            "С‹",
            "СЋ",
            "СЏ",
            "СЂ",
            "вЂ",
            "Â",
        )
        return commonMojibakeFragments.any(text::contains)
    }

    private fun scheduleItem(id: Long, nextEpisodeAtSeconds: Long): ScheduleAnime {
        return ScheduleAnime(
            anime = Anime(
                id = id,
                title = "Anime $id",
                description = "",
                posterUrl = "",
                animeUrl = "",
                year = null,
                rating = null,
                views = 0L,
                status = "",
                type = "",
                genres = emptyList(),
                blockedIn = emptyList(),
            ),
            airedEpisodes = 0,
            totalEpisodes = 0,
            previousEpisodeAtSeconds = 0L,
            nextEpisodeAtSeconds = nextEpisodeAtSeconds,
        )
    }
}
