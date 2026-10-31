package anomaly.hex.speech

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import anomaly.hex.logic.CapturePolicy
import anomaly.hex.logic.SpeechErrors
import anomaly.hex.logic.WhisperModelSpec
import java.io.File
import kotlin.math.sqrt

internal interface CaptureCallbacks {
    fun onPartial(text: String)
    fun onLevel(unit: Float)
    fun onFinal(raw: String)
    fun onError(code: Int?, message: String)
    fun onTooShort()
    fun onAutoFinish()
}

internal interface ActiveCapture {
    fun start()
    fun stop()
    fun cancel()
}

internal class SystemCapture(
    private val context: Context,
    private val onDevice: Boolean,
    private val languageTag: String,
    private val main: Handler,
    private val callbacks: CaptureCallbacks,
) : ActiveCapture {
    private var recognizer: SpeechRecognizer? = null
    private var cancelled = false

    @SuppressLint("MissingPermission")
    override fun start() {
        val created = createRecognizer()
        if (created == null) {
            callbacks.onError(SpeechErrors.CLIENT, "Speech recognition isn't available on this device.")
            return
        }
        recognizer = created
        created.setRecognitionListener(listener)
        try {
            created.startListening(intent())
        } catch (_: SecurityException) {
            callbacks.onError(
                SpeechErrors.INSUFFICIENT_PERMISSIONS,
                SpeechErrors.message(SpeechErrors.INSUFFICIENT_PERMISSIONS),
            )
        } catch (error: RuntimeException) {
            callbacks.onError(SpeechErrors.CLIENT, error.message ?: SpeechErrors.message(SpeechErrors.CLIENT))
        }
    }

    override fun stop() {
        try {
            recognizer?.stopListening()
        } catch (error: RuntimeException) {
            callbacks.onError(SpeechErrors.CLIENT, error.message ?: SpeechErrors.message(SpeechErrors.CLIENT))
        }
    }

    override fun cancel() {
        cancelled = true
        val current = recognizer
        recognizer = null
        try {
            current?.cancel()
        } catch (_: RuntimeException) {
        }
        main.post {
            try {
                current?.destroy()
            } catch (_: RuntimeException) {
            }
        }
    }

    private fun createRecognizer(): SpeechRecognizer? {
        if (onDevice && Build.VERSION.SDK_INT >= 31) {
            try {
                return SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            } catch (_: RuntimeException) {
                return null
            }
        }
        if (!SpeechRecognizer.isRecognitionAvailable(context)) return null
        return SpeechRecognizer.createSpeechRecognizer(context)
    }

    private fun intent(): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onEndOfSpeech() = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit

        override fun onRmsChanged(rmsdB: Float) {
            if (!cancelled) callbacks.onLevel((rmsdB / 10f).coerceIn(0f, 1f))
        }

        override fun onPartialResults(partialResults: Bundle?) {
            if (cancelled) return
            val text = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                .orEmpty()
            if (text.isNotEmpty()) callbacks.onPartial(text)
        }

        override fun onResults(results: Bundle?) {
            if (cancelled) return
            destroyRecognizer()
            val text = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                .orEmpty()
            callbacks.onFinal(text)
        }

        override fun onError(error: Int) {
            if (cancelled) return
            destroyRecognizer()
            callbacks.onError(error, SpeechErrors.message(error))
        }
    }

    private fun destroyRecognizer() {
        val current = recognizer
        recognizer = null
        main.post {
            try {
                current?.destroy()
            } catch (_: RuntimeException) {
            }
        }
    }
}

