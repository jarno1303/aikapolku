package fi.aikapolku.app.week

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fi.aikapolku.app.R
import fi.aikapolku.app.cards.CardPhoto
import fi.aikapolku.app.data.ProgramStore
import fi.aikapolku.app.data.AckStore
import fi.aikapolku.app.data.WeekEvent
import fi.aikapolku.app.data.WeekSchedule
import fi.aikapolku.app.data.WeekStore
import fi.aikapolku.app.ui.AppColors
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.delay

/** Päivän tapahtumat ja niiden tila (tehty, korostettu) kellonajan mukaan päivittyen. */
private class TodayState(
    val date: LocalDate,
    val events: List<ResolvedEvent>,
    val ackTimes: Map<String, String?>,
    val current: Int
) {
    val doneCount: Int get() = ackTimes.values.count { it != null }
}

@Composable
private fun rememberTodayState(): TodayState? {
    val context = LocalContext.current
    val plan = remember(context) { WeekStore(context.applicationContext).load() }
    val programs = remember(context) { ProgramStore(context.applicationContext).load() }
    val acks = remember(context) { AckStore(context.applicationContext) }
    var now by remember { mutableStateOf(LocalTime.now()) }
    var date by remember { mutableStateOf(LocalDate.now()) }

    val events = WeekSchedule.eventsFor(plan, date)
        .map { resolveEvent(it, programs) }
        .filter { it.title.isNotBlank() }
    if (events.isEmpty()) return null
    // Korostus siirtyy ajan kuluessa (puolen minuutin välein). Vain kun listassa on jotain.
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000L)
            now = LocalTime.now()
            date = LocalDate.now()
        }
    }
    // Kuitatut (tehdyt) eivät enää ole korostettuina; korostus siirtyy seuraavaan tekemättömään.
    val ackTimes = events.associate { it.event.id to acks.ackTime(date, it.event.id) }
    val done = ackTimes.filterValues { it != null }.keys
    return TodayState(date, events, ackTimes, WeekSchedule.currentIndex(events.map { it.event }, now, done))
}

/**
 * Aloitusnäkymän "Tänään": aina tiivis, oli päivässä tapahtumia kuinka monta tahansa.
 * Tehdyt yhtenä rivinä, korostettu "nyt"-tapahtuma, kaksi seuraavaa ja linkki koko päivään.
 * Väliin jääneet näkyvät vain koko päivän näkymässä ja ohjaajan kuittauksissa.
 */
@Composable
internal fun TodaySection(
    onOpenProgram: (WeekEvent) -> Unit,
    onOpenCard: (WeekEvent) -> Unit,
    onOpenDay: () -> Unit = {},
    /** Otsikkorivin oikeaan reunaan (esim. profiilikuva). */
    trailing: @Composable () -> Unit = {}
): Boolean {
    val state = rememberTodayState() ?: return false
    fun open(item: ResolvedEvent) {
        if (item.isProgram) onOpenProgram(item.event) else onOpenCard(item.event)
    }

    // Otsikko ja yhteenveto yhdellä rivillä; koko rivi avaa koko päivän. Kun kaikki on tehty,
    // osio on pelkkä tämä rivi.
    val title = stringResource(R.string.today_title, dayName(state.date.dayOfWeek.value))
    val progress = stringResource(R.string.today_progress, state.doneCount, state.events.size)
    val a11y = title + ", " + stringResource(R.string.today_progress_a11y, state.doneCount, state.events.size)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    Surface(
        onClick = onOpenDay,
        shape = RoundedCornerShape(14.dp),
        color = Color.Transparent,
        modifier = Modifier
            .weight(1f)
            .heightIn(min = 48.dp)
            .semantics(mergeDescendants = true) {
                heading()
                contentDescription = a11y
            }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
            Text(
                title,
                modifier = Modifier.weight(1f),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = AppColors.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(progress, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = AppColors.Teal)
        }
    }
    trailing()
    }
    WeekSchedule.compactIndices(state.events.map { it.event }, state.current, state.ackTimes.filterValues { it != null }.keys)
        .forEach { index ->
            TodayRow(state.events[index], isCurrent = index == state.current, isPast = false, doneAt = null) {
                open(state.events[index])
            }
        }
    return true
}

