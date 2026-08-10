package me.yummydroid.app

import androidx.lifecycle.ViewModel
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class YummyDroidRuntimeTest {
    @Test
    fun runtimeRemainsIndependentFromAndroidViewModelLifecycle() {
        assertFalse(ViewModel::class.java.isAssignableFrom(YummyDroidRuntime::class.java))
    }

    @Test
    fun runtimeKeepsActionsExposedByLifecycleFacade() {
        val runtimeMethods = YummyDroidRuntime::class.java.methods.mapTo(mutableSetOf()) { it.name }
        val facadeActions = setOf(
            "refresh",
            "updateSearchQuery",
            "openAnime",
            "playVideo",
            "navigateBack",
            "updateSettings",
            "logout",
        )

        assertTrue(runtimeMethods.containsAll(facadeActions))
    }
}
