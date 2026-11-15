package fi.aikapolku.app.week

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fi.aikapolku.app.R
import fi.aikapolku.app.cards.CardArt
import fi.aikapolku.app.cards.CardPhoto
import fi.aikapolku.app.data.ChoiceOption
import fi.aikapolku.app.data.WaitState
import fi.aikapolku.app.data.Waiting
import fi.aikapolku.app.ui.AppColors
import kotlinx.coroutines.delay

/**
 * Asiakkaan odotusnäkymä: mitä odotetaan, rauhallinen kuvio (kesto ei tiedossa) tai hitaasti
 * vähenevä kaari (kesto tiedossa; ei sekuntilaskentaa) ja "Odottaessa voit" -kortit.
 * Odotus päättyy, kun ohjaaja painaa "Nyt mennään".
 */
@Composable
internal fun WaitingView(state: WaitState, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val options = remember(Waiting.revision) { Waiting.options(context) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(state) {
        while (true) {
            now = System.currentTimeMillis()
            delay(5_000L)
        }
    }
    var picked by rememberSaveable { mutableStateOf(-1) }
    val fraction = state.fractionLeft(now)
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            stringResource(R.string.wait_title, state.name),
            modifier = Modifier.fillMaxWidth().semantics { heading() },
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = AppColors.Ink,
            textAlign = TextAlign.Center
        )
        Box(Modifier.fillMaxWidth().sizeIn(maxWidth = 260.dp, maxHeight = 260.dp).aspectRatio(1f), contentAlignment = Alignment.Center) {
            CalmWaitVisual(fraction, Modifier.fillMaxSize())
            CardPhoto(state.name, state.photo, Modifier.fillMaxSize(0.62f).aspectRatio(1f).clip(RoundedCornerShape(22.dp)))
        }
        Text(
            stringResource(if (fraction != null && fraction <= 0f) R.string.wait_a_little_more else R.string.wait_adult_tells),
            fontSize = 19.sp,
            color = AppColors.Ink,
            textAlign = TextAlign.Center
        )
        if (options.isNotEmpty()) {
            Text(stringResource(R.string.wait_you_can), fontSize = 17.sp, fontWeight = FontWeight.Bold, color = AppColors.Teal)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                options.forEachIndexed { i, option ->
                    val isPicked = picked == i
                    Card(
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(16.dp))
                            .semantics { contentDescription = option.name; selected = isPicked }
                            .clickable(role = Role.RadioButton) { picked = i },
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(if (isPicked) 4.dp else 1.dp, if (isPicked) AppColors.Teal else AppColors.Border),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Column(Modifier.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            CardPhoto(option.name, option.photo, Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp)))
                            Text(option.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

/** Hidas "hengittävä" ympyrä tai (kesto tiedossa) vähenevä kaari. Ei numeroita. */
@Composable
private fun CalmWaitVisual(fraction: Float?, modifier: Modifier) {
    val transition = rememberInfiniteTransition(label = "wait")
    val breath by transition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4_000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breath"
    )
    val teal = AppColors.Teal
    val soft = AppColors.TealSoft
    Canvas(modifier.scale(if (fraction == null) breath else 1f)) {
        val stroke = 14.dp.toPx()
        val r = size.minDimension / 2f - stroke
        val topLeft = Offset(center.x - r, center.y - r)
        drawCircle(soft, radius = r + stroke / 2f, center = center)
        if (fraction == null) {
            drawCircle(teal.copy(alpha = 0.55f), radius = r, center = center, style = Stroke(stroke))
        } else if (fraction > 0f) {
            drawArc(
                teal, startAngle = -90f, sweepAngle = 360f * fraction, useCenter = false,
                topLeft = topLeft, size = Size(r * 2f, r * 2f), style = Stroke(stroke, cap = StrokeCap.Round)
            )
        }
    }
}

/** Ohjaajan etusivulla: odotus käynnissä ja "Nyt mennään". */
@Composable
internal fun WaitingCaregiverCard() {
    val context = LocalContext.current
    val state = remember(Waiting.revision) { Waiting.current(context) } ?: return
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(2.dp, AppColors.Teal),
        colors = CardDefaults.cardColors(containerColor = AppColors.TealSoft)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CardPhoto(state.name, state.photo, Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)))
            Text(stringResource(R.string.wait_running, state.name), modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, color = AppColors.Ink)
            Button(
                onClick = { Waiting.stop(context) },
                modifier = Modifier.heightIn(min = 48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.Teal)
            ) { Text(stringResource(R.string.wait_go_now)) }
        }
    }
}

/** Ohjaaja aloittaa odotuksen: mitä odotetaan, kesto (tai ei tiedossa) ja "Odottaessa voit" -kortit. */
@Composable
internal fun WaitStartDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val quick = remember { listOf("waiting", "leaving", "lunch", "outdoors").mapNotNull { id -> CardArt.templates.firstOrNull { it.id == id } } }
    var name by rememberSaveable { mutableStateOf(context.getString(quick.first().nameRes)) }
    var photo by rememberSaveable { mutableStateOf("builtin:${quick.first().id}") }
    var minutes by rememberSaveable { mutableStateOf(0) }
    var options by remember { mutableStateOf(Waiting.options(context)) }
    var pick by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.wait_start_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.wait_what), fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    quick.forEach { t ->
                        val label = stringResource(t.nameRes)
                        FilterChip(selected = photo == "builtin:${t.id}", onClick = { name = label; photo = "builtin:${t.id}" }, label = { Text(label, fontSize = 13.sp) })
                    }
                }
                TextButton(onClick = { pick = "what" }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.wait_pick_card, name))
                }
                Text(stringResource(R.string.wait_how_long), fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Waiting.DURATIONS.forEach { m ->
                        FilterChip(
                            selected = minutes == m,
                            onClick = { minutes = m },
                            label = { Text(if (m == 0) stringResource(R.string.wait_unknown) else stringResource(R.string.timer_minutes_big, m), fontSize = 13.sp) }
                        )
                    }
                }
                Text(stringResource(R.string.wait_you_can_setting), fontWeight = FontWeight.Bold)
                options.forEachIndexed { i, o ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CardPhoto(o.name, o.photo, Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)))
                        Text(o.name, modifier = Modifier.weight(1f))
                        TextButton(onClick = { options = options.filterIndexed { j, _ -> j != i } }) { Text(stringResource(R.string.wait_remove)) }
                    }
                }
                if (options.size < 3) {
                    TextButton(onClick = { pick = "option" }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.wait_add_option)) }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                Waiting.setOptions(context, options)
                Waiting.start(context, name, photo, minutes)
                onDismiss()
            }) { Text(stringResource(R.string.wait_start)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
    pick?.let { target ->
        fi.aikapolku.app.cards.CardLibraryDialog(
            onDismiss = { pick = null },
            onSelect = { card ->
                if (target == "what") {
                    name = card.name
                    photo = card.photoPath
                } else {
                    options = (options + ChoiceOption(card.name, card.photoPath, card.voicePath)).take(3)
                }
                pick = null
            }
        )
    }
}
