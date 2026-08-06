package me.yummydroid.app

import android.content.Context
import me.yummydroid.app.data.ContentLanguage

internal data class DownloadNotificationSummary(
    val title: String,
    val text: String,
    val progressMax: Int,
    val progress: Int,
    val indeterminate: Boolean,
    val ongoing: Boolean,
)

internal fun DownloadQueueSnapshot.notificationSummary(
    context: Context,
    language: ContentLanguage,
): DownloadNotificationSummary {
    val active = activeTasks
    if (active.isEmpty()) {
        return DownloadNotificationSummary(
            title = "YummyDroid",
            text = context.localizedString(R.string.ui_download_notification_idle_text, language),
            progressMax = 0,
            progress = 0,
            indeterminate = true,
            ongoing = false,
        )
    }

    val activeBatchKeys = active.mapTo(mutableSetOf()) { it.notificationBatchKey() }
    val groupedTasks = tasks
        .filter { it.notificationBatchKey() in activeBatchKeys }
        .filterNot { it.state == DownloadTaskState.Cancelled }
        .ifEmpty { active }
    val batchTotal = groupedTasks.maxOfOrNull { it.batchTotal }?.takeIf { it > 0 }
    val batchCompleted = groupedTasks.maxOfOrNull { it.batchCompleted }?.takeIf { it > 0 }
    val total = batchTotal ?: groupedTasks.size.coerceAtLeast(1)
    val completed = batchCompleted ?: groupedTasks.count { it.state == DownloadTaskState.Completed }
    val speedBytesPerSecond = groupedTasks
        .filter { it.state == DownloadTaskState.Running }
        .sumOf { it.bytesPerSecond.coerceAtLeast(0L) }
    val status = context.localizedString(R.string.ui_download_notification_progress, language, completed, total)
    val speed = speedBytesPerSecond
        .takeIf { it > 0L }
        ?.let { "${context.localizedByteSize(it, language)}/${context.localizedString(R.string.ui_s, language)}" }
    return DownloadNotificationSummary(
        title = context.localizedString(R.string.ui_download_notification_title, language),
        text = downloadNotificationSummaryText(status, speed),
        progressMax = total,
        progress = completed.coerceAtMost(total),
        indeterminate = false,
        ongoing = true,
    )
}

internal fun downloadNotificationSummaryText(status: String, speed: String?): String {
    return listOfNotNull(status, speed).joinToString(" - ")
}

private fun Context.localizedByteSize(bytes: Long, language: ContentLanguage): String {
    return formatByteSize(
        bytes = bytes,
        byteUnit = localizedString(R.string.ui_unit_byte, language),
        kilobyteUnit = localizedString(R.string.ui_unit_kilobyte, language),
        megabyteUnit = localizedString(R.string.ui_unit_megabyte, language),
        gigabyteUnit = localizedString(R.string.ui_unit_gigabyte, language),
    )
}

private fun DownloadTaskUi.notificationBatchKey(): String {
    return batchKey.takeIf { it.isNotBlank() } ?: "task:$id"
}
