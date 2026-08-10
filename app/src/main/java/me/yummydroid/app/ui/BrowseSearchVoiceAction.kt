package me.yummydroid.app.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import me.yummydroid.app.data.ContentLanguage

@Composable
internal fun rememberSearchVoiceAction(
    language: ContentLanguage,
    prompt: String,
    unavailableMessage: String,
    onBeforeLaunch: () -> Unit,
    onRecognized: (String) -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val recognizedText = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                ?.trim()
                .orEmpty()
            if (recognizedText.isNotBlank()) {
                onRecognized(recognizedText)
            }
        }
    }
    return {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, language.voiceRecognizerTag())
            putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
        }
        runCatching {
            onBeforeLaunch()
            launcher.launch(intent)
        }.onFailure { throwable ->
            if (throwable is ActivityNotFoundException) {
                Toast.makeText(context, unavailableMessage, Toast.LENGTH_SHORT).show()
            } else {
                throw throwable
            }
        }
        Unit
    }
}
