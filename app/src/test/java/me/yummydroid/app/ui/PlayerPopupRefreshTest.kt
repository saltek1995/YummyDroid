package me.yummydroid.app.ui

import android.app.Activity
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import androidx.media3.ui.PlayerView
import me.yummydroid.app.InputAction
import me.yummydroid.app.InputActionEvent
import me.yummydroid.app.R
import me.yummydroid.app.cvhSourceVideo
import me.yummydroid.app.kodikSourceVideo
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
@LooperMode(LooperMode.Mode.PAUSED)
class PlayerPopupRefreshTest {
    @Test
    fun sourceMetadataRefreshKeepsOpenMenuAndHighlightedSourceAfterReordering() = withPlayer { player ->
        val anchor = player.findViewById<View>(R.id.yummy_player_source)
        val first = SourceOption("cvh", "CVH", cvhSourceVideo())
        val second = SourceOption("kodik", "Kodik", kodikSourceVideo())
        var selected: Long? = null
        var backs = 0
        player.findViewById<View>(R.id.yummy_player_back).setOnClickListener { backs++ }
        prepareSourcePopup(anchor, listOf(first, second), first.key) { selected = it.id }.show()
        assertTrue(player.handleRemoteInputAction(InputActionEvent(InputAction.Down)))

        prepareSourcePopup(anchor, listOf(second.copy(label = "Kodik • subtitles"), first), first.key) {
            selected = it.id
        }

        assertTrue(player.hasPlayerPopupMenu(), "metadata refresh must not dismiss the active source menu")
        assertTrue(player.handleRemoteInputAction(InputActionEvent(InputAction.Confirm)))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(second.video.id, selected)
        assertEquals(0, backs)
    }

    @Test
    fun voiceMetadataRefreshKeepsHighlightedVoice() = withPlayer { player ->
        val anchor = player.findViewById<View>(R.id.yummy_player_voice)
        val first = PlayerVoiceSelectionOption("a", "Voice A", "group-a", cvhSourceVideo())
        val second = PlayerVoiceSelectionOption("b", "Voice B", "group-b", kodikSourceVideo())
        var selected: String? = null
        prepareVoicePopup(anchor, listOf(first, second), first.key) { key, _ -> selected = key }.show()
        player.handleRemoteInputAction(InputActionEvent(InputAction.Down))

        prepareVoicePopup(anchor, listOf(first, second.copy(label = "Voice B • 12 episodes")), first.key) {
            key, _ -> selected = key
        }

        assertTrue(player.hasPlayerPopupMenu())
        player.handleRemoteInputAction(InputActionEvent(InputAction.Confirm))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(second.groupKey, selected)
    }

    @Test
    fun layoutRefreshKeepsOpenMenuAndSelection() = withPlayer { player ->
        val anchor = player.findViewById<View>(R.id.yummy_player_voice)
        var selected: Int? = null
        val popup = PopupMenu(player.context, anchor).apply {
            menu.add(1, 0, 0, "First")
            menu.add(1, 1, 1, "Second")
            setOnMenuItemClickListener { selected = it.itemId; true }
        }
        popup.show()
        player.handleRemoteInputAction(InputActionEvent(InputAction.Down))
        player.layout(0, 0, 1280, 720)
        popup.prepare()

        assertTrue(player.hasPlayerPopupMenu())
        player.handleRemoteInputAction(InputActionEvent(InputAction.Confirm))
        assertEquals(1, selected)
    }

    @Test
    fun removedHighlightedSourceFallsBackToCurrentSourceAndBackOnlyClosesMenu() = withPlayer { player ->
        val anchor = player.findViewById<View>(R.id.yummy_player_source)
        val current = SourceOption("cvh", "CVH", cvhSourceVideo())
        val removed = SourceOption("kodik", "Kodik", kodikSourceVideo())
        var selections = 0
        prepareSourcePopup(anchor, listOf(current, removed), current.key) { selections++ }.show()
        player.handleRemoteInputAction(InputActionEvent(InputAction.Down))
        val refreshed = prepareSourcePopup(anchor, listOf(current), current.key) { selections++ }

        assertTrue(player.hasPlayerPopupMenu())
        player.handleRemoteInputAction(InputActionEvent(InputAction.Confirm))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(0, selections, "the removed row must not select a different source")
        refreshed.show()
        assertTrue(player.handleRemoteInputAction(InputActionEvent(InputAction.Back)))
        assertEquals(false, player.hasPlayerPopupMenu())
    }

    @Test
    fun refreshingClosedMenuDoesNotOpenIt() = withPlayer { player ->
        val anchor = player.findViewById<View>(R.id.yummy_player_source)
        val option = SourceOption("cvh", "CVH", cvhSourceVideo())
        prepareSourcePopup(anchor, listOf(option), option.key) {}
        prepareSourcePopup(anchor, listOf(option.copy(label = "CVH • subtitles")), option.key) {}
        assertEquals(false, player.hasPlayerPopupMenu())
    }

    @Test
    fun clearingMenuWhenAlternativesDisappearDoesNotLetConfirmClickBack() = withPlayer { player ->
        val anchor = player.findViewById<View>(R.id.yummy_player_source)
        val first = SourceOption("cvh", "CVH", cvhSourceVideo())
        val second = SourceOption("kodik", "Kodik", kodikSourceVideo())
        var backs = 0
        player.findViewById<View>(R.id.yummy_player_back).setOnClickListener { backs++ }
        prepareSourcePopup(anchor, listOf(first, second), first.key) {}.show()

        // The production binder clears the cache and disables the anchor at <= 1 option.
        anchor.clearCachedPlayerPopupMenu()
        anchor.isEnabled = false
        assertEquals(false, player.hasPlayerPopupMenu())
        assertTrue(player.isFocused, "removed menus must leave focus on the player, not Back")
        assertTrue(player.handleRemoteInputAction(InputActionEvent(InputAction.Confirm)))
        assertEquals(0, backs)
    }

    private fun withPlayer(block: (PlayerView) -> Unit) {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        try {
            val activity = controller.get()
            val player = LayoutInflater.from(activity).inflate(R.layout.yummy_player_view, null) as PlayerView
            activity.setContentView(player)
            player.measure(View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY))
            player.layout(0, 0, 1920, 1080)
            player.showPlayerControls()
            block(player)
        } finally {
            controller.pause().stop().destroy()
        }
    }
}
