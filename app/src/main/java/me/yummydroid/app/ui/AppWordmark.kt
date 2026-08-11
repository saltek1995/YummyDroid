package me.yummydroid.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import me.yummydroid.app.R

@Composable
internal fun AppWordmark(
    modifier: Modifier = Modifier,
    height: Dp,
) {
    Box(
        modifier = modifier.height(height),
        contentAlignment = Alignment.CenterStart,
    ) {
        Image(
            painter = painterResource(R.drawable.app_wordmark),
            contentDescription = "YummyDroid",
            contentScale = ContentScale.Fit,
            alignment = Alignment.CenterStart,
            modifier = Modifier.fillMaxHeight().width(height * 5.45f),
        )
    }
}
