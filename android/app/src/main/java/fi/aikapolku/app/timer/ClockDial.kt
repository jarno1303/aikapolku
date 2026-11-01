package fi.aikapolku.app.timer

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fi.aikapolku.app.ui.AppColors
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin

private val DialRed = Color(0xFFF07868)

internal val DialMuted = Color(0xFF64717A)

@Composable
internal fun ClockDial(
    remaining: Long,
    seconds: Int,
    lastTen: Boolean,
    onAdjustMinutes: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentAdjust by rememberUpdatedState(onAdjustMinutes)

    Box(
        modifier = modifier
            .clip(CircleShape)
            .pointerInput(Unit) {
                var previousMinute = -1

                detectDragGestures(
                    onDragStart = { position ->
                        val minute = dialMinuteFromPoint(
                            position,
                            size.width,
                            size.height
                        )
                        previousMinute = minute
                        currentAdjust(minute)
                    },
                    onDragEnd = {
                        previousMinute = -1
                    },
                    onDragCancel = {
                        previousMinute = -1
                    },
                    onDrag = { change, _ ->
                        val minute = dialMinuteFromPoint(
                            change.position,
                            size.width,
                            size.height
                        )

                        if (minute != previousMinute) {
                            previousMinute = minute
                            currentAdjust(minute)
                        }
                        change.consume()
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(
                size.width / 2f,
                size.height / 2f
            )

            val outer = size.minDimension / 2f
            val face = outer - 44.dp.toPx()

            fun polar(angle: Double, radius: Float): Offset {
                return Offset(
                    center.x + cos(angle).toFloat() * radius,
                    center.y + sin(angle).toFloat() * radius
                )
            }

            drawCircle(
                color = Color.White,
                radius = outer
            )

            drawCircle(
                color = Color(0xFFF7F8F8),
                radius = face
            )

            drawCircle(
                color = Color(0xFFE3E6E6),
                radius = face,
                style = Stroke(width = 1.dp.toPx())
            )

            val fraction = (
                remaining.coerceIn(0L, 3_600_000L).toFloat()
                / 3_600_000f
            )

            if (fraction > 0f) {
                drawArc(
                    color = DialRed,
                    startAngle = -90f,
                    sweepAngle = -360f * fraction,
                    useCenter = true,
                    topLeft = Offset(
                        center.x - face,
                        center.y - face
                    ),
                    size = Size(face * 2f, face * 2f)
                )
            }

            // Calm, high-contrast center for the numeric time readout.
            drawCircle(
                color = Color.White,
                radius = face * 0.62f,
                center = center
            )

            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.rgb(35,39,41)
                textSize = 14.sp.toPx()
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create(
                    "sans-serif-medium",
                    Typeface.NORMAL
                )
            }

            for (minute in 0 until 60) {
                val major = minute % 5 == 0
                val angle = Math.toRadians(
                    -90.0 - minute * 6.0
                )

                val markStart = polar(
                    angle,
                    face + 5.dp.toPx()
                )
                val markEnd = polar(
                    angle,
                    face + if (major) 16.dp.toPx()
                        else 12.dp.toPx()
                )

                drawLine(
                    color = if (major) AppColors.Ink
                        else Color(0xFFADB4B6),
                    start = markStart,
                    end = markEnd,
                    strokeWidth = if (major)
                        1.8.dp.toPx()
                    else 0.8.dp.toPx()
                )

                if (major) {
                    val labelPosition = polar(
                        angle,
                        face + 25.dp.toPx()
                    )

                    drawContext.canvas.nativeCanvas.drawText(
                        if (minute == 0) "60" else minute.toString(),
                        labelPosition.x,
                        labelPosition.y + paint.textSize * 0.35f,
                        paint
                    )
                }
            }

        }

        if (lastTen) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    seconds.toString(),
                    fontSize = 67.sp,
                    fontWeight = FontWeight.Bold,
                    color = DialRed
                )
                Text(
                    "SEKUNTIA",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.Ink
                )
            }
        }
    }
}

internal fun dialMinuteFromPoint(
    position: Offset,
    width: Int,
    height: Int
): Int {
    val dx = position.x - width / 2f
    val dy = position.y - height / 2f

    val angle = Math.toDegrees(
        atan2(dy.toDouble(), dx.toDouble())
    )

    val counterClockwise =
        ((270.0 - angle) % 360.0 + 360.0) % 360.0

    val minute = ceil(counterClockwise / 6.0).toInt()

    return if (minute == 0) 60
    else minute.coerceIn(1, 60)
}
