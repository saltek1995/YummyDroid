package me.yummydroid.app

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.core.content.edit
import me.yummydroid.app.data.decodeAppJsonOrNull
import me.yummydroid.app.data.encodeAppJson

internal class DownloadQueueStorage(private val context: Context) {
    fun read(): List<DownloadTaskUi> {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_TASKS, null)
            ?.takeIf { it.isNotBlank() }
            ?.decodeAppJsonOrNull<List<DownloadTaskUi>>()
            .orEmpty()
    }

    fun write(tasks: List<DownloadTaskUi>) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putString(KEY_TASKS, tasks.cappedDownloadTasks().encodeAppJson())
        }
    }

    private companion object {
        const val PREFS_NAME = "yummydroid_download_queue"
        const val KEY_TASKS = "tasks"
    }
}

internal class DownloadNetworkObserver {
    private var registered = false

    fun register(context: Context, onNetworkAvailable: () -> Unit) {
        if (registered) return
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                onNetworkAvailable()
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                onNetworkAvailable()
            }
        }
        runCatching { manager.registerDefaultNetworkCallback(callback) }
            .onSuccess { registered = true }
    }
}
