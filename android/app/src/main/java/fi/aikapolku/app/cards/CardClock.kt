package fi.aikapolku.app.cards

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fi.aikapolku.app.data.CardClockMode
import fi.aikapolku.app.data.CardClockPrefs
import fi.aikapolku.app.settings.SettingsRevision
import kotlin.math.cos
import kotlin.math.sin

private val ClockInk = Color(0xFF1F2937)

/** Asukkaan asetus: näytetäänkö korteissa kello (päivittyy asetuksen muuttuessa). */
@Composable
internal fun cardClockMode(): CardClockMode {
    val context = LocalContext.current
    SettingsRevision.value
    return CardClockPrefs.mode(context)
}

/**
 * Kello kortin oikeaan yläkulmaan: kellotaulu, jonka viisarit näyttävät alkamisajan, ja
 * valinnan mukaan aika numeroina sen alla. Pienissä kuvissa kelloa ei piirretä (ei luettavissa).
 */
internal fun Modifier.clockMark(time: String?, mode: CardClockMode, measurer: TextMeasurer): Modifier {
    val minutes = CardClockPrefs.minutes(time)
    if (mode == CardClockMode.OFF || minutes == null) return this
    return drawWithContent {
        drawContent()
        val small = size.minDimension
        if (small < 56.dp.toPx()) return@drawWithContent
        val d = (small * 0.28f).coerceIn(28.dp.toPx(), 72.dp.toPx())
        val inset = (small * 0.06f).coerceAtLeast(4.dp.toPx())
        val center = Offset(size.width - inset - d / 2, inset + d / 2)
        drawDial(center, d / 2, minutes, measurer)
        if (mode == CardClockMode.BOTH) {
            val text = "%d.%02d".format(minutes / 60, minutes % 60)
            val style = TextStyle(fontSize = (d * 0.3f).toSp(), fontWeight = FontWeight.Bold, color = ClockInk)
            val layout = measurer.measure(text, style)
            val padX = 4.dp.toPx()
            val w = layout.size.width + padX * 2
            val h = layout.size.height.toFloat()
            val left = (center.x - w / 2).coerceAtMost(size.width - inset - w)
            val top = center.y + d / 2 + 3.dp.toPx()
            drawRoundRect(Color.White, topLeft = Offset(left, top), size = Size(w, h), cornerRadius = CornerRadius(h / 2))
            drawText(layout, topLeft = Offset(left + padX, top))
        }
    }
}

/** Kellotaulu: valkoinen pohja, tumma reuna, numerot 12, 3, 6 ja 9 (isossa koossa), viisarit. */
private fun DrawScope.drawDial(center: Offset, r: Float, minutes: Int, measurer: TextMeasurer) {
    drawCircle(Color.White, radius = r, center = center)
    drawCircle(ClockInk, radius = r - r * 0.05f, center = center, style = Stroke(width = r * 0.1f))
    val numbers = r >= 22.dp.toPx()
    listOf(0 to "12", 90 to "3", 180 to "6", 270 to "9").forEach { (deg, label) ->
        val a = Math.toRadians(deg.toDouble())
        if (numbers) {
            val layout = measurer.measure(label, TextStyle(fontSize = (r * 0.36f).toSp(), fontWeight = FontWeight.Bold, color = ClockInk))
            val pos = Offset(center.x + (r * 0.7f * sin(a)).toFloat(), center.y - (r * 0.7f * cos(a)).toFloat())
            drawText(layout, topLeft = Offset(pos.x - layout.size.width / 2f, pos.y - layout.size.height / 2f))
        } else {
            val outer = Offset(center.x + (r * 0.82f * sin(a)).toFloat(), center.y - (r * 0.82f * cos(a)).toFloat())
            val inner = Offset(center.x + (r * 0.62f * sin(a)).toFloat(), center.y - (r * 0.62f * cos(a)).toFloat())
            drawLine(ClockInk, inner, outer, strokeWidth = r * 0.1f, cap = StrokeCap.Round)
        }
    }
    val (hourDeg, minuteDeg) = CardClockPrefs.handAngles(minutes)
    fun hand(deg: Float, length: Float, width: Float) {
        val a = Math.toRadians(deg.toDouble())
        val end = Offset(center.x + (length * sin(a)).toFloat(), center.y - (length * cos(a)).toFloat())
        drawLine(ClockInk, center, end, strokeWidth = width, cap = StrokeCap.Round)
    }
    hand(hourDeg, r * 0.36f, r * 0.14f)
    hand(minuteDeg, r * 0.56f, r * 0.09f)
    drawCircle(ClockInk, radius = r * 0.1f, center = center)
}
