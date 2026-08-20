package me.yummydroid.app

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.io.IOException
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.yummydroid.app.data.ApiHttpException
import me.yummydroid.app.data.AppSettingsStorage
import me.yummydroid.app.data.AuthStorage
import me.yummydroid.app.data.CaptchaRequiredException
import me.yummydroid.app.data.SiteDomainResolver
import me.yummydroid.app.data.SiteNotification
import me.yummydroid.app.data.YummyAnimeRepository
import me.yummydroid.app.data.isUnauthorizedApiError

// SubscriptionNotificationAlarmReceiver
class SubscriptionNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (!isSubscriptionNotificationAlarmAction(intent?.action)) return
        SubscriptionNotificationScheduler.handleAlarmAsync(context.applicationContext)
    }

    companion object {
        const val ACTION_CHECK_SUBSCRIPTIONS = "me.yummydroid.app.CHECK_SUBSCRIPTION_NOTIFICATIONS"
    }
}

internal fun isSubscriptionNotificationAlarmAction(action: String?): Boolean =
    action == SubscriptionNotificationReceiver.ACTION_CHECK_SUBSCRIPTIONS

class SubscriptionNotificationDismissReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (!isSubscriptionNotificationDismissAction(intent?.action)) return
        val dismissalIntent = intent ?: return
        val profileId = dismissalIntent.getLongExtra(EXTRA_PROFILE_ID, 0L)
        val notificationIds = dismissalIntent.getLongArrayExtra(EXTRA_NOTIFICATION_IDS) ?: longArrayOf()
        if (profileId <= 0L || notificationIds.isEmpty()) return
        SubscriptionNotificationStore(context).markUnreadShadeItemsDismissed(profileId, notificationIds)
    }

    companion object {
        const val ACTION_DISMISS_PROFILE_NOTIFICATIONS =
            "me.yummydroid.app.DISMISS_PROFILE_NOTIFICATIONS"
        const val EXTRA_PROFILE_ID = "profile_id"
        const val EXTRA_NOTIFICATION_IDS = "notification_ids"
    }
}

internal fun isSubscriptionNotificationDismissAction(action: String?): Boolean =
    action == SubscriptionNotificationDismissReceiver.ACTION_DISMISS_PROFILE_NOTIFICATIONS

// SubscriptionNotificationEventKey
internal fun subscriptionNotificationEventKey(notification: SiteNotification): String {
    val animeKey = notification.objectId.takeIf { it > 0L }
        ?.let { "anime:$it" }
        ?: notification.clickUrl.animeIdFromNotificationUrl()?.let { "anime:$it" }
    val episodeKey = notification.episodeNumberFromNotificationText()?.let { "episode:$it" }
    if (animeKey != null && episodeKey != null) return "$animeKey|$episodeKey"

    return listOf(notification.title, notification.text)
        .joinToString("|")
        .lowercase(Locale.ROOT)
        .replace(IGNORED_NOTIFICATION_SOURCE_PATTERN, "")
        .replace(NOTIFICATION_KEY_SEPARATOR_PATTERN, " ")
        .trim()
}

private fun SiteNotification.episodeNumberFromNotificationText(): String? {
    val searchableText = "$title $text".lowercase(Locale.ROOT).replace(',', '.')
    return EPISODE_PATTERNS.firstNotNullOfOrNull { regex ->
        regex.find(searchableText)?.groupValues?.getOrNull(1)
    }
}

private fun String.animeIdFromNotificationUrl(): Long? {
    return ANIME_ID_URL_PATTERN.find(this)
        ?.groupValues
        ?.getOrNull(1)
        ?.toLongOrNull()
}

private const val RU_PLAYER_KEY = "\u043f\u043b\u0435\u0435\u0440"
private const val RU_VOICE_KEY = "\u043e\u0437\u0432\u0443\u0447\u043a\u0430"
private const val RU_EPISODE_WORD_PATTERN =
    "\u0441\u0435\u0440(?:\u0438\u044f|\u0438\u0438|\u0438\u044e|\u0438\u0435\u0439)?|\u044d\u043f\u0438\u0437\u043e\u0434"

private val IGNORED_NOTIFICATION_SOURCE_PATTERN =
    Regex(
        """(?<![\p{L}\p{N}_])(?:cvh|kodik|alloha|aksor|sibnet|hls|mp4|$RU_PLAYER_KEY|$RU_VOICE_KEY)(?![\p{L}\p{N}_])""",
    )
