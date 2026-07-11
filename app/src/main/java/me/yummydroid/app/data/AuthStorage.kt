package me.yummydroid.app.data

import android.content.Context

class AuthStorage(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun readToken(): String? {
        return prefs.getString(KEY_TOKEN, null)?.takeIf { it.isNotBlank() }
    }

    fun readProfile(): UserProfile? {
        val id = prefs.getLong(KEY_PROFILE_ID, 0L).takeIf { it > 0L } ?: return null
        val nickname = prefs.getString(KEY_PROFILE_NICKNAME, null)?.takeIf { it.isNotBlank() } ?: return null
        val roles = prefs.getString(KEY_PROFILE_ROLES, "").orEmpty()
            .split(ROLES_SEPARATOR)
            .filter { it.isNotBlank() }

        return UserProfile(
            id = id,
            nickname = nickname,
            avatarUrl = prefs.getString(KEY_PROFILE_AVATAR, "").orEmpty(),
            about = prefs.getString(KEY_PROFILE_ABOUT, "").orEmpty(),
            banned = prefs.getBoolean(KEY_PROFILE_BANNED, false),
            roles = roles,
            unreadNotifications = prefs.getInt(KEY_PROFILE_NOTIFICATIONS, 0),
            unreadMessages = prefs.getInt(KEY_PROFILE_MESSAGES, 0),
        )
    }

    fun saveToken(token: String) {
        prefs.edit().putString(KEY_TOKEN, token).apply()
    }

    fun saveProfile(profile: UserProfile) {
        prefs.edit()
            .putLong(KEY_PROFILE_ID, profile.id)
            .putString(KEY_PROFILE_NICKNAME, profile.nickname)
            .putString(KEY_PROFILE_AVATAR, profile.avatarUrl)
            .putString(KEY_PROFILE_ABOUT, profile.about)
            .putBoolean(KEY_PROFILE_BANNED, profile.banned)
            .putString(KEY_PROFILE_ROLES, profile.roles.joinToString(ROLES_SEPARATOR))
            .putInt(KEY_PROFILE_NOTIFICATIONS, profile.unreadNotifications)
            .putInt(KEY_PROFILE_MESSAGES, profile.unreadMessages)
            .apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val PREFS_NAME = "yummydroid_auth"
        const val KEY_TOKEN = "access_token"
        const val KEY_PROFILE_ID = "profile_id"
        const val KEY_PROFILE_NICKNAME = "profile_nickname"
        const val KEY_PROFILE_AVATAR = "profile_avatar"
        const val KEY_PROFILE_ABOUT = "profile_about"
        const val KEY_PROFILE_BANNED = "profile_banned"
        const val KEY_PROFILE_ROLES = "profile_roles"
        const val KEY_PROFILE_NOTIFICATIONS = "profile_notifications"
        const val KEY_PROFILE_MESSAGES = "profile_messages"
        const val ROLES_SEPARATOR = "|"
    }
}
