package me.yummydroid.app

import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

enum class DownloadTaskState {
    Queued,
    Running,
    Completed,
    Failed,
}

data class DownloadTaskUi(
    val id: Long,
    val animeId: Long,
    val videoId: Long?,
    val title: String,
    val episodeTitle: String,
    val qualityTitle: String = "Авто",
    val progress: Float = 0f,
    val state: DownloadTaskState = DownloadTaskState.Queued,
    val message: String = "",
) {
    val isActive: Boolean
        get() = state == DownloadTaskState.Queued || state == DownloadTaskState.Running
}

data class DownloadQueueSnapshot(
    val tasks: List<DownloadTaskUi> = emptyList(),
) {
    val activeTasks: List<DownloadTaskUi>
        get() = tasks.filter { it.isActive }

    val finishedTasks: List<DownloadTaskUi>
        get() = tasks.filterNot { it.isActive }
}

object DownloadCenter {
    private val ids = AtomicLong(1L)
    val state = MutableStateFlow(DownloadQueueSnapshot())

    fun addTask(
        animeId: Long,
        videoId: Long?,
        title: String,
        episodeTitle: String,
        qualityTitle: String = "Авто",
    ): Long {
        val existing = state.value.tasks.firstOrNull {
            it.isActive && it.animeId == animeId && it.videoId == videoId
        }
        if (existing != null) return existing.id

        val task = DownloadTaskUi(
            id = ids.getAndIncrement(),
            animeId = animeId,
            videoId = videoId,
            title = title,
            episodeTitle = episodeTitle,
            qualityTitle = qualityTitle,
        )
        state.update { snapshot ->
            snapshot.copy(tasks = (listOf(task) + snapshot.tasks).take(MAX_TASKS))
        }
        return task.id
    }

    fun updateTask(
        id: Long,
        progress: Float? = null,
        state: DownloadTaskState? = null,
        message: String? = null,
    ) {
        this.state.update { snapshot ->
            snapshot.copy(
                tasks = snapshot.tasks.map { task ->
                    if (task.id == id) {
                        task.copy(
                            progress = progress?.coerceIn(0f, 1f) ?: task.progress,
                            state = state ?: task.state,
                            message = message ?: task.message,
                        )
                    } else {
                        task
                    }
                },
            )
        }
    }

    fun clearFinished() {
        state.update { snapshot -> snapshot.copy(tasks = snapshot.activeTasks) }
    }

    private const val MAX_TASKS = 120
}
