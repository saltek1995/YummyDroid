package me.yummydroid.app.ui

import android.content.res.Configuration
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
    fun receiverEpisodeCommandsUsePlayerNavigationCallbacks() {
        val calls = mutableListOf<String>()
        val binding = PlayerCastControllerBinding(
            title = "Title",
            subtitle = "Subtitle",
            hasPrevious = false,
            hasNext = true,
            onPrevious = { calls += "previous" },
            onNext = { calls += "next" },
            selectionState = castSelectionState(),
            onSelectVoice = {},
            onSelectSource = {},
            onSelectQuality = {},
        )

        assertFalse(dispatchCastEpisodeCommand(CastEpisodeCommand.Previous, binding))
        assertTrue(dispatchCastEpisodeCommand(CastEpisodeCommand.Next, binding))
        assertEquals(listOf("next"), calls)
    }

    @Test
    fun receiverEpisodeCommandParserRejectsUnrelatedMessages() {
        assertEquals(
            CastEpisodeCommand.Previous,
            parseCastEpisodeCommand("""{"type":"episode-navigation","direction":"previous"}"""),
        )
        assertEquals(
            CastEpisodeCommand.Next,
            parseCastEpisodeCommand("""{"type":"episode-navigation","direction":"next"}"""),
        )
        assertNull(parseCastEpisodeCommand("""{"type":"other","direction":"next"}"""))
        assertNull(parseCastEpisodeCommand("""{"type":"episode-navigation","direction":"later"}"""))
        assertNull(parseCastEpisodeCommand("not-json"))
    }

    @Test
    fun receiverSelectionCommandsUseOnlyAvailablePlayerCallbacks() {
        val calls = mutableListOf<String>()
        val binding = PlayerCastControllerBinding(
            title = "Title",
            subtitle = "Subtitle",
            hasPrevious = false,
            hasNext = false,
            onPrevious = {},
            onNext = {},
            selectionState = castSelectionState(),
            onSelectVoice = { calls += "voice:$it" },
            onSelectSource = { calls += "source:$it" },
            onSelectQuality = { calls += "quality:$it" },
        )

        assertTrue(dispatchCastSelectionCommand(CastSelectionCommand(CastSelectionType.Voice, "voice-2"), binding))
        assertTrue(dispatchCastSelectionCommand(CastSelectionCommand(CastSelectionType.Source, "source-2"), binding))
        assertTrue(dispatchCastSelectionCommand(CastSelectionCommand(CastSelectionType.Quality, "height:720"), binding))
        assertFalse(dispatchCastSelectionCommand(CastSelectionCommand(CastSelectionType.Quality, "height:360"), binding))
        assertTrue(dispatchCastSelectionCommand(CastSelectionCommand(CastSelectionType.Voice, "voice-1"), binding))
        assertEquals(listOf("voice:voice-2", "source:source-2", "quality:height:720"), calls)
    }

    @Test
    fun receiverSelectionCommandParserRejectsIncompleteMessages() {
        assertEquals(
            CastSelectionCommand(CastSelectionType.Voice, "voice-2"),
            parseCastSelectionCommand(
                """{"type":"playback-selection","selectionType":"voice","key":"voice-2"}""",
            ),
        )
        assertEquals(
            CastSelectionCommand(CastSelectionType.Quality, "height:1080"),
            parseCastSelectionCommand(
                """{"type":"playback-selection","selectionType":"quality","key":"height:1080"}""",
            ),
        )
        assertNull(parseCastSelectionCommand("""{"type":"playback-selection","selectionType":"speed","key":"1"}"""))
        assertNull(parseCastSelectionCommand("""{"type":"playback-selection","selectionType":"source"}"""))
        assertNull(parseCastSelectionCommand("""{"type":"other","selectionType":"source","key":"source-2"}"""))
    }

    @Test
    fun receiverSelectionStateIncludesMessageTypeAndCurrentValues() {
        val encoded = encodeCastSelectionState(castSelectionState())

        assertTrue("\"type\":\"selection-state\"" in encoded)
        assertTrue("\"selectedKey\":\"voice-1\"" in encoded)
        assertTrue("\"key\":\"height:720\"" in encoded)
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

    private fun castSelectionState(): CastSelectionState {
        return CastSelectionState(
            voice = CastSelectionGroup(
                title = "Voice",
                options = listOf(
                    CastSelectionOption("voice-1", "Voice 1"),
                    CastSelectionOption("voice-2", "Voice 2"),
                ),
                selectedKey = "voice-1",
            ),
            source = CastSelectionGroup(
                title = "Source",
                options = listOf(
                    CastSelectionOption("source-1", "Source 1"),
                    CastSelectionOption("source-2", "Source 2"),
                ),
                selectedKey = "source-1",
            ),
            quality = CastSelectionGroup(
                title = "Quality",
                options = listOf(
                    CastSelectionOption("height:480", "480p"),
                    CastSelectionOption("height:720", "720p"),
                ),
                selectedKey = "height:480",
            ),
        )
    }
}
