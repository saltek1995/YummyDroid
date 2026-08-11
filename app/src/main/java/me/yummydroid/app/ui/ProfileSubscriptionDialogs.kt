package me.yummydroid.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.Icons
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.util.Locale
import me.yummydroid.app.data.preferredProfileSubscription
import me.yummydroid.app.data.profileDisplayKey
import me.yummydroid.app.data.profileVoiceTitle
import me.yummydroid.app.data.VideoSubscription
import me.yummydroid.app.LoadState
import me.yummydroid.app.ui.components.dpadClickable
import me.yummydroid.app.ui.components.focusRing
import me.yummydroid.app.ui.theme.yummySurfaceColor
import me.yummydroid.app.ui.theme.yummySurfaceContentColor
import me.yummydroid.app.ui.theme.YummySurfaceRole

@Composable
internal fun ProfileSubscriptionsDialog(
    subscriptionsState: LoadState<List<VideoSubscription>>,
    onOpenAnime: (Long) -> Unit,
    onUnsubscribe: (VideoSubscription) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        modifier = Modifier.yummyDialogMotion(),
        onDismissRequest = onDismiss,
        title = { Text(uiText(UiStringKey.Subscriptions)) },
        text = {
            ProfileSubscriptionsContent(subscriptionsState, onOpenAnime, onUnsubscribe)
        },
        confirmButton = {
            DialogActionRow {
                DialogActionButton(text = uiText(UiStringKey.Close), primary = true, onClick = onDismiss)
            }
        },
    )
}

@Composable
private fun ProfileSubscriptionsContent(
    subscriptionsState: LoadState<List<VideoSubscription>>,
    onOpenAnime: (Long) -> Unit,
    onUnsubscribe: (VideoSubscription) -> Unit,
) {
    when (subscriptionsState) {
        LoadState.Loading -> ProfileSubscriptionsStatusPane(Alignment.Center) {
            LoadingPane(
                Modifier
                    .fillMaxWidth()
                    .height(140.dp),
            )
        }
        is LoadState.Error -> ProfileSubscriptionsStatusPane(Alignment.CenterStart) {
            InlineErrorMessage(message = subscriptionsState.message)
        }
        is LoadState.Ready -> ProfileSubscriptionsReadyContent(
            subscriptions = subscriptionsState.data.profileSubscriptionsForManagement(),
            onOpenAnime = onOpenAnime,
            onUnsubscribe = onUnsubscribe,
        )
    }
}

@Composable
private fun ProfileSubscriptionsStatusPane(
    contentAlignment: Alignment,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 160.dp, max = 420.dp)
            .verticalScroll(rememberScrollState()),
        contentAlignment = contentAlignment,
    ) {
        content()
    }
}

@Composable
private fun ProfileSubscriptionsReadyContent(
    subscriptions: List<VideoSubscription>,
    onOpenAnime: (Long) -> Unit,
    onUnsubscribe: (VideoSubscription) -> Unit,
) {
    if (subscriptions.isEmpty()) {
        ProfileSubscriptionsStatusPane(Alignment.CenterStart) {
            Text(
                text = uiText(UiStringKey.NoSubscriptions),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 420.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(
            subscriptions,
            key = { index, subscription ->
                "profile-subscription:${subscription.profileDisplayKey}:$index"
            },
        ) { _, subscription ->
            SubscriptionManagementRow(
                subscription = subscription,
                onOpenAnime = { onOpenAnime(subscription.animeId) },
                onUnsubscribe = { onUnsubscribe(subscription) },
            )
        }
    }
}

internal fun List<VideoSubscription>.profileSubscriptionsForManagement(): List<VideoSubscription> =
    groupBy { it.profileDisplayKey }
        .values
        .map { group -> group.preferredProfileSubscription() }
        .filter { it.profileVoiceTitle.isNotBlank() }
        .sortedWith(
            compareBy<VideoSubscription> { it.title.lowercase(Locale.ROOT) }
                .thenBy { it.profileVoiceTitle.lowercase(Locale.ROOT) },
        )

@Composable
internal fun SubscriptionManagementRow(
    subscription: VideoSubscription,
    onOpenAnime: () -> Unit,
    onUnsubscribe: () -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    Surface(
        modifier = Modifier.dpadClickable(shape, onOpenAnime),
        color = yummySurfaceColor(YummySurfaceRole.Row),
        contentColor = yummySurfaceContentColor(YummySurfaceRole.Row),
        shape = shape,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PosterImage(
                url = subscription.posterUrl,
                contentDescription = subscription.title,
                modifier = Modifier
                    .width(48.dp)
                    .aspectRatio(0.72f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surface),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = subscription.title.ifBlank { uiText(UiStringKey.Anime) },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subscription.profileVoiceTitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(
                onClick = onUnsubscribe,
                modifier = Modifier.focusRing(RoundedCornerShape(8.dp)),
            ) {
                Icon(Icons.Default.Close, contentDescription = uiText(UiStringKey.Disable))
            }
        }
    }
}