private val NOTIFICATION_KEY_SEPARATOR_PATTERN = Regex("""[\s./|\u2022:_-]+""")
private val ANIME_ID_URL_PATTERN = Regex("""-(\d+)(?:[/#?]|$)""")
private val EPISODE_PATTERNS = listOf(
    Regex("(?:$RU_EPISODE_WORD_PATTERN|episode|ep\\.?)\\s*#?\\s*(\\d+(?:\\.\\d+)?)"),
    Regex("""#\s*(\d+(?:\.\d+)?)"""),
)

// SubscriptionNotificationPolicy
internal enum class NotificationWorkerFailure {
    Rethrow,
    ClearAuth,
    Success,
    Retry,
    Failure,
}

internal object SubscriptionNotificationPolicy {
    fun classifyWorkerFailure(throwable: Throwable): NotificationWorkerFailure {
        return when {
            throwable is CancellationException -> NotificationWorkerFailure.Rethrow
            throwable is CaptchaRequiredException -> NotificationWorkerFailure.Success
            throwable.isUnauthorizedApiError() -> NotificationWorkerFailure.ClearAuth
            throwable is ApiHttpException && throwable.statusCode in 400..499 -> {
                NotificationWorkerFailure.Success
            }
            throwable is IOException -> NotificationWorkerFailure.Retry
            else -> NotificationWorkerFailure.Failure
        }
    }

    fun canSchedule(
        notificationsEnabled: Boolean,
        hasToken: Boolean,
        hasProfile: Boolean,
    ): Boolean {
        return notificationsEnabled && hasToken && hasProfile
    }

    fun newEpisodeNotifications(notifications: List<SiteNotification>): List<SiteNotification> {
        return notifications.filter { notification ->
            !notification.viewed &&
                notification.type == EPISODE_TYPE &&
                notification.subType == NEW_EPISODE_SUB_TYPE
        }
    }

    fun freshNotifications(
        notifications: List<SiteNotification>,
        isSeen: (SiteNotification) -> Boolean,
        eventKey: (SiteNotification) -> String,
    ): List<SiteNotification> {
        return notifications
            .filterNot(isSeen)
            .distinctBy(eventKey)
            .takeLast(MAX_NOTIFICATIONS_PER_CHECK)
    }

    fun notificationId(id: Long): Int {
        return (id % Int.MAX_VALUE).toInt().coerceAtLeast(1)
    }
}

private const val MAX_NOTIFICATIONS_PER_CHECK = 8
private const val EPISODE_TYPE = "anime_episode"
private const val NEW_EPISODE_SUB_TYPE = "new_episode"

