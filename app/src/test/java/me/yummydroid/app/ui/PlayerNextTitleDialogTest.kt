package me.yummydroid.app.ui

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import me.yummydroid.app.NextTitleSuggestion
import me.yummydroid.app.data.ContentLanguage
import me.yummydroid.app.ui.theme.YummyDroidTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class PlayerNextTitleDialogTest {
    @get:Rule val compose = createComposeRule()
    @Test fun nextTitleRequiresAnExplicitChoiceAndCanBeDeclined() {
        var starts = 0
        var dismissed = 0
        compose.setContent {
            CompositionLocalProvider(
                LocalAppNavigationController provides remember { AppNavigationController() },
                LocalUiLanguage provides ContentLanguage.English,
            ) {
                YummyDroidTheme { PlayerNextTitleDialog(NextTitleSuggestion(1, 10, 20, "Next title"), { starts++ }, { dismissed++ }) }
            }
        }
        compose.onNodeWithText("Next title").assertExists()
        compose.runOnIdle { assertEquals(0, starts) }
        compose.onNodeWithText("Not now").performClick()
        compose.runOnIdle { assertEquals(1, dismissed); assertEquals(0, starts) }
        compose.onNodeWithText("Watch").performClick()
        compose.runOnIdle { assertEquals(1, starts) }
    }
}
