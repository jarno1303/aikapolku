package fi.aikapolku.app

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin
import java.util.Locale

private val DialRed = Color(0xFFEC2547)
private val DialInk = Color(0xFF24282B)
private val DialMuted = Color(0xFF64717A)
private val DialTeal = Color(0xFF0F766E)

@Composable
internal fun P012TimerScreen(
    remaining: Long,
    duration: Long,
    running: Boolean,
    showLastTen: Boolean,
    activeTask: String,
    firstPhoto: String,
    nextPhoto: String,
    nextActivity: String,
    onGoNext: () -> Unit,
    onShowLastTen: (Boolean) -> Unit,
    onPreset: (Int) -> Unit,
    onStartPause: () -> Unit,
    onReset: () -> Unit,
    onAddMinute: () -> Unit,
    onSubtractMinute: () -> Unit
) {
    val guided = activeTask.isNotBlank()
    val finished = guided && remaining == 0L

    var moreOpen by rememberSaveable { mutableStateOf(false) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var confirmReset by rememberSaveable { mutableStateOf(false) }

    if (finished) {
        P011CompletedCard(
            first = activeTask,
            next = nextActivity,
            nextPhoto = nextPhoto
        )

        Button(
            onClick = onGoNext,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = DialTeal
            )
        ) {
            Text(
                if (nextActivity == "Valitse toiminto")
                    "Valmis"
                else "Siirry seuraavaan",
                fontSize = 18.sp
            )
        }
    } else {
        if (guided) {
            Text(
                "TOIMINTOPOLKU",
                fontSize = 13.sp,
                letterSpacing = 1.sp,
                fontWeight = FontWeight.Bold,
                color = DialMuted
            )
            P012TaskCards(
                first = activeTask,
                firstPhoto = firstPhoto,
                next = nextActivity,
                nextPhoto = nextPhoto
            )
        } else {
            Text(
                "Ajastin",
                fontSize = 25.sp,
                fontWeight = FontWeight.Bold,
                color = DialInk
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                listOf(1,2,5,10,15,20,30,60).forEach { min ->
                    OutlinedButton(onClick = { onPreset(min) }) {
                        Text("$min min")
                    }
                }
            }
        }

        val secs = ((remaining + 999L) / 1000L)
            .coerceAtLeast(0L)
            .toInt()

        val lastTen = showLastTen && secs in 1..10
        val compact = LocalConfiguration.current.screenHeightDp < 750

        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            val target = when {
                compact -> 258.dp
                guided -> 330.dp
                else -> 345.dp
            }

            val diameter = minOf(maxWidth - 8.dp, target)

            P012ClockDial(
                remaining = remaining,
                seconds = secs,
                lastTen = lastTen,
                running = running,
                onToggle = onStartPause,
                modifier = Modifier.size(diameter)
            )
        }

        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(40.dp),
                color = Color(0xFFF0F1F2),
                shadowElevation = 1.dp
            ) {
                Text(
                    String.format(
                        Locale.getDefault(),
                        "%02d:%02d",
                        secs / 60,
                        secs % 60
                    ),
                    modifier = Modifier.padding(
                        horizontal = 43.dp,
                        vertical = 8.dp
                    ),
                    fontSize = 31.sp,
                    fontWeight = FontWeight.Bold,
                    color = DialInk,
                    textAlign = TextAlign.Center
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    TextButton(
                        onClick = { moreOpen = true },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Text(
                            "⋯",
                            fontSize = 27.sp,
                            color = DialInk
                        )
                    }
                    Text(
                        "Ajan säätö",
                        fontSize = 11.sp,
                        color = DialMuted
                    )
                }

                DropdownMenu(
                    expanded = moreOpen,
                    onDismissRequest = { moreOpen = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("−1 minuutti") },
                        enabled = remaining > 0L,
                        onClick = {
                            moreOpen = false
                            onSubtractMinute()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("+1 minuutti") },
                        onClick = {
                            moreOpen = false
                            onAddMinute()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Nollaa ajastin") },
                        onClick = {
                            moreOpen = false
                            confirmReset = true
                        }
                    )
                }
            }

            androidx.compose.material3.IconButton(
                onClick = onStartPause,
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(DialInk)
            ) {
                Text(
                    if (running) "Ⅱ" else "▶",
                    fontSize = if (running) 39.sp else 30.sp,
                    color = Color.White
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                TextButton(
                    onClick = { settingsOpen = true },
                    modifier = Modifier.size(48.dp)
                ) {
                    Text(
                        "⚙",
                        fontSize = 25.sp,
                        color = DialInk
                    )
                }
                Text(
                    "Asetukset",
                    fontSize = 11.sp,
                    color = DialMuted
                )
            }
        }
    }

    if (settingsOpen) {
        AlertDialog(
            onDismissRequest = { settingsOpen = false },
            title = { Text("Ajastimen asetukset") },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Viimeisten 10 sekunnin laskenta",
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = showLastTen,
                        onCheckedChange = onShowLastTen
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { settingsOpen = false }) {
                    Text("Valmis")
                }
            }
        )
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Nollataanko ajastin?") },
            text = {
                Text("Ajastin palautetaan valittuun aloitusaikaan.")
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    onReset()
                }) {
                    Text("Nollaa")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) {
                    Text("Peruuta")
                }
            }
        )
    }
}

@Composable
private fun P012TaskCards(
    first: String,
    firstPhoto: String,
    next: String,
    nextPhoto: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        P012TaskCard(
            label = "ENSIN",
            name = first,
            photo = firstPhoto,
            modifier = Modifier.weight(1f)
        )
        P012TaskCard(
            label = "SITTEN",
            name = next,
            photo = nextPhoto,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun P012TaskCard(
    label: String,
    name: String,
    photo: String,
    modifier: Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(17.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            modifier = Modifier.padding(7.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(92.dp)
                    .clip(RoundedCornerShape(12.dp))
            ) {
                P011Photo(
                    name = name,
                    imagePath = photo,
                    modifier = Modifier.fillMaxSize()
                )

                Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = if (label == "ENSIN")
                        DialTeal else Color.White
                ) {
                    Text(
                        label,
                        modifier = Modifier.padding(
                            horizontal = 9.dp,
                            vertical = 5.dp
                        ),
                        color = if (label == "ENSIN")
                            Color.White else DialTeal,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Text(
                name,
                modifier = Modifier.fillMaxWidth(),
                color = DialInk,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun P012ClockDial(
    remaining: Long,
    seconds: Int,
    lastTen: Boolean,
    running: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .clickable(
                role = Role.Button,
                onClickLabel = if (running)
                    "Keskeytä ajastin"
                else "Käynnistä tai jatka ajastinta",
                onClick = onToggle
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(
                size.width / 2f,
                size.height / 2f
            )

            val outer = size.minDimension / 2f
            val face = outer - 32.dp.toPx()

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
                    color = if (major) DialInk
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
                        minute.toString(),
                        labelPosition.x,
                        labelPosition.y + paint.textSize * 0.35f,
                        paint
                    )
                }
            }

            drawLine(
                color = DialInk,
                start = center,
                end = Offset(center.x, center.y - face),
                strokeWidth = 1.2.dp.toPx()
            )

            if (!lastTen) {
                drawCircle(
                    color = DialInk,
                    radius = 5.5.dp.toPx(),
                    center = center
                )
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
                    color = DialInk
                )
            }
        }
    }
}