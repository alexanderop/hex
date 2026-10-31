package anomaly.hex.ui

import android.content.SharedPreferences
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import anomaly.hex.R
import anomaly.hex.data.HexSettings
import anomaly.hex.logic.DictationPhase
import anomaly.hex.logic.DictationStyle
import anomaly.hex.speech.DictationSession
import kotlinx.coroutines.delay

class KeyboardPresenter {
    var micGranted by mutableStateOf(false)
    var returnLabel by mutableStateOf("Return")
    var revision by mutableIntStateOf(0)
    var styleOpen by mutableStateOf(false)
}

@Composable
fun HexKeyboard(
    session: DictationSession,
    settings: HexSettings,
    presenter: KeyboardPresenter,
    onDictate: () -> Unit,
    onAllowMic: () -> Unit,
    onStyle: (DictationStyle) -> Unit,
    onSpace: () -> Unit,
    onDelete: () -> Unit,
    onReturn: () -> Unit,
    onSwitchKeyboard: () -> Unit,
) {
    val phase by session.phase.collectAsStateWithLifecycle()
    val partial by session.partial.collectAsStateWithLifecycle()
    val status by session.status.collectAsStateWithLifecycle()
    val elapsed by session.elapsedMs.collectAsStateWithLifecycle()
    val level by session.level.collectAsStateWithLifecycle()
    val route by session.routeLabel.collectAsStateWithLifecycle()
    var revision by remember { mutableIntStateOf(0) }
    DisposableEffect(settings) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> revision += 1 }
        settings.register(listener)
        onDispose { settings.unregister(listener) }
    }
    val style = settings.style().also { revision }
    val language = settings.language().also { presenter.revision }
    Column(
        Modifier
            .fillMaxSize()
            .background(HexColors.BgDeep)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TopBar(
            phase = phase,
            partial = partial,
            status = status,
            elapsed = elapsed,
            route = route,
            language = language.label,
            style = style,
            styleOpen = presenter.styleOpen,
            onOpenStyle = { presenter.styleOpen = !presenter.styleOpen },
            onStyle = {
                onStyle(it)
                presenter.styleOpen = false
            },
        )
        DictationStage(
            phase = phase,
            level = level,
            actionLabel = if (presenter.micGranted) "Dictate" else "Allow mic",
            onDictate = if (presenter.micGranted) onDictate else onAllowMic,
            onFinish = session::finish,
            onCancel = session::cancel,
            height = 52.dp,
        )
        UtilityRow(
            returnLabel = presenter.returnLabel,
            onSpace = onSpace,
            onDelete = onDelete,
            onReturn = onReturn,
            onSwitchKeyboard = onSwitchKeyboard,
        )
    }
}

@Composable
private fun TopBar(
    phase: DictationPhase,
    partial: String,
    status: String,
    elapsed: Long,
    route: String,
    language: String,
    style: DictationStyle,
    styleOpen: Boolean,
    onOpenStyle: () -> Unit,
    onStyle: (DictationStyle) -> Unit,
) {
    if (styleOpen && phase != DictationPhase.Listening && phase != DictationPhase.Transcribing) {
        Row(
            Modifier.fillMaxWidth().height(26.dp).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DictationStyle.entries.forEach { candidate ->
                val active = candidate == style
                Text(
                    candidate.label,
                    color = if (active) HexColors.TextBase else HexColors.TextMuted,
                    fontSize = 12.sp,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                    modifier = Modifier
                        .clickable { onStyle(candidate) }
                        .backgroundChip(active)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
        return
    }
    Row(
        Modifier.fillMaxWidth().height(26.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (phase == DictationPhase.Listening) {
            Box(Modifier.size(7.dp).background(HexColors.Danger, CircleShape))
            Text(
                formatClock(elapsed),
                color = HexColors.TextBase,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
            )
        }
        Text(
            when {
                phase == DictationPhase.Listening && partial.isNotEmpty() -> partial
                phase == DictationPhase.Listening && route.isNotEmpty() -> route
                phase == DictationPhase.Transcribing -> "Transcribing…"
                status.isNotEmpty() -> status
                else -> language
            },
            color = HexColors.TextMuted,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (phase == DictationPhase.Idle || phase == DictationPhase.Failed) {
            Text(
                style.label,
                color = HexColors.TextBase,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clickable(onClick = onOpenStyle)
                    .backgroundChip(false)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun UtilityRow(
    returnLabel: String,
    onSpace: () -> Unit,
    onDelete: () -> Unit,
    onReturn: () -> Unit,
    onSwitchKeyboard: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        KeyButton(Modifier.width(44.dp), onClick = onSwitchKeyboard) {
            Image(painterResource(R.drawable.ic_globe), "Switch keyboard", Modifier.size(16.dp))
        }
        KeyButton(Modifier.weight(1f), onClick = onSpace) {
            Text("space", color = HexColors.TextMuted, fontSize = 14.sp)
        }
        DeleteKey(onDelete)
        KeyButton(Modifier.width(56.dp), onClick = onReturn) {
            Image(painterResource(R.drawable.ic_return), returnLabel, Modifier.size(16.dp))
        }
    }
}

@Composable
private fun DeleteKey(onDelete: () -> Unit) {
    var pressing by remember { mutableStateOf(false) }
    LaunchedEffect(pressing) {
        if (!pressing) return@LaunchedEffect
        onDelete()
        delay(360)
        while (true) {
            onDelete()
            delay(75)
        }
    }
    KeyButton(
        Modifier
            .width(52.dp)
            .pointerInput(onDelete) {
                awaitEachGesture {
                    awaitFirstDown()
                    pressing = true
                    waitForUpOrCancellation()
                    pressing = false
                }
            },
        pressed = pressing,
    ) {
        Image(painterResource(R.drawable.ic_delete), "Delete", Modifier.size(16.dp))
    }
}

private fun Modifier.backgroundChip(active: Boolean): Modifier {
    val shape = CircleShape
    return this
        .background(if (active) HexColors.Layer02 else HexColors.Layer01, shape)
        .border(1.dp, HexColors.BorderWeak, shape)
}
