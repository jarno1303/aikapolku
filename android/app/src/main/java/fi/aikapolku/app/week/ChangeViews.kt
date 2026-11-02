package fi.aikapolku.app.week

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fi.aikapolku.app.R
import fi.aikapolku.app.data.AckStore
import fi.aikapolku.app.data.ChangeReveal
import fi.aikapolku.app.data.ChangeStore
import fi.aikapolku.app.data.ChangeType
import fi.aikapolku.app.data.DayChanges
import fi.aikapolku.app.data.DayProgram
import fi.aikapolku.app.data.EventChange
import fi.aikapolku.app.data.ProgramStore
import fi.aikapolku.app.data.WeekEvent
import fi.aikapolku.app.data.WeekPlan
import fi.aikapolku.app.data.WeekSchedule
import fi.aikapolku.app.data.WeekStore
import fi.aikapolku.app.ui.AppColors
import fi.aikapolku.app.ui.AppIcons
import fi.aikapolku.app.ui.ChangeSymbol
import fi.aikapolku.app.ui.PrimaryButton
import fi.aikapolku.app.ui.SecondaryButton
import fi.aikapolku.app.voice.VoicePlayer
import java.time.LocalDate
import java.time.LocalDateTime

/** Muutoksen lyhyt kuvaus: "Uinti on peruttu", "Uinti → Ulkoilu", "Uinti klo 13.00". */
@Composable
internal fun changeSummary(change: EventChange, original: ResolvedEvent, effective: ResolvedEvent): String =
    when (change.type) {
        ChangeType.CANCEL -> stringResource(R.string.change_summary_cancel, original.title)
        ChangeType.REPLACE ->
            if (change.showOriginal) stringResource(R.string.change_summary_replace, original.title, effective.title)
            else stringResource(R.string.change_summary_replace_short, effective.title)
        ChangeType.MOVE -> stringResource(R.string.change_summary_move, effective.title, effective.event.time.replace(':', '.'))
    }

@Composable
private fun dayLabel(date: LocalDate): String =
    if (date == LocalDate.now().plusDays(1)) stringResource(R.string.change_day_tomorrow) else stringResource(R.string.change_day_today)

/**
 * Aloitusnäkymän muutosrivit: käyttäjälle jo näkyvät, kuittaamattomat muutokset tältä
 * päivältä ja huomiselta. Palauttaa true, jos jokin rivi näytettiin.
 */
@Composable
internal fun ChangeAnnouncements(onOpen: (EventChange) -> Unit): Boolean {
    val context = LocalContext.current
    val items = remember(context) {
        val plan = WeekStore(context).load()
        val programs = ProgramStore(context).load()
        val acks = AckStore(context)
        DayChanges.announced(plan, ChangeStore(context).load(), LocalDateTime.now()) { acks.ackTime(it.date, it.ackId) != null }
            .map { (change, original) ->
                Triple(change, resolveEvent(original, programs), resolveEvent(DayChanges.effective(original, change), programs))
            }
    }
    items.forEach { (change, original, effective) ->
        ChangeRow(dayLabel(change.date), changeSummary(change, original, effective)) { onOpen(change) }
    }
    return items.isNotEmpty()
}

@Composable
private fun ChangeRow(day: String, summary: String, onClick: () -> Unit) {
    val label = stringResource(R.string.change_label)
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = AppColors.ChangeSoft,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .border(2.dp, AppColors.ChangeFill, RoundedCornerShape(16.dp))
            .semantics(mergeDescendants = true) { contentDescription = "$label, $day: $summary" }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ChangeSymbol(Modifier.size(38.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("$label · $day", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = AppColors.Change)
                Text(summary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = AppColors.Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Text("›", fontSize = 26.sp, color = AppColors.Change)
        }
    }
}

/** Kuva, jonka päällä ruksi: korvattu tai peruttu toiminta. */
@Composable
private fun CrossedImage(item: ResolvedEvent, modifier: Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        EventImage(item, Modifier.fillMaxSize().clip(RoundedCornerShape(20.dp)).alpha(0.4f))
        Canvas(Modifier.fillMaxSize().padding(14.dp)) {
            val w = 9.dp.toPx()
            drawLine(AppColors.ChangeFill, Offset(0f, 0f), Offset(size.width, size.height), w, StrokeCap.Round)
            drawLine(AppColors.ChangeFill, Offset(size.width, 0f), Offset(0f, size.height), w, StrokeCap.Round)
        }
    }
}

