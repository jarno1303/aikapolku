package fi.aikapolku.app.timer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fi.aikapolku.app.data.TimeDisplayPrefs

/** Same options are available in both the timer and the daily routine. */
@Composable
internal fun TimeModeChoices(mode: String, onChange: (String) -> Unit) {
    Text("Miten aika näytetään?", fontWeight = FontWeight.SemiBold)
    listOf(
        Triple(TimeDisplayPrefs.NUMBERS, "Numerot ja rengas", "Tarkka aika näkyy."),
        Triple(TimeDisplayPrefs.RING, "Vain rengas", "Ei väheneviä numeroita."),
        Triple(TimeDisplayPrefs.CALM, "Rauhallinen", "Ei jatkuvaa aikalaskentaa.")
    ).forEach { (value, label, detail) ->
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = mode == value, onClick = { onChange(value) })
            Column(modifier = Modifier.weight(1f)) {
                Text(label, fontSize = 15.sp)
                Text(detail, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    Text(
        "Aika ja hälytykset toimivat kaikissa vaihtoehdoissa samalla tavalla.",
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** Non-numeric display for both the dial and the programme step. */
@Composable
internal fun TimeVisual(
    remaining: Long,
    duration: Long,
    mode: String,
    running: Boolean,
    modifier: Modifier = Modifier
) {
    val done = remaining <= 0L
    val teal = Color(0xFF0F766E)
    val coral = Color(0xFFC2413A)
    val color = if (done) teal else coral

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val radius = size.minDimension / 2f - 17.dp.toPx()
            val center = Offset(size.width / 2f, size.height / 2f)
            drawCircle(
                color = Color(0xFFE7EDEB), radius = radius, center = center,
                style = Stroke(width = 15.dp.toPx(), cap = StrokeCap.Round)
            )
            if (mode == TimeDisplayPrefs.RING) {
                val fraction = (remaining.toDouble() / duration.coerceAtLeast(1L))
                    .coerceIn(0.0, 1.0).toFloat()
                if (fraction > 0f) {
                    drawArc(
                        color = color, startAngle = -90f,
                        sweepAngle = fraction * 360f,
                        useCenter = false,
                        topLeft = Offset(center.x - radius, center.y - radius),
                        size = androidx.compose.ui.geometry.Size(radius * 2f, radius * 2f),
                        style = Stroke(width = 15.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            } else {
                // Calm mode: a constant ring, no progress information.
                drawCircle(
                    color = teal.copy(alpha = 0.8f), radius = radius, center = center,
                    style = Stroke(width = 9.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }
        Text(
            text = when {
                done -> "Aika päättyi"
                running -> "Käynnissä"
                remaining < duration -> "Tauolla"
                else -> "Valmis aloittamaan"
            },
            modifier = Modifier.padding(horizontal = 16.dp),
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            textAlign = TextAlign.Center
        )
    }
}