// SubscriptionNotificationPreferences
class SubscriptionNotificationStore internal constructor(
    private val prefs: SharedPreferences,
    private val currentTimeMs: () -> Long,
) {
    constructor(context: Context) : this(
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
        currentTimeMs = System::currentTimeMillis,
    )

    fun isInitialized(): Boolean = prefs.getBoolean(KEY_INITIALIZED, false)

    fun markInitialized() {
        prefs.edit { putBoolean(KEY_INITIALIZED, true) }
    }

    fun shouldRunCheck(minSpacingMs: Long): Boolean {
        val lastCheckAt = prefs.getLong(KEY_LAST_CHECK_AT, 0L)
        val now = currentTimeMs()
        return lastCheckAt <= 0L || now < lastCheckAt || now - lastCheckAt >= minSpacingMs
    }

    fun markCheckRun() {
        prefs.edit { putLong(KEY_LAST_CHECK_AT, currentTimeMs()) }
    }

    fun isSeen(notification: SiteNotification): Boolean {
        return notification.id.toString() in seenIds() || eventKey(notification) in seenEvents()
    }

    fun markSeen(notifications: List<SiteNotification>) {
        if (notifications.isEmpty()) return
        val updatedIds = (seenIds().toList() + notifications.map { it.id.toString() })
            .takeLast(MAX_SEEN_ITEMS)
            .toSet()
        val updatedEvents = (seenEvents().toList() + notifications.map(::eventKey))
            .takeLast(MAX_SEEN_ITEMS)
            .toSet()
        prefs.edit {
            putStringSet(KEY_SEEN_IDS, updatedIds)
            putStringSet(KEY_SEEN_EVENTS, updatedEvents)
            putBoolean(KEY_INITIALIZED, true)
        }
    }

    internal fun saveUnreadShadeItems(notifications: List<SiteNotification>) {
        val json = notifications.unreadNotificationShadeItemsJson(MAX_STORED_UNREAD_ITEMS)
        prefs.edit {
            if (json == null) remove(KEY_UNREAD_SHADE_ITEMS) else putString(KEY_UNREAD_SHADE_ITEMS, json)
        }
    }

    internal fun clearUnreadShadeItems() {
        prefs.edit { remove(KEY_UNREAD_SHADE_ITEMS) }
    }

    internal fun unreadShadeItems(): List<NotificationShadeItem> {
        return decodeNotificationShadeItems(prefs.getString(KEY_UNREAD_SHADE_ITEMS, null))
    }

    internal fun areUnreadShadeItemsDismissed(
        profileId: Long,
        notificationIds: LongArray,
    ): Boolean {
        if (profileId <= 0L || notificationIds.isEmpty()) return false
        val dismissedIds = dismissedShadeIds(profileId)
        return notificationIds.all { it.toString() in dismissedIds }
    }

    internal fun markUnreadShadeItemsDismissed(
        profileId: Long,
        notificationIds: LongArray,
    ) {
        if (profileId <= 0L || notificationIds.isEmpty()) return
        val updatedIds = (dismissedShadeIds(profileId).toList() + notificationIds.map(Long::toString))
            .takeLast(MAX_DISMISSED_SHADE_ITEMS)
            .toSet()
        prefs.edit { putStringSet(dismissedShadeKey(profileId), updatedIds) }
    }

    fun eventKey(notification: SiteNotification): String {
        return subscriptionNotificationEventKey(notification)
    }

    private fun seenIds(): Set<String> = prefs.getStringSet(KEY_SEEN_IDS, emptySet()).orEmpty()

    private fun seenEvents(): Set<String> = prefs.getStringSet(KEY_SEEN_EVENTS, emptySet()).orEmpty()

    private fun dismissedShadeIds(profileId: Long): Set<String> {
        return prefs.getStringSet(dismissedShadeKey(profileId), emptySet()).orEmpty()
    }

    private fun dismissedShadeKey(profileId: Long): String = "$KEY_DISMISSED_SHADE_IDS_PREFIX$profileId"

    private companion object {
        const val PREFS_NAME = "yummydroid_subscription_notifications"
        const val KEY_INITIALIZED = "initialized"
        const val KEY_SEEN_IDS = "seen_ids"
        const val KEY_SEEN_EVENTS = "seen_events"
        const val KEY_LAST_CHECK_AT = "last_check_at"
        const val KEY_UNREAD_SHADE_ITEMS = "unread_shade_items"
        const val KEY_DISMISSED_SHADE_IDS_PREFIX = "dismissed_shade_ids_"
        const val MAX_SEEN_ITEMS = 300
        const val MAX_STORED_UNREAD_ITEMS = 20
        const val MAX_DISMISSED_SHADE_ITEMS = 300
    }
}

// SubscriptionNotificationRescheduleReceiver
private val SubscriptionNotificationRescheduleActions = setOf(
    Intent.ACTION_BOOT_COMPLETED,
    Intent.ACTION_MY_PACKAGE_REPLACED,
    Intent.ACTION_TIME_CHANGED,
    Intent.ACTION_TIMEZONE_CHANGED,
)

class SubscriptionNotificationRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (!isSubscriptionNotificationRescheduleAction(intent?.action)) return
        SubscriptionNotificationScheduler.configureFromStoredStateAsync(
            context = context.applicationContext,
            runImmediately = false,
        )
    }
}

internal fun isSubscriptionNotificationRescheduleAction(action: String?): Boolean =
    action in SubscriptionNotificationRescheduleActions

