package fi.aikapolku.app.week

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fi.aikapolku.app.R
import fi.aikapolku.app.data.ClientWeek
import fi.aikapolku.app.data.Group
import fi.aikapolku.app.data.GroupKind
import fi.aikapolku.app.data.GroupStore
import fi.aikapolku.app.data.ProfileStore
import fi.aikapolku.app.data.Units
import fi.aikapolku.app.data.WeekSchedule
import fi.aikapolku.app.ui.AppColors
import java.time.LocalDate
import java.time.LocalTime

/** Asukkaan tilanne nyt yksikössä (V3). */
internal data class ResidentNow(
    val memberId: String,
    val activity: Group?,
    val until: String?,
    val absent: Boolean,
    /** Lomalla tai pidemmin poissa: palaa tällöin. */
    val leave: fi.aikapolku.app.data.Leave? = null
)

/** Yksikön tilanne laskettuna: puhdas logiikka (testattavissa ilman näkymää). */
internal object UnitToday {
    /** Yksikön ja sen alayksiköiden asukkaat. */
    fun residents(unit: Group, all: List<Group>): List<String> = Units.residents(unit, all)

    fun now(
        memberId: String,
        all: List<Group>,
        date: LocalDate,
        time: String,
        absentFromUnit: (String) -> Boolean,
        day: (String) -> fi.aikapolku.app.data.ClientDay
    ): ResidentNow {
        val m = WeekSchedule.minutesOf(time)
        val d = day(memberId)
        d.leaves.firstOrNull { it.covers(date.atTime(m / 60, m % 60)) }?.let { return ResidentNow(memberId, null, null, false, it) }
        if (absentFromUnit(memberId)) return ResidentNow(memberId, null, null, true)
        val span = Units.at(d.spans(date), time)
        return ResidentNow(memberId, span?.group, span?.end, false)
    }
}

/**
 * Ohjaajan etusivulle "Tänään yksikössä" (V3): kuka on missä ja milloin palaa, ja montako on
 * kotona yksikön seuraavassa kohdassa (esim. lounas). Vain ohjaajalle: asukkaat eivät näe toisiaan.
 */