internal class WhisperCapture(
    private val context: Context,
    private val modelFile: File,
    private val whisperCode: String,
    private val main: Handler,
    private val callbacks: CaptureCallbacks,
) : ActiveCapture {
    private val samples = ArrayList<Float>(16_000 * 4)
    private var record: AudioRecord? = null
    private var thread: Thread? = null

    @Volatile private var running = false
    @Volatile private var cancelled = false
    private var startedAt = 0L

    @SuppressLint("MissingPermission")
    override fun start() {
        if (!modelFile.isFile || modelFile.length() < WhisperModelSpec.MIN_BYTES) {
            callbacks.onError(null, "The offline model isn't downloaded.")
            return
        }
        if (!WhisperNative.available()) {
            callbacks.onError(null, "This build does not include whisper.cpp.")
            return
        }
        val min = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (min <= 0) {
            callbacks.onError(SpeechErrors.AUDIO, SpeechErrors.message(SpeechErrors.AUDIO))
            return
        }
        val bufferBytes = min.coerceAtLeast(3_200)
        val created = try {
            AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                        .build(),
                )
                .setBufferSizeInBytes(bufferBytes)
                .build()
        } catch (_: SecurityException) {
            callbacks.onError(
                SpeechErrors.INSUFFICIENT_PERMISSIONS,
                SpeechErrors.message(SpeechErrors.INSUFFICIENT_PERMISSIONS),
            )
            return
        } catch (_: IllegalArgumentException) {
            callbacks.onError(SpeechErrors.AUDIO, SpeechErrors.message(SpeechErrors.AUDIO))
            return
        }
        if (created.state != AudioRecord.STATE_INITIALIZED) {
            created.release()
            callbacks.onError(SpeechErrors.AUDIO, SpeechErrors.message(SpeechErrors.AUDIO))
            return
        }
        record = created
        startedAt = SystemClock.elapsedRealtime()
        running = true
        try {
            created.startRecording()
        } catch (_: IllegalStateException) {
            releaseRecord()
            callbacks.onError(SpeechErrors.AUDIO, SpeechErrors.message(SpeechErrors.AUDIO))
            return
        }
        val shortBuffer = ShortArray(bufferBytes / 2)
        thread = Thread({
            readLoop(created, shortBuffer)
        }, "hex-whisper-mic").also { it.start() }
    }

    override fun stop() {
        val elapsed = SystemClock.elapsedRealtime() - startedAt
        running = false
        try {
            record?.stop()
        } catch (_: IllegalStateException) {
        }
        joinReader()
        releaseRecord()
        if (cancelled) return
        if (!CapturePolicy.shouldTranscribe(elapsed, cancelled = false)) {
            callbacks.onTooShort()
            return
        }
        val audio = synchronized(samples) { samples.toFloatArray() }
        synchronized(samples) { samples.clear() }
        Thread({
            try {
                val text = WhisperNative.transcribe(modelFile.absolutePath, audio, whisperCode)
                main.post { if (!cancelled) callbacks.onFinal(text) }
            } catch (error: RuntimeException) {
                main.post {
                    if (!cancelled) callbacks.onError(null, error.message ?: "Offline transcription failed.")
                }
            }
        }, "hex-whisper").start()
    }

    override fun cancel() {
        cancelled = true
        running = false
        try {
            record?.stop()
        } catch (_: IllegalStateException) {
        }
        joinReader()
        releaseRecord()
    }

    private fun readLoop(created: AudioRecord, buffer: ShortArray) {
        while (running) {
            val count = created.read(buffer, 0, buffer.size)
            if (count <= 0) continue
            var energy = 0.0
            synchronized(samples) {
                for (index in 0 until count) {
                    val sample = buffer[index] / 32768.0f
                    samples.add(sample)
                    energy += sample * sample
                }
            }
            val rms = sqrt(energy / count).toFloat()
            val unit = ((rms - 0.01f) / 0.15f).coerceIn(0f, 1f)
            main.post { if (!cancelled) callbacks.onLevel(unit) }
            val size = synchronized(samples) { samples.size }
            if (size >= SAMPLE_RATE * (CapturePolicy.MAX_WHISPER_MS / 1000)) {
                main.post { if (!cancelled && running) callbacks.onAutoFinish() }
                break
            }
        }
    }

    private fun joinReader() {
        try {
            thread?.join(2_000)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        thread = null
    }

    private fun releaseRecord() {
        try {
            record?.release()
        } catch (_: RuntimeException) {
        }
        record = null
    }

    private companion object {
        const val SAMPLE_RATE = 16_000
    }
}
