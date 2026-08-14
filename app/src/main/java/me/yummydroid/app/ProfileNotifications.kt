package me.yummydroid.app

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import me.yummydroid.app.data.AuthStorage
import me.yummydroid.app.data.SiteNotification
import me.yummydroid.app.data.decodeAppJsonOrNull
import me.yummydroid.app.data.encodeAppJson

// NotificationShadeItems
internal data class NotificationShadeItem(
    val id: Long,
    val title: String,
    val text: String,
    val dateSeconds: Long,
)

internal fun List<SiteNotification>.toNotificationShadeItems(): List<NotificationShadeItem> {
    return sortedByDescending { it.dateSeconds }
        .map { notification ->
            NotificationShadeItem(
                id = notification.id,
                title = notification.title.trim(),
                text = notification.text.trim(),
                dateSeconds = notification.dateSeconds,
            )
        }
}

internal fun List<SiteNotification>.unreadNotificationShadeItemsJson(maxItems: Int): String? {
    val storedItems = filterNot(SiteNotification::viewed)
        .toNotificationShadeItems()
        .take(maxItems)
        .map(StoredNotificationShadeItem::fromShadeItem)
    return storedItems.takeIf(List<StoredNotificationShadeItem>::isNotEmpty)?.encodeAppJson()
}

internal fun decodeNotificationShadeItems(json: String?): List<NotificationShadeItem> {
    return json?.decodeAppJsonOrNull<List<StoredNotificationShadeItem>>()
        .orEmpty()
        .map(StoredNotificationShadeItem::toShadeItem)
}

@Serializable
private data class StoredNotificationShadeItem(
    val id: Long,
    val title: String,
    val text: String,
    val dateSeconds: Long,
) {
    fun toShadeItem() = NotificationShadeItem(id, title, text, dateSeconds)

    companion object {
        fun fromShadeItem(item: NotificationShadeItem) = StoredNotificationShadeItem(
            id = item.id,
            title = item.title,
            text = item.text,
            dateSeconds = item.dateSeconds,
        )
    }
}

// NotificationUpdateGate
internal class NotificationUpdateGate(
    private val minIntervalMs: Long,
    private val clockMs: () -> Long = { System.currentTimeMillis() },
) {
    private val lock = Any()
    private var lastPostedAtMs: Long? = null

    fun shouldPost(force: Boolean = false): Boolean = synchronized(lock) {
        val now = clockMs()
        val last = lastPostedAtMs
        if (!isPostDue(force, now, last)) return@synchronized false
        lastPostedAtMs = now
        true
    }

    fun reset() = synchronized(lock) {
        lastPostedAtMs = null
    }

    private fun isPostDue(force: Boolean, now: Long, last: Long?): Boolean {
        if (force || last == null) return true
        if (now < last) return true
        return now - last >= minIntervalMs
    }
}

// ProfileNotificationCoordinator
internal const val PROFILE_NOTIFICATION_FETCH_LIMIT = 80

internal class ProfileNotificationCoordinator(
    private val runtime: ProfileNotificationRuntime,
    private val fetchNotifications: suspend (Int) -> List<SiteNotification>,
    private val markNotificationRead: suspend (Long) -> Unit,
    private val markAllNotificationsRead: suspend () -> Unit,
    private val deleteNotification: suspend (Long) -> Unit,
) {
    private val operationMutex = Mutex()

    suspend fun load(profileId: Long): List<SiteNotification> = operationMutex.withLock {
        fetchNotifications(PROFILE_NOTIFICATION_FETCH_LIMIT)
            .sortedByDescending(SiteNotification::dateSeconds)
            .also { notifications ->
                runtime.synchronize(profileId, notifications)
            }
    }

    suspend fun markRead(
        profileId: Long,
        notificationId: Long,
        notifications: List<SiteNotification>,
    ) = operationMutex.withLock {
        synchronizeBeforeMutation(profileId, notifications, listOf(notificationId)) {
            markNotificationRead(notificationId)
        }
    }

    suspend fun markAllRead(
        profileId: Long,
        notifications: List<SiteNotification>,
    ) = operationMutex.withLock {
        synchronizeBeforeMutation(profileId, notifications, notifications.map(SiteNotification::id)) {
            markAllNotificationsRead()
        }
    }

    suspend fun delete(
        profileId: Long,
        notificationId: Long,
        notifications: List<SiteNotification>,
    ) = operationMutex.withLock {
        synchronizeBeforeMutation(profileId, notifications, listOf(notificationId)) {
            deleteNotification(notificationId)
        }
    }

    private suspend fun synchronizeBeforeMutation(
        profileId: Long,
        notifications: List<SiteNotification>,
        cancelledNotificationIds: List<Long>,
        mutation: suspend () -> Unit,
    ) {
        runtime.synchronize(
            profileId = profileId,
            notifications = notifications,
            cancelledNotificationIds = cancelledNotificationIds,
        )
        mutation()
    }
}

