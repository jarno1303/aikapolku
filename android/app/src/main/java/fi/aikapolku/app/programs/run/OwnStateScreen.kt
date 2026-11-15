package fi.aikapolku.app.programs.run

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fi.aikapolku.app.R
import fi.aikapolku.app.data.OwnState
import fi.aikapolku.app.data.StateLevel
import fi.aikapolku.app.data.StateScale
import fi.aikapolku.app.link.ClientMessages
import fi.aikapolku.app.ui.AppColors
import kotlinx.coroutines.launch

internal object OwnStateTexts {
    fun question(scale: StateScale) = if (scale == StateScale.BATTERY) R.string.own_state_question_battery else R.string.own_state_question_faces

    fun label(scale: StateScale, level: StateLevel): Int = when (scale) {
        StateScale.FACES -> when (level) {
            StateLevel.GOOD -> R.string.own_state_face_good
            StateLevel.OK -> R.string.own_state_face_ok
            StateLevel.LOW -> R.string.own_state_face_low
        }
        StateScale.BATTERY -> when (level) {
            StateLevel.GOOD -> R.string.own_state_battery_good
            StateLevel.OK -> R.string.own_state_battery_ok
            StateLevel.LOW -> R.string.own_state_battery_low
        }
    }
}

/**
 * Oma tila: asiakas valitsee, miltä tuntuu (kasvot) tai paljonko jaksaa (akku). Tieto on asiakkaan
 * oma: se tallentuu vain tälle laitteelle. Asiakas voi näyttää sen aikuiselle tai lähettää
 * ohjaajan puhelimeen. Huonolla tai vähäisellä jaksamisella tarjotaan tauko ja apu.
 * Painikkeita enintään viisi kerrallaan (laatuportti R2).
 */
@Composable
internal fun OwnStateFlow(
    onClose: () -> Unit,
    onBreak: (() -> Unit)? = null,
    onHelp: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scale = remember { OwnState.scale(context) }
    var level by rememberSaveable { mutableStateOf<String?>(null) }
    var showAdult by rememberSaveable { mutableStateOf(false) }
    var sent by rememberSaveable { mutableStateOf<fi.aikapolku.app.link.SendResult?>(null) }
    val scope = rememberCoroutineScope()
    val chosen = StateLevel.fromKey(level)
    // Matala ruutu (puhelin vaakatasossa): vaihtoehdot rinnakkain, tulos ja painikkeet vierekkäin.
    val low = fi.aikapolku.app.ui.windowSize() == fi.aikapolku.app.ui.WindowSize.LOW
    Column(
        modifier = modifier.fillMaxSize().background(RunColors.background).padding(bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when {
            chosen == null -> {
                Text(
                    stringResource(OwnStateTexts.question(scale)),
                    modifier = Modifier.fillMaxWidth().semantics { heading() },
                    fontSize = 28.sp, fontWeight = FontWeight.Bold, color = RunColors.ink, textAlign = TextAlign.Center
                )
                if (low) {
                    Row(Modifier.fillMaxWidth().weight(1f, fill = false), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StateLevel.entries.forEach { l ->
                            val label = stringResource(OwnStateTexts.label(scale, l))
                            Card(
                                modifier = Modifier.weight(1f).heightIn(min = 72.dp)
                                    .clip(RoundedCornerShape(20.dp))
                                    .clickable(role = Role.Button, onClickLabel = label) {
                                        OwnState.record(context, l)
                                        level = l.key
                                    },
                                shape = RoundedCornerShape(20.dp),
                                border = BorderStroke(2.dp, RunColors.border),
                                colors = CardDefaults.cardColors(containerColor = RunColors.card)
                            ) {
                                Column(
                                    Modifier.fillMaxWidth().padding(10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    StateIcon(scale, l, 64.dp)
                                    Text(label, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = RunColors.ink, textAlign = TextAlign.Center)
                                }
                            }
                        }
                    }
                } else StateLevel.entries.forEach { l ->
                    val label = stringResource(OwnStateTexts.label(scale, l))
                    Card(
                        modifier = Modifier.fillMaxWidth().weight(1f, fill = false).heightIn(min = 72.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .clickable(role = Role.Button, onClickLabel = label) {
                                OwnState.record(context, l)
                                level = l.key
                            },
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(2.dp, RunColors.border),
                        colors = CardDefaults.cardColors(containerColor = RunColors.card)
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            StateIcon(scale, l, 64.dp)
                            Text(label, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = RunColors.ink)
                        }
                    }
                }
                TextButton(onClick = onClose, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.own_state_skip), fontSize = 17.sp, color = RunColors.teal)
                }
            }
            showAdult -> {
                // Iso kortti aikuiselle näytettäväksi.
                Spacer(Modifier.weight(0.2f))
                StateIcon(scale, chosen, if (low) 96.dp else 180.dp)
                Text(
                    stringResource(R.string.own_state_card, stringResource(OwnStateTexts.label(scale, chosen))),
                    fontSize = if (low) 28.sp else 40.sp, lineHeight = if (low) 34.sp else 46.sp, fontWeight = FontWeight.Bold, color = RunColors.ink, textAlign = TextAlign.Center
                )
                Spacer(Modifier.weight(0.3f))
                BigButton(stringResource(R.string.own_state_back)) { showAdult = false }
            }
            else -> {
                val info: @Composable ColumnScope.() -> Unit = {
                StateIcon(scale, chosen, if (low) 80.dp else 120.dp)
                Text(
                    stringResource(R.string.own_state_card, stringResource(OwnStateTexts.label(scale, chosen))),
                    fontSize = 28.sp, fontWeight = FontWeight.Bold, color = RunColors.ink, textAlign = TextAlign.Center
                )
                Text(stringResource(R.string.own_state_private), fontSize = 15.sp, color = RunColors.secondary, textAlign = TextAlign.Center)
                sent?.let {
                    val ok = it == fi.aikapolku.app.link.SendResult.DELIVERED
                    Text(
                        stringResource(
                            when (it) {
                                fi.aikapolku.app.link.SendResult.DELIVERED -> R.string.link_sent_ok
                                fi.aikapolku.app.link.SendResult.QUEUED -> R.string.link_queued
                                fi.aikapolku.app.link.SendResult.FAILED -> R.string.link_sent_failed
                            }
                        ),
                        fontSize = 16.sp, fontWeight = FontWeight.Bold,
                        color = if (ok) RunColors.teal else AppColors.Coral, textAlign = TextAlign.Center
                    )
                }
                }
                val actions: @Composable ColumnScope.() -> Unit = {
                if (chosen == StateLevel.LOW) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        onBreak?.let { SmallAction(stringResource(R.string.support_break), Modifier.weight(1f), it) }
                        onHelp?.let { SmallAction(stringResource(R.string.support_help), Modifier.weight(1f), it) }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SmallAction(stringResource(R.string.own_state_show_adult), Modifier.weight(1f)) { showAdult = true }
                    if (ClientMessages.canSend(context)) {
                        SmallAction(stringResource(R.string.link_tell_caregiver), Modifier.weight(1f)) {
                            val text = context.getString(R.string.own_state_card, context.getString(OwnStateTexts.label(scale, chosen)))
                            scope.launch { sent = ClientMessages.send(context, ClientMessages.KIND_STATE, text) }
                        }
                    }
                }
                BigButton(stringResource(R.string.own_state_done), onClose)
                }
                if (low) {
                    Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Column(
                            Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) { info() }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically)) { actions() }
                    }
                } else {
                    info()
                    Spacer(Modifier.weight(1f))
                    actions()
                }
            }
        }
    }
}