@Composable
private fun ImageWithName(item: ResolvedEvent, size: Int, crossed: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val modifier = Modifier.sizeIn(maxWidth = size.dp, maxHeight = size.dp).aspectRatio(1f)
        if (crossed) CrossedImage(item, modifier) else EventImage(item, modifier.clip(RoundedCornerShape(20.dp)))
        Text(
            item.title,
            fontSize = if (crossed) 18.sp else 24.sp,
            fontWeight = FontWeight.Bold,
            color = if (crossed) AppColors.Muted else AppColors.Ink,
            textDecoration = if (crossed) TextDecoration.LineThrough else null,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Käyttäjän muutosnäkymä: muutosmerkki, mitä muuttui, syy (teksti ja ääni) ja "Selvä".
 * Selvä kuittaa muutoksen nähdyksi (näkyy ohjaajalle).
 */
@Composable
internal fun ChangeScreen(changeId: String, onDone: () -> Unit) {
    val context = LocalContext.current
    val data = remember(changeId) {
        val change = ChangeStore(context).load().firstOrNull { it.id == changeId }
        val original = change?.let { c -> WeekStore(context).load().events.firstOrNull { it.id == c.eventId } }
        val programs = ProgramStore(context).load()
        if (change == null || original == null) null
        else Triple(change, resolveEvent(original, programs), resolveEvent(DayChanges.effective(original, change), programs))
    }
    if (data == null) {
        Text(stringResource(R.string.week_no_events), color = AppColors.Muted)
        return
    }
    val (change, original, effective) = data
    LaunchedEffect(changeId) {
        if (change.autoPlayVoice && change.voicePath.isNotBlank()) VoicePlayer.play(context, change.voicePath)
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.semantics(mergeDescendants = true) { heading() }
        ) {
            ChangeSymbol(Modifier.size(56.dp))
            Column {
                Text(stringResource(R.string.change_label), fontSize = 28.sp, fontWeight = FontWeight.Bold, color = AppColors.Change)
                Text(dayLabel(change.date), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = AppColors.Muted)
            }
        }

        when (change.type) {
            ChangeType.CANCEL -> {
                ImageWithName(original, 200, crossed = true)
                Text(stringResource(R.string.change_cancelled_text), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = AppColors.Ink)
            }
            ChangeType.REPLACE -> if (change.showOriginal) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { ImageWithName(original, 130, crossed = true) }
                    Box(
                        modifier = Modifier.size(40.dp).clip(RoundedCornerShape(50)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, tint = AppColors.Change, modifier = Modifier.size(32.dp))
                    }
                    Box(Modifier.weight(1.2f), contentAlignment = Alignment.Center) { ImageWithName(effective, 170, crossed = false) }
                }
            } else {
                ImageWithName(effective, 240, crossed = false)
            }
            ChangeType.MOVE -> {
                ImageWithName(effective, 200, crossed = false)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (change.showOriginal) {
                        Text(
                            stringResource(R.string.change_time_now, original.event.time.replace(':', '.')),
                            fontSize = 20.sp,
                            color = AppColors.Muted,
                            textDecoration = TextDecoration.LineThrough
                        )
                        Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, tint = AppColors.Change, modifier = Modifier.size(28.dp))
                    }
                    Text(
                        stringResource(R.string.change_time_now, effective.event.time.replace(':', '.')),
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.Ink
                    )
                }
            }
        }

        if (change.reason.isNotBlank()) {
            Text(change.reason, fontSize = 20.sp, color = AppColors.Ink, textAlign = TextAlign.Center)
        }
        if (change.voicePath.isNotBlank()) {
            SecondaryButton(
                text = stringResource(R.string.change_listen),
                icon = AppIcons.Speaker,
                onClick = { VoicePlayer.play(context, change.voicePath) }
            )
        }
        PrimaryButton(
            text = stringResource(R.string.change_ok),
            icon = Icons.Rounded.Check,
            onClick = {
                AckStore(context).ack(change.date, change.ackId)
                onDone()
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** Ohjaajalle: tämän päivän ja huomisen muutokset, milloin ne näkyvät ja onko ne nähty. */
@Composable
internal fun ChangeOverview(plan: WeekPlan, programs: List<DayProgram>, today: LocalDate) {
    val context = LocalContext.current
    val rows = remember(plan) {
        val changes = ChangeStore(context).load()
        val acks = AckStore(context)
        listOf(today, today.plusDays(1)).flatMap { date ->
            WeekSchedule.eventsFor(plan, date).mapNotNull { e ->
                changes.firstOrNull { it.date == date && it.eventId == e.id }?.let { c ->
                    ChangeInfo(c, e, DayChanges.revealAt(c, e), acks.ackTime(date, c.ackId))
                }
            }
        }
    }
    if (rows.isEmpty()) return
    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChangeSymbol(Modifier.size(26.dp))
                Text(stringResource(R.string.change_overview_title), fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            rows.forEach { info ->
                val original = resolveEvent(info.original, programs)
                val effective = resolveEvent(DayChanges.effective(info.original, info.change), programs)
                Column {
                    Text(
                        dayLabel(info.change.date) + " · " + changeSummary(info.change, original, effective),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(changeStatus(info), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(stringResource(R.string.change_only_this_device), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

internal data class ChangeInfo(val change: EventChange, val original: WeekEvent, val revealAt: LocalDateTime?, val seenAt: String?)

/** "Nähty klo 9.12", "Näkyy käyttäjälle klo 10.45" tai "Ei vielä nähty". */
@Composable
internal fun changeStatus(info: ChangeInfo): String {
    val now = LocalDateTime.now()
    return when {
        info.seenAt != null -> stringResource(R.string.change_seen_at, info.seenAt.replace(':', '.'))
        info.revealAt != null && now.isBefore(info.revealAt) ->
            if (info.change.reveal == ChangeReveal.MORNING) stringResource(R.string.change_pending_morning)
            else stringResource(R.string.change_pending_at, WeekSchedule.formatTime(info.revealAt.hour, info.revealAt.minute).replace(':', '.'))
        else -> stringResource(R.string.change_not_seen)
    }
}
