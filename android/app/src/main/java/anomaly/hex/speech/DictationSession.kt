package anomaly.hex.speech

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import anomaly.hex.data.HexSettings
import anomaly.hex.logic.DictationPhase
import anomaly.hex.logic.EngineChoice
import anomaly.hex.logic.EnginePreference
import anomaly.hex.logic.EngineSelector
import anomaly.hex.logic.EngineSnapshot
import anomaly.hex.logic.SpeechErrors
import kotlinx.coroutines.flow.MutableStateFlow

class DictationSession(
    context: Context,
    private val settings: HexSettings,
    private val models: WhisperModelStore,
) {
    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())

    val phase = MutableStateFlow(DictationPhase.Idle)
    val partial = MutableStateFlow("")
    val status = MutableStateFlow("")
    val elapsedMs = MutableStateFlow(0L)
    val level = MutableStateFlow(0f)
    val routeLabel = MutableStateFlow("")

    var onFinalText: ((String) -> Unit)? = null

    private var token = 0
    private var systemFailed = false
    private var holdingMic = false
    private var startedAt = 0L
    private var active: ActiveCapture? = null

    init {
        WhisperNative.acquire()
    }

    fun microphoneGranted(): Boolean = ContextCompat.checkSelfPermission(
        appContext,
        Manifest.permission.RECORD_AUDIO,
    ) == PackageManager.PERMISSION_GRANTED

    fun start() {
        if (phase.value == DictationPhase.Listening || phase.value == DictationPhase.Transcribing) return
        if (!microphoneGranted()) {
            fail("Allow microphone access.", release = false)
            return
        }
        if (!MicLock.tryAcquire()) {
            fail("Microphone is in use.", release = false)
            return
        }
        holdingMic = true
        token += 1
        partial.value = ""
        level.value = 0f
        val snapshot = snapshot()
        val choice = EngineSelector.select(snapshot)
        if (choice == EngineChoice.NONE) {
            val message = if (snapshot.preference == EnginePreference.WHISPER || !snapshot.systemAvailable) {
                "Download the offline model to dictate on this device."
            } else {
                "Speech recognition isn't available on this device."
            }
            fail(message)
            return
        }
        begin(choice, token, snapshot, announce = true)
    }

    fun finish() {
        val capture = active ?: return
        if (phase.value != DictationPhase.Listening) return
        phase.value = DictationPhase.Transcribing
        status.value = "Transcribing…"
        main.removeCallbacks(tick)
        capture.stop()
    }

    fun cancel() {
        token += 1
        stopActive()
        main.removeCallbacks(tick)
        partial.value = ""
        level.value = 0f
        elapsedMs.value = 0L
        routeLabel.value = ""
        status.value = ""
        phase.value = DictationPhase.Idle
        releaseMic()
    }

    fun close() {
        cancel()
        WhisperNative.release()
    }

    private fun begin(choice: EngineChoice, run: Int, snapshot: EngineSnapshot, announce: Boolean) {
        stopActive()
        if (run != token) return
        startedAt = SystemClock.elapsedRealtime()
        elapsedMs.value = 0L
        routeLabel.value = when (choice) {
            EngineChoice.ON_DEVICE -> "On device"
            EngineChoice.SYSTEM -> "Android speech"
            EngineChoice.WHISPER -> "Offline model"
            EngineChoice.NONE -> ""
        }
        if (announce) status.value = ""
        val language = settings.language()
        val callbacks = object : CaptureCallbacks {
            override fun onPartial(text: String) {
                if (run != token) return
                partial.value = text
            }

            override fun onLevel(unit: Float) {
                if (run != token) return
                level.value = unit
            }

            override fun onFinal(raw: String) {
                if (run != token) return
                active = null
                main.removeCallbacks(tick)
                partial.value = ""
                level.value = 0f
                val text = raw.trim()
                if (text.isEmpty()) {
                    fail("No speech detected.")
                    return
                }
                if (choice == EngineChoice.ON_DEVICE || choice == EngineChoice.SYSTEM) {
                    systemFailed = false
                }
                routeLabel.value = ""
                status.value = ""
                phase.value = DictationPhase.Idle
                releaseMic()
                onFinalText?.invoke(text)
            }

            override fun onError(code: Int?, message: String) {
                if (run != token) return
                active = null
                val next = if (code != null) EngineSelector.fallback(choice, snapshot, code) else null
                if (next != null) {
                    if (next == EngineChoice.WHISPER) systemFailed = true
                    status.value = when (next) {
                        EngineChoice.SYSTEM -> "On-device speech failed. Trying Android speech."
                        EngineChoice.WHISPER -> "Android speech failed. Listening with the offline model."
                        else -> message
                    }
                    begin(next, run, snapshot, announce = false)
                    return
                }
                val needsModel = code != null &&
                    SpeechErrors.shouldFallBack(code) &&
                    !snapshot.whisperReady &&
                    snapshot.preference != EnginePreference.SYSTEM
                fail(if (needsModel) "$message Download the offline model to fall back." else message)
            }

            override fun onTooShort() {
                if (run != token) return
                active = null
                fail("Too short.")
            }

            override fun onAutoFinish() {
                if (run != token) return
                finish()
            }
        }
        val capture: ActiveCapture = when (choice) {
            EngineChoice.ON_DEVICE, EngineChoice.SYSTEM -> SystemCapture(
                appContext,
                onDevice = choice == EngineChoice.ON_DEVICE,
                languageTag = language.tag,
                main = main,
                callbacks = callbacks,
            )
            EngineChoice.WHISPER -> WhisperCapture(
                appContext,
                modelFile = models.file(settings.model()),
                whisperCode = language.whisperCode,
                main = main,
                callbacks = callbacks,
            )
            EngineChoice.NONE -> {
                fail("Speech recognition isn't available on this device.")
                return
            }
        }
        active = capture
        phase.value = DictationPhase.Listening
        main.removeCallbacks(tick)
        main.post(tick)
        capture.start()
    }

    private fun snapshot(): EngineSnapshot {
        val onDevice = if (Build.VERSION.SDK_INT >= 31) {
            try {
                SpeechRecognizer.isOnDeviceRecognitionAvailable(appContext)
            } catch (_: RuntimeException) {
                false
            }
        } else {
            false
        }
        val system = try {
            SpeechRecognizer.isRecognitionAvailable(appContext)
        } catch (_: RuntimeException) {
            false
        }
        val model = settings.model()
        return EngineSnapshot(
            preference = settings.preference(),
            systemAvailable = system,
            onDeviceAvailable = onDevice,
            whisperReady = models.isReady(model) && WhisperNative.available(),
            systemFailed = systemFailed,
        )
    }

    private fun fail(message: String, release: Boolean = true) {
        stopActive()
        main.removeCallbacks(tick)
        partial.value = ""
        level.value = 0f
        routeLabel.value = ""
        status.value = message
        phase.value = DictationPhase.Failed
        if (release) releaseMic()
    }

    private fun stopActive() {
        val capture = active
        active = null
        capture?.cancel()
    }

    private fun releaseMic() {
        if (!holdingMic) return
        holdingMic = false
        MicLock.release()
    }

    private val tick = object : Runnable {
        override fun run() {
            if (phase.value == DictationPhase.Listening) {
                elapsedMs.value = SystemClock.elapsedRealtime() - startedAt
                main.postDelayed(this, 200)
            }
        }
    }
}
