package me.yummydroid.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.IBinder
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import okhttp3.OkHttpClient
import okhttp3.Request

// UpdateDownloadRuntime
internal class UpdateDownloadRuntime(
    private val service: UpdateDownloadService,
) {
    private val client = OkHttpClient.Builder()
        .callTimeout(0, TimeUnit.MILLISECONDS)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            service.getString(R.string.ui_update_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        )
        notificationManager().createNotificationChannel(channel)
    }

    fun showDownloadNotification() {
        service.startForeground(
            NOTIFICATION_ID,
            notification(service.getString(R.string.ui_update_download_title), 0, null),
        )
    }

    fun downloadAndInstall(url: String, version: String) {
        val updateDir = File(service.externalCacheDir ?: service.cacheDir, "updates").apply { mkdirs() }
        val apkFile = File(updateDir, updateApkFileName(version))
        val partFile = File(updateDir, "${apkFile.name}.part")

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "YummyDroid Android")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            val body = response.body ?: error(service.getString(R.string.ui_update_download_empty_file))
            val totalBytes = body.contentLength()
            FileOutputStream(partFile).use { output ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var downloaded = 0L
                    var lastNotifyAt = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        val now = System.currentTimeMillis()
                        if (now - lastNotifyAt > PROGRESS_UPDATE_INTERVAL_MS) {
                            lastNotifyAt = now
                            notifyProgress(updateDownloadProgress(downloaded, totalBytes), downloaded, totalBytes)
                        }
                    }
                }
            }
        }
        if (apkFile.exists()) apkFile.delete()
        check(partFile.renameTo(apkFile)) { service.getString(R.string.ui_update_save_apk_failed) }
        notifyDone(
            service.getString(R.string.ui_update_downloaded_title),
            service.getString(R.string.ui_update_downloaded_text),
        )
        installApk(apkFile, version)
    }

    fun notifyFailure(throwable: Throwable) {
        notifyDone(service.getString(R.string.ui_update_download_failed), throwable.message.orEmpty())
    }

    private fun installApk(apkFile: File, version: String) {
        when (UpdateInstallLauncher.startInstallOrRequestPermission(service, apkFile, version)) {
            UpdateInstallResult.RequestedPermission -> {
                notifyDone(
                    service.getString(R.string.ui_update_install_permission_title),
                    service.getString(R.string.ui_update_install_permission_text),
                )
            }
            UpdateInstallResult.MissingApk -> {
                notifyDone(
                    service.getString(R.string.ui_update_download_failed),
                    service.getString(R.string.ui_update_save_apk_failed),
                )
            }
            UpdateInstallResult.StartedInstall -> Unit
        }
    }

    private fun notifyProgress(progress: Int, downloadedBytes: Long, totalBytes: Long) {
        val text = if (totalBytes > 0L) {
            service.getString(
                R.string.ui_update_download_progress,
                progress,
                updateByteSize(downloadedBytes),
                updateByteSize(totalBytes),
            )
        } else {
            updateByteSize(downloadedBytes)
        }
        notificationManager().notify(
            NOTIFICATION_ID,
            notification(service.getString(R.string.ui_update_download_title), progress, text),
        )
    }

    private fun updateByteSize(bytes: Long): String {
        return formatByteSize(
            bytes = bytes,
            byteUnit = service.getString(R.string.ui_unit_byte),
            kilobyteUnit = service.getString(R.string.ui_unit_kilobyte),
            megabyteUnit = service.getString(R.string.ui_unit_megabyte),
            gigabyteUnit = service.getString(R.string.ui_unit_gigabyte),
        )
    }

    private fun notifyDone(title: String, text: String) {
        notificationManager().notify(NOTIFICATION_ID, notification(title, 100, text, done = true))
    }

    private fun notification(
        title: String,
        progress: Int,
        text: String?,
        done: Boolean = false,
    ): Notification {
        val pendingIntent = PendingIntent.getActivity(
            service,
            0,
            Intent(service, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(service, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(title)
            .setContentText(text.orEmpty())
            .setContentIntent(pendingIntent)
            .setOngoing(!done)
            .setOnlyAlertOnce(true)
            .setProgress(100, progress.coerceIn(0, 100), progress <= 0 && !done)
            .build()
    }

    private fun notificationManager(): NotificationManager {
        return service.getSystemService(NotificationManager::class.java)
    }
}

internal enum class UpdateInstallResult {
    StartedInstall,
    RequestedPermission,
    MissingApk,
}

internal object UpdateInstallLauncher {
    fun startInstallOrRequestPermission(
        context: Context,
        apkFile: File,
        version: String,
    ): UpdateInstallResult {
        if (!apkFile.isFile) return UpdateInstallResult.MissingApk
        if (!context.packageManager.canRequestPackageInstalls()) {
            PendingUpdateInstallStore.save(context, apkFile, version)
            context.startActivity(updateInstallPermissionIntent(context))
            return UpdateInstallResult.RequestedPermission
        }

        PendingUpdateInstallStore.clear(context)
        context.startActivity(updateInstallIntent(context, apkFile))
        return UpdateInstallResult.StartedInstall
    }

    fun startPendingInstallIfAllowed(context: Context): Boolean {
        if (!context.packageManager.canRequestPackageInstalls()) return false
        val pendingInstall = PendingUpdateInstallStore.read(context) ?: return false
        val apkFile = File(pendingInstall.apkPath)
        if (!apkFile.isFile) {
            PendingUpdateInstallStore.clear(context)
            return false
        }

        PendingUpdateInstallStore.clear(context)
        context.startActivity(updateInstallIntent(context, apkFile))
        return true
    }

    private fun updateInstallPermissionIntent(context: Context): Intent {
        return Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            "package:${context.packageName}".toUri(),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    private fun updateInstallIntent(context: Context, apkFile: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
        return Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, APK_MIME_TYPE)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}

internal data class PendingUpdateInstall(
    val apkPath: String,
    val version: String,
)

internal object PendingUpdateInstallStore {
    fun save(context: Context, apkFile: File, version: String) {
        save(preferences(context), apkPath = apkFile.absolutePath, version = version)
    }

    fun read(context: Context): PendingUpdateInstall? {
        return read(preferences(context))
    }

    fun clear(context: Context) {
        clear(preferences(context))
    }

    fun save(preferences: SharedPreferences, apkPath: String, version: String) {
        preferences.edit()
            .putString(KEY_APK_PATH, apkPath)
            .putString(KEY_VERSION, version)
            .apply()
    }

    fun read(preferences: SharedPreferences): PendingUpdateInstall? {
        val apkPath = preferences.getString(KEY_APK_PATH, null)?.takeIf { it.isNotBlank() }
            ?: return null
        val version = preferences.getString(KEY_VERSION, null).orEmpty()
        return PendingUpdateInstall(apkPath = apkPath, version = version)
    }

    fun clear(preferences: SharedPreferences) {
        preferences.edit().clear().apply()
    }

    private fun preferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private const val PREFS_NAME = "pending_update_install"
    private const val KEY_APK_PATH = "apk_path"
    private const val KEY_VERSION = "version"
}

internal fun updateApkFileName(version: String): String {
    return "YummyDroid-${version.trim().removePrefix("v")}.apk"
}

internal fun updateDownloadProgress(downloadedBytes: Long, totalBytes: Long): Int {
    if (totalBytes <= 0L) return 0
    return (downloadedBytes * 100L / totalBytes).toInt().coerceIn(0, 100)
}

private const val CHANNEL_ID = "yummydroid_updates"
private const val NOTIFICATION_ID = 2001
private const val APK_MIME_TYPE = "application/vnd.android.package-archive"
private const val PROGRESS_UPDATE_INTERVAL_MS = 600L

// UpdateDownloadService
class UpdateDownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val downloadOperations = LatestStateOperationCoordinator()
    private val runtime by lazy(LazyThreadSafetyMode.NONE) { UpdateDownloadRuntime(this) }

    override fun onCreate() {
        super.onCreate()
        runtime.createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val url = intent?.getStringExtra(EXTRA_URL).orEmpty()
        val version = intent?.getStringExtra(EXTRA_VERSION).orEmpty().ifBlank { "update" }
        if (url.isBlank()) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        runtime.showDownloadNotification()
        downloadOperations.launchLatest(scope) { lease ->
            runCatching { runtime.downloadAndInstall(url, version) }
                .onFailure { throwable -> if (lease.isCurrent) runtime.notifyFailure(throwable) }
            if (lease.isCurrent) {
                stopForeground(STOP_FOREGROUND_DETACH)
                stopSelf(startId)
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        downloadOperations.cancel()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_URL = "url"
        private const val EXTRA_VERSION = "version"

        fun start(context: Context, url: String, version: String) {
            val intent = Intent(context, UpdateDownloadService::class.java)
                .putExtra(EXTRA_URL, url)
                .putExtra(EXTRA_VERSION, version)
            context.startForegroundService(intent)
        }
    }
}
