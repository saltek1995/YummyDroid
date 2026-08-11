package me.yummydroid.app.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal fun ScheduleReadyGridRoot(params: ScheduleReadyParams) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val data = rememberScheduleReadyData(params)
        val layout = rememberScheduleReadyLayout(params, data, maxWidth, maxHeight)
        ScheduleReadyCoordinator(params, data, layout)
    }
}