@Composable
private fun SmallAction(text: String, modifier: Modifier, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 56.dp),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(2.dp, RunColors.teal),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = RunColors.card, contentColor = RunColors.teal)
    ) { Text(text, fontSize = 16.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center) }
}

@Composable
private fun BigButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(containerColor = RunColors.teal, contentColor = Color.White)
    ) { Text(text, fontSize = 21.sp, fontWeight = FontWeight.Bold) }
}

/** Piirretty kuvake: kasvot (hymy / suora / alaspäin) tai akku (täynnä / puolillaan / vähissä). */
@Composable
internal fun StateIcon(scale: StateScale, level: StateLevel, size: Dp) {
    val color = when (level) {
        StateLevel.GOOD -> AppColors.Teal
        StateLevel.OK -> Color(0xFFB7791F)
        StateLevel.LOW -> AppColors.Coral
    }
    val ink = AppColors.Ink
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val stroke = w * 0.06f
        if (scale == StateScale.FACES) {
            drawCircle(color.copy(alpha = 0.18f), radius = w / 2f)
            drawCircle(color, radius = w / 2f - stroke / 2f, style = Stroke(stroke))
            drawCircle(ink, radius = w * 0.06f, center = Offset(w * 0.35f, w * 0.40f))
            drawCircle(ink, radius = w * 0.06f, center = Offset(w * 0.65f, w * 0.40f))
            val mouth = Path().apply {
                moveTo(w * 0.30f, w * 0.66f)
                when (level) {
                    StateLevel.GOOD -> quadraticBezierTo(w * 0.50f, w * 0.86f, w * 0.70f, w * 0.66f)
                    StateLevel.OK -> lineTo(w * 0.70f, w * 0.66f)
                    StateLevel.LOW -> quadraticBezierTo(w * 0.50f, w * 0.50f, w * 0.70f, w * 0.70f)
                }
            }
            drawPath(mouth, ink, style = Stroke(stroke, cap = StrokeCap.Round))
        } else {
            val bodyTop = w * 0.25f
            val bodyH = w * 0.50f
            val bodyW = w * 0.80f
            drawRoundRect(ink, topLeft = Offset(w * 0.04f, bodyTop), size = Size(bodyW, bodyH), cornerRadius = CornerRadius(w * 0.08f), style = Stroke(stroke))
            drawRoundRect(ink, topLeft = Offset(w * 0.86f, w * 0.40f), size = Size(w * 0.10f, w * 0.20f), cornerRadius = CornerRadius(w * 0.03f))
            val fill = when (level) { StateLevel.GOOD -> 1f; StateLevel.OK -> 0.5f; StateLevel.LOW -> 0.18f }
            val pad = stroke * 1.4f
            drawRoundRect(
                color,
                topLeft = Offset(w * 0.04f + pad, bodyTop + pad),
                size = Size((bodyW - 2 * pad) * fill, bodyH - 2 * pad),
                cornerRadius = CornerRadius(w * 0.04f)
            )
        }
    }
}
