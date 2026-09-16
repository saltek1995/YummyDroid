package me.yummydroid.app.data

import java.io.IOException

internal class SourceHttpRestricted(val statusCode: Int) : IOException("Source returned HTTP $statusCode")

/** Once restricted, stop the rest of this discovery session, including WebView subrequests. */
internal class PlaybackResolveRequestPolicy : HttpRequestPolicy() {
    @Volatile private var restriction: SourceHttpRestricted? = null

    override fun beforeRequest() {
        restriction?.let { throw it }
    }

    override fun onResponse(statusCode: Int) {
        if (statusCode == 403 || statusCode == 429) {
            val failure = SourceHttpRestricted(statusCode)
            restriction = failure
            throw failure
        }
    }
}
