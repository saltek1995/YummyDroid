package me.yummydroid.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import me.yummydroid.app.BrowseSection

@Composable
internal fun BrowsePagerSectionFocusEffect(
    effectiveSection: BrowseSection,
    usePager: Boolean,
    dpadFocusEnabled: Boolean,
    runtime: BrowsePagerRuntime,
    onRequestSectionTabsFocus: (BrowseSection, Boolean) -> Boolean,
) {
    LaunchedEffect(effectiveSection) {
        if (runtime.pageFocusRequestSection == effectiveSection) return@LaunchedEffect
        runtime.pageFocusRequestSection = effectiveSection
        if (runtime.keepTabsFocusedForSectionChange) {
            retainSectionTabFocus(
                effectiveSection = effectiveSection,
                dpadFocusEnabled = dpadFocusEnabled,
                runtime = runtime,
                onRequestSectionTabsFocus = onRequestSectionTabsFocus,
            )
        } else {
            runtime.pendingTabsFocusSection = null
            if (canRequestPageFocus(usePager, dpadFocusEnabled, runtime)) {
                runtime.pageFocusRequestNonce += 1L
            }
        }
    }
}

private suspend fun retainSectionTabFocus(
    effectiveSection: BrowseSection,
    dpadFocusEnabled: Boolean,
    runtime: BrowsePagerRuntime,
    onRequestSectionTabsFocus: (BrowseSection, Boolean) -> Boolean,
) {
    val targetFocusSection = runtime.pendingTabsFocusSection ?: effectiveSection
    runtime.pendingTabsFocusSection = null
    runtime.keepTabsFocusedForSectionChange = false
    if (dpadFocusEnabled) {
        withFrameNanos { }
        onRequestSectionTabsFocus(targetFocusSection, false)
    }
}

private fun canRequestPageFocus(
    usePager: Boolean,
    dpadFocusEnabled: Boolean,
    runtime: BrowsePagerRuntime,
): Boolean {
    return dpadFocusEnabled &&
        (!usePager ||
            (runtime.programmaticScrollTarget == null && runtime.transitionFocusSourcePage == null))
}
