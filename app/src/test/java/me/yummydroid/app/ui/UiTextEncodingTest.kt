package me.yummydroid.app.ui

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import me.yummydroid.app.data.Anime
import me.yummydroid.app.data.ScheduleAnime

class UiTextEncodingTest {
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
        val roots = listOf(
            Path.of("src/main"),
            Path.of("app/src/main"),
            Path.of("../data/src/main"),
            Path.of("data/src/main"),
        )
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
        val suspiciousPairRegex = Regex("""[\u0413\u0420\u0421][\u00a0-\u00ff\u0400-\u040f\u2010-\u202f\u20ac]""")
        return suspiciousPairRegex.containsMatchIn(text)
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
