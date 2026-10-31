package anomaly.hex.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin

@Composable
fun DotScope(
    level: Float,
    listening: Boolean,
    processing: Boolean,
    modifier: Modifier = Modifier,
) {
    val tint = if (processing) HexColors.AgentBlue else HexColors.Pearl
    val energy = if (listening) level.coerceIn(0.15f, 1f) else 0.28f
    Canvas(modifier) {
        val columns = 21
        val rows = 7
        val pitch = size.minDimension / 8.2f
        val midX = size.width * 0.5f
        val midY = size.height * 0.5f
        val halfRows = (rows - 1) * 0.5
        val halfCols = (columns - 1) * 0.5
        for (column in 0 until columns) {
            val xn = column.toDouble() / (columns - 1)
            val edge = sin(PI * xn)
            val amp = (0.35 + 0.65 * sin(PI * xn)) * halfRows * 0.82 * energy
            val yA = halfRows + amp * sin(2.0 * PI * 1.45 * xn - 0.5)
            val yB = halfRows - amp * sin(2.0 * PI * 1.45 * xn - 0.5)
            for (row in 0 until rows) {
                val dA = abs(row - yA)
                val dB = abs(row - yB)
                val litA = exp(-(dA * dA) / 0.42)
                val litB = exp(-(dB * dB) / 0.42)
                val power = ((1.0 - (1.0 - litA) * (1.0 - litB)) * (0.25 + 0.75 * edge)).coerceAtMost(1.0)
                val alpha = (0.07 * edge + 0.89 * power.pow(0.9)).toFloat()
                val radius = ((0.26 + 0.16 * power.pow(0.6)) * pitch).toFloat()
                val cx = midX + (column - halfCols).toFloat() * pitch
                val cy = midY + (row - halfRows).toFloat() * pitch
                drawCircle(tint.copy(alpha = alpha.coerceIn(0f, 1f)), radius, Offset(cx, cy))
            }
        }
    }
}
