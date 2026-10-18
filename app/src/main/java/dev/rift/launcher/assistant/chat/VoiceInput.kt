package dev.rift.launcher.assistant.chat

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognitionService
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
 *
 * Three ways, tried in this order and remembered once one fails: the on-device recognizer, the regular one, and the
 * system's own voice input window ([Problem.UseSystemDialog], which the screen opens with [systemDialogIntent]).
 * Android reports an on-device recognizer whenever the firmware names one, even when its app is not installed (the
 * RedMagic tablet names one that isn't there, and every start failed with error 10), so it is used only when its
 * service can actually be found.
 */
class VoiceInput internal constructor(private val context: Context) {

    enum class Problem { Unavailable, NotHeard, Failed, NoPermission, Network, Busy, Language, UseSystemDialog }

    var listening by mutableStateOf(false)
        private set

    /** What has been recognized so far while talking. */
    var partial by mutableStateOf("")
        private set

    private var recognizer: SpeechRecognizer? = null
    private var onDevice = onDeviceServiceInstalled(context)
    private var systemDialog = false
    private var holding = false
    private var onText: (String) -> Unit = {}
    private var onProblem: (Problem) -> Unit = {}

    fun start(onText: (String) -> Unit, onProblem: (Problem) -> Unit) {
        this.onText = onText
        this.onProblem = onProblem
        partial = ""
        if (systemDialog) {
            onProblem(if (hasSystemDialog()) Problem.UseSystemDialog else Problem.Failed)
            return
        }
        holding = true
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

    /** The system's voice input window, for when no recognizer service can be used directly. */
    fun systemDialogIntent(): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
    }

    private fun hasSystemDialog(): Boolean = systemDialogIntent().resolveActivity(context.packageManager) != null

    private fun listen() {
        recognizer?.destroy()
        recognizer = null
        val created = create() ?: run {
            listening = false
            holding = false
            onProblem(fallbackToSystemDialog() ?: Problem.Unavailable)
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
            holding = false
            onProblem(Problem.Failed)
        }
    }

    private fun create(): SpeechRecognizer? {
        if (onDevice && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching { return SpeechRecognizer.createOnDeviceSpeechRecognizer(context) }
                .onFailure { Timber.w(it, "VoiceInput: on-device recognizer can't be created") }
        }
        onDevice = false
        return if (SpeechRecognizer.isRecognitionAvailable(context)) SpeechRecognizer.createSpeechRecognizer(context) else null
    }

    /** From now on the system's voice input window, if there is one. */
    private fun fallbackToSystemDialog(): Problem? {
        if (!hasSystemDialog()) return null
        systemDialog = true
        return Problem.UseSystemDialog
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
            val heard = partial
            if (heard.isBlank() && onDevice && (error in LANGUAGE_ERRORS || error in SERVICE_ERRORS)) {
                // No on-device model for this language, or its service can't be reached: the regular recognizer
                // from now on.
                Timber.w("VoiceInput: on-device recognizer error $error, using the regular one")
                onDevice = false
                if (holding) listen()
                return
            }
            holding = false
            partial = ""
            if (heard.isNotBlank()) {
                onText(heard)
                return
            }
            Timber.w("VoiceInput: recognizer error $error")
            onProblem(
                when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> Problem.NotHeard
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> Problem.NoPermission
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> Problem.Network
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> Problem.Busy
                    in LANGUAGE_ERRORS -> Problem.Language
                    in SERVICE_ERRORS -> fallbackToSystemDialog() ?: Problem.Failed
                    else -> Problem.Failed
                },
            )
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

        /**
         * The service could not be bound or dropped the connection: ERROR_SERVER, ERROR_CLIENT,
         * ERROR_TOO_MANY_REQUESTS (also what Android answers when the service's app is missing) and
         * ERROR_SERVER_DISCONNECTED.
         */
        val SERVICE_ERRORS = setOf(4, 5, 10, 11)

        /**
         * Whether the on-device recognizer the firmware names is really installed;
         * [SpeechRecognizer.isOnDeviceRecognitionAvailable] only checks that a name is configured.
         */
        fun onDeviceServiceInstalled(context: Context): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
            return runCatching {
                val system = Resources.getSystem()
                val id = system.getIdentifier("config_defaultOnDeviceSpeechRecognitionService", "string", "android")
                val component = ComponentName.unflattenFromString(if (id == 0) "" else system.getString(id))
                    ?: return@runCatching false
                context.packageManager
                    .queryIntentServices(Intent(RecognitionService.SERVICE_INTERFACE).setPackage(component.packageName), 0)
                    .any { it.serviceInfo?.name == component.className }
            }.getOrDefault(false)
        }
    }
}

@Composable
fun rememberVoiceInput(): VoiceInput {
    val context = LocalContext.current
    val voice = remember { VoiceInput(context) }
    DisposableEffect(voice) { onDispose { voice.release() } }
    return voice
}
