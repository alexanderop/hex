package anomaly.hex.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import anomaly.hex.R
import anomaly.hex.logic.DictationPhase

@Composable
fun DictationStage(
    phase: DictationPhase,
    level: Float,
    actionLabel: String,
    onDictate: () -> Unit,
    onFinish: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 56.dp,
) {
    val active = phase == DictationPhase.Listening || phase == DictationPhase.Transcribing
    val shape = RoundedCornerShape(percent = 50)
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(shape)
            .background(HexColors.BgBase)
            .border(1.dp, if (active) HexColors.BorderStrong else HexColors.BorderWeak, shape),
    ) {
        DotScope(
            level = level,
            listening = phase == DictationPhase.Listening,
            processing = phase == DictationPhase.Transcribing,
            modifier = Modifier
                .align(if (active) Alignment.Center else Alignment.CenterStart)
                .padding(start = if (active) 0.dp else 12.dp)
                .size(width = 168.dp, height = 38.dp),
        )
        when (phase) {
            DictationPhase.Listening -> Row(
                Modifier.fillMaxSize().padding(horizontal = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RoundIconButton(R.drawable.ic_close, "Cancel dictation", onCancel)
                Spacer(Modifier.weight(1f))
                ContrastLabel("Done", Modifier.clickable(role = Role.Button, onClick = onFinish))
            }
            DictationPhase.Transcribing -> Text(
                "Transcribing…",
                color = HexColors.TextMuted,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 18.dp),
            )
            DictationPhase.Idle, DictationPhase.Failed -> Box(
                Modifier
                    .fillMaxSize()
                    .clickable(role = Role.Button, onClick = onDictate),
                contentAlignment = Alignment.CenterEnd,
            ) {
                ContrastLabel(actionLabel, Modifier.padding(end = 7.dp))
            }
        }
    }
}

@Composable
private fun ContrastLabel(label: String, modifier: Modifier = Modifier) {
    Text(
        label,
        color = HexColors.TextContrast,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier
            .clip(CircleShape)
            .background(HexColors.BgContrast)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

@Composable
private fun RoundIconButton(icon: Int, description: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(HexColors.Layer02)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Image(painterResource(icon), description, Modifier.size(14.dp))
    }
}

@Composable
fun KeyButton(
    modifier: Modifier = Modifier,
    pressed: Boolean = false,
    onClick: (() -> Unit)? = null,
        content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(10.dp)
    val click = if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier
    Box(
        modifier
            .height(40.dp)
            .clip(shape)
            .background(if (pressed) HexColors.Layer02 else HexColors.Layer01)
            .border(1.dp, HexColors.BorderWeak, shape)
            .then(click),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
