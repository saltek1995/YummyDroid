package me.yummyani.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class PipActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_TOGGLE_PLAY_PAUSE) {
            PlayerPipController.togglePlayPause()
        }
    }

    companion object {
        const val ACTION_TOGGLE_PLAY_PAUSE = "me.yummyani.app.action.TOGGLE_PLAY_PAUSE"
    }
}
