package fi.aikapolku.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke

/**
 * Muutoksen vakiomerkki: oranssi kolmio ja kaksi vastakkaista nuolta. Sama merkki joka
 * paikassa (aloitusnäkymä, päivän lista, muutosnäkymä), jotta käyttäjä oppii tunnistamaan sen.
 */
@Composable
internal fun ChangeSymbol(modifier: Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val corner = w * 0.09f
        val triangle = Path().apply {
            moveTo(w / 2f, h * 0.08f)
            lineTo(w * 0.94f, h * 0.88f)
            lineTo(w * 0.06f, h * 0.88f)
            close()
        }
        drawPath(triangle, AppColors.ChangeFill, style = Fill)
        drawPath(triangle, AppColors.ChangeFill, style = Stroke(width = corner, join = StrokeJoin.Round))

        val stroke = w * 0.075f
        val head = w * 0.1f
        val left = w * 0.32f
        val right = w * 0.68f
        val upper = h * 0.55f
        val lower = h * 0.72f
        // Ylempi nuoli oikealle, alempi vasemmalle.
        drawLine(Color.White, Offset(left, upper), Offset(right, upper), stroke, StrokeCap.Round)
        drawLine(Color.White, Offset(right, upper), Offset(right - head, upper - head), stroke, StrokeCap.Round)
        drawLine(Color.White, Offset(right, upper), Offset(right - head, upper + head), stroke, StrokeCap.Round)
        drawLine(Color.White, Offset(right, lower), Offset(left, lower), stroke, StrokeCap.Round)
        drawLine(Color.White, Offset(left, lower), Offset(left + head, lower - head), stroke, StrokeCap.Round)
        drawLine(Color.White, Offset(left, lower), Offset(left + head, lower + head), stroke, StrokeCap.Round)
    }
}
