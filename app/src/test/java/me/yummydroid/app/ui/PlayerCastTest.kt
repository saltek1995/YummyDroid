package me.yummydroid.app.ui

import android.content.res.Configuration
import androidx.media3.common.MediaItem
import com.google.android.gms.common.ConnectionResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import me.yummydroid.app.R

class PlayerCastTest {
    @Test
    fun castIsAvailableOnlyOnSenderDevicesWithPlayServices() {
        assertTrue(
            isCastSenderSupported(
                uiModeType = Configuration.UI_MODE_TYPE_NORMAL,
                playServicesStatus = ConnectionResult.SUCCESS,
            ),
        )
        assertFalse(
            isCastSenderSupported(
                uiModeType = Configuration.UI_MODE_TYPE_TELEVISION,
                playServicesStatus = ConnectionResult.SUCCESS,
            ),
        )
        assertFalse(
            isCastSenderSupported(
                uiModeType = Configuration.UI_MODE_TYPE_NORMAL,
                playServicesStatus = ConnectionResult.SERVICE_MISSING,
            ),
        )
    }

    @Test
    fun castControlParticipatesInPlayerFocusNavigation() {
        assertTrue(R.id.yummy_player_cast in playerControlIds)
    }

    @Test
    fun castMediaIdentityPreventsDuplicateLoadsButAllowsEpisodeChanges() {
        val currentItem = MediaItem.Builder().setMediaId("video:10").build()

        val sameEpisode = MediaItem.Builder().setMediaId("video:10").build()
        val nextEpisode = MediaItem.Builder().setMediaId("video:11").build()

        assertTrue(castMediaItemsMatch(currentItem, sameEpisode))
        assertFalse(castMediaItemsMatch(currentItem, nextEpisode))
    }

    @Test
    fun localMediaRangeSupportsOpenAndBoundedRequests() {
        assertEquals(LocalCastByteRange(100L, 999L), parseLocalCastByteRange("bytes=100-", 1_000L))
        assertEquals(LocalCastByteRange(100L, 299L), parseLocalCastByteRange("bytes=100-299", 1_000L))
        assertEquals(LocalCastByteRange(900L, 999L), parseLocalCastByteRange("bytes=-100", 1_000L))
    }

    @Test
    fun localMediaRangeRejectsInvalidOrMultipartRequests() {
        assertNull(parseLocalCastByteRange("bytes=100-99", 1_000L))
        assertNull(parseLocalCastByteRange("bytes=0-10,20-30", 1_000L))
        assertNull(parseLocalCastByteRange("items=0-10", 1_000L))
        assertNull(parseLocalCastByteRange("bytes=1000-", 1_000L))
    }
}
