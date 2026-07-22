package me.yummydroid.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.cancel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import okhttp3.Request

class UpdateDownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = OkHttpClient.Builder()
        .callTimeout(0, TimeUnit.MILLISECONDS)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val url = intent?.getStringExtra(EXTRA_URL).orEmpty()
        val version = intent?.getStringExtra(EXTRA_VERSION).orEmpty().ifBlank { "update" }
        if (url.isBlank()) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, notification("Скачивание обновления", 0, null))
        scope.launch {
            runCatching { downloadAndInstall(url, version) }
                .onFailure { throwable ->
                    notifyDone("Не удалось скачать обновление", throwable.message.orEmpty())
                }
            stopForeground(STOP_FOREGROUND_DETACH)
            stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun downloadAndInstall(url: String, version: String) {
        val updateDir = File(externalCacheDir ?: cacheDir, "updates").apply { mkdirs() }
        val apkFile = File(updateDir, "YummyDroid-${version.trim().removePrefix("v")}.apk")
        val partFile = File(updateDir, "${apkFile.name}.part")

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "YummyDroid Android")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            val body = response.body ?: error("Пустой файл обновления")
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
                        if (now - lastNotifyAt > 600L) {
                            lastNotifyAt = now
                            val progress = if (totalBytes > 0L) {
                                (downloaded * 100L / totalBytes).toInt().coerceIn(0, 100)
                            } else {
                                0
                            }
                            notifyProgress(progress, downloaded, totalBytes)
                        }
                    }
                }
            }
        }
        if (apkFile.exists()) apkFile.delete()
        check(partFile.renameTo(apkFile)) { "Не удалось сохранить APK" }
        notifyDone("Обновление скачано", "Откройте установщик Android")
        installApk(apkFile)
    }

    private fun installApk(apkFile: File) {
        if (!packageManager.canRequestPackageInstalls()) {
            val settingsIntent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                "package:$packageName".toUri(),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(settingsIntent)
            notifyDone(
                "Разрешите установку",
                "После разрешения снова нажмите «Обновить» в приложении",
            )
            return
        }

        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", apkFile)
        val installIntent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, APK_MIME_TYPE)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(installIntent)
    }

    private fun notifyProgress(progress: Int, downloadedBytes: Long, totalBytes: Long) {
        val text = if (totalBytes > 0L) {
            "$progress% • ${formatByteSize(downloadedBytes)} из ${formatByteSize(totalBytes)}"
        } else {
            formatByteSize(downloadedBytes)
        }
        notificationManager().notify(NOTIFICATION_ID, notification("Скачивание обновления", progress, text))
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
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(title)
            .setContentText(text.orEmpty())
            .setContentIntent(pendingIntent)
            .setOngoing(!done)
            .setOnlyAlertOnce(true)
            .setProgress(100, progress.coerceIn(0, 100), progress <= 0 && !done)
            .build()
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Обновления YummyDroid",
            NotificationManager.IMPORTANCE_LOW,
        )
        notificationManager().createNotificationChannel(channel)
    }

    private fun notificationManager(): NotificationManager {
        return getSystemService(NotificationManager::class.java)
    }

    companion object {
        private const val CHANNEL_ID = "yummydroid_updates"
        private const val NOTIFICATION_ID = 2001
        private const val APK_MIME_TYPE = "application/vnd.android.package-archive"
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
