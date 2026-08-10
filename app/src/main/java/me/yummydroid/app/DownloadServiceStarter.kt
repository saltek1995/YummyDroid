package me.yummydroid.app

import android.content.Context
import android.content.Intent
import me.yummydroid.app.data.PreferredQuality

internal object DownloadServiceStarter {
    fun enqueueTask(context: Context, task: DownloadTaskUi) {
        context.startDownloadService(
            downloadServiceIntent(context)
                .setAction(downloadActionForTask(task))
                .putExtra(DOWNLOAD_EXTRA_TASK_ID, task.id)
                .putExtra(DOWNLOAD_EXTRA_PLAN_ID, task.planId)
                .putExtra(DOWNLOAD_EXTRA_ANIME_ID, task.animeId)
                .putExtra(DOWNLOAD_EXTRA_VIDEO_ID, task.videoId ?: 0L)
                .putExtra(DOWNLOAD_EXTRA_GROUP_KEY, task.groupKey)
                .putExtra(DOWNLOAD_EXTRA_QUALITY_NAME, task.preferredQualityName),
        )
    }

    fun enqueueVideo(
        context: Context,
        animeId: Long,
        videoId: Long,
        groupKey: String? = null,
        quality: PreferredQuality = PreferredQuality.Auto,
    ) {
        DownloadCenter.initialize(context)
        context.startDownloadService(
            downloadServiceIntent(context)
                .setAction(DOWNLOAD_ACTION_VIDEO)
                .putExtra(DOWNLOAD_EXTRA_ANIME_ID, animeId)
                .putExtra(DOWNLOAD_EXTRA_VIDEO_ID, videoId)
                .putExtra(DOWNLOAD_EXTRA_GROUP_KEY, groupKey.orEmpty())
                .putExtra(DOWNLOAD_EXTRA_QUALITY_NAME, quality.name),
        )
    }

    fun enqueueAnime(
        context: Context,
        animeId: Long,
        groupKey: String? = null,
        quality: PreferredQuality = PreferredQuality.Auto,
    ) {
        DownloadCenter.initialize(context)
        context.startDownloadService(
            downloadServiceIntent(context)
                .setAction(DOWNLOAD_ACTION_ANIME)
                .putExtra(DOWNLOAD_EXTRA_ANIME_ID, animeId)
                .putExtra(DOWNLOAD_EXTRA_GROUP_KEY, groupKey.orEmpty())
                .putExtra(DOWNLOAD_EXTRA_QUALITY_NAME, quality.name),
        )
    }

    fun enqueuePlan(context: Context, planId: String) {
        if (planId.isBlank()) return
        DownloadCenter.initialize(context)
        context.startDownloadService(
            downloadServiceIntent(context)
                .setAction(DOWNLOAD_ACTION_PLAN)
                .putExtra(DOWNLOAD_EXTRA_PLAN_ID, planId),
        )
    }

    private fun downloadServiceIntent(context: Context): Intent {
        return Intent(context, DownloadService::class.java)
    }
}

private fun Context.startDownloadService(intent: Intent) {
    startForegroundService(intent)
}

internal fun downloadActionForTask(task: DownloadTaskUi): String {
    return when {
        task.isBatchSummary && task.planId.isNotBlank() -> DOWNLOAD_ACTION_PLAN
        task.videoId == null -> DOWNLOAD_ACTION_ANIME
        else -> DOWNLOAD_ACTION_VIDEO
    }
}

internal const val DOWNLOAD_ACTION_VIDEO = "me.yummydroid.app.DOWNLOAD_VIDEO"
internal const val DOWNLOAD_ACTION_ANIME = "me.yummydroid.app.DOWNLOAD_ANIME"
internal const val DOWNLOAD_ACTION_PLAN = "me.yummydroid.app.DOWNLOAD_PLAN"
internal const val DOWNLOAD_EXTRA_TASK_ID = "task_id"
internal const val DOWNLOAD_EXTRA_PLAN_ID = "plan_id"
internal const val DOWNLOAD_EXTRA_ANIME_ID = "anime_id"
internal const val DOWNLOAD_EXTRA_VIDEO_ID = "video_id"
internal const val DOWNLOAD_EXTRA_GROUP_KEY = "group_key"
internal const val DOWNLOAD_EXTRA_QUALITY_NAME = "quality_name"
