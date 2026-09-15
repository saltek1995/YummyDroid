package me.yummydroid.app.ui

import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import me.yummydroid.app.R
import me.yummydroid.app.data.VideoSkipKind
import me.yummydroid.app.data.VideoSkipSegment
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@androidx.annotation.OptIn(UnstableApi::class)
class PlayerSkipVisibilityTest {
    @Test
    fun watchingKeepsSkipControlsAvailableWhenFullControlsAreReopened() = withPrompt { view, prompt ->
        view.hidePlayerControls()
        assertFalse(view.hasVisiblePlayerControls())
        assertFalse(view.isSkipOnlyControllerMode())
        assertFalse(view.dismissedSkipKeys().contains(prompt.key))
        assertFalse(view.tagValue<ActiveSkipPrompt>(R.id.yummy_player_active_skip_segment)!!.showWhenControlsHidden)

        view.showPlayerControls()
        assertTrue(view.hasVisiblePlayerControls())
        assertEquals(View.VISIBLE, view.findViewById<View>(R.id.yummy_skip_controls).visibility)
        assertNotNull(view.tagValue<ActiveSkipPrompt>(R.id.yummy_player_active_skip_segment))

        view.autoHidePlayerControls()
        assertFalse(view.hasVisiblePlayerControls())
        assertNotNull(view.tagValue<ActiveSkipPrompt>(R.id.yummy_player_active_skip_segment))
    }

    @Test
    fun automaticHidingPreservesFloatingPromptUntilUserChoosesToWatch() = withPrompt { view, _ ->
        view.setSkipOnlyControllerMode(false)
        view.autoHidePlayerControls()
        assertTrue(view.isSkipOnlyControllerMode())
        assertTrue(view.hasVisiblePlayerControls())
        assertEquals(View.VISIBLE, view.findViewById<View>(R.id.yummy_skip_controls).visibility)
    }

    private fun withPrompt(test: (PlayerView, ActiveSkipPrompt) -> Unit) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val context = ContextThemeWrapper(instrumentation.targetContext, R.style.Theme_YummyDroid_Player)
            val view = LayoutInflater.from(context)
                .inflate(R.layout.yummy_player_view, FrameLayout(context), false) as PlayerView
            val segment = VideoSkipSegment(VideoSkipKind.Opening, 0L, 90_000L)
            val prompt = ActiveSkipPrompt(segment.key, segment)
            view.setControllerAnimationEnabled(false)
            view.setTag(R.id.yummy_player_active_skip_key, prompt.key)
            view.setTag(R.id.yummy_player_active_skip_segment, prompt)
            view.setSkipControlsActive(true)
            view.setSkipOnlyControllerMode(true)
            view.showPlayerControls()
            try {
                test(view, prompt)
            } finally {
                view.unbindSkipControls()
                view.hidePlayerControls()
            }
        }
    }
}