// ProfileNotificationRuntime
internal interface ProfileNotificationRuntime {
    suspend fun synchronize(
        profileId: Long,
        notifications: List<SiteNotification>,
        cancelledNotificationIds: List<Long> = emptyList(),
    )
}

internal class AndroidProfileNotificationRuntime(
    context: Context,
    private val authStorage: AuthStorage = AuthStorage(context.applicationContext),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ProfileNotificationRuntime {
    private val appContext = context.applicationContext

    override suspend fun synchronize(
        profileId: Long,
        notifications: List<SiteNotification>,
        cancelledNotificationIds: List<Long>,
    ) = updateMutex.withLock {
        withContext(ioDispatcher) {
            cancelledNotificationIds.distinct().forEach { notificationId ->
                SubscriptionNotificationBadge.cancelNotification(appContext, notificationId)
            }

            val profile = authStorage.readProfile()
                ?.takeIf { it.id == profileId }
                ?: return@withContext
            val unreadNotifications = notifications.filterNot(SiteNotification::viewed)
            authStorage.saveProfile(profile.copy(unreadNotifications = unreadNotifications.size))
            SubscriptionNotificationBadge.update(appContext, unreadNotifications)
        }
    }

    private companion object {
        val updateMutex = Mutex()
    }
}

internal object SubscriptionNotificationBadge {
    private const val BADGE_NOTIFICATION_ID = 28042
    private const val MAX_INBOX_LINES = 5

    fun update(context: Context, unreadCount: Int) {
        val appContext = context.applicationContext
        if (unreadCount <= 0) {
            SubscriptionNotificationStore(appContext).clearUnreadShadeItems()
            appContext.getSystemService(NotificationManager::class.java).cancel(BADGE_NOTIFICATION_ID)
            return
        }
        update(
            context = appContext,
            unreadCount = unreadCount,
            shadeItems = SubscriptionNotificationStore(appContext).unreadShadeItems(),
        )
    }

    fun update(context: Context, unreadNotifications: List<SiteNotification>) {
        val appContext = context.applicationContext
        val unread = unreadNotifications.filterNot(SiteNotification::viewed)
        val store = SubscriptionNotificationStore(appContext)
        if (unread.isEmpty()) {
            store.clearUnreadShadeItems()
            appContext.getSystemService(NotificationManager::class.java).cancel(BADGE_NOTIFICATION_ID)
            return
        }
        store.saveUnreadShadeItems(unread)
        update(
            context = appContext,
            unreadCount = unread.size,
            shadeItems = unread.toNotificationShadeItems(),
        )
    }

    private fun update(
        context: Context,
        unreadCount: Int,
        shadeItems: List<NotificationShadeItem>,
    ) {
        val appContext = context.applicationContext
        val manager = appContext.getSystemService(NotificationManager::class.java)
        val count = unreadCount.coerceAtLeast(0)
        if (count == 0 || !appContext.canPostNotifications()) {
            manager.cancel(BADGE_NOTIFICATION_ID)
            return
        }

        SubscriptionNotificationChannels.create(appContext)
        val countText = appContext.resources.getQuantityString(
            R.plurals.notification_unread_count,
            count,
            count,
        )
        val fallbackTitle = appContext.getString(R.string.notification_unread_title)
        val content = notificationShadeContent(
            unreadCount = count,
            shadeItems = shadeItems,
            fallbackTitle = fallbackTitle,
            countText = countText,
        )
        val inboxStyle = Notification.InboxStyle()
            .setBigContentTitle(countText)
            .setSummaryText(countText)
        content.inboxLines.forEach(inboxStyle::addLine)
        val notification = Notification.Builder(appContext, SubscriptionNotificationChannels.BADGE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(content.title)
            .setContentText(content.text)
            .setStyle(inboxStyle)
            .setContentIntent(profileNotificationsPendingIntent(appContext))
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setLocalOnly(true)
            .setNumber(count)
            .setBadgeIconType(Notification.BADGE_ICON_SMALL)
            .build()
        manager.notify(BADGE_NOTIFICATION_ID, notification)
    }

    fun clear(context: Context) {
        context.applicationContext
            .getSystemService(NotificationManager::class.java)
            .cancel(BADGE_NOTIFICATION_ID)
    }

    fun cancelNotification(context: Context, notificationId: Long) {
        context.applicationContext
            .getSystemService(NotificationManager::class.java)
            .cancel(notificationId.notificationId())
    }

    private fun profileNotificationsPendingIntent(context: Context): PendingIntent {
        return PendingIntent.getActivity(
            context,
            BADGE_NOTIFICATION_ID,
            profileNotificationsIntent(context),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    internal fun notificationShadeContent(
        unreadCount: Int,
        shadeItems: List<NotificationShadeItem>,
        fallbackTitle: String,
        countText: String,
    ): NotificationShadeContent {
        val titles = shadeItems.notificationShadeTitles(fallbackTitle).take(MAX_INBOX_LINES)
        val title = titles.firstOrNull() ?: fallbackTitle
        val text = when {
            titles.size >= 2 -> titles[1]
            unreadCount > 1 -> countText
            else -> shadeItems.firstOrNull()?.text?.takeIf(String::isNotBlank) ?: countText
        }
        return NotificationShadeContent(
            title = title,
            text = text,
            inboxLines = titles.ifEmpty { listOf(countText) },
        )
    }

    internal fun List<NotificationShadeItem>.notificationShadeTitles(fallbackTitle: String): List<String> {
        return sortedByDescending(NotificationShadeItem::dateSeconds)
            .map { item -> item.title.ifBlank { item.text }.ifBlank { fallbackTitle } }
            .distinct()
    }

    private fun Long.notificationId(): Int {
        return (this % Int.MAX_VALUE).toInt().coerceAtLeast(1)
    }
}

internal data class NotificationShadeContent(
    val title: String,
    val text: String,
    val inboxLines: List<String>,
)

internal fun profileNotificationsIntent(context: Context): Intent {
    return Intent(context, MainActivity::class.java)
        .setAction(ACTION_OPEN_PROFILE_NOTIFICATIONS)
        .putExtra(EXTRA_OPEN_PROFILE_NOTIFICATIONS, true)
        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
}

internal fun Intent.requestsProfileNotifications(): Boolean {
    return requestsProfileNotifications(
        action = action,
        openExtra = getBooleanExtra(EXTRA_OPEN_PROFILE_NOTIFICATIONS, false),
    )
}

internal fun requestsProfileNotifications(action: String?, openExtra: Boolean): Boolean {
    return action == ACTION_OPEN_PROFILE_NOTIFICATIONS || openExtra
}

internal const val ACTION_OPEN_PROFILE_NOTIFICATIONS =
    "me.yummydroid.app.action.OPEN_PROFILE_NOTIFICATIONS"
internal const val EXTRA_OPEN_PROFILE_NOTIFICATIONS = "open_profile_notifications"

internal object SubscriptionNotificationChannels {
    const val EPISODE_CHANNEL_ID = "anime_episode_notifications"
    const val BADGE_CHANNEL_ID = "profile_notification_badge"

    fun create(context: Context) {
        val manager = context.applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                EPISODE_CHANNEL_ID,
                context.getString(R.string.notification_episode_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.notification_episode_channel_description)
                setShowBadge(true)
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                BADGE_CHANNEL_ID,
                context.getString(R.string.notification_badge_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = context.getString(R.string.notification_badge_channel_description)
                setShowBadge(true)
            },
        )
    }
}

internal fun Context.canPostNotifications(): Boolean {
    return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
}

// ProfileNotificationState
internal fun AuthUiState.withUnreadNotifications(count: Int): AuthUiState {
    val currentProfile = profile ?: return this
    return copy(profile = currentProfile.copy(unreadNotifications = count.coerceAtLeast(0)))
}

internal fun AuthUiState.withUnreadNotificationDelta(delta: Int): AuthUiState {
    val currentProfile = profile ?: return this
    return withUnreadNotifications(currentProfile.unreadNotifications + delta)
}

internal fun List<SiteNotification>.unreadCount(): Int {
    return count { !it.viewed }
}

internal fun YummyDroidUiState.withProfileNotifications(
    notifications: List<SiteNotification>,
): YummyDroidUiState {
    return copy(
        profileNotifications = LoadState.Ready(notifications),
        auth = auth.withUnreadNotifications(notifications.unreadCount()),
    )
}

internal fun YummyDroidUiState.withProfileNotificationRead(notificationId: Long): YummyDroidUiState {
    val notifications = profileNotifications.readyDataOrNull() ?: return this
    val updatedNotifications = notifications.map { notification ->
        if (notification.id == notificationId) notification.copy(viewed = true) else notification
    }
    return if (updatedNotifications == notifications) this else withProfileNotifications(updatedNotifications)
}

internal fun YummyDroidUiState.withAllProfileNotificationsRead(): YummyDroidUiState {
    val notifications = profileNotifications.readyDataOrNull()
    return copy(
        profileNotifications = notifications
            ?.map { notification -> notification.copy(viewed = true) }
            ?.let { updated -> LoadState.Ready(updated) }
            ?: profileNotifications,
        auth = auth.withUnreadNotifications(0),
    )
}

internal fun YummyDroidUiState.withoutProfileNotification(
    notification: SiteNotification,
): YummyDroidUiState {
    val notifications = profileNotifications.readyDataOrNull()
    if (notifications == null) {
        return copy(
            auth = auth.withUnreadNotificationDelta(if (notification.viewed) 0 else -1),
        )
    }
    return withProfileNotifications(notifications.filterNot { it.id == notification.id })
}

internal class ProfileNotificationStateRuntime(
    private val scope: CoroutineScope,
    private val coordinator: ProfileNotificationCoordinator,
    private val currentState: () -> YummyDroidUiState,
    private val updateState: ((YummyDroidUiState) -> YummyDroidUiState) -> Unit,
    private val requestCaptchaRetry: (Throwable, suspend () -> Unit) -> Boolean,
    private val showErrorNotice: (String) -> Unit,
) {
    private val loadOperations = LatestStateOperationCoordinator()
    private val mutations = SerialStateOperationCoordinator()

    fun refresh() {
        syncFromSite()
    }

    fun cancel() {
        loadOperations.cancel()
        mutations.cancel()
    }

    fun markRead(notification: SiteNotification) {
        val profile = currentState().auth.profile
        if (currentState().forcedOfflineMode || profile == null || notification.viewed) return
        updateState { state -> state.withProfileNotificationRead(notification.id) }
        val notifications = currentState().profileNotifications.readyDataOrNull().orEmpty()
        launchMutation(
            profileId = profile.id,
            retryAction = { markRead(notification) },
        ) {
            coordinator.markRead(
                profileId = profile.id,
                notificationId = notification.id,
                notifications = notifications,
            )
        }
    }

    fun markAllRead() {
        val profile = currentState().auth.profile
        if (currentState().forcedOfflineMode || profile == null) return
        updateState(YummyDroidUiState::withAllProfileNotificationsRead)
        val notifications = currentState().profileNotifications.readyDataOrNull().orEmpty()
        launchMutation(
            profileId = profile.id,
            retryAction = ::markAllRead,
        ) {
            coordinator.markAllRead(
                profileId = profile.id,
                notifications = notifications,
            )
        }
    }

    fun delete(notification: SiteNotification) {
        val profile = currentState().auth.profile
        if (currentState().forcedOfflineMode || profile == null) return
        updateState { state -> state.withoutProfileNotification(notification) }
        val notifications = currentState().profileNotifications.readyDataOrNull().orEmpty()
        launchMutation(
            profileId = profile.id,
            retryAction = { delete(notification) },
        ) {
            coordinator.delete(
                profileId = profile.id,
                notificationId = notification.id,
                notifications = notifications,
            )
        }
    }

    private fun syncFromSite() {
        val profileId = profileIdOrNull() ?: return
        updateState { it.copy(profileNotifications = LoadState.Loading) }
        loadOperations.launchLatest(scope) { lease ->
            load(profileId, lease)
        }
    }

    private fun profileIdOrNull(): Long? {
        val current = currentState()
        val profileId = current.auth.profile?.id
        if (current.forcedOfflineMode || profileId == null) {
            updateState { it.copy(profileNotifications = LoadState.Ready(emptyList())) }
            return null
        }
        return profileId
    }

    private suspend fun load(profileId: Long, lease: StateOperationLease) {
        try {
            val notifications = coordinator.load(profileId)
            publish(profileId, lease, notifications)
        } catch (throwable: Throwable) {
            handleFailure(profileId, lease, throwable)
        }
    }

    private fun publish(
        profileId: Long,
        lease: StateOperationLease,
        notifications: List<SiteNotification>,
    ) {
        if (!lease.isCurrent || !isActiveProfile(profileId)) return
        updateState { state -> state.withProfileNotifications(notifications) }
    }

    private fun handleFailure(
        profileId: Long,
        lease: StateOperationLease,
        throwable: Throwable,
    ) {
        if (throwable is CancellationException) throw throwable
        if (!lease.isCurrent || !isActiveProfile(profileId)) return
        if (!requestCaptchaRetry(throwable) { syncFromSite() }) {
            updateState { it.copy(profileNotifications = LoadState.Error(throwable.userMessage())) }
        }
    }

    private fun launchMutation(
        profileId: Long,
        retryAction: suspend () -> Unit,
        action: suspend () -> Unit,
    ) {
        mutations.launch(scope) { lease ->
            try {
                action()
            } catch (throwable: Throwable) {
                if (throwable is CancellationException) throw throwable
                if (!lease.isCurrent || !isActiveProfile(profileId)) return@launch
                if (!requestCaptchaRetry(throwable, retryAction)) {
                    syncFromSite()
                    showErrorNotice(throwable.userMessage())
                }
            }
        }
    }

    private fun isActiveProfile(profileId: Long): Boolean {
        val current = currentState()
        return !current.forcedOfflineMode && current.auth.profile?.id == profileId
    }
}
