package me.yummydroid.app.ui

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import me.yummydroid.app.data.PlaybackProgress
import me.yummydroid.app.data.VideoVariant

class ResumeVideoIndexTest {
    @Test fun indexedHistoryResolutionPreservesOriginalMatcherAndStableOrder() {
        val random = Random(9741)
        val episodes = listOf("", "1", "01", "1,0", "-0.0", "0", "1e2", "100", "NaN", "Infinity", "Special", "2.5")
        repeat(100) {
            val videos = List(40) { index -> video(random.nextInt(0, 15).toLong(), episodes.random(random), index) }
            val history = List(25) { position -> progress(
                id = random.nextInt(0, 18).toLong(), episode = episodes.random(random),
                group = listOf("", "Player 0|Voice 0", "Player 1|Voice 1", "Other|Voice 0").random(random),
                time = (position / 2).toLong(),
            ) } + listOf(progress(999, "missing-newest", time = 1000), progress(998, "missing-second", time = 999))
            val expected = history.sortedByDescending { it.updatedAtMs }
                .firstNotNullOfOrNull { it.resolveResumeTarget(videos) }
            assertEquals(expected, history.resolveLatestResumeTarget(videos))
            val prepared = ResumeVideoIndex(videos)
            history.forEach { saved -> assertEquals(saved.resolveResumeTarget(videos), saved.resolveResumeTarget(prepared.candidates(saved))) }
        }
    }

    @Test fun absentHistoryBucketsNeverReenumerateTheLargeVideoList() {
        val originals = (1..17715).map { video(it.toLong(), it.toString(), it) }
        var reads = 0
        val observed = object : AbstractList<VideoVariant>() {
            override val size = originals.size
            override fun get(index: Int): VideoVariant { reads++; return originals[index] }
        }
        val prepared = ResumeVideoIndex(observed)
        val indexedReads = reads
        repeat(1181) { assertTrue(prepared.candidates(progress(-1, "unavailable-$it")).isEmpty()) }
        assertEquals(indexedReads, reads)
        val aliases = ResumeVideoIndex(listOf(video(1, "-0.0", 1), video(2, "0", 2)))
        assertEquals(2, aliases.candidates(progress(0, "0.0")).size)
    }

    private fun video(id: Long, episode: String, index: Int) = VideoVariant(
        id, 10, "Player ${index % 2}", dubbing = "Voice ${index % 2}", episode = episode,
        url = "", index = index, durationSeconds = 1440, views = 0,
    )
    private fun progress(id: Long, episode: String, group: String = "", time: Long = 1) = PlaybackProgress(
        animeId = 10, videoId = id, groupKey = group, episode = episode,
        positionMs = 5000, durationMs = 60000, updatedAtMs = time,
    )
}
