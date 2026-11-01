package fi.aikapolku.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * P0-22a – Suoritusnäkymä, jossa kuva on pääosassa (mallikuva hyväksytty 1.11.2026).
 *
 * Järjestys ylhäältä alas: nykyinen tehtävä (kuva + nimi) → Tarvitsen apua / Tarvitsen tauon
 * (koskevat aina nykyistä) → Valmis → Seuraavaksi → Ohita / Lisää.
 * Apu, tauko, Valmis ja ohitus eivät ole koskaan ohjaajan lukon takana.
 */

private object RunColors {
    val background = Color(0xFFF8FAFC)
    val card = Color(0xFFFFFFFF)
    val ink = Color(0xFF1F2937)
    val secondary = Color(0xFF475569)
    val border = Color(0xFFCBD5E1)
    val dotOutline = Color(0xFF94A3B8)
    val teal = Color(0xFF0F766E)
    val tealSoft = Color(0xFF5FA8A0)
    val timer = Color(0xFFC2413A)
    val timerTrack = Color(0xFFF1D9D6)
    val nextSurface = Color(0xFFEEF2F6)
}

/** Seuraavan vaiheen esikatselu: osavaihe tai seuraava tehtävä. */
internal data class RunNext(
    val name: String,
    val taskName: String,
    val taskPhoto: String,
    val isSubStep: Boolean
)

/** Ajan tila yläkulman aikamerkkiin. null = vaiheella ei ole aikaa. */
internal sealed class RunTime {
    data class Clock(val time: String) : RunTime()
    data class Countdown(
        val remainingMs: Long,
        val durationMs: Long,
        val running: Boolean,
        val displayMode: String,
        val pauseLocked: Boolean
    ) : RunTime()
}

