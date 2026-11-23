package com.openjarvis.android.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SpeechRecognitionState(
    val isListening: Boolean = false,
    val partialText: String = "",
    val rmsDb: Float = 0f,
    val errorMessage: String? = null,
    val isAvailable: Boolean = true,
)

@Singleton
class SpeechRecognizerManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private var recognizer: SpeechRecognizer? = null

    private val _state = MutableStateFlow(
        SpeechRecognitionState(isAvailable = SpeechRecognizer.isRecognitionAvailable(context))
    )
    val state: StateFlow<SpeechRecognitionState> = _state.asStateFlow()

    fun hasAudioPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED

    fun startListening(onFinalResult: (String) -> Unit) {
        if (!hasAudioPermission()) {
            _state.value = _state.value.copy(
                isListening = false,
                errorMessage = "Microphone permission is required for speech recognition.",
            )
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            _state.value = _state.value.copy(
                isAvailable = false,
                isListening = false,
                errorMessage = "Speech recognition service is not available on this device.",
            )
            return
        }

        stopListening()
        val sr = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = sr

        sr.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                _state.value = _state.value.copy(isListening = true, errorMessage = null, partialText = "")
            }

            override fun onBeginningOfSpeech() {}

            override fun onRmsChanged(rmsdB: Float) {
                _state.value = _state.value.copy(rmsDb = rmsdB)
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                _state.value = _state.value.copy(isListening = false, rmsDb = 0f)
            }

            override fun onError(error: Int) {
                val message = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized. Try again."
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected."
                    SpeechRecognizer.ERROR_AUDIO -> "Audio recording error."
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                        "Speech recognizer network error."
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                        "Microphone permission denied."
                    else -> "Speech recognition error ($error)."
                }
                _state.value = _state.value.copy(isListening = false, rmsDb = 0f, errorMessage = message)
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val best = matches?.firstOrNull().orEmpty().trim()
                _state.value = _state.value.copy(isListening = false, partialText = best, rmsDb = 0f)
                if (best.isNotEmpty()) {
                    onFinalResult(best)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val partial = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    .orEmpty()
                _state.value = _state.value.copy(partialText = partial)
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        sr.startListening(intent)
    }

    fun stopListening() {
        runCatching { recognizer?.stopListening() }
        runCatching { recognizer?.destroy() }
        recognizer = null
        _state.value = _state.value.copy(isListening = false, rmsDb = 0f)
    }

    fun clearError() {
        _state.value = _state.value.copy(errorMessage = null)
    }
}
