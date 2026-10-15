package me.yummydroid.app

import android.app.Application
import android.content.res.Configuration
import android.util.TypedValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import me.yummydroid.app.data.ContentLanguage
import me.yummydroid.app.data.InterfaceScale
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "w360dp-h640dp-port-mdpi")
class AppUiConfigurationRotationTest {
    @Test
    fun scaledContextFollowsRotationWithoutChangingControlOrTextScale() {
        val context = RuntimeEnvironment.getApplication().withAppUiConfiguration(
            InterfaceScale(130), ContentLanguage.English,
        )
        val metrics = context.resources.displayMetrics
        val density = metrics.densityDpi
        val buttonSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 52f, metrics)
        val textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 15f, metrics)
        val portraitWidth = context.resources.configuration.screenWidthDp
        RuntimeEnvironment.setQualifiers("w640dp-h360dp-land-mdpi")
        assertEquals(Configuration.ORIENTATION_LANDSCAPE, context.resources.configuration.orientation)
        assertNotEquals(portraitWidth, context.resources.configuration.screenWidthDp)
        assertEquals(density, context.resources.displayMetrics.densityDpi)
        assertEquals(buttonSize, TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 52f, context.resources.displayMetrics))
        assertEquals(textSize, TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 15f, context.resources.displayMetrics))
        assertEquals(1f, context.resources.configuration.fontScale)
        assertEquals("en", context.resources.configuration.locales[0].language)
    }

    @Test
    fun initiallyMatchingConfigurationStillPinsTheUserScalePolicy() {
        val context = RuntimeEnvironment.getApplication().withAppUiConfiguration(
            InterfaceScale.Default, ContentLanguage.English,
        )
        val density = context.resources.displayMetrics.densityDpi
        RuntimeEnvironment.setQualifiers("w640dp-h360dp-land-hdpi")
        assertEquals(Configuration.ORIENTATION_LANDSCAPE, context.resources.configuration.orientation)
        assertEquals(density, context.resources.displayMetrics.densityDpi)
        assertEquals(1f, context.resources.configuration.fontScale)
    }
}
