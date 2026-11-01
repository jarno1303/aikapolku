package fi.aikapolku.app

import android.media.AudioManager
import android.media.ToneGenerator
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.sin
import java.util.Locale

private val DialRed = Color(0xFFF07868)
private val DialInk = Color(0xFF1F2937)
private val DialMuted = Color(0xFF64717A)
private val DialTeal = Color(0xFF0F766E)

@Composable
internal fun P013TimerScreen(
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
    onReset: () -> Unit
) {
    val guided = activeTask.isNotBlank()
    val finished = guided && remaining == 0L

    // P0-21e: ajan säätö vain pysäytettynä ja ohjaajana; nollaus ja asetukset ohjaajalle.
    val caregiver = LocalCaregiver.current
    val canAdjust = !running && caregiver.allows(AppAction.ADJUST_TIME)
    val showReset = caregiver.allows(AppAction.RESET_TIMER)
    val showSettings = caregiver.allows(AppAction.SETTINGS)
    // Tauko vaatii lukittuna PIN-koodin; Aloita ja Jatka ovat aina vapaita.
    val pauseLocked = running && !caregiver.allows(AppAction.PAUSE_TIMER)
    val currentRunning by rememberUpdatedState(running)
    val startPause: () -> Unit = {
        if (pauseLocked) {
            caregiver.run(AppAction.PAUSE_TIMER) { if (currentRunning) onStartPause() }
        } else {
            onStartPause()
        }
    }


    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var confirmReset by rememberSaveable { mutableStateOf(false) }

    val context = LocalContext.current
    val preferences = remember(context) {
        context.applicationContext.getSharedPreferences(
            "aikapolku_timer_preferences",
            android.content.Context.MODE_PRIVATE
        )
    }

    var endSound by rememberSaveable {
        mutableStateOf(
            preferences.getString("end_sound", "off") ?: "off"
        )
    }

    val soundScope = rememberCoroutineScope()
    var timeMode by rememberSaveable {
        mutableStateOf(P020TimeDisplay.load(context))
    }

    val landscape = LocalConfiguration.current.orientation ==
        android.content.res.Configuration.ORIENTATION_LANDSCAPE

    if (!finished && landscape) {
        P017LandscapeTimer(
            remaining = remaining,
            duration = duration,
            timeMode = timeMode,
            running = running,
            showLastTen = showLastTen,
            activeTask = activeTask,
            firstPhoto = firstPhoto,
            nextActivity = nextActivity,
            nextPhoto = nextPhoto,
            onPreset = onPreset,
            onStartPause = startPause,
            pauseLocked = pauseLocked,
            onResetClick = { confirmReset = true },
            onSettingsClick = { settingsOpen = true },
            canAdjust = canAdjust,
            showReset = showReset,
            showSettings = showSettings
        )
    } else if (finished) {
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

            // Pikavalinnat näkyvät vain, kun ajastin on pysähdyksissä ja ajan saa muuttaa.
            if (canAdjust) {
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

            Box(
                modifier = Modifier.size(diameter),
                contentAlignment = Alignment.Center
            ) {
                if (timeMode == P020TimeDisplay.NUMBERS) {
                    P013ClockDial(
                        remaining = remaining,
                        seconds = secs,
                        lastTen = lastTen,
                        onAdjustMinutes = { minutes -> if (canAdjust) onPreset(minutes) },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    P020TimeVisual(
                        remaining = remaining,
                        duration = duration,
                        mode = timeMode,
                        running = running,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                if (timeMode == P020TimeDisplay.NUMBERS && !lastTen) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = Color.White.copy(alpha = 0.96f)
                    ) {
                        Text(
                            String.format(
                                Locale.getDefault(),
                                "%02d:%02d",
                                secs / 60,
                                secs % 60
                            ),
                            modifier = Modifier.padding(horizontal = 15.dp, vertical = 5.dp),
                            fontSize = 35.sp,
                            fontWeight = FontWeight.Bold,
                            color = DialInk,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (showReset) {
                    IconButton(
                        onClick = { confirmReset = true },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Text(
                            "↺",
                            fontSize = 32.sp,
                            color = DialInk
                        )
                    }
                    Text(
                        "Nollaa",
                        fontSize = 12.sp,
                        color = DialMuted
                    )
                } else {
                    CaregiverLockButton()
                }
            }
            androidx.compose.material3.IconButton(
                onClick = startPause,
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(DialInk)
            ) {
                Text(
                    when {
                        pauseLocked -> "🔒"
                        running -> "Ⅱ"
                        else -> "▶"
                    },
                    fontSize = if (pauseLocked) 28.sp else if (running) 39.sp else 30.sp,
                    color = Color.White
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (showSettings) {
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
    }

    // Jos lukitus menee päälle ikkunan ollessa auki, ikkuna suljetaan.
    LaunchedEffect(showSettings, showReset) {
        if (!showSettings) settingsOpen = false
        if (!showReset) confirmReset = false
    }

    if (settingsOpen) {
        AlertDialog(
            onDismissRequest = { settingsOpen = false },
            title = { Text("Ajastimen asetukset") },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 460.dp)
                        .verticalScroll(androidx.compose.foundation.rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    P020TimeModeChoices(timeMode) { mode ->
                        timeMode = mode
                        P020TimeDisplay.save(context, mode)
                    }
                    HorizontalDivider()

                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Viimeisten 10 sekunnin laskenta",
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = showLastTen,
                            onCheckedChange = onShowLastTen,
                            enabled = timeMode == P020TimeDisplay.NUMBERS
                        )
                    }

                    HorizontalDivider()

                    Text(
                        "Äänimerkki ajan päättyessä",
                        fontWeight = FontWeight.SemiBold
                    )

                    listOf(
                        "off" to "Ei ääntä",
                        "short" to "Lyhyt merkkiääni",
                        "clear" to "Selkeä piippaus"
                    ).forEach { (value, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .clickable {
                                    endSound = value
                                    preferences.edit()
                                        .putString("end_sound", value)
                                        .apply()
                                },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = endSound == value,
                                onClick = null
                            )
                            Text(label)
                        }
                    }

                    TextButton(
                        onClick = {
                            soundScope.launch {
                                p016PlaySound(endSound)
                            }
                        },
                        enabled = endSound != "off"
                    ) {
                        Text("▶ Kuuntele äänimerkki")
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { settingsOpen = false }
                ) {
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
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(50.dp),
                color = if (label == "ENSIN")
                    DialTeal else Color(0xFFE7F5F1)
            ) {
                Text(
                    label,
                    modifier = Modifier.padding(
                        horizontal = 14.dp,
                        vertical = 5.dp
                    ),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (label == "ENSIN")
                        Color.White else DialTeal
                )
            }

            P011Photo(
                name = name,
                imagePath = photo,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(88.dp)
                    .clip(RoundedCornerShape(12.dp))
            )

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
internal fun P013ClockDial(
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
                        val minute = p013MinuteFromPoint(
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
                        val minute = p013MinuteFromPoint(
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
                    color = DialInk
                )
            }
        }
    }
}
internal fun p013MinuteFromPoint(
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
private suspend fun p015bPlaySound(mode: String) {
    if (mode == "off") return

    val generator = runCatching {
        ToneGenerator(
            AudioManager.STREAM_NOTIFICATION,
            65
        )
    }.getOrNull() ?: return

    try {
        val tone = if (mode == "short")
            ToneGenerator.TONE_PROP_ACK
        else
            ToneGenerator.TONE_PROP_BEEP

        val durationMs = if (mode == "short") 220 else 500

        generator.startTone(tone, durationMs)
        delay(durationMs.toLong() + 120L)
    } finally {
        generator.release()
    }
}