@Composable
internal fun RunStepScreen(
    taskName: String,
    taskPhoto: String,
    description: String,
    subSteps: List<String>,
    subStepIndex: Int,
    taskIndex: Int,
    taskCount: Int,
    time: RunTime?,
    next: RunNext?,
    nextMode: String,
    onToggleTimer: () -> Unit,
    onHelp: () -> Unit,
    onBreak: () -> Unit,
    onDone: () -> Unit,
    onSkip: () -> Unit,
    onMore: () -> Unit
) {
    val hasSubSteps = subSteps.isNotEmpty()
    val safeIndex = subStepIndex.coerceIn(0, maxOf(0, subSteps.lastIndex))
    val currentSubStep = if (hasSubSteps) subSteps[safeIndex] else null
    val title = currentSubStep ?: taskName

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(RunColors.background)) {
        // Pystytilassa kaikki mahtuu aina ruudulle: kuva joustaa, jotta apu, tauko ja
        // Valmis näkyvät myös suurella fonttikoolla (puhelintesti 1.11.2026).
        // Vieritys vain vaakatilassa tai hyvin matalalla näytöllä.
        val compact = maxWidth > maxHeight || maxHeight < 440.dp
        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(if (compact) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .padding(bottom = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            RunHeader(
                hasSubSteps = hasSubSteps,
                taskName = taskName,
                taskIndex = taskIndex,
                taskCount = taskCount,
                time = time,
                onToggleTimer = onToggleTimer
            )

            if (hasSubSteps) {
                RunProgressDots(index = safeIndex, count = subSteps.size)
            }

            Card(
                modifier = if (compact) Modifier.fillMaxWidth() else Modifier.fillMaxWidth().weight(1f),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = RunColors.card),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .then(if (compact) Modifier.fillMaxWidth() else Modifier.fillMaxSize())
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically)
                ) {
                    val imageModifier = if (compact) {
                        Modifier.size(160.dp)
                    } else {
                        // Kuva täyttää vapaan tilan (enintään 340 dp, aina neliönä) ja pienenee
                        // tarvittaessa, jotta painikkeet mahtuvat näkyviin.
                        Modifier
                            .weight(1f, fill = false)
                            .sizeIn(maxWidth = 340.dp, maxHeight = 340.dp)
                            .aspectRatio(1f)
                    }
                    RunImage(
                        taskName = taskName,
                        taskPhoto = taskPhoto,
                        subStepName = currentSubStep,
                        modifier = imageModifier.clip(RoundedCornerShape(24.dp))
                    )
                    Text(
                        title,
                        modifier = Modifier.semantics { heading() },
                        fontSize = if (title.length > 14) 26.sp else 30.sp,
                        lineHeight = if (title.length > 14) 31.sp else 35.sp,
                        fontWeight = FontWeight.Bold,
                        color = RunColors.ink,
                        textAlign = TextAlign.Center,
                        maxLines = 2
                    )
                    if (!hasSubSteps && description.isNotBlank()) {
                        Text(
                            description,
                            fontSize = 18.sp,
                            lineHeight = 24.sp,
                            color = RunColors.secondary,
                            textAlign = TextAlign.Center,
                            maxLines = 3
                        )
                    }
                }
            }

            // Apu ja tauko koskevat aina nykyistä tehtävää: heti kuvan alla.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                RunSupportButton(
                    label = "Tarvitsen apua",
                    icon = { RunHelpIcon() },
                    onClick = onHelp,
                    modifier = Modifier.weight(1f)
                )
                RunSupportButton(
                    label = "Tarvitsen tauon",
                    icon = { RunPauseIcon(color = RunColors.ink) },
                    onClick = onBreak,
                    modifier = Modifier.weight(1f)
                )
            }

            Button(
                onClick = onDone,
                modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = RunColors.teal,
                    contentColor = Color.White
                )
            ) {
                Text("✓  Valmis", fontSize = 21.sp, fontWeight = FontWeight.Bold)
            }

            if (next != null) {
                RunNextRow(next = next, mode = nextMode)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(onClick = onSkip, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(
                        if (hasSubSteps) "Ohita vaihe" else "Ohita tehtävä",
                        color = RunColors.teal,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                TextButton(onClick = onMore, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Lisää", color = RunColors.teal, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun RunHeader(
    hasSubSteps: Boolean,
    taskName: String,
    taskIndex: Int,
    taskCount: Int,
    time: RunTime?,
    onToggleTimer: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "NYT",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
                color = RunColors.secondary
            )
            if (hasSubSteps) {
                Text(
                    taskName,
                    fontSize = 18.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = RunColors.ink,
                    maxLines = 2
                )
            } else {
                Text(
                    "Tehtävä ${taskIndex + 1} / $taskCount",
                    fontSize = 15.sp,
                    color = RunColors.secondary
                )
            }
        }
        when (time) {
            is RunTime.Clock -> RunClockPill(time.time)
            is RunTime.Countdown -> RunCountdownPill(time, onToggleTimer)
            null -> Unit
        }
    }
}

