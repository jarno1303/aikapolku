package fi.aikapolku.app.week

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.text.style.TextDecoration
import fi.aikapolku.app.caregiver.AppAction
import fi.aikapolku.app.caregiver.LocalCaregiver
import fi.aikapolku.app.data.ChangeStore
import fi.aikapolku.app.data.DayChanges
import fi.aikapolku.app.data.DayEntry
import fi.aikapolku.app.data.EventChange
import fi.aikapolku.app.ui.ChangeSymbol
import fi.aikapolku.app.ui.OverflowMenu
import fi.aikapolku.app.ui.SegmentedChoice
import java.time.LocalDateTime
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

/** Päivän rivi näyttämistä varten: muutosten jälkeinen tapahtuma ja sen muutos. */
internal class DayItem(val resolved: ResolvedEvent, val entry: DayEntry, val original: ResolvedEvent) {
    val event: WeekEvent get() = entry.event
    val change: EventChange? get() = entry.change
    val cancelled: Boolean get() = entry.cancelled
}

/** Päivän tapahtumat ja niiden tila (tehty, korostettu) kellonajan mukaan päivittyen. */
private class TodayState(
    val date: LocalDate,
    val items: List<DayItem>,
    val ackTimes: Map<String, String?>,
    val current: Int
) {
    private val active get() = items.filterNot { it.cancelled }
    val doneCount: Int get() = active.count { ackTimes[it.event.id] != null }
    val total: Int get() = active.size
}

/**
 * [caregiverView] = ohjaajan näkymä: kaikki muutokset heti. Muuten vain käyttäjälle jo
 * näkyvät muutokset (ks. ChangeReveal). [refresh] lataa tiedot uudelleen muutoksen jälkeen.
 */
@Composable
private fun rememberTodayState(dayOffset: Int = 0, caregiverView: Boolean = false, refresh: Int = 0): TodayState? {
    val context = LocalContext.current
    val plan = remember(context, refresh) { WeekStore(context.applicationContext).load() }
    val programs = remember(context, refresh) { ProgramStore(context.applicationContext).load() }
    val changes = remember(context, refresh) { ChangeStore(context.applicationContext).load() }
    val acks = remember(context) { AckStore(context.applicationContext) }
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    val date = now.toLocalDate().plusDays(dayOffset.toLong())

    val items = DayChanges.entries(WeekSchedule.eventsFor(plan, date), changes, date, if (caregiverView) null else now)
        .map { DayItem(resolveEvent(it.event, programs), it, resolveEvent(it.original, programs)) }
        .filter { it.resolved.title.isNotBlank() }
    if (items.isEmpty()) return null
    // Korostus siirtyy ajan kuluessa (puolen minuutin välein). Vain kun listassa on jotain.
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000L)
            now = LocalDateTime.now()
        }
    }
    // Kuitatut (tehdyt) ja perutut eivät ole korostettuina; korostus siirtyy seuraavaan.
    val ackTimes = items.associate { it.event.id to acks.ackTime(date, it.event.id) }
    val skipped = ackTimes.filterValues { it != null }.keys + items.filter { it.cancelled }.map { it.event.id }
    val current = if (dayOffset == 0) WeekSchedule.currentIndex(items.map { it.event }, now.toLocalTime(), skipped) else -1
    return TodayState(date, items, ackTimes, current)
}

/**
 * Aloitusnäkymän "Tänään": aina tiivis, oli päivässä tapahtumia kuinka monta tahansa.
 * Korostettu "nyt"-tapahtuma, kaksi seuraavaa ja linkki koko päivään. Tehdyt, perutut ja
 * väliin jääneet näkyvät vain koko päivän näkymässä. [afterHeader] = muutosrivit otsikon alle.
 */
@Composable
internal fun TodaySection(
    onOpenProgram: (WeekEvent) -> Unit,
    onOpenCard: (WeekEvent) -> Unit,
    onOpenDay: () -> Unit = {},
    /** Otsikkorivin oikeaan reunaan (esim. profiilikuva). */
    trailing: @Composable () -> Unit = {},
    afterHeader: @Composable () -> Unit = {}
): Boolean {
    val state = rememberTodayState() ?: return false
    fun open(item: DayItem) {
        if (item.resolved.isProgram) onOpenProgram(item.event) else onOpenCard(item.event)
    }

    // Otsikko ja yhteenveto yhdellä rivillä; koko rivi avaa koko päivän. Kun kaikki on tehty,
    // osio on pelkkä tämä rivi.
    val title = stringResource(R.string.today_title, dayName(state.date.dayOfWeek.value))
    val progress = stringResource(R.string.today_progress, state.doneCount, state.total)
    val a11y = title + ", " + stringResource(R.string.today_progress_a11y, state.doneCount, state.total)
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
    afterHeader()
    val skipped = state.ackTimes.filterValues { it != null }.keys + state.items.filter { it.cancelled }.map { it.event.id }
    WeekSchedule.compactIndices(state.items.map { it.event }, state.current, skipped)
        .forEach { index ->
            val item = state.items[index]
            TodayRow(item, isCurrent = index == state.current, isPast = false, doneAt = null) { open(item) }
        }
    return true
}