/** Koko päivän tapahtumat omana näkymänään: tehdyt, väliin jääneet, nyt ja tulevat. */
@Composable
internal fun DayScreen(onOpenProgram: (WeekEvent) -> Unit, onOpenCard: (WeekEvent) -> Unit) {
    val state = rememberTodayState()
    Text(
        stringResource(R.string.today_title, dayName(LocalDate.now().dayOfWeek.value)),
        modifier = Modifier.semantics { heading() },
        fontSize = 28.sp,
        fontWeight = FontWeight.Bold,
        color = AppColors.Ink
    )
    if (state == null) {
        Text(stringResource(R.string.week_no_events), color = AppColors.Muted)
        return
    }
    state.events.forEachIndexed { index, item ->
        val doneAt = state.ackTimes[item.event.id]
        val isCurrent = index == state.current
        val isPast = doneAt != null || (if (state.current >= 0) index < state.current else true)
        TodayRow(item, isCurrent = isCurrent, isPast = isPast, doneAt = doneAt) {
            if (item.isProgram) onOpenProgram(item.event) else onOpenCard(item.event)
        }
    }
}

@Composable
private fun TodayRow(item: ResolvedEvent, isCurrent: Boolean, isPast: Boolean, doneAt: String?, onClick: () -> Unit) {
    val nowLabel = stringResource(R.string.now_label)
    val doneText = doneAt?.let { stringResource(R.string.ack_done_at, it.replace(':', '.')) }
    val a11y = item.event.time + " " + item.title + (if (isCurrent) ", $nowLabel" else "") + (doneText?.let { ", $it" } ?: "")
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = if (isCurrent) AppColors.TealSoft else Color.White,
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (isPast) 0.45f else 1f)
            .then(if (isCurrent) Modifier.border(2.dp, AppColors.Teal, RoundedCornerShape(18.dp)) else Modifier)
            .semantics(mergeDescendants = true) { contentDescription = a11y }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = if (isCurrent) 12.dp else 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                item.event.time,
                modifier = Modifier.width(54.dp),
                fontSize = if (isCurrent) 18.sp else 16.sp,
                fontWeight = FontWeight.Bold,
                color = if (isCurrent) AppColors.Teal else AppColors.Muted
            )
            EventImage(
                item,
                Modifier.size(if (isCurrent) 64.dp else 44.dp).clip(RoundedCornerShape(if (isCurrent) 16.dp else 12.dp))
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    item.title,
                    fontSize = if (isCurrent) 20.sp else 17.sp,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                    color = AppColors.Ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (doneText != null) {
                    Text(doneText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = AppColors.Teal)
                } else if (isCurrent) {
                    // Koko rivi on painike; "Aloita" näyttää, mistä päivä jatkuu.
                    Surface(color = AppColors.Teal, shape = RoundedCornerShape(12.dp)) {
                        Text(
                            stringResource(R.string.today_start) + " ›",
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

/**
 * Yksittäisen kortin tapahtuma isona: kuva, nimi ja Valmis. Jos tapahtuma on jo kuitattu
 * ([doneAt]), Valmiin tilalla näkyy kuittausaika (takaisin yläreunasta), eikä aika muutu.
 */
@Composable
internal fun EventCardScreen(name: String, photo: String, doneAt: String?, onDone: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        CardPhoto(
            name,
            photo,
            Modifier
                .fillMaxWidth()
                .sizeIn(maxWidth = 340.dp, maxHeight = 340.dp)
                .aspectRatio(1f)
                .clip(RoundedCornerShape(28.dp))
        )
        Text(
            name,
            modifier = Modifier.semantics { heading() },
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = AppColors.Ink,
            textAlign = TextAlign.Center
        )
        if (doneAt != null) {
            Text(
                stringResource(R.string.ack_done_at, doneAt.replace(':', '.')),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = AppColors.Teal
            )
        } else {
            Button(
                onClick = onDone,
                modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.Teal)
            ) { Text(stringResource(R.string.run_done_button), fontSize = 21.sp, fontWeight = FontWeight.Bold) }
        }
    }
}
