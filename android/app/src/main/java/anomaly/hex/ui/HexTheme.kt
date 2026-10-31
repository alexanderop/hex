package anomaly.hex.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object HexColors {
    val BgDeep = Color(0xFF0A0B0D.toInt())
    val BgBase = Color(0xFF111316.toInt())
    val Layer01 = Color(0xFF17191D.toInt())
    val Layer02 = Color(0xFF202329.toInt())
    val TextBase = Color.White.copy(alpha = 0.94f)
    val TextMuted = Color.White.copy(alpha = 0.56f)
    val TextFaint = Color.White.copy(alpha = 0.36f)
    val BorderWeak = Color.White.copy(alpha = 0.06f)
    val BorderStrong = Color.White.copy(alpha = 0.11f)
    val BgContrast = Color.White.copy(alpha = 0.96f)
    val TextContrast = Color(0xFF0A0B0D.toInt())
    val Danger = Color(0xFFE54D42.toInt())
    val WarmMic = Color(0xFFFF9500.toInt())
    val Success = Color(0xFF34C759.toInt())
    val Pearl = Color(0.96f, 0.93f, 0.87f)
    val AgentBlue = Color(0.78f, 0.86f, 0.97f)
}

@Composable
fun HexTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = HexColors.BgDeep,
            surface = HexColors.BgBase,
            primary = HexColors.BgContrast,
            onPrimary = HexColors.TextContrast,
            onBackground = HexColors.TextBase,
            onSurface = HexColors.TextBase,
        ),
        content = content,
    )
}

fun formatClock(ms: Long): String {
    val seconds = (ms / 1000L).coerceAtLeast(0L)
    return "%d:%02d".format(seconds / 60L, seconds % 60L)
}
