package anomaly.hex.speech

internal object WhisperNative {
    private val lock = Any()
    private var users = 0
    private var handle = 0L
    private var loadedPath: String? = null

    private val loaded: Boolean = try {
        System.loadLibrary("hexwhisper")
        true
    } catch (_: UnsatisfiedLinkError) {
        false
    }

    fun available(): Boolean {
        if (!loaded) return false
        return try {
            WhisperLib.nativeAvailable()
        } catch (_: UnsatisfiedLinkError) {
            false
        }
    }

    fun acquire() {
        synchronized(lock) { users += 1 }
    }

    fun release() {
        synchronized(lock) {
            users = (users - 1).coerceAtLeast(0)
            if (users == 0 && handle != 0L) {
                WhisperLib.nativeFree(handle)
                handle = 0L
                loadedPath = null
            }
        }
    }

    fun transcribe(path: String, samples: FloatArray, language: String): String {
        if (!available()) error("whisper.cpp is not linked in this build.")
        if (samples.isEmpty()) return ""
        synchronized(lock) {
            if (handle == 0L || loadedPath != path) {
                if (handle != 0L) WhisperLib.nativeFree(handle)
                handle = WhisperLib.nativeInit(path)
                loadedPath = path
                if (handle == 0L) error("Couldn't load the offline model.")
            }
            val threads = Runtime.getRuntime().availableProcessors().coerceIn(2, 4)
            return WhisperLib.nativeTranscribe(handle, samples, samples.size, language, threads)
                ?: error("Offline transcription failed.")
        }
    }
}
