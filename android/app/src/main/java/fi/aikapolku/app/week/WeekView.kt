package fi.aikapolku.app.week

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fi.aikapolku.app.R
import fi.aikapolku.app.data.ClientWeek
import fi.aikapolku.app.data.DaySource
import fi.aikapolku.app.data.AckStore
import fi.aikapolku.app.data.ChangeStore
import fi.aikapolku.app.data.DayChanges
import fi.aikapolku.app.data.DayProgram
import fi.aikapolku.app.data.EventKind
import fi.aikapolku.app.data.WeekEvent
import fi.aikapolku.app.data.WeekPlan
import fi.aikapolku.app.data.WeekSchedule
import fi.aikapolku.app.data.WeekStore
import fi.aikapolku.app.ui.AppColors
import java.time.LocalDate

/** Päiväohjelmat-näkymän Viikko-välilehti: viikonpäivien tapahtumat ja vuoroviikot. */
@Composable
internal fun WeekView(programs: List<DayProgram>, canEdit: Boolean) {
    val context = LocalContext.current
    val store = remember(context) { WeekStore(context.applicationContext) }
    val today = remember { LocalDate.now() }
    var plan by remember {
        mutableStateOf(WeekSchedule.withoutMissingPrograms(store.load(), programs.map { it.id }.toSet()))
    }
    fun update(next: WeekPlan) {
        plan = next
        store.save(next)
        // Poistetun tapahtuman päivän muutoskin poistuu.
        ChangeStore(context.applicationContext).clean(today, next.events.map { it.id }.toSet())
        EventReminders.reschedule(context.applicationContext)
    }

    val currentWeek = WeekSchedule.weekOf(plan, today)
    var day by rememberSaveable { mutableStateOf(today.dayOfWeek.value) }
    var week by rememberSaveable { mutableStateOf(currentWeek) }
    var editing by remember { mutableStateOf<WeekEvent?>(null) }
    var adding by remember { mutableStateOf(false) }
    val shownWeek = if (plan.alternating) week else WeekSchedule.WEEK_A


    if (plan.alternating) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            listOf(WeekSchedule.WEEK_A, WeekSchedule.WEEK_B).forEach { w ->
                val label = weekLabel(w) + if (w == currentWeek) " · " + stringResource(R.string.week_this_week) else ""
                ChoiceButton(label, selected = week == w, modifier = Modifier.weight(1f)) { week = w }
            }
        }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
        (1..7).forEach { d ->
            val hasEvents = WeekSchedule.eventsOn(plan, d, shownWeek).isNotEmpty()
            DayButton(
                day = d,
                selected = d == day,
                hasEvents = hasEvents,
                modifier = Modifier.weight(1f)
            ) { day = d }
        }
    }

    Text(dayName(day), fontSize = 20.sp, fontWeight = FontWeight.Bold)

    val events = WeekSchedule.eventsOn(plan, day, shownWeek)
    if (events.isEmpty()) {
        Text(stringResource(R.string.week_no_events), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    events.forEach { event ->
        val resolved = resolveEvent(event, programs)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (canEdit) Modifier.clickable(role = Role.Button) { editing = event } else Modifier),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Row(
                modifier = Modifier.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(event.time, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(56.dp))
                EventImage(resolved, Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        resolved.title.ifBlank { stringResource(R.string.week_missing_program) },
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        stringResource(if (resolved.isProgram) R.string.week_kind_program else R.string.week_kind_card),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (canEdit) Text("›", fontSize = 24.sp, color = AppColors.Teal)
            }
        }
    }

    if (canEdit) {
        Button(
            onClick = { adding = true },
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
        ) { Text(stringResource(R.string.week_add_event)) }

        Card(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = AppColors.TealSoft)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val switchLabel = stringResource(R.string.week_alternating)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(switchLabel, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.week_alternating_info), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = plan.alternating,
                        onCheckedChange = { on ->
                            var next = plan.copy(alternating = on)
                            if (on && plan.anchorMonday == 0L) next = WeekSchedule.withCurrentWeek(next, today, WeekSchedule.WEEK_A)
                            update(next)
                            week = WeekSchedule.weekOf(next, today)
                        },
                        modifier = Modifier.semantics { contentDescription = switchLabel }
                    )
                }
                if (plan.alternating) {
                    Text(stringResource(R.string.week_current_is), fontSize = 15.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        listOf(WeekSchedule.WEEK_A, WeekSchedule.WEEK_B).forEach { w ->
                            ChoiceButton(weekLabel(w), selected = currentWeek == w, modifier = Modifier.weight(1f)) {
                                update(WeekSchedule.withCurrentWeek(plan, today, w))
                                week = w
                            }
                        }
                    }
                }
            }
        }
    }

    if (canEdit) {
        ChangeOverview(remember(plan) { ClientWeek.load(context.applicationContext, own = plan) }, programs, today)
        AckOverview(plan, today)
    }

    if (adding) {
        WeekEventDialog(
            initial = null,
            day = day,
            week = shownWeek,
            programs = programs,
            onSave = { created ->
                update(plan.copy(events = plan.events + created))
                adding = false
            },
            onDelete = {},
            onDismiss = { adding = false }
        )
    }
    editing?.let { event ->
        WeekEventDialog(
            initial = event,
            day = event.day,
            week = event.week,
            programs = programs,
            onSave = { changed ->
                update(plan.copy(events = plan.events.filterNot { it.id == event.id } + changed))
                editing = null
            },
            onDelete = {
                update(plan.copy(events = plan.events.filterNot { it.id == event.id }))
                editing = null
            },
            onDismiss = { editing = null }
        )
    }
}