@Composable
internal fun UnitTodayCard() {
    val context = LocalContext.current
    val app = context.applicationContext
    val revision = GroupSession.revision
    val all = remember(revision) { GroupStore(app).load() }
    val tops = all.filter { it.kind == GroupKind.UNIT && (it.parentId.isBlank() || all.none { p -> p.id == it.parentId }) }
    if (tops.isEmpty()) return
    val profiles = remember(revision) { ProfileStore.list(app) }
    fun name(id: String) = profiles.firstOrNull { it.id == id }?.name?.ifBlank { null } ?: context.getString(R.string.profile_unnamed)
    val today = LocalDate.now()
    val nowTime = LocalTime.now().let { WeekSchedule.formatTime(it.hour, it.minute) }
    val absences = remember(revision) { GroupStore(app).absences() }
    var leaveFor by remember { mutableStateOf<List<String>?>(null) }
    leaveFor?.let { ids ->
        LeaveDialog(ids, ::name, onDismiss = { leaveFor = null }) { leave ->
            GroupStore(app).upsertLeave(leave)
            EventReminders.reschedule(app)
            GroupSession.changed()
            leaveFor = null
        }
    }
    val scope = remember(revision) { fi.aikapolku.app.data.UnitScope.residents(fi.aikapolku.app.data.UnitScope.get(app), all) }
    tops.forEach { unit ->
        val residents = UnitToday.residents(unit, all).filter { scope == null || it in scope }
        if (residents.isEmpty()) return@forEach
        val unitIds = listOf(unit.id) + all.filter { g -> g.kind == GroupKind.UNIT && Units.ancestors(g, all).any { it.id == unit.id } }.map { it.id }
        val days = remember(revision, residents) { residents.associateWith { ClientWeek.load(app, it) } }
        val status = residents.map { id ->
            UnitToday.now(id, all, today, nowTime, { m -> unitIds.any { u -> m in absences[u]?.get(today).orEmpty() } }) { days.getValue(it) }
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(R.string.unit_today_title) + " · " + unit.name,
                    fontSize = 18.sp, fontWeight = FontWeight.Bold, color = AppColors.Ink,
                    modifier = Modifier.semantics { heading() }
                )
                Text(stringResource(R.string.unit_today_home, status.count { !it.absent && it.activity == null && it.leave == null }), fontSize = 16.sp, color = AppColors.Ink)
                status.filter { it.activity != null }.forEach {
                    Text(stringResource(R.string.unit_today_at, name(it.memberId), it.activity!!.name, formatClock(it.until.orEmpty())), fontSize = 15.sp, color = AppColors.Ink)
                }
                status.filter { it.absent }.forEach {
                    Text(stringResource(R.string.unit_today_absent, name(it.memberId)), fontSize = 15.sp, color = AppColors.Muted)
                }
                status.mapNotNull { r -> r.leave?.let { r.memberId to it } }.forEach { (id, leave) ->
                    Text(
                        stringResource(R.string.unit_today_leave, name(id), leaveBack(leave)),
                        fontSize = 15.sp, color = AppColors.Muted
                    )
                }
                // Yksikön seuraava yhteinen kohta: montako on silloin kotona.
                val next = WeekSchedule.eventsFor(unit.plan, today).firstOrNull { WeekSchedule.minutesOf(it.time) >= WeekSchedule.minutesOf(nowTime) }
                if (next != null) {
                    val home = residents.count { id ->
                        val st = status.first { it.memberId == id }
                        val m = WeekSchedule.minutesOf(next.time)
                        !st.absent && Units.at(days.getValue(id).spans(today), next.time) == null &&
                            days.getValue(id).leaves.none { it.covers(today.atTime(m / 60, m % 60)) }
                    }
                    Text(stringResource(R.string.unit_today_next, next.cardName, formatClock(next.time), home), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = AppColors.Teal)
                }
                // Lomat ja pidemmät poissaolot: tulevat ja meneillään olevat.
                val upcoming = days.values.flatMap { it.leaves }.filter { it.until.isAfter(java.time.LocalDateTime.now()) }.distinctBy { it.id }.sortedBy { it.from }
                upcoming.forEach { leave ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            stringResource(R.string.leave_row, name(leave.memberId), leaveRange(leave)),
                            fontSize = 14.sp, color = AppColors.Ink, modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = {
                            GroupStore(app).deleteLeave(leave.id)
                            EventReminders.reschedule(app)
                            GroupSession.changed()
                        }) { Text(stringResource(R.string.leave_remove)) }
                    }
                }
                fi.aikapolku.app.ui.SecondaryButton(stringResource(R.string.leave_add), { leaveFor = residents }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

/** Ohjaajalle asukkaan päivän alla: yksikön kohdat, joita asukas ei näe, koska on silloin toiminnassa. */
@Composable
internal fun HiddenForClient() {
    val context = LocalContext.current
    val app = context.applicationContext
    val revision = GroupSession.revision
    val hidden = remember(revision) { ClientWeek.load(app).hiddenFor(LocalDate.now()) }
    if (hidden.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(stringResource(R.string.unit_hidden_title), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AppColors.Muted)
        hidden.forEach { (event, group) ->
            Text(
                stringResource(R.string.unit_hidden_line, formatClock(event.time), event.cardName, group.name),
                fontSize = 14.sp, color = AppColors.Muted
            )
        }
    }
}

/** "su 22.11. klo 18.00" */
internal fun leaveBack(leave: fi.aikapolku.app.data.Leave): String {
    val b = leave.until
    return dayShort(b.dayOfWeek.value).lowercase() + " ${b.dayOfMonth}.${b.monthValue}. " + formatClock(WeekSchedule.formatTime(b.hour, b.minute))
}

/** "pe 20.11. 15.00 – su 22.11. 18.00" */
internal fun leaveRange(leave: fi.aikapolku.app.data.Leave): String {
    fun f(t: java.time.LocalDateTime) = dayShort(t.dayOfWeek.value).lowercase() + " ${t.dayOfMonth}.${t.monthValue}. " +
        formatClock(WeekSchedule.formatTime(t.hour, t.minute))
    return f(leave.from) + " – " + f(leave.until)
}

/** Lomen merkintä: asukas, syy, alku ja paluu (päivä ja kellonaika). */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun LeaveDialog(residents: List<String>, nameOf: (String) -> String, onDismiss: () -> Unit, onSave: (fi.aikapolku.app.data.Leave) -> Unit) {
    val context = LocalContext.current
    var who by remember { mutableStateOf(residents.firstOrNull()) }
    var reason by remember { mutableStateOf(fi.aikapolku.app.data.LeaveReason.HOME) }
    val now = java.time.LocalDateTime.now().withSecond(0).withNano(0)
    var from by remember { mutableStateOf(now.withMinute(0)) }
    var until by remember { mutableStateOf(now.plusDays(2).withHour(18).withMinute(0)) }
    fun pick(at: java.time.LocalDateTime, onPicked: (java.time.LocalDateTime) -> Unit) {
        android.app.DatePickerDialog(context, { _, y, m, d ->
            val date = java.time.LocalDate.of(y, m + 1, d)
            pickTime(context, WeekSchedule.formatTime(at.hour, at.minute)) { t ->
                val mm = WeekSchedule.minutesOf(t)
                onPicked(date.atTime(mm / 60, mm % 60))
            }
        }, at.year, at.monthValue - 1, at.dayOfMonth).show()
    }
    val leave = who?.let { fi.aikapolku.app.data.Leave(java.util.UUID.randomUUID().toString(), it, from, until, reason) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.leave_add)) },
        text = {
            Column(androidx.compose.ui.Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.leave_info), fontSize = 14.sp, color = AppColors.Muted)
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    residents.forEach { id ->
                        androidx.compose.material3.FilterChip(selected = who == id, onClick = { who = id }, label = { Text(nameOf(id)) })
                    }
                }
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        fi.aikapolku.app.data.LeaveReason.HOME to R.string.leave_reason_home,
                        fi.aikapolku.app.data.LeaveReason.HOLIDAY to R.string.leave_reason_holiday,
                        fi.aikapolku.app.data.LeaveReason.HOSPITAL to R.string.leave_reason_hospital,
                        fi.aikapolku.app.data.LeaveReason.OTHER to R.string.leave_reason_other
                    ).forEach { (r, label) ->
                        androidx.compose.material3.FilterChip(selected = reason == r, onClick = { reason = r }, label = { Text(stringResource(label)) })
                    }
                }
                androidx.compose.material3.OutlinedButton(onClick = { pick(from) { from = it } }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.leave_from, leaveRange(fi.aikapolku.app.data.Leave("x", "x", from, from.plusMinutes(1))).substringBefore(" –")))
                }
                androidx.compose.material3.OutlinedButton(onClick = { pick(until) { until = it } }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.leave_until, leaveBack(fi.aikapolku.app.data.Leave("x", "x", until.minusMinutes(1), until))))
                }
                if (!until.isAfter(from)) Text(stringResource(R.string.leave_until_before), color = androidx.compose.material3.MaterialTheme.colorScheme.error, fontSize = 13.sp)
            }
        },
        confirmButton = {
            TextButton(enabled = leave != null && until.isAfter(from), onClick = { leave?.let(onSave) }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}
