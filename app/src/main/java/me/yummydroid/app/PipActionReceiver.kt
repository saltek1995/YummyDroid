package me.yummydroid.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class PipActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_TOGGLE_PLAY_PAUSE -> PlayerPipController.togglePlayPause()
            ACTION_PREVIOUS_EPISODE -> PlayerPipController.playPreviousEpisode()
            ACTION_NEXT_EPISODE -> PlayerPipController.playNextEpisode()
        }
    }

    companion object {
        const val ACTION_TOGGLE_PLAY_PAUSE = "me.yummydroid.app.action.TOGGLE_PLAY_PAUSE"
        const val ACTION_PREVIOUS_EPISODE = "me.yummydroid.app.action.PREVIOUS_EPISODE"
        const val ACTION_NEXT_EPISODE = "me.yummydroid.app.action.NEXT_EPISODE"
    }
}
