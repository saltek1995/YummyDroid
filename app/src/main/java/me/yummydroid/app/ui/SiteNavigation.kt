package me.yummydroid.app.ui

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.net.toUri
import me.yummydroid.app.data.DEFAULT_SITE_BASE_URL
import me.yummydroid.app.data.UserProfile
import me.yummydroid.app.R

internal fun UserProfile.siteProfileUrl(siteBaseUrl: String): String {
    val base = siteBaseUrl.trim().ifBlank { DEFAULT_SITE_BASE_URL }.trimEnd('/')
    return "$base/users/id$id"
}

internal fun sitePageUrl(siteBaseUrl: String, path: String): String {
    val base = siteBaseUrl.trim().ifBlank { DEFAULT_SITE_BASE_URL }.trimEnd('/')
    return "$base/" + path.trim().trimStart('/')
}

internal fun Context.openUrl(url: String) {
    val normalized = url.trim()
    if (normalized.isBlank()) return
    runCatching {
        startActivity(Intent(Intent.ACTION_VIEW, normalized.toUri()))
    }.onFailure {
        Toast.makeText(this, getString(R.string.ui_could_not_open_the_site), Toast.LENGTH_SHORT).show()
    }
}
