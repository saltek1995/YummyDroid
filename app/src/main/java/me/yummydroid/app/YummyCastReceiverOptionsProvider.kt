package me.yummydroid.app

import android.content.Context
import com.google.android.gms.cast.tv.CastReceiverOptions
import com.google.android.gms.cast.tv.ReceiverOptionsProvider

class YummyCastReceiverOptionsProvider : ReceiverOptionsProvider {
    override fun getOptions(context: Context): CastReceiverOptions {
        return CastReceiverOptions.Builder(context)
            .setStatusText(context.getString(R.string.app_name))
            .apply {
                BuildConfig.CAST_RECEIVER_APP_ID.trim()
                    .takeIf(String::isNotBlank)
                    ?.let(::setCastAppId)
            }
            .build()
    }
}