// SubscriptionNotificationScheduler
object SubscriptionNotificationScheduler {
    private val schedulerExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "YummyDroidNotifications").apply {
            isDaemon = true
        }
    }

    fun configure(context: Context, enabled: Boolean, runImmediately: Boolean = true) {
        val appContext = context.applicationContext
        if (!enabled) {
            cancel(appContext)
            return
        }
        SubscriptionNotificationChannels.create(appContext)
        val unreadCount = AuthStorage(appContext).readProfile()?.unreadNotifications ?: 0
        SubscriptionNotificationBadge.update(appContext, unreadCount)
        schedule(appContext)
        if (runImmediately) {
            runOnce(appContext)
        }
    }

    fun configureAsync(context: Context, enabled: Boolean, runImmediately: Boolean = true) {
        val appContext = context.applicationContext
        schedulerExecutor.execute {
            configure(
                context = appContext,
                enabled = enabled,
                runImmediately = runImmediately,
            )
        }
    }

    fun configureFromStoredState(context: Context, runImmediately: Boolean = false) {
        val appContext = context.applicationContext
        configure(
            context = appContext,
            enabled = storedNotificationsEnabled(appContext),
            runImmediately = runImmediately,
        )
    }

    fun configureFromStoredStateAsync(context: Context, runImmediately: Boolean = false) {
        val appContext = context.applicationContext
        schedulerExecutor.execute {
            configureFromStoredState(
                context = appContext,
                runImmediately = runImmediately,
            )
        }
    }

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<SubscriptionNotificationWorker>(
            PERIODIC_WORK_INTERVAL_MINUTES,
            TimeUnit.MINUTES,
        )
            .setConstraints(notificationWorkConstraints())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_MINUTES, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
        scheduleNextAlarm(context)
    }

    fun handleAlarm(context: Context) {
        val appContext = context.applicationContext
        if (!storedNotificationsEnabled(appContext)) {
            cancel(appContext)
            return
        }
        runOnce(appContext)
        scheduleNextAlarm(appContext)
    }

    fun handleAlarmAsync(context: Context) {
        val appContext = context.applicationContext
        schedulerExecutor.execute {
            handleAlarm(appContext)
        }
    }

    fun runOnce(context: Context) {
        val request = OneTimeWorkRequestBuilder<SubscriptionNotificationWorker>()
            .setConstraints(notificationWorkConstraints())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_MINUTES, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            IMMEDIATE_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    fun runOnceAsync(context: Context) {
        val appContext = context.applicationContext
        schedulerExecutor.execute {
            runOnce(appContext)
        }
    }

    fun cancel(context: Context) {
        val workManager = WorkManager.getInstance(context.applicationContext)
        workManager.cancelUniqueWork(PERIODIC_WORK_NAME)
        workManager.cancelUniqueWork(IMMEDIATE_WORK_NAME)
        cancelAlarm(context)
        SubscriptionNotificationBadge.clear(context)
    }

    fun scheduleNextAlarm(context: Context) {
        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(AlarmManager::class.java)
        val triggerAt = System.currentTimeMillis() + ALARM_INTERVAL_MS
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAt,
            createAlarmPendingIntent(appContext),
        )
    }

    private fun cancelAlarm(context: Context) {
        val appContext = context.applicationContext
        val pendingIntent = alarmPendingIntent(appContext, PendingIntent.FLAG_NO_CREATE) ?: return
        appContext.getSystemService(AlarmManager::class.java).cancel(pendingIntent)
        pendingIntent.cancel()
    }

    private fun createAlarmPendingIntent(context: Context): PendingIntent {
        return PendingIntent.getBroadcast(
            context.applicationContext,
            ALARM_REQUEST_CODE,
            notificationAlarmIntent(context),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun alarmPendingIntent(context: Context, flags: Int): PendingIntent? {
        return PendingIntent.getBroadcast(
            context.applicationContext,
            ALARM_REQUEST_CODE,
            notificationAlarmIntent(context),
            flags or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun notificationAlarmIntent(context: Context): Intent {
        return Intent(context.applicationContext, SubscriptionNotificationReceiver::class.java).apply {
            action = SubscriptionNotificationReceiver.ACTION_CHECK_SUBSCRIPTIONS
        }
    }

    private fun notificationWorkConstraints(): Constraints {
        return Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
    }

    private fun storedNotificationsEnabled(context: Context): Boolean {
        val appContext = context.applicationContext
        val settings = AppSettingsStorage(appContext).read()
        val authStorage = AuthStorage(appContext)
        return SubscriptionNotificationPolicy.canSchedule(
            notificationsEnabled = settings.notificationsEnabled,
            hasToken = authStorage.readToken() != null,
            hasProfile = authStorage.readProfile() != null,
        )
    }
}

private const val PERIODIC_WORK_NAME = "subscription_notification_periodic_check"
private const val IMMEDIATE_WORK_NAME = "subscription_notification_immediate_check"
private const val PERIODIC_WORK_INTERVAL_MINUTES = 15L
private const val ALARM_INTERVAL_MINUTES = 5L
private const val ALARM_INTERVAL_MS = ALARM_INTERVAL_MINUTES * 60 * 1000L
private const val BACKOFF_MINUTES = 30L
private const val ALARM_REQUEST_CODE = 28043

// SubscriptionNotificationSync
internal object SubscriptionNotificationSync {
    private const val MIN_CHECK_SPACING_MS = 5 * 60 * 1000L

    suspend fun check(context: Context) {
        val appContext = context.applicationContext
        val settings = AppSettingsStorage(appContext).read()
        val authStorage = AuthStorage(appContext)
        val token = authStorage.readToken()
        val profile = authStorage.readProfile()
        if (
            !SubscriptionNotificationPolicy.canSchedule(
                notificationsEnabled = settings.notificationsEnabled,
                hasToken = token != null,
                hasProfile = profile != null,
            )
        ) {
            SubscriptionNotificationBadge.clear(appContext)
            return
        }

        val store = SubscriptionNotificationStore(appContext)
        if (!store.shouldRunCheck(MIN_CHECK_SPACING_MS)) return

        val repository = YummyAnimeRepository(
            context = appContext,
            siteDomainResolver = SiteDomainResolver(candidates = settings.siteDomains),
            authStorage = authStorage,
        )
        val notifications = repository.getProfileNotifications(limit = PROFILE_NOTIFICATION_FETCH_LIMIT)
            .sortedBy { it.dateSeconds }
        store.markCheckRun()

        AndroidProfileNotificationRuntime(appContext, authStorage).synchronize(
            profileId = requireNotNull(profile).id,
            notifications = notifications,
        )

        val episodeNotifications = SubscriptionNotificationPolicy
            .newEpisodeNotifications(notifications)
        if (!store.isInitialized()) {
            store.markSeen(episodeNotifications)
            store.markInitialized()
            return
        }

        val fresh = SubscriptionNotificationPolicy.freshNotifications(
            notifications = episodeNotifications,
            isSeen = store::isSeen,
            eventKey = store::eventKey,
        )
        if (fresh.isEmpty()) return

        postNotifications(appContext, fresh, store)
    }

    private fun postNotifications(
        context: Context,
        notifications: List<SiteNotification>,
        store: SubscriptionNotificationStore,
    ) {
        if (!context.canPostNotifications()) return
        SubscriptionNotificationChannels.create(context)
        val manager = context.getSystemService(NotificationManager::class.java)
        notifications.forEach { notification ->
            manager.notify(
                SubscriptionNotificationPolicy.notificationId(notification.id),
                notification.toAndroidNotification(context),
            )
            store.markSeen(listOf(notification))
        }
    }

    private fun SiteNotification.toAndroidNotification(context: Context): Notification {
        val titleText = title.ifBlank { context.getString(R.string.notification_new_episode_title) }
        val bodyText = text.ifBlank { context.getString(R.string.notification_new_episode_text) }
        val pendingIntent = PendingIntent.getActivity(
            context,
            SubscriptionNotificationPolicy.notificationId(id),
            profileNotificationsIntent(context),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(context, SubscriptionNotificationChannels.EPISODE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_yummydroid)
            .setContentTitle(titleText)
            .setContentText(bodyText)
            .setStyle(Notification.BigTextStyle().bigText(bodyText.ifBlank { titleText }))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setBadgeIconType(Notification.BADGE_ICON_SMALL)
            .build()
    }
}

// SubscriptionNotificationWorker
class SubscriptionNotificationWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            SubscriptionNotificationSync.check(applicationContext)
            Result.success()
        } catch (throwable: Throwable) {
            when (SubscriptionNotificationPolicy.classifyWorkerFailure(throwable)) {
                NotificationWorkerFailure.Rethrow -> throw throwable
                NotificationWorkerFailure.ClearAuth -> clearAuthentication()
                NotificationWorkerFailure.Success -> Result.success()
                NotificationWorkerFailure.Retry -> Result.retry()
                NotificationWorkerFailure.Failure -> Result.failure()
            }
        }
    }

    private fun clearAuthentication(): Result {
        AuthStorage(applicationContext).clear()
        SubscriptionNotificationBadge.clear(applicationContext)
        return Result.success()
    }
}
