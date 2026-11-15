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
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import fi.aikapolku.app.data.ClientWeek
import fi.aikapolku.app.data.DaySource
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
import fi.aikapolku.app.programs.run.BreakButton
import fi.aikapolku.app.programs.run.BreakMessage
import fi.aikapolku.app.voice.Speech
import fi.aikapolku.app.voice.VoicePlayer
import java.time.LocalDate
import java.time.LocalDateTime

/** Kortti näytettävänä tapahtumana (vaihemuutokset ja Ensin–Sitten). */
internal fun cardResolved(name: String, photo: String): ResolvedEvent =
    ResolvedEvent(WeekEvent("", 1, 0, "00:00", fi.aikapolku.app.data.EventKind.CARD, cardName = name, cardPhoto = photo), name, name, photo, false)

/**
 * Muutoksen lyhyt kuvaus: "Uinti on peruttu", "Uinti → Ulkoilu", "Uinti klo 13.00",
 * vaiheelle "Koulupäivä: Uinti → Ulkoilu". [original] = tapahtuma, [effective] = muutoksen jälkeen.
 */
@Composable
internal fun changeSummary(change: EventChange, original: ResolvedEvent, effective: ResolvedEvent): String =
    if (change.isStep) when (change.type) {
        ChangeType.REPLACE ->
            if (change.showOriginal) stringResource(R.string.change_summary_step_replace, original.title, change.stepName, change.cardName)
            else stringResource(R.string.change_summary_step_replace_short, original.title, change.cardName)
        else -> stringResource(R.string.change_summary_step_cancel, original.title, change.stepName)
    } else when (change.type) {
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
        val plan = ClientWeek.load(context)
        val programs = ProgramStore(context).load()
        val acks = AckStore(context)
        DayChanges.announced(plan, plan.withGroupChanges(ChangeStore(context).load()), LocalDateTime.now()) { acks.ackTime(it.date, it.ackId) != null }
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
 * Käyttäjän muutosnäkymä aina samassa järjestyksessä: mikä muuttuu, miksi, mitä tilalle,
 * mikä pysyy samana ja mitä voin tehdä (Selvä, Kuuntele, Tarvitsen tauon).
 * Selvä kuittaa muutoksen nähdyksi (näkyy ohjaajalle). Tauko on aina vapaa.
 */
@Composable
internal fun ChangeScreen(changeId: String, onDone: () -> Unit) {
    val context = LocalContext.current
    val data = remember(changeId) {
        val all = ClientWeek.changes(context)
        val change = all.firstOrNull { it.id == changeId }
        val plan = ClientWeek.load(context)
        val original = change?.let { c -> plan.event(c.eventId) }
        val programs = ProgramStore(context).load()
        if (change == null || original == null) null
        else {
            // Pysyy samana: käyttäjän näkymän mukaan (vain jo näkyvät muutokset huomioidaan).
            val entries = DayChanges.entries(plan.eventsFor(change.date), all, change.date, LocalDateTime.now())
            // Kaksi seuraavaa ennallaan olevaa: koko muutosnäkymä mahtuu ruudulle (R11).
            val unchanged = DayChanges.unchangedAfter(entries, change, limit = 2).map { resolveEvent(it, programs) }
            ChangeData(change, resolveEvent(original, programs), resolveEvent(DayChanges.effective(original, change), programs), unchanged)
        }
    }
    if (data == null) {
        Text(stringResource(R.string.week_no_events), color = AppColors.Muted)
        return
    }
    var showBreak by rememberSaveable { mutableStateOf(false) }
    // Ohjaajan äänite soi itsestään vain kerran (ei uudelleen tauon jälkeen).
    var autoPlayed by rememberSaveable { mutableStateOf(false) }
    if (showBreak) {
        // Takaisin-nuoli sulkee tauon eikä koko muutosnäkymää (BreakMessage).
        BreakMessage(timerPaused = false, onContinue = { showBreak = false }, onClose = { showBreak = false })
        return
    }
    val (change, event, effectiveEvent, unchanged) = data
    // Vaihemuutoksessa näytetään vaihe ja korvaava kortti, otsikkona ohjelman nimi.
    val original = if (change.isStep) cardResolved(change.stepName, change.stepPhoto) else event
    val effective = when {
        change.isStep && change.type == ChangeType.REPLACE -> cardResolved(change.cardName, change.cardPhoto)
        change.isStep -> original
        else -> effectiveEvent
    }
    val spoken = changeSpeech(change, event, effectiveEvent, unchanged)
    ChangeView(
        subtitle = dayLabel(change.date) + if (change.isStep) " · " + event.title else "",
        type = change.type,
        original = original,
        effective = effective,
        showOriginal = change.showOriginal,
        reason = change.reason,
        voicePath = change.voicePath,
        autoPlayVoice = change.autoPlayVoice && !autoPlayed,
        onAutoPlayed = { autoPlayed = true },
        unchanged = unchanged,
        spokenText = spoken,
        onBreak = { showBreak = true },
        onOk = {
            AckStore(context).ack(change.date, change.ackId)
            fi.aikapolku.app.data.UsageLog.record(context, fi.aikapolku.app.data.UsageKind.CHANGE_SEEN)
            onDone()
        }
    )
}

/** Ensin–Sitten: Sitten-kortti vaihdettiin kesken kulun ja ohjaaja halusi näyttää sen muutoksena. */
@Composable
internal fun FlowChangeScreen(oldName: String, oldPhoto: String, newName: String, newPhoto: String, onOk: () -> Unit) {
    ChangeView(
        subtitle = stringResource(R.string.ensin_sitten),
        type = ChangeType.REPLACE,
        original = cardResolved(oldName, oldPhoto),
        effective = cardResolved(newName, newPhoto),
        showOriginal = true,
        reason = "",
        voicePath = "",
        autoPlayVoice = false,
        onOk = onOk
    )
}

@Composable
private fun ChangeView(
    subtitle: String,
    type: ChangeType,
    original: ResolvedEvent,
    effective: ResolvedEvent,
    showOriginal: Boolean,
    reason: String,
    voicePath: String,
    autoPlayVoice: Boolean,
    onAutoPlayed: () -> Unit = {},
    unchanged: List<ResolvedEvent> = emptyList(),
    spokenText: String = "",
    onBreak: (() -> Unit)? = null,
    onOk: () -> Unit
) {
    val context = LocalContext.current
    LaunchedEffect(voicePath) {
        if (autoPlayVoice && voicePath.isNotBlank()) {
            VoicePlayer.play(context, voicePath)
            onAutoPlayed()
        }
    }
    // Puhesynteesi vain, kun ohjaajan äänitettä ei ole.
    val speech = remember(context, voicePath, spokenText) {
        if (voicePath.isBlank() && spokenText.isNotBlank()) Speech(context.applicationContext) else null
    }
    DisposableEffect(speech) {
        onDispose { speech?.close() }
    }

    // Tiivis: koko muutos ja kaikki painikkeet näkyvät ruudulla ilman vieritystä (R11).
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.semantics(mergeDescendants = true) { heading() }
        ) {
            ChangeSymbol(Modifier.size(44.dp))
            Column {
                Text(stringResource(R.string.change_label), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = AppColors.Change)
                Text(subtitle, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = AppColors.Muted)
            }
        }

        when (type) {
            ChangeType.CANCEL -> {
                ImageWithName(original, 150, crossed = true)
                Text(stringResource(R.string.change_cancelled_text), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = AppColors.Ink)
            }
            ChangeType.REPLACE -> if (showOriginal) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { ImageWithName(original, 100, crossed = true) }
                    Box(
                        modifier = Modifier.size(40.dp).clip(RoundedCornerShape(50)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, tint = AppColors.Change, modifier = Modifier.size(32.dp))
                    }
                    Box(Modifier.weight(1.2f), contentAlignment = Alignment.Center) { ImageWithName(effective, 130, crossed = false) }
                }
            } else {
                ImageWithName(effective, 170, crossed = false)
            }
            ChangeType.MOVE -> {
                ImageWithName(effective, 150, crossed = false)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (showOriginal) {
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

        if (reason.isNotBlank()) {
            Text(reason, fontSize = 20.sp, color = AppColors.Ink, textAlign = TextAlign.Center)
        }
        if (unchanged.isNotEmpty()) UnchangedSection(unchanged)
        // Kuuntele ennen Selvää: kuuntelija ehtii kuulla ennen kuittausta.
        // Ohjaajan äänite, muuten puhesynteesi muutoksesta, syystä ja siitä mikä pysyy.
        if (voicePath.isNotBlank() || spokenText.isNotBlank()) {
            SecondaryButton(
                text = stringResource(R.string.change_listen),
                icon = AppIcons.Speaker,
                onClick = {
                    if (voicePath.isNotBlank()) VoicePlayer.play(context, voicePath) else speech?.speak(spokenText)
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
        PrimaryButton(
            text = stringResource(R.string.change_ok),
            icon = Icons.Rounded.Check,
            onClick = onOk,
            modifier = Modifier.fillMaxWidth()
        )
        if (onBreak != null) BreakButton(onBreak)
    }
}

/**
 * Puhuttava muutos kokonaisina lauseina (ei nuolia): mitä muuttuu, miksi ja mikä pysyy.
 * Vaihemuutoksessa puhutaan vaiheesta, ei koko ohjelmasta.
 */
@Composable
private fun changeSpeech(change: EventChange, original: ResolvedEvent, effective: ResolvedEvent, unchanged: List<ResolvedEvent>): String {
    val from = if (change.isStep) change.stepName else original.title
    val main = when (change.type) {
        ChangeType.CANCEL -> stringResource(R.string.change_speech_cancel, from)
        ChangeType.REPLACE ->
            if (change.showOriginal) stringResource(R.string.change_speech_replace, from, if (change.isStep) change.cardName else effective.title)
            else stringResource(R.string.change_speech_replace_short, if (change.isStep) change.cardName else effective.title)
        ChangeType.MOVE -> stringResource(R.string.change_speech_move, effective.title, effective.event.time.replace(':', '.'))
    }
    val same = unchanged.takeIf { it.isNotEmpty() }?.let { list ->
        stringResource(R.string.change_speech_unchanged, list.joinToString(", ") { it.title + " " + it.event.time.replace(':', '.') })
    }
    return listOfNotNull(main, change.reason.trim().takeIf { it.isNotBlank() }?.trimEnd('.')?.plus("."), same).joinToString(" ")
}

private data class ChangeData(
    val change: EventChange,
    val event: ResolvedEvent,
    val effective: ResolvedEvent,
    val unchanged: List<ResolvedEvent>
)

/** "Pysyy samana": muutoksen jälkeiset ennallaan olevat tapahtumat rauhallisella värillä ja merkillä. */
@Composable
private fun UnchangedSection(items: List<ResolvedEvent>) {
    val title = stringResource(R.string.change_unchanged_title)
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = AppColors.TealSoft,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                title,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = AppColors.Teal,
                modifier = Modifier.semantics { heading() }
            )
            items.forEach { item ->
                val time = item.event.time.replace(':', '.')
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .semantics(mergeDescendants = true) { contentDescription = "$time ${item.title}" }
                ) {
                    EventImage(item, Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)))
                    Text(time, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = AppColors.Muted)
                    Text(
                        item.title,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AppColors.Ink,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(Icons.Rounded.Check, contentDescription = null, tint = AppColors.Teal, modifier = Modifier.size(28.dp))
                }
            }
        }
    }
}

/** Ohjaajalle: tämän päivän ja huomisen muutokset, milloin ne näkyvät ja onko ne nähty. */
@Composable
internal fun ChangeOverview(plan: DaySource, programs: List<DayProgram>, today: LocalDate) {
    val context = LocalContext.current
    val rows = remember(plan) {
        val own = ChangeStore(context).load()
        val changes = (plan as? fi.aikapolku.app.data.ClientDay)?.withGroupChanges(own) ?: own
        val acks = AckStore(context)
        listOf(today, today.plusDays(1)).flatMap { date ->
            plan.eventsFor(date).flatMap { e ->
                changes.filter { it.date == date && it.eventId == e.id }.sortedBy { it.isStep }.map { c ->
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
            else if (info.change.reveal == ChangeReveal.EVENING) stringResource(R.string.change_pending_evening, DayChanges.EVENING_HOUR)
            else stringResource(R.string.change_pending_at, WeekSchedule.formatTime(info.revealAt.hour, info.revealAt.minute).replace(':', '.'))
        else -> stringResource(R.string.change_not_seen)
    }
}
