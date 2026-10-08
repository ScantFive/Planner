package com.scantfive.planner.quickadd

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.scantfive.planner.R

/** Системное окно распознавания: русский язык, по возможности офлайн. */
private fun recognizeIntent(context: Context): Intent =
    Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
        .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        .putExtra(RecognizerIntent.EXTRA_PROMPT, context.getString(R.string.speech_prompt))

fun isSpeechAvailable(context: Context): Boolean =
    recognizeIntent(context).resolveActivity(context.packageManager) != null

class SpeechLauncher internal constructor(
    private val context: Context,
    private val launcher: ManagedActivityResultLauncher<Intent, ActivityResult>,
) {
    fun launch() {
        try {
            launcher.launch(recognizeIntent(context))
        } catch (_: ActivityNotFoundException) {
            // Распознаватель удалили, пока окно было открыто: остаётся ввод текстом.
        }
    }
}

/** [onResult] получает лучший вариант распознанного текста. */
@Composable
fun rememberSpeechLauncher(onResult: (String) -> Unit): SpeechLauncher {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let(onResult)
        }
    }
    return remember(launcher) { SpeechLauncher(context, launcher) }
}
