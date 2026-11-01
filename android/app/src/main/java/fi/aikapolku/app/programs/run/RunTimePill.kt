package fi.aikapolku.app.programs.run

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fi.aikapolku.app.R
import fi.aikapolku.app.data.TimeDisplayPrefs

@Composable
internal fun RunClockPill(time: String) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = RunColors.card,
        border = BorderStroke(1.dp, RunColors.border)
    ) {
        Text(
            stringResource(R.string.clock_value, time),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            color = RunColors.ink
        )
    }
}

/**
 * Aika ja sen ohjaus yhdessä: napautus aloittaa tai jatkaa (aina vapaa) tai pysäyttää
 * (lukittuna PIN-koodilla, näkyy 🔒). Näyttötapa noudattaa käyttäjän valintaa:
 * numerot ja rengas, pelkkä rengas tai rauhallinen tila ilman edistymistä.
 */
@Composable
internal fun RunCountdownPill(time: RunTime.Countdown, onToggle: () -> Unit) {
    val seconds = ((time.remainingMs + 999L) / 1000L).coerceAtLeast(0L)
    val finished = time.remainingMs <= 0L
    val numbers = time.displayMode == TimeDisplayPrefs.NUMBERS
    val action = when {
        time.pauseLocked -> "🔒"
        time.running -> "Ⅱ"
        else -> "▶"
    }
    val spoken = buildString {
        if (finished) append(stringResource(R.string.a11y_time_ended))
        else if (numbers) append(stringResource(R.string.aikaa_jaljella_minuuttia_sekuntia, seconds / 60, seconds % 60))
        append(
            when {
                time.pauseLocked -> stringResource(R.string.tauko_vaatii_ohjaajan_pin_koodin)
                time.running -> stringResource(R.string.pysayta_aika)
                else -> stringResource(R.string.aloita_aika)
            }
        )
    }
    Surface(
        onClick = onToggle,
        shape = RoundedCornerShape(999.dp),
        color = RunColors.card,
        border = BorderStroke(1.dp, RunColors.border),
        modifier = Modifier
            .heightIn(min = 52.dp)
            .semantics(mergeDescendants = true) { contentDescription = spoken }
    ) {
        Row(
            modifier = Modifier.padding(start = 8.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            RunTimeRing(
                fraction = if (time.durationMs <= 0L) 0f
                    else (time.remainingMs.toFloat() / time.durationMs).coerceIn(0f, 1f),
                calm = time.displayMode == TimeDisplayPrefs.CALM,
                finished = finished,
                modifier = Modifier.size(32.dp)
            )
            if (finished) {
                Text(stringResource(R.string.time_ended), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = RunColors.teal)
            } else if (numbers) {
                Text(
                    "%d:%02d".format(seconds / 60, seconds % 60),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = RunColors.ink
                )
            }
            Text(action, fontSize = if (time.pauseLocked) 17.sp else 19.sp, color = RunColors.ink)
        }
    }
}

@Composable
private fun RunTimeRing(fraction: Float, calm: Boolean, finished: Boolean, modifier: Modifier) {
    Canvas(modifier = modifier) {
        val stroke = 5.dp.toPx()
        val radius = size.minDimension / 2f - stroke / 2f
        val topLeft = Offset(center.x - radius, center.y - radius)
        val arcSize = Size(radius * 2f, radius * 2f)
        if (calm) {
            // Rauhallinen tila: tasainen rengas, ei tietoa edistymisestä.
            drawCircle(RunColors.tealSoft, radius, center, style = Stroke(stroke))
            return@Canvas
        }
        drawCircle(
            color = if (finished) RunColors.teal.copy(alpha = 0.25f) else RunColors.timerTrack,
            radius = radius, center = center, style = Stroke(stroke)
        )
        if (!finished && fraction > 0f) {
            drawArc(
                color = RunColors.timer,
                startAngle = -90f,
                sweepAngle = 360f * fraction,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round)
            )
        }
    }
}
