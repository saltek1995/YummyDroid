package me.yummydroid.app.data

internal fun Throwable.isUnauthorizedApiError(): Boolean {
    return this is ApiHttpException && statusCode in UNAUTHORIZED_STATUS_CODES
}

private val UNAUTHORIZED_STATUS_CODES = setOf(401, 403)