@Composable
internal fun weekLabel(week: Int): String =
    stringResource(if (week == WeekSchedule.WEEK_A) R.string.week_a else R.string.week_b)

@Composable
internal fun ChoiceButton(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    if (selected) {
        Button(
            onClick = onClick,
            modifier = modifier.heightIn(min = 48.dp).semantics { this.selected = true },
            colors = ButtonDefaults.buttonColors(containerColor = AppColors.Teal)
        ) { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier.heightIn(min = 48.dp).semantics { this.selected = false }
        ) { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis, color = AppColors.Teal) }
    }
}

@Composable
private fun DayButton(day: Int, selected: Boolean, hasEvents: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val full = dayName(day)
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) AppColors.Teal else Color.White,
        modifier = modifier
            .heightIn(min = 52.dp)
            .border(1.dp, if (selected) AppColors.Teal else AppColors.Border, RoundedCornerShape(12.dp))
            .semantics {
                contentDescription = full
                this.selected = selected
            }
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(vertical = 6.dp)
        ) {
            Text(
                dayShort(day),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = if (selected) Color.White else AppColors.Ink
            )
            // Pieni piste kertoo, että päivälle on tapahtumia.
            Text(
                if (hasEvents) "•" else " ",
                fontSize = 14.sp,
                color = if (selected) Color.White else AppColors.Teal
            )
        }
    }
}

/**
 * Ohjaajalle: korttitapahtumien kuittaukset eiliseltä ja tältä päivältä.
 * Vanhempia ei säilytetä (ks. AckStore).
 */
@Composable
private fun AckOverview(plan: WeekPlan, today: LocalDate) {
    val context = LocalContext.current
    val acks = remember(context) { AckStore(context.applicationContext) }
    val now = remember { java.time.LocalTime.now() }
    val days = listOf(today.minusDays(1) to R.string.ack_yesterday, today to R.string.ack_today)
    val changes = remember(plan) { ChangeStore(context.applicationContext).load() }
    // Päivän muutokset huomioiden: perutut pois, korvatut uudella nimellä.
    val rows = days.map { (date, label) ->
        Triple(
            date,
            label,
            DayChanges.entries(WeekSchedule.eventsFor(plan, date), changes, date, null)
                .filterNot { it.cancelled }
                .map { it.event }
                .filter { it.kind == EventKind.CARD }
        )
    }
    if (rows.all { it.third.isEmpty() }) return

    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.ack_title), fontSize = 18.sp, fontWeight = FontWeight.Bold)
            rows.forEach { (date, label, events) ->
                if (events.isEmpty()) return@forEach
                Text(stringResource(label), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
                events.forEach { event ->
                    val ack = acks.ackTime(date, event.id)
                    val missed = ack == null &&
                        (date.isBefore(today) || WeekSchedule.minutesOf(event.time) <= now.hour * 60 + now.minute)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(event.time, fontSize = 15.sp, modifier = Modifier.width(52.dp))
                        Text(event.cardName, fontSize = 15.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        when {
                            ack != null -> Text(
                                stringResource(R.string.ack_done_at, ack.replace(':', '.')),
                                fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = AppColors.Teal
                            )
                            missed -> Text(
                                stringResource(R.string.ack_missing),
                                fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFB45309)
                            )
                            else -> Text(stringResource(R.string.ack_upcoming), fontSize = 14.sp, color = AppColors.Muted)
                        }
                    }
                }
            }
            Text(stringResource(R.string.ack_disclaimer), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
