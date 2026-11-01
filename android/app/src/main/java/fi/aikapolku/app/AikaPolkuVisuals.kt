package fi.aikapolku.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val MainTeal = Color(0xFF0F766E)
private val SoftTeal = Color(0xFFE6F4F1)

@Composable
internal fun AikaFlowHeader(
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        StageChip("1", "ENSIN", true, compact)

        Text(
            "→",
            fontSize = 20.sp,
            color = Color(0xFF64748B)
        )

        StageChip("2", "SITTEN", false, compact)
    }
}

@Composable
private fun StageChip(
    number: String,
    title: String,
    active: Boolean,
    compact: Boolean
) {
    Surface(
        color = if (active) MainTeal else SoftTeal,
        shape = RoundedCornerShape(50.dp)
    ) {
        Text(
            "$number  $title",
            modifier = Modifier.padding(
                horizontal = if (compact) 10.dp else 14.dp,
                vertical = 9.dp
            ),
            fontSize = if (compact) 11.sp else 13.sp,
            fontWeight = FontWeight.Bold,
            color = if (active) Color.White else MainTeal
        )
    }
}

@Composable
internal fun AikaTimerRing(
    progress: Float,
    urgent: Boolean,
    modifier: Modifier = Modifier
) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val diameter = size.minDimension

        drawCircle(
            color = Color(0xFFF3F9F7),
            radius = diameter / 2f
        )

        drawCircle(
            color = Color.White,
            radius = diameter / 2f - 7.dp.toPx()
        )

        val stroke = 14.dp.toPx()
        val inset = stroke / 2f + 15.dp.toPx()

        val arcSize = Size(
            width = w - inset * 2f,
            height = h - inset * 2f
        )
        val arcTopLeft = Offset(inset, inset)

        drawArc(
            color = Color(0xFFD9E9E7),
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = arcTopLeft,
            size = arcSize,
            style = Stroke(
                width = stroke,
                cap = StrokeCap.Round
            )
        )

        if (progress > 0f) {
            drawArc(
                brush = Brush.sweepGradient(
                    colors = if (urgent) {
                        listOf(
                            Color(0xFFC2413A),
                            Color(0xFFF08569),
                            Color(0xFFC2413A)
                        )
                    } else {
                        listOf(
                            MainTeal,
                            Color(0xFF44C7B6),
                            MainTeal
                        )
                    }
                ),
                startAngle = -90f,
                sweepAngle = 360f * progress.coerceIn(0f, 1f),
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = Stroke(
                    width = stroke,
                    cap = StrokeCap.Round
                )
            )
        }

        drawCircle(
            color = Color(0xFFEAF3F1),
            radius = diameter / 2f - 38.dp.toPx(),
            style = Stroke(width = 1.dp.toPx())
        )
    }
}