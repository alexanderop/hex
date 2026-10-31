package anomaly.hex.ui

import android.app.Application
import android.view.inputmethod.InputMethodManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import anomaly.hex.data.HexSettings
import anomaly.hex.logic.TextInsertion
import anomaly.hex.logic.TranscriptFormatter
import anomaly.hex.speech.DictationSession
import anomaly.hex.speech.WhisperModelStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class HomeViewModel(app: Application) : AndroidViewModel(app) {
    val settings = HexSettings(app)
    val models = WhisperModelStore(app)
    val session = DictationSession(app, settings, models)

    var practice by mutableStateOf(TextFieldValue(""))
    var transcript by mutableStateOf("")
    var copied by mutableStateOf(false)
    var micGranted by mutableStateOf(false)
    var keyboardEnabled by mutableStateOf(false)
    var downloadProgress by mutableStateOf<Float?>(null)
    var downloadError by mutableStateOf<String?>(null)

    init {
        session.onFinalText = { raw ->
            val style = settings.style()
            val inserted = TextInsertion.apply(
                practice.text,
                practice.selection.start,
                practice.selection.end,
                raw,
                style,
            )
            practice = TextFieldValue(inserted.text, TextRange(inserted.cursor))
            transcript = TranscriptFormatter.format(raw, style)
            copied = false
        }
    }

    fun refresh() {
        val context = getApplication<Application>()
        micGranted = session.microphoneGranted()
        val imm = context.getSystemService(InputMethodManager::class.java)
        keyboardEnabled = imm?.enabledInputMethodList?.any { it.packageName == context.packageName } == true
    }

    fun modelReady(): Boolean = models.isReady(settings.model())

    fun downloadModel() {
        if (downloadProgress != null) return
        viewModelScope.launch {
            downloadError = null
            downloadProgress = 0f
            try {
                models.download(settings.model()) { fraction -> downloadProgress = fraction }
                downloadProgress = null
            } catch (cancelled: CancellationException) {
                downloadProgress = null
                throw cancelled
            } catch (error: Exception) {
                downloadProgress = null
                downloadError = error.message ?: "Download failed."
            }
        }
    }

    override fun onCleared() {
        session.close()
    }
}