/**
 * Koko päivän tapahtumat omana näkymänään: tehdyt, perutut, väliin jääneet, nyt ja tulevat.
 * Ohjaaja voi tehdä tämän päivän tai huomisen tapahtumaan muutoksen (⋮).
 */
@Composable
internal fun DayScreen(
    onOpenProgram: (WeekEvent) -> Unit,
    onOpenCard: (WeekEvent) -> Unit,
    onOpenChange: (EventChange) -> Unit = {},
    /** Pikamuutos valmiiksi valitulle tapahtumalle (päivä 0 = tänään, 1 = huomenna). */
    onMakeChange: (dayOffset: Int, eventId: String) -> Unit = { _, _ -> }
) {
    val caregiver = LocalCaregiver.current
    val canChange = caregiver.allows(AppAction.EDIT_PROGRAMS)
    // Ohjaaja näkee muutokset heti; käyttäjä vasta valitulla hetkellä.
    val caregiverView = caregiver.lockEnabled && !caregiver.isLocked
    var chosenOffset by rememberSaveable { mutableIntStateOf(0) }
    // Huominen vain ohjaajalle; lukittuna aina tämä päivä.
    val dayOffset = if (canChange) chosenOffset else 0

    if (canChange) {
        SegmentedChoice(
            options = listOf(stringResource(R.string.change_day_today), stringResource(R.string.change_day_tomorrow)),
            selected = dayOffset,
            onSelect = { chosenOffset = it }
        )
    }
    val state = rememberTodayState(dayOffset, caregiverView || dayOffset > 0)
    // Otsikko (päivän nimi) on yläpalkissa.
    if (state == null) {
        Text(stringResource(R.string.week_no_events), color = AppColors.Muted)
        return
    }
    val changeLabel = stringResource(R.string.change_make)
    val editLabel = stringResource(R.string.change_edit)
    val menuLabel = stringResource(R.string.muut_toiminnot)
    state.items.forEachIndexed { index, item ->
        val doneAt = state.ackTimes[item.event.id]
        val isCurrent = index == state.current
        val isPast = dayOffset == 0 && (item.cancelled || doneAt != null || (if (state.current >= 0) index < state.current else true))
        TodayRow(
            item,
            isCurrent = isCurrent,
            isPast = isPast,
            doneAt = doneAt,
            trailing = if (canChange) {
                {
                    OverflowMenu(
                        contentDescription = "$menuLabel: ${item.original.title}",
                        items = listOf(
                            (if (item.change != null || item.entry.stepChanges.isNotEmpty()) editLabel else changeLabel) to
                                { onMakeChange(dayOffset, item.entry.original.id) }
                        )
                    )
                }
            } else null
        ) {
            val change = item.change
            when {
                change != null && item.cancelled -> onOpenChange(change)
                item.resolved.isProgram -> onOpenProgram(item.event)
                else -> onOpenCard(item.event)
            }
        }
    }

}

@Composable
private fun TodayRow(
    item: DayItem,
    isCurrent: Boolean,
    isPast: Boolean,
    doneAt: String?,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    val nowLabel = stringResource(R.string.now_label)
    val doneText = doneAt?.let { stringResource(R.string.ack_done_at, it.replace(':', '.')) }
    val change = item.change ?: item.entry.stepChanges.firstOrNull()
    val changeText = change?.let { changeSummary(it, item.original, item.resolved) }
    val a11y = item.event.time + " " + item.resolved.title + (if (isCurrent) ", $nowLabel" else "") +
        (changeText?.let { ", ${stringResource(R.string.change_title)}: $it" } ?: "") + (doneText?.let { ", $it" } ?: "")
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = if (isCurrent) AppColors.TealSoft else Color.White,
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (isPast) 0.45f else 1f)
            .then(
                when {
                    isCurrent -> Modifier.border(2.dp, AppColors.Teal, RoundedCornerShape(18.dp))
                    change != null -> Modifier.border(2.dp, AppColors.ChangeFill, RoundedCornerShape(18.dp))
                    else -> Modifier
                }
            )
            .semantics(mergeDescendants = true) { contentDescription = a11y }
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = if (trailing != null) 0.dp else 12.dp, top = if (isCurrent) 12.dp else 8.dp, bottom = if (isCurrent) 12.dp else 8.dp),
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
            Box {
                EventImage(
                    item.resolved,
                    Modifier.size(if (isCurrent) 64.dp else 44.dp).clip(RoundedCornerShape(if (isCurrent) 16.dp else 12.dp))
                )
                // Muutosmerkki kuvan kulmassa: sama merkki kuin muutosnäkymässä.
                if (change != null) ChangeSymbol(Modifier.size(22.dp).align(Alignment.TopEnd).offset(x = 6.dp, y = (-6).dp))
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    item.resolved.title,
                    fontSize = if (isCurrent) 20.sp else 17.sp,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                    color = AppColors.Ink,
                    textDecoration = if (item.cancelled) TextDecoration.LineThrough else null,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (changeText != null && !item.cancelled) {
                    Text(changeText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = AppColors.Change, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
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
            trailing?.invoke()
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
            ) {
                Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(28.dp))
                Text(stringResource(R.string.run_done_button), fontSize = 21.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 6.dp))
            }
        }
    }
}
