package anomaly.hex.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import anomaly.hex.logic.DictationLanguage
import anomaly.hex.logic.DictationLanguages

@Composable
fun OnboardingScreen(
    micGranted: Boolean,
    keyboardEnabled: Boolean,
    language: DictationLanguage,
    onAllowMic: () -> Unit,
    onOpenKeyboardSettings: () -> Unit,
    onLanguage: (DictationLanguage) -> Unit,
    onFinished: () -> Unit,
) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    var selectedTag by rememberSaveable { mutableStateOf(language.tag) }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text("Hex", color = HexColors.TextBase, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Text(
            "${step + 1} of 4",
            color = HexColors.TextFaint,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
        when (step) {
            0 -> StepCopy(
                title = "Dictate from the keyboard",
                body = "Hex is a dictation keyboard. It stays small: you speak, and the text lands in the field you're typing in.",
            )
            1 -> StepCopy(
                title = "Microphone",
                body = "Hex listens only while a dictation is running. Android speech and the offline model both use the microphone.",
            )
            2 -> StepCopy(
                title = "Turn on the Hex keyboard",
                body = "Settings › System › Languages & input › On-screen keyboard › Hex. Switch to it from the keyboard key when you want to dictate.",
            )
            else -> StepCopy(
                title = "Language",
                body = "Android speech and the offline model both use this language. You can change it later.",
            )
        }
        if (step == 1) {
            StatusLine("Microphone", if (micGranted) "Allowed" else "Off", micGranted)
        }
        if (step == 2) {
            StatusLine("Hex keyboard", if (keyboardEnabled) "On" else "Off", keyboardEnabled)
        }
        if (step == 3) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                DictationLanguages.all.forEach { candidate ->
                    val selected = candidate.tag == selectedTag
                    Text(
                        candidate.label,
                        color = if (selected) HexColors.TextBase else HexColors.TextMuted,
                        fontSize = 15.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedTag = candidate.tag
                                onLanguage(candidate)
                            }
                            .background(if (selected) HexColors.Layer02 else HexColors.BgBase, RoundedCornerShape(12.dp))
                            .border(
                                1.dp,
                                if (selected) HexColors.BorderStrong else HexColors.BorderWeak,
                                RoundedCornerShape(12.dp),
                            )
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                    )
                }
            }
        }
        when (step) {
            0 -> PrimaryButton("Continue") { step = 1 }
            1 -> {
                if (!micGranted) PrimaryButton("Allow microphone", onAllowMic)
                PrimaryButton(if (micGranted) "Continue" else "Not now") { step = 2 }
            }
            2 -> {
                PrimaryButton("Open keyboard settings", onOpenKeyboardSettings)
                PrimaryButton("Continue") { step = 3 }
            }
            else -> PrimaryButton("Start dictating", onFinished)
        }
    }
}

@Composable
private fun StepCopy(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, color = HexColors.TextBase, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Text(body, color = HexColors.TextMuted, fontSize = 15.sp, lineHeight = 21.sp)
    }
}

@Composable
private fun StatusLine(title: String, value: String, done: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(HexColors.BgBase, RoundedCornerShape(14.dp))
            .border(1.dp, HexColors.BorderWeak, RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = HexColors.TextBase, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(value, color = if (done) HexColors.Success else HexColors.TextMuted, fontSize = 14.sp)
    }
}

@Composable
private fun PrimaryButton(label: String, onClick: () -> Unit) {
    Text(
        label,
        color = HexColors.TextContrast,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(HexColors.BgContrast, CircleShape)
            .padding(vertical = 14.dp),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
    )
}
