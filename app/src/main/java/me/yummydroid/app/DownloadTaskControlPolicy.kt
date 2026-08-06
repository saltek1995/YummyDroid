package me.yummydroid.app

internal enum class DownloadTaskInterruption {
    Cancelled,
    Paused,
}

internal data class DownloadTaskInterruptionHandling(
    val interruption: DownloadTaskInterruption,
    val clearStopRequest: Boolean,
    val waitingForUnmetered: Boolean?,
)

internal fun resolveDownloadTaskInterruption(
    taskCancelRequested: Boolean,
    parentCancelRequested: Boolean,
    taskPauseRequested: Boolean,
    parentPauseRequested: Boolean,
): DownloadTaskInterruption? = when {
    taskCancelRequested || parentCancelRequested -> DownloadTaskInterruption.Cancelled
    taskPauseRequested || parentPauseRequested -> DownloadTaskInterruption.Paused
    else -> null
}

internal fun resolveDownloadTaskInterruptionHandling(
    taskCancelRequested: Boolean,
    parentCancelRequested: Boolean,
    taskPauseRequested: Boolean,
    parentPauseRequested: Boolean,
    clearStopRequestOnCancel: Boolean,
    clearStopRequestOnPause: Boolean,
    waitingForUnmetered: Boolean? = null,
): DownloadTaskInterruptionHandling? {
    val interruption = resolveDownloadTaskInterruption(
        taskCancelRequested = taskCancelRequested,
        parentCancelRequested = parentCancelRequested,
        taskPauseRequested = taskPauseRequested,
        parentPauseRequested = parentPauseRequested,
    ) ?: return null
    return DownloadTaskInterruptionHandling(
        interruption = interruption,
        clearStopRequest = when (interruption) {
            DownloadTaskInterruption.Cancelled -> clearStopRequestOnCancel
            DownloadTaskInterruption.Paused -> clearStopRequestOnPause
        },
        waitingForUnmetered = waitingForUnmetered,
    )
}
