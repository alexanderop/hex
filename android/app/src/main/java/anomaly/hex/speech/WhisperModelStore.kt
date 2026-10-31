package anomaly.hex.speech

import android.content.Context
import anomaly.hex.logic.WhisperModelSpec
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class WhisperModelStore(context: Context) {
    private val dir = File(context.applicationContext.filesDir, "models").apply { mkdirs() }

    fun file(model: WhisperModelSpec): File = File(dir, model.fileName)

    fun isReady(model: WhisperModelSpec): Boolean {
        val candidate = file(model)
        return candidate.isFile && candidate.length() >= WhisperModelSpec.MIN_BYTES
    }

    suspend fun download(model: WhisperModelSpec, onProgress: (Float) -> Unit) = withContext(Dispatchers.IO) {
        val dest = file(model)
        val partial = File(dir, model.fileName + ".partial")
        if (partial.exists()) partial.delete()
        val connection = (URI(model.url).toURL().openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 20_000
            readTimeout = 60_000
            setRequestProperty("User-Agent", "Hex")
        }
        try {
            connection.connect()
            if (connection.responseCode !in 200..299) {
                error("Model download failed (${connection.responseCode}).")
            }
            val total = connection.contentLengthLong
            var readTotal = 0L
            var lastPercent = -1
            connection.inputStream.use { input ->
                partial.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        readTotal += count
                        val percent = if (total > 0) ((readTotal * 100) / total).toInt() else -1
                        if (percent != lastPercent) {
                            lastPercent = percent
                            val fraction = if (total > 0) readTotal.toFloat() / total.toFloat() else -1f
                            withContext(Dispatchers.Main) { onProgress(fraction) }
                        }
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
        if (partial.length() < WhisperModelSpec.MIN_BYTES) {
            partial.delete()
            error("Model download was incomplete.")
        }
        if (dest.exists() && !dest.delete()) error("Couldn't replace the offline model.")
        if (!partial.renameTo(dest)) {
            partial.copyTo(dest, overwrite = true)
            partial.delete()
        }
    }
}
