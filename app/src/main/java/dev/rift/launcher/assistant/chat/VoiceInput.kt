package dev.rift.launcher.assistant.chat

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import java.util.Locale
import timber.log.Timber

/**
 * Speech to text with Android's own recognizer, on the device where it can (the sound never goes to the AI provider).
 * Hold to talk: [start] when the button goes down, [stop] when it comes up, and the text arrives in [onText].
 * If the on-device recognizer has no model for the language, the regular one is used from then on.
 */
class VoiceInput internal constructor(private val context: Context) {

    enum class Problem { Unavailable, NotHeard, Failed }

    var listening by mutableStateOf(false)
        private set

    /** What has been recognized so far while talking. */
    var partial by mutableStateOf("")
        private set

    private var recognizer: SpeechRecognizer? = null
    private var onDevice = true
    private var holding = false
    private var onText: (String) -> Unit = {}
    private var onProblem: (Problem) -> Unit = {}

    val available: Boolean
        get() = SpeechRecognizer.isRecognitionAvailable(context) ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && SpeechRecognizer.isOnDeviceRecognitionAvailable(context))

    fun start(onText: (String) -> Unit, onProblem: (Problem) -> Unit) {
        this.onText = onText
        this.onProblem = onProblem
        holding = true
        partial = ""
        listen()
    }

    /** Stops listening; what was said so far still arrives in onText. */
    fun stop() {
        holding = false
        recognizer?.stopListening()
    }

    fun release() {
        holding = false
        recognizer?.destroy()
        recognizer = null
        listening = false
    }

    private fun listen() {
        recognizer?.destroy()
        val created = create() ?: run {
            listening = false
            onProblem(Problem.Unavailable)
            return
        }
        recognizer = created
        created.setRecognitionListener(listener)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            // Holding the button means "still talking"; don't cut off at a short pause.
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 4000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 4000L)
        }
        listening = true
        runCatching { created.startListening(intent) }.onFailure {
            Timber.w(it, "VoiceInput: start failed")
            listening = false
            onProblem(Problem.Failed)
        }
    }

    private fun create(): SpeechRecognizer? {
        if (onDevice && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
            return SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        }
        onDevice = false
        return if (SpeechRecognizer.isRecognitionAvailable(context)) SpeechRecognizer.createSpeechRecognizer(context) else null
    }

    private val listener = object : RecognitionListener {
        override fun onPartialResults(partialResults: Bundle?) {
            partialResults.firstText()?.let { partial = it }
        }

        override fun onResults(results: Bundle?) {
            listening = false
            holding = false
            val text = results.firstText().orEmpty().ifBlank { partial }
            partial = ""
            if (text.isBlank()) onProblem(Problem.NotHeard) else onText(text)
        }

        override fun onError(error: Int) {
            listening = false
            if (onDevice && error in LANGUAGE_ERRORS) {
                // No on-device model for this language: the regular recognizer from now on.
                onDevice = false
                if (holding) listen()
                return
            }
            holding = false
            val heard = partial
            partial = ""
            when {
                heard.isNotBlank() -> onText(heard)
                error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> onProblem(Problem.NotHeard)
                else -> {
                    Timber.w("VoiceInput: recognizer error $error")
                    onProblem(Problem.Failed)
                }
            }
        }

        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    private fun Bundle?.firstText(): String? =
        this?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }

    private companion object {
        /** ERROR_LANGUAGE_NOT_SUPPORTED and ERROR_LANGUAGE_UNAVAILABLE (API 31). */
        val LANGUAGE_ERRORS = setOf(12, 13)
    }
}

@Composable
fun rememberVoiceInput(): VoiceInput {
    val context = LocalContext.current
    val voice = remember { VoiceInput(context) }
    DisposableEffect(voice) { onDispose { voice.release() } }
    return voice
}
