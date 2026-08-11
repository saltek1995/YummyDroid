package me.yummydroid.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import me.yummydroid.app.ui.theme.YummySpacing

@Composable
internal fun FiltersDialogActions(
    applyFocusRequester: FocusRequester,
    onReset: () -> Unit,
    onCancel: () -> Unit,
    onApply: () -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        if (maxWidth < 300.dp) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(YummySpacing.sm),
            ) {
                SecondaryFiltersDialogActions(onReset, onCancel)
                DialogActionButton(
                    text = uiText(UiStringKey.Apply),
                    primary = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(applyFocusRequester),
                    onClick = onApply,
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(YummySpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SecondaryFiltersDialogAction(uiText(UiStringKey.Reset), onReset)
                SecondaryFiltersDialogAction(uiText(UiStringKey.Cancel), onCancel)
                DialogActionButton(
                    text = uiText(UiStringKey.Apply),
                    primary = true,
                    compact = true,
                    modifier = Modifier
                        .weight(1.25f)
                        .focusRequester(applyFocusRequester),
                    onClick = onApply,
                )
            }
        }
    }
}

@Composable
private fun SecondaryFiltersDialogActions(
    onReset: () -> Unit,
    onCancel: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(YummySpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SecondaryFiltersDialogAction(uiText(UiStringKey.Reset), onReset)
        SecondaryFiltersDialogAction(uiText(UiStringKey.Cancel), onCancel)
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.SecondaryFiltersDialogAction(
    text: String,
    onClick: () -> Unit,
) {
    DialogActionButton(
        text = text,
        modifier = Modifier.weight(1f),
        compact = true,
        onClick = onClick,
    )
}
