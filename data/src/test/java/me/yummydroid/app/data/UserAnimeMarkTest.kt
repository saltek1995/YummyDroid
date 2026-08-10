package me.yummydroid.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UserAnimeMarkTest {
    @Test
    fun idsAndDisplayOrderMatchRemoteContract() {
        assertEquals(UserAnimeListMark.Watching, UserAnimeListMark.fromId(0))
        assertEquals(UserAnimeListMark.Postponed, UserAnimeListMark.fromId(5))
        assertNull(UserAnimeListMark.fromId(4))
        assertNull(UserAnimeListMark.fromId(null))
        assertEquals(
            listOf(
                UserAnimeListMark.Watching,
                UserAnimeListMark.Planned,
                UserAnimeListMark.Watched,
                UserAnimeListMark.Postponed,
                UserAnimeListMark.Dropped,
            ),
            UserAnimeListMark.displayOrder,
        )
    }

    @Test
    fun markDefaultsContainNoSelection() {
        assertEquals(UserAnimeMark(list = null, isFavorite = false), UserAnimeMark())
    }
}
