package me.yummydroid.app.ui

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.yummydroid.app.PagingUiState
import me.yummydroid.app.data.AnimeComment
import me.yummydroid.app.formatCommentTimestamp
import me.yummydroid.app.ui.components.focusRing

@Composable
internal fun DetailsCommentCard(
    comment: AnimeComment,
    focusGridState: VisualFocusGridState?,
    focusIndex: Int,
    focusBlockKey: Any?,
    blockEntryIndex: Int,
) {
    val shape = RoundedCornerShape(8.dp)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .visualFocusGridItemIfPresent(
                state = focusGridState,
                index = focusIndex,
                blockKey = focusBlockKey,
                blockEntryIndex = blockEntryIndex,
            )
            .focusRing(shape)
            .focusable(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.70f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = shape,
    ) {
        DetailsCommentCardContent(comment)
    }
}

@Composable
private fun DetailsCommentCardContent(comment: AnimeComment) {
    Column(
        modifier = Modifier.padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val commentDate = remember(comment.createdAtSeconds) {
            formatCommentTimestamp(comment.createdAtSeconds)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                text = comment.userName.ifBlank { uiText(UiStringKey.User) },
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (commentDate.isNotBlank()) {
                Text(
                    text = commentDate,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                )
            }
        }
        Text(
            text = comment.text,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
internal fun DetailsCommentsPagingFooter(
    commentsPaging: PagingUiState,
    onRetry: () -> Unit,
) {
    when {
        commentsPaging.isLoadingMore -> DetailsCommentsLoadingFooter()
        commentsPaging.error != null -> DetailsCommentsErrorFooter(commentsPaging.error, onRetry)
    }
}

@Composable
private fun DetailsCommentsLoadingFooter() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun DetailsCommentsErrorFooter(error: String, onRetry: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = error,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        DialogActionButton(
            text = uiText(UiStringKey.Retry),
            primary = true,
            onClick = onRetry,
            modifier = Modifier,
        )
    }
}
