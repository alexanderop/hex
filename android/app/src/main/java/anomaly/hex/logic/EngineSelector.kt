package anomaly.hex.logic

enum class EnginePreference(val label: String) {
    AUTO("Auto"),
    SYSTEM("Android speech"),
    WHISPER("Offline model"),
    ;

    companion object {
        fun byId(id: String?): EnginePreference =
            entries.firstOrNull { it.name.equals(id, ignoreCase = true) } ?: AUTO
    }
}

enum class EngineChoice {
    ON_DEVICE,
    SYSTEM,
    WHISPER,
    NONE,
}

data class EngineSnapshot(
    val preference: EnginePreference,
    val systemAvailable: Boolean,
    val onDeviceAvailable: Boolean,
    val whisperReady: Boolean,
    val systemFailed: Boolean = false,
)

object SpeechErrors {
    const val NETWORK_TIMEOUT = 1
    const val NETWORK = 2
    const val AUDIO = 3
    const val SERVER = 4
    const val CLIENT = 5
    const val SPEECH_TIMEOUT = 6
    const val NO_MATCH = 7
    const val RECOGNIZER_BUSY = 8
    const val INSUFFICIENT_PERMISSIONS = 9
    const val TOO_MANY_REQUESTS = 10
    const val SERVER_DISCONNECTED = 11
    const val LANGUAGE_NOT_SUPPORTED = 12
    const val LANGUAGE_UNAVAILABLE = 13

    fun shouldFallBack(code: Int): Boolean = when (code) {
        SPEECH_TIMEOUT, NO_MATCH, INSUFFICIENT_PERMISSIONS -> false
        else -> true
    }

    fun message(code: Int): String = when (code) {
        NETWORK, NETWORK_TIMEOUT -> "Network speech recognition failed."
        AUDIO -> "The microphone couldn't be read."
        INSUFFICIENT_PERMISSIONS -> "Microphone permission is off."
        LANGUAGE_NOT_SUPPORTED, LANGUAGE_UNAVAILABLE -> "That language isn't available for Android speech."
        NO_MATCH, SPEECH_TIMEOUT -> "No speech detected."
        RECOGNIZER_BUSY, TOO_MANY_REQUESTS -> "Speech recognition is busy."
        SERVER, SERVER_DISCONNECTED, CLIENT -> "Android speech recognition failed."
        else -> "Android speech recognition failed."
    }
}

object CapturePolicy {
    const val MIN_HOLD_MS = 300L
    const val MAX_WHISPER_MS = 60_000L

    fun shouldTranscribe(durationMs: Long, cancelled: Boolean): Boolean =
        !cancelled && durationMs >= MIN_HOLD_MS
}

object EngineSelector {
    fun select(snapshot: EngineSnapshot): EngineChoice {
        val system = systemChoice(snapshot)
        return when (snapshot.preference) {
            EnginePreference.WHISPER ->
                if (snapshot.whisperReady) EngineChoice.WHISPER else system ?: EngineChoice.NONE
            EnginePreference.SYSTEM ->
                system ?: if (snapshot.whisperReady) EngineChoice.WHISPER else EngineChoice.NONE
            EnginePreference.AUTO -> when {
                system != null && !snapshot.systemFailed -> system
                snapshot.whisperReady -> EngineChoice.WHISPER
                else -> system ?: EngineChoice.NONE
            }
        }
    }

    fun fallback(current: EngineChoice, snapshot: EngineSnapshot, errorCode: Int): EngineChoice? {
        if (!SpeechErrors.shouldFallBack(errorCode)) return null
        return when (current) {
            EngineChoice.ON_DEVICE ->
                if (snapshot.systemAvailable) EngineChoice.SYSTEM else whisperOrNull(snapshot)
            EngineChoice.SYSTEM -> whisperOrNull(snapshot)
            EngineChoice.WHISPER, EngineChoice.NONE -> null
        }
    }

    private fun whisperOrNull(snapshot: EngineSnapshot): EngineChoice? =
        if (snapshot.whisperReady && snapshot.preference != EnginePreference.SYSTEM) {
            EngineChoice.WHISPER
        } else {
            null
        }

    private fun systemChoice(snapshot: EngineSnapshot): EngineChoice? = when {
        snapshot.onDeviceAvailable -> EngineChoice.ON_DEVICE
        snapshot.systemAvailable -> EngineChoice.SYSTEM
        else -> null
    }
}
