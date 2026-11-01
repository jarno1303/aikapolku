package fi.aikapolku.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

@Composable
internal fun P011TimerScreen(
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
            activeTask,
            nextActivity,
            nextPhoto
        )

        Button(
            onClick = onGoNext,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = P011Colors.teal
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
            P011PairCards(
                activeTask,
                firstPhoto,
                nextActivity,
                nextPhoto
            )
        } else {
            Text(
                "Ajastin",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = P011Colors.ink
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(1, 2, 5, 10, 15, 30, 60).forEach { value ->
                    OutlinedButton(
                        onClick = { onPreset(value) }
                    ) {
                        Text("$value min")
                    }
                }
            }
        }

        val seconds = ((remaining + 999L) / 1000L).toInt()
        val countdown = showLastTen && running && seconds in 1..10
        val time = if (countdown) seconds.toString() else {
            String.format(
                Locale.getDefault(),
                "%02d:%02d",
                seconds / 60,
                seconds % 60
            )
        }

        val progress = (
            remaining.toFloat() /
            duration.coerceAtLeast(1L).toFloat()
        ).coerceIn(0f, 1f)

        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            val screenHeight = LocalConfiguration.current.screenHeightDp

            val diameter = when {
                maxWidth < 335.dp -> 212.dp
                screenHeight < 700 -> 222.dp
                maxWidth >= 400.dp && screenHeight >= 800 -> 280.dp
                else -> 252.dp
            }

            Box(
                modifier = Modifier
                    .size(diameter)
                    .shadow(6.dp, CircleShape)
                    .clip(CircleShape)
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val inset = 19.dp.toPx()
                    val stroke = 12.dp.toPx()
                    val drawingSize = Size(
                        size.width - inset * 2f,
                        size.height - inset * 2f
                    )
                    val topLeft = Offset(inset, inset)

                    drawCircle(
                        color = Color(0xFFF3FAF8),
                        radius = size.minDimension / 2f - 3.dp.toPx()
                    )

                    drawCircle(
                        color = Color.White,
                        radius = size.minDimension / 2f - 10.dp.toPx()
                    )

                    drawArc(
                        color = Color(0xFFDDEDE9),
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = drawingSize,
                        style = Stroke(stroke, cap = StrokeCap.Round)
                    )

                    if (progress > 0f) {
                        drawArc(
                            brush = Brush.sweepGradient(
                                if (countdown) {
                                    listOf(
                                        P011Colors.coral,
                                        Color(0xFFF17A61),
                                        P011Colors.coral
                                    )
                                } else {
                                    listOf(
                                        P011Colors.teal,
                                        P011Colors.aqua,
                                        P011Colors.teal
                                    )
                                }
                            ),
                            startAngle = -90f,
                            sweepAngle = 360f * progress,
                            useCenter = false,
                            topLeft = topLeft,
                            size = drawingSize,
                            style = Stroke(
                                width = stroke,
                                cap = StrokeCap.Round
                            )
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            role = Role.Button,
                            onClickLabel = if (running)
                                "Tauko"
                            else "Aloita tai jatka",
                            onClick = onStartPause
                        )
                )

                Column(
                    modifier = Modifier.offset(y = (-18).dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        time,
                        fontSize = if (countdown) 62.sp else 40.sp,
                        lineHeight = if (countdown) 70.sp else 48.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (countdown)
                            P011Colors.coral else P011Colors.ink,
                        maxLines = 1
                    )

                    Text(
                        if (countdown) "SEKUNTIA"
                        else "AIKAA JÄLJELLÄ",
                        fontSize = 11.sp,
                        color = P011Colors.muted,
                        letterSpacing = 0.6.sp
                    )

                    Spacer(Modifier.height(4.dp))

                    Text(
                        if (running) "Ⅱ Tauko"
                        else if (remaining in 1 until duration)
                            "▶ Jatka"
                        else "▶ Aloita",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = P011Colors.teal
                    )
                }

                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box {
                        IconButton(
                            onClick = { moreOpen = true },
                            modifier = Modifier
                                .size(48.dp)
                                .semantics {
                                    contentDescription = "Ajan säätö"
                                }
                        ) {
                            Surface(
                                color = P011Colors.soft,
                                shape = CircleShape
                            ) {
                                Box(
                                    Modifier.size(42.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "⋯",
                                        fontSize = 27.sp,
                                        color = P011Colors.teal
                                    )
                                }
                            }
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

                    IconButton(
                        onClick = { settingsOpen = true },
                        modifier = Modifier
                            .size(48.dp)
                            .semantics {
                                contentDescription = "Ajastimen asetukset"
                            }
                    ) {
                        Surface(
                            color = P011Colors.soft,
                            shape = CircleShape
                        ) {
                            Box(
                                Modifier.size(42.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "⚙",
                                    fontSize = 22.sp,
                                    color = P011Colors.teal
                                )
                            }
                        }
                    }
                }
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
                        "Viimeiset 10 sekuntia",
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
                TextButton(
                    onClick = {
                        confirmReset = false
                        onReset()
                    }
                ) { Text("Nollaa") }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) {
                    Text("Peruuta")
                }
            }
        )
    }
}