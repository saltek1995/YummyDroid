package me.yummydroid.app

internal class WatchHistoryRefreshState(
    private val monotonicClockMs: () -> Long,
    private val refreshIntervalMs: Long,
) {
    private var cacheInitialized = false
    private var lastRemoteCheckAtMs = 0L

    fun beginRefresh(
        force: Boolean,
        hasReadyHistory: Boolean,
        canUseRemote: Boolean,
        loadActive: Boolean,
    ): WatchHistoryRefreshPlan? {
        val plan = watchHistoryRefreshPlan(
            force = force,
            hasReadyHistory = hasReadyHistory,
            canUseRemote = canUseRemote,
            loadActive = loadActive,
            cacheInitialized = cacheInitialized,
            remoteRefreshDue = canUseRemote && remoteRefreshDue(),
        ) ?: return null
        cacheInitialized = true
        return plan
    }

    fun reset() {
        cacheInitialized = false
        lastRemoteCheckAtMs = 0L
    }

    fun markRemoteCheckStarted() {
        lastRemoteCheckAtMs = monotonicClockMs()
    }

    fun markRemoteSynchronized() {
        cacheInitialized = true
        markRemoteCheckStarted()
    }

    private fun remoteRefreshDue(): Boolean {
        return lastRemoteCheckAtMs == 0L ||
            monotonicClockMs() - lastRemoteCheckAtMs >= refreshIntervalMs
    }
}
