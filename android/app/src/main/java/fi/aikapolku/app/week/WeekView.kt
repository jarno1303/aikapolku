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
import fi.aikapolku.app.data.GroupStore
import fi.aikapolku.app.data.ProfileStore
import fi.aikapolku.app.data.Runko
import fi.aikapolku.app.ui.AppColors
import java.time.LocalDate

/**
 * Asiakkaan viikko: omat kohdat aikajanalla (RunkoEditor). Ryhmien kohdat näkyvät samalla
 * aikajanalla ryhmän nimellä merkittyinä; niiden napautus avaa asiakkaan toistuvan
 * poikkeuksen (ei osallistu / tilalla muuta). Ryhmän runkoa muokataan Ryhmät-näkymässä.
 */
@Composable
internal fun WeekView(programs: List<DayProgram>, canEdit: Boolean) {
    val context = LocalContext.current
    val app = context.applicationContext
    val store = remember(context) { WeekStore(app) }
    val today = remember { LocalDate.now() }
    val me = remember { ProfileStore.currentId(app) }
    val clientName = remember { ProfileStore.list(app).firstOrNull { it.id == me }?.name.orEmpty() }
    var plan by remember {
        mutableStateOf(WeekSchedule.withoutMissingPrograms(store.load(), programs.map { it.id }.toSet()))
    }
    var groups by remember { mutableStateOf(GroupStore(app).load()) }
    val myGroups = groups.filter { me in it.members }
    var exceptionRow by remember { mutableStateOf<GroupRow?>(null) }
    var groupMove by remember { mutableStateOf<Pair<GroupRow, String>?>(null) }

    fun update(next: WeekPlan) {
        plan = next
        store.save(next)
        // Poistetun kohdan päivän muutoskin poistuu (ryhmien kohdat säilyvät).
        ChangeStore(app).clean(today, ClientWeek.load(app, me, own = next).eventIds())
        EventReminders.reschedule(app)
    }

    // Ryhmän kohdat valitulle viikonpäivälle: lähin sellainen päivä (oma A/B-viikko huomioiden).
    fun groupRows(day: Int, week: Int): List<GroupRow> {
        if (myGroups.isEmpty()) return emptyList()
        var date = today.plusDays(((day - today.dayOfWeek.value + 7) % 7).toLong())
        if (plan.alternating && WeekSchedule.weekOf(plan, date) != week) date = date.plusWeeks(1)
        return myGroups.flatMap { g ->
            WeekSchedule.eventsFor(g.plan, date).filter { it.kind == EventKind.CARD }.map { e ->
                GroupRow(g, e, g.exceptions.firstOrNull { it.eventId == e.id && it.memberId == me })
            }
        }
    }

    Text(
        stringResource(R.string.settings_for_client, clientName.ifBlank { stringResource(R.string.profile_unnamed) }),
        fontSize = 17.sp,
        fontWeight = FontWeight.SemiBold,
        color = AppColors.Teal
    )
    if (myGroups.isNotEmpty()) {
        Text(
            stringResource(R.string.runko_client_groups, myGroups.joinToString(", ") { it.name }),
            fontSize = 14.sp,
            color = AppColors.Muted
        )
    }

    val copySources = remember(groups) {
        ProfileStore.list(app).filter { it.id != me }.mapNotNull { p ->
            val other = WeekStore(app, p.id).load()
            if (other.events.none { it.kind == EventKind.CARD }) null else p.name to other
        } + groups.filter { it.plan.events.isNotEmpty() }.map { context.getString(R.string.runko_group_source, it.name) to it.plan }
    }

    RunkoEditor(
        plan = plan,
        onChange = ::update,
        programs = programs,
        canEdit = canEdit,
        allowPrograms = true,
        groupRows = ::groupRows,
        onGroupRow = { exceptionRow = it },
        onGroupMove = { row, time -> groupMove = row to time },
        copySources = copySources
    )

    if (canEdit) {
        ChangeOverview(remember(plan, groups) { ClientWeek.load(app, me, own = plan) }, programs, today)
        AckOverview(remember(plan, groups) { ClientWeek.load(app, me, own = plan) }, today)
    }

    // Ryhmän kohta raahattiin uuteen aikaan: tälle asiakkaalle joka viikko (varmistus).
    groupMove?.let { (row, time) ->
        MoveDialog(
            name = row.exception?.takeIf { it.kind == fi.aikapolku.app.data.ExceptionKind.REPLACE }?.cardName ?: row.event.cardName,
            fromTime = row.event.time,
            initialTime = time,
            clientName = clientName,
            isGroup = true,
            onToday = null,
            onWeekly = { chosen ->
                val date = today.plusDays(((row.event.day - today.dayOfWeek.value + 7) % 7).toLong())
                val result = fi.aikapolku.app.data.Moves.weekly(
                    plan, GroupStore(app).load(), me,
                    fi.aikapolku.app.data.GroupSchedule.eventId(row.group.id, row.event.id), date, chosen
                ) { java.util.UUID.randomUUID().toString() }
                if (result != null) {
                    result.group?.let { GroupStore(app).upsert(it) }
                    groups = GroupStore(app).load()
                    update(result.own)
                }
                groupMove = null
            },
            onDismiss = { groupMove = null }
        )
    }

    exceptionRow?.let { row ->
        GroupExceptionDialog(
            row = row,
            clientName = clientName,
            onSet = { exception ->
                val group = GroupStore(app).load().firstOrNull { it.id == row.group.id }
                if (group != null) {
                    GroupStore(app).upsert(Runko.withException(group, row.event.id, me, exception?.copy(memberId = me)))
                    groups = GroupStore(app).load()
                    EventReminders.reschedule(app)
                }
                exceptionRow = null
            },
            onDismiss = { exceptionRow = null }
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

/**
 * Ohjaajalle: korttitapahtumien kuittaukset eiliseltä ja tältä päivältä.
 * Vanhempia ei säilytetä (ks. AckStore).
 */
@Composable
private fun AckOverview(plan: DaySource, today: LocalDate) {
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
            DayChanges.entries(plan.eventsFor(date), changes, date, null)
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
