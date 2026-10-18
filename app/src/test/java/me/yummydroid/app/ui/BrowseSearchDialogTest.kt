package me.yummydroid.app.ui

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import kotlin.test.assertEquals
import me.yummydroid.app.data.ContentLanguage
import me.yummydroid.app.ui.theme.YummyDroidTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class BrowseSearchDialogTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun imeAndPhysicalEnterSubmitCurrentDraftAndClearResetsAppliedSearch() {
        val applied = mutableStateOf("")
        val submissions = mutableListOf<String>()
        compose.setContent {
            CompositionLocalProvider(
                LocalAppNavigationController provides remember { AppNavigationController() },
                LocalUiLanguage provides ContentLanguage.English,
            ) {
                YummyDroidTheme {
                    SearchDialog(
                        query = applied.value,
                        onQueryChange = { applied.value = it },
                        onSubmitQuery = { submissions += it; applied.value = it },
                        onDismiss = { error("Submitting or clearing must not dismiss the dialog") },
                    )
                }
            }
        }
        val input = compose.onNode(hasSetTextAction())
        input.performTextInput("Naruto")
        input.performImeAction()
        compose.runOnIdle { assertEquals(listOf("Naruto"), submissions) }
        compose.onNodeWithContentDescription("Clear").performClick()
        input.assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        input.assertIsFocused()
        compose.runOnIdle { assertEquals("", applied.value) }
        input.performTextInput("Bleach")
        input.performKeyInput { keyDown(Key.Enter); keyUp(Key.Enter) }
        compose.runOnIdle {
            assertEquals(listOf("Naruto", "Bleach"), submissions)
            assertEquals("Bleach", applied.value)
        }
    }
}
