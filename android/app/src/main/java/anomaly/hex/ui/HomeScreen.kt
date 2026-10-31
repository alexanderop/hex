package anomaly.hex.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import anomaly.hex.logic.DictationLanguages
import anomaly.hex.logic.DictationPhase
import anomaly.hex.logic.DictationStyle
import anomaly.hex.logic.EnginePreference
import anomaly.hex.logic.WhisperModelSpec

@Composable
fun HomeScreen(model: HomeViewModel, onRequestMic: () -> Unit) {
    val context = LocalContext.current
    var revision by remember { mutableIntStateOf(0) }
    DisposableEffect(model.settings) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> revision += 1 }
        model.settings.register(listener)
        onDispose { model.settings.unregister(listener) }
    }
    val settingsTick = revision
    val phase by model.session.phase.collectAsStateWithLifecycle()
    val status by model.session.status.collectAsStateWithLifecycle()
    val level by model.session.level.collectAsStateWithLifecycle()
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Hex", color = HexColors.TextBase, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        if (!model.micGranted || !model.keyboardEnabled) {
            Card(shape) {
                if (!model.micGranted) {
                    SettingRow("Microphone", "Off") { onRequestMic() }
                }
                if (!model.keyboardEnabled) {
                    SettingRow("Hex keyboard", "Off") {
                        context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                    }
                }
            }
        }
        DictationStage(
            phase = phase,
            level = level,
            actionLabel = if (model.micGranted) "Dictate" else "Allow microphone",
            onDictate = { if (model.micGranted) model.session.start() else onRequestMic() },
            onFinish = model.session::finish,
            onCancel = model.session::cancel,
        )
        if (status.isNotEmpty() && phase == DictationPhase.Failed) {
            Text(status, color = HexColors.Danger, fontSize = 13.sp)
        }
        if (model.transcript.isNotEmpty()) {
            Card(shape) {
                Text(model.transcript, color = HexColors.TextBase, fontSize = 16.sp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Text(
                        if (model.copied) "Copied" else "Copy",
                        color = if (model.copied) HexColors.Success else HexColors.TextBase,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .clickable {
                                val clipboard = context.getSystemService(ClipboardManager::class.java)
                                clipboard?.setPrimaryClip(ClipData.newPlainText("Hex", model.transcript))
                                model.copied = true
                            }
                            .padding(8.dp),
                    )
                    Text(
                        "Clear",
                        color = HexColors.TextMuted,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .clickable {
                                model.transcript = ""
                                model.copied = false
                            }
                            .padding(8.dp),
                    )
                }
            }
        }
        BasicTextField(
            value = model.practice,
            onValueChange = { model.practice = it },
            textStyle = TextStyle(color = HexColors.TextBase, fontSize = 15.sp),
            cursorBrush = SolidColor(HexColors.TextBase),
            modifier = Modifier
                .fillMaxWidth()
                .background(HexColors.BgBase, shape)
                .border(1.dp, HexColors.BorderWeak, shape)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            decorationBox = { inner ->
                if (model.practice.text.isEmpty()) {
                    Text("Test keyboard…", color = HexColors.TextFaint, fontSize = 15.sp)
                }
                inner()
            },
        )
        Card(shape) {
            MenuRow("Style", model.settings.style().label, DictationStyle.entries.map { it.label to it.name }.also { settingsTick }) { id ->
                model.settings.setStyle(DictationStyle.byId(id))
            }
            Divider()
            MenuRow(
                "Language",
                model.settings.language().label,
                DictationLanguages.all.map { it.label to it.tag },
            ) { tag -> model.settings.setLanguage(tag) }
            Divider()
            MenuRow(
                "Speech",
                model.settings.preference().label,
                EnginePreference.entries.map { it.label to it.name },
            ) { id -> model.settings.setPreference(EnginePreference.byId(id)) }
            Divider()
            MenuRow(
                "Offline model",
                model.settings.model().label,
                WhisperModelSpec.entries.map { it.label to it.id },
            ) { id -> model.settings.setModel(WhisperModelSpec.byId(id)) }
            Divider()
            val ready = model.modelReady()
            SettingRow(
                "Download ${model.settings.model().label.lowercase()}",
                when {
                    model.downloadProgress != null -> "Downloading"
                    ready -> "Ready"
                    else -> "Not downloaded"
                },
            ) { if (!ready) model.downloadModel() }
            val progress = model.downloadProgress
            if (progress != null) {
                if (progress < 0f) {
                    LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp))
                } else {
                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
            model.downloadError?.let { message ->
                Text(message, color = HexColors.Danger, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
            Divider()
            SettingRow("Keyboard settings", "") {
                context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
            }
            if (model.keyboardEnabled) {
                Divider()
                SettingRow("Switch to Hex", "") {
                    context.getSystemService(InputMethodManager::class.java)?.showInputMethodPicker()
                }
            }
        }
    }
}

@Composable
private fun Card(shape: RoundedCornerShape, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(HexColors.BgBase, shape)
            .border(1.dp, HexColors.BorderWeak, shape)
            .padding(vertical = 4.dp),
        content = { content() },
    )
}

@Composable
private fun Divider() {
    androidx.compose.foundation.layout.Box(
        Modifier
            .padding(start = 16.dp)
            .fillMaxWidth()
            .height(1.dp)
            .background(HexColors.BorderWeak),
    )
}

@Composable
private fun SettingRow(title: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = HexColors.TextBase, fontSize = 14.sp, modifier = Modifier.weight(1f))
        if (value.isNotEmpty()) Text(value, color = HexColors.TextMuted, fontSize = 14.sp)
    }
}

@Composable
private fun MenuRow(title: String, value: String, options: List<Pair<String, String>>, onSelect: (String) -> Unit) {
    var open by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { open = true }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = HexColors.TextBase, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(value, color = HexColors.TextMuted, fontSize = 14.sp)
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (label, id) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        open = false
                        onSelect(id)
                    },
                )
            }
        }
    }
}
