package me.yummydroid.app

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import me.yummydroid.app.data.AppSettings

object DownloadNetworkPolicy {
    fun canDownloadNow(context: Context, settings: AppSettings): Boolean {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        if (!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return false
        if (settings.allowMeteredDownloads) return true
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    fun waitingMessage(context: Context, settings: AppSettings): String {
        val messageResId = if (settings.allowMeteredDownloads) {
            R.string.ui_download_network_waiting
        } else {
            R.string.ui_download_network_waiting_unmetered
        }
        return context.localizedString(messageResId, settings.contentLanguage)
    }
}
