package me.yummydroid.app

internal enum class DownloadStopRequest {
    Pause,
    Cancel,
}

internal class DownloadStopRequests {
    private val cancelRequests = mutableSetOf<Long>()
    private val pauseRequests = mutableSetOf<Long>()

    @Synchronized
    fun request(ids: Set<Long>, request: DownloadStopRequest) {
        when (request) {
            DownloadStopRequest.Pause -> {
                pauseRequests.addAll(ids)
                cancelRequests.removeAll(ids)
            }
            DownloadStopRequest.Cancel -> {
                cancelRequests.addAll(ids)
                pauseRequests.removeAll(ids)
            }
        }
    }

    @Synchronized
    fun isCancelRequested(id: Long): Boolean = id in cancelRequests

    @Synchronized
    fun isPauseRequested(id: Long): Boolean = id in pauseRequests

    @Synchronized
    fun isStopRequested(id: Long): Boolean = id in cancelRequests || id in pauseRequests

    @Synchronized
    fun clear(id: Long) {
        cancelRequests -= id
        pauseRequests -= id
    }

    @Synchronized
    fun clearAll() {
        cancelRequests.clear()
        pauseRequests.clear()
    }
}
