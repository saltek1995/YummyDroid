package me.yummydroid.app

internal class NotificationUpdateGate(
    private val minIntervalMs: Long,
    private val clockMs: () -> Long = { System.currentTimeMillis() },
) {
    private val lock = Any()
    private var lastPostedAtMs: Long? = null

    fun shouldPost(force: Boolean = false): Boolean = synchronized(lock) {
        val now = clockMs()
        val last = lastPostedAtMs
        if (force || last == null || now < last || now - last >= minIntervalMs) {
            lastPostedAtMs = now
            true
        } else {
            false
        }
    }

    fun reset() = synchronized(lock) {
        lastPostedAtMs = null
    }
}
