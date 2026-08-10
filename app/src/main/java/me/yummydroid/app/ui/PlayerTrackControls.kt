package me.yummydroid.app.ui

import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.media3.ui.PlayerView
import me.yummydroid.app.R

internal fun PlayerView.bindPlayerQualityControl(binding: PlayerControllerBinding) {
    findViewById<TextView>(R.id.yummy_player_quality)?.apply {
        val qualityTitle = binding.qualityOptions.selectedQualityControlText(binding.selectedQualityKey)
        applyPlayerQualityControl(qualityTitle, "${binding.texts.quality}: $qualityTitle")
        visibility = View.VISIBLE
        setPlayerControlEnabled(binding.qualityOptions.isNotEmpty())
        setOnClickListener {
            if (binding.qualityOptions.isEmpty()) return@setOnClickListener
            showPlayerControls()
            showQualityPopup(
                anchor = this,
                player = binding.player,
                options = binding.qualityOptions,
                selectedQualityKey = binding.selectedQualityKey,
                onSelectedQualityKeyChange = binding.onSelectedQualityKeyChange,
                onSelectLocalQuality = binding.onSelectLocalQuality,
                onSelectPreferredQuality = binding.onSelectPreferredQuality,
                onRememberPlayerControlFocus = binding.onRememberPlayerControlFocus,
            )
        }
    }
}

internal fun PlayerView.bindPlayerSubtitleControl(binding: PlayerControllerBinding) {
    findViewById<ImageButton>(R.id.yummy_player_subtitles)?.apply {
        val label = if (binding.subtitlesLoading && binding.subtitleOptions.isEmpty()) {
            "${binding.texts.subtitles}..."
        } else {
            binding.texts.subtitles
        }
        applyPlayerIconControl(
            iconResId = R.drawable.ic_player_subtitles,
            label = label,
            active = binding.selectedSubtitleKey != SUBTITLE_OFF_KEY && binding.subtitleOptions.isNotEmpty(),
        )
        visibility = View.VISIBLE
        setPlayerControlEnabled(binding.subtitleOptions.isNotEmpty())
        setOnClickListener {
            if (binding.subtitleOptions.isEmpty()) return@setOnClickListener
            showPlayerControls()
            showSubtitlePopup(
                anchor = this,
                player = binding.player,
                options = binding.subtitleOptions,
                selectedSubtitleKey = binding.selectedSubtitleKey,
                texts = binding.texts,
                onRememberPlayerControlFocus = binding.onRememberPlayerControlFocus,
                onSelectedSubtitleKeyChange = binding.onSelectedSubtitleKeyChange,
            )
        }
    }
}
