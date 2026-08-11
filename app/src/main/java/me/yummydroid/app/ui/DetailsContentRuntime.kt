package me.yummydroid.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun DetailsContentRuntime(
    model: DetailsContentModel,
    actions: DetailsContentActions,
) {
    val presentation = rememberDetailsContentPresentation(model)
    val focusGridState = rememberVisualFocusGridState(
        size = presentation.focusLayout.size,
        key = model.details.id,
        allowLoosePerpendicularMatch = true,
    )
    val layerFocusState = rememberDetailsLayerFocusState()
    DetailsContentFocusEffects(
        model = model,
        actions = actions,
        presentation = presentation,
        focusGridState = focusGridState,
        layerFocusState = layerFocusState,
    )
    CompositionLocalProvider(LocalBringIntoViewSpec provides DetailsBringIntoViewSpec) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .onFocusChanged { focusState ->
                    layerFocusState.hasFocus = focusState.isFocused || focusState.hasFocus
                }
                .focusGroup()
                .visualFocusGridNavigation(focusGridState)
                .verticalScroll(model.screenUiState.scrollState),
        ) {
            DetailsContentSections(model, actions, presentation, focusGridState)
        }
    }
}
