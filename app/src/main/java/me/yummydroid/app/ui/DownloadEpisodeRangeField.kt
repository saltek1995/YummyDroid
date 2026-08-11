package me.yummydroid.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.yummydroid.app.DownloadEpisodeSelectionError
import me.yummydroid.app.ui.theme.YummyColors
import me.yummydroid.app.ui.theme.YummyRadii

@Composable
internal fun DownloadEpisodeSelectionError.localizedMessage(): String = when (this) {
    is DownloadEpisodeSelectionError.InvalidEpisodeNumber ->
        uiText(UiStringKey.EpisodeNumberInvalid, token)
    is DownloadEpisodeSelectionError.InvalidEpisodeRange ->
        uiText(UiStringKey.EpisodeRangeInvalid, token)
    is DownloadEpisodeSelectionError.MissingEpisodes ->
        uiText(UiStringKey.VoiceHasNoEpisodes, ranges)
}

@Composable
internal fun DownloadEpisodeRangeField(
    value: String,
    error: String?,
    onValueChange: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(
            text = uiText(UiStringKey.Episodes),
            style = MaterialTheme.typography.labelSmall,
            color = if (error == null) YummyColors.focus else MaterialTheme.colorScheme.error,
            fontWeight = FontWeight.Black,
        )
        Surface(
            color = if (error == null) {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
            } else {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.32f)
            },
            contentColor = MaterialTheme.colorScheme.onSurface,
            shape = YummyRadii.smallShape,
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                ),
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 42.dp)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        if (value.isBlank()) {
                            Text(
                                text = uiText(UiStringKey.AllEf8ff2),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        innerTextField()
                    }
                },
            )
        }
        if (error != null) {
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}