@Composable
private fun RunClockPill(time: String) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = RunColors.card,
        border = BorderStroke(1.dp, RunColors.border)
    ) {
        Text(
            "Klo $time",
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
private fun RunCountdownPill(time: RunTime.Countdown, onToggle: () -> Unit) {
    val seconds = ((time.remainingMs + 999L) / 1000L).coerceAtLeast(0L)
    val finished = time.remainingMs <= 0L
    val numbers = time.displayMode == P020TimeDisplay.NUMBERS
    val action = when {
        time.pauseLocked -> "🔒"
        time.running -> "Ⅱ"
        else -> "▶"
    }
    val spoken = buildString {
        if (finished) append("Aika päättyi. ")
        else if (numbers) append("Aikaa jäljellä ${seconds / 60} minuuttia ${seconds % 60} sekuntia. ")
        append(
            when {
                time.pauseLocked -> "Tauko vaatii ohjaajan PIN-koodin."
                time.running -> "Pysäytä aika."
                else -> "Aloita aika."
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
                calm = time.displayMode == P020TimeDisplay.CALM,
                finished = finished,
                modifier = Modifier.size(32.dp)
            )
            if (finished) {
                Text("Aika päättyi", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = RunColors.teal)
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

/** Eteneminen palloina ja tekstinä: ei vaadi numeroiden ymmärtämistä. */
@Composable
private fun RunProgressDots(index: Int, count: Int) {
    Row(
        modifier = Modifier.semantics(mergeDescendants = true) {
            contentDescription = "Vaihe ${index + 1} / $count"
        },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(if (count > 10) 4.dp else 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(count) { i ->
                val shape = RoundedCornerShape(5.dp)
                val base = Modifier
                    .height(10.dp)
                    .width(if (i == index) 22.dp else 10.dp)
                    .clip(shape)
                Box(
                    modifier = if (i <= index) base.background(RunColors.teal)
                        else base.border(2.dp, RunColors.dotOutline, shape)
                )
            }
        }
        Text("Vaihe ${index + 1} / $count", fontSize = 15.sp, color = RunColors.secondary)
    }
}

@Composable
private fun RunSupportButton(
    label: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier
) {
    // Kuvake tekstin yläpuolella: teksti mahtuu kahdelle riville myös suurella fontilla.
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 64.dp),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.5.dp, RunColors.border),
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = RunColors.card,
            contentColor = RunColors.ink
        )
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            icon()
            Text(
                label,
                fontSize = 15.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 2
            )
        }
    }
}

@Composable
private fun RunHelpIcon(iconSize: Int = 22, color: Color = RunColors.ink) {
    Box(
        modifier = Modifier.size(iconSize.dp).border((iconSize / 11f).coerceAtLeast(2f).dp, color, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text("?", fontSize = (iconSize * 0.62f).sp, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
private fun RunPauseIcon(iconSize: Int = 22, color: Color) {
    Canvas(modifier = Modifier.size(iconSize.dp)) {
        val stroke = 2.dp.toPx()
        drawCircle(color, size.minDimension / 2f - stroke / 2f, center, style = Stroke(stroke))
        val h = size.height * 0.28f
        val dx = size.width * 0.11f
        drawLine(color, Offset(center.x - dx, center.y - h), Offset(center.x - dx, center.y + h), stroke, StrokeCap.Round)
        drawLine(color, Offset(center.x + dx, center.y - h), Offset(center.x + dx, center.y + h), stroke, StrokeCap.Round)
    }
}

/** Seuraava vaihe himmennettynä Valmiin jälkeen. Ei painettavissa. Näkyvyys käyttäjän valinnan mukaan. */
@Composable
private fun RunNextRow(next: RunNext, mode: String) {
    if (mode == P021cPreferences.NEXT_HIDDEN) return
    val label = if (next.isSubStep) "Seuraavaksi" else "Seuraava tehtävä"
    Surface(
        color = RunColors.nextSurface,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { contentDescription = "$label: ${next.name}" }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (mode == P021cPreferences.NEXT_CARD) {
                RunImage(
                    taskName = next.taskName,
                    taskPhoto = next.taskPhoto,
                    subStepName = if (next.isSubStep) next.name else null,
                    modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).alpha(0.55f)
                )
            }
            Column {
                Text(label, fontSize = 13.sp, color = RunColors.secondary)
                Text(next.name, fontSize = 17.sp, fontWeight = FontWeight.Medium, color = RunColors.secondary)
            }
        }
    }
}

/** Osavaiheen oma kuva, jos sellainen on; muuten tehtävän kuva (oma valokuva tai valmis kortti). */
@Composable
internal fun RunImage(taskName: String, taskPhoto: String, subStepName: String?, modifier: Modifier) {
    val subStepImage = subStepName?.let { SubstepArt.imageRes(it) }
    if (subStepImage != null) {
        Image(
            painter = painterResource(subStepImage),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier
        )
    } else {
        P011Photo(taskName, taskPhoto, modifier)
    }
}

/**
 * Tarvitsen apua / Tarvitsen tauon: koko näytön viesti, jonka voi näyttää toiselle.
 * Ajastin on pysäytetty pyynnön yhteydessä. Jatka palauttaa tilanteen ilman PIN-koodia.
 */
@Composable
internal fun SupportMessageScreen(
    message: String,
    timerPaused: Boolean,
    onContinue: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val kind = if (message == "Tarvitsen tauon") P021cPreferences.BREAK else P021cPreferences.HELP
    val voiceMode = remember(message) { P021cPreferences.voiceMode(context, kind) }
    val recording = remember(message) { P021cPreferences.recording(context, kind) }
    val speech = remember(context) { P021dSpeech(context.applicationContext) }
    DisposableEffect(speech) {
        onDispose {
            speech.close()
            P017VoicePlayer.stop()
        }
    }
    fun playConfigured() {
        when (voiceMode) {
            P021cPreferences.AUDIO_TTS -> speech.speak(message)
            P021cPreferences.AUDIO_RECORDED ->
                if (recording.isNotBlank()) P017VoicePlayer.play(context, recording)
            else -> Unit
        }
    }
    // Käyttäjä on valinnut äänen asetuksista: yksi painallus riittää viestimään.
    LaunchedEffect(message, voiceMode, recording) { playConfigured() }
    // Takaisin-ele sulkee viestin eikä poistu päiväohjelmasta.
    BackHandler(onBack = onClose)

    val image = SubstepArt.messageImageRes(message)

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(RunColors.background)) {
        val compact = maxWidth > maxHeight || maxHeight < 440.dp
        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(if (compact) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .padding(bottom = 4.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column(modifier = Modifier.heightIn(min = 52.dp), verticalArrangement = Arrangement.Center) {
                Text(
                    "VIESTI",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    color = RunColors.secondary
                )
                Text(
                    if (timerPaused) "Ajastin on pysäytetty" else "Näytä viesti tarvittaessa toiselle",
                    fontSize = 15.sp,
                    color = RunColors.secondary
                )
            }

            Card(
                modifier = if (compact) Modifier.fillMaxWidth() else Modifier.fillMaxWidth().weight(1f),
                shape = RoundedCornerShape(28.dp),
                border = BorderStroke(3.dp, RunColors.tealSoft),
                colors = CardDefaults.cardColors(containerColor = RunColors.card)
            ) {
                Column(
                    modifier = Modifier
                        .then(if (compact) Modifier.fillMaxWidth() else Modifier.fillMaxSize())
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
                ) {
                    if (image != null) {
                        Image(
                            painter = painterResource(image),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = (if (compact) Modifier.size(160.dp) else Modifier
                                .weight(1f, fill = false)
                                .sizeIn(maxWidth = 320.dp, maxHeight = 320.dp)
                                .aspectRatio(1f))
                                .clip(RoundedCornerShape(24.dp))
                        )
                    } else {
                        RunHelpIcon(iconSize = 120, color = RunColors.teal)
                    }
                    Text(
                        message,
                        modifier = Modifier.semantics { heading() },
                        fontSize = 42.sp,
                        lineHeight = 48.sp,
                        fontWeight = FontWeight.Bold,
                        color = RunColors.ink,
                        textAlign = TextAlign.Center
                    )
                }
            }

            if (voiceMode != P021cPreferences.AUDIO_SILENT) {
                OutlinedButton(
                    onClick = { playConfigured() },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.5.dp, RunColors.border),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = RunColors.card,
                        contentColor = RunColors.ink
                    )
                ) {
                    Text("🔊  Toista viesti", fontSize = 17.sp, fontWeight = FontWeight.Medium)
                }
            }

            Button(
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = RunColors.teal,
                    contentColor = Color.White
                )
            ) {
                Text("▶  Jatka", fontSize = 21.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
