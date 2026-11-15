package fi.aikapolku.app.week

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fi.aikapolku.app.R
import fi.aikapolku.app.data.ActivityConflict
import fi.aikapolku.app.data.ActivitySession
import fi.aikapolku.app.data.Group
import fi.aikapolku.app.data.GroupKind
import fi.aikapolku.app.data.MemberTime
import fi.aikapolku.app.data.WeekSchedule
import fi.aikapolku.app.ui.AppColors

/*
 * Yksikkömallin ohjaajan näkymät (Y1–Y3, Y6): ryhmän laji, yläyksikkö, toiminnan kokoontumiset,
 * asukkaan oma aika ja päällekkäisyysvaroitus.
 */

internal fun formatClock(time: String): String = time.replace(':', '.')

/** Aikaväli tekstinä: "9.00–14.00". */
internal fun rangeText(start: String, end: String): String = formatClock(start) + "–" + formatClock(end)

/** Kellonajan valinta Androidin omalla kellotaululla (24 h). */
internal fun pickTime(context: android.content.Context, time: String, onPicked: (String) -> Unit) {
    val m = WeekSchedule.minutesOf(time)
    android.app.TimePickerDialog(context, { _, h, min -> onPicked(WeekSchedule.formatTime(h, min)) }, m / 60, m % 60, true).show()
}

/** Ryhmän laji: ryhmä, yksikkö tai päivän toiminta. */
@Composable
internal fun GroupKindChoice(kind: GroupKind, onChange: (GroupKind) -> Unit) {
    Text(stringResource(R.string.unit_kind_title), fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
    listOf(
        GroupKind.UNIT to (R.string.unit_kind_unit to R.string.unit_kind_unit_info),
        GroupKind.ACTIVITY to (R.string.unit_kind_activity to R.string.unit_kind_activity_info),
        GroupKind.GROUP to (R.string.unit_kind_group to R.string.unit_kind_group_info)
    ).forEach { (value, texts) ->
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(role = Role.RadioButton) { onChange(value) },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            RadioButton(selected = kind == value, onClick = null)
            Column {
                Text(stringResource(texts.first), fontSize = 16.sp)
                Text(stringResource(texts.second), fontSize = 13.sp, color = AppColors.Muted)
            }
        }
    }
}

/** Yksikön yläyksikkö (alayksiköt: Villa Metsäketo → Omena). */
@Composable
internal fun ParentUnitChoice(parentId: String, units: List<Group>, onChange: (String) -> Unit) {
    if (units.isEmpty()) return
    Text(stringResource(R.string.unit_parent_title), fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
    (listOf("" to stringResource(R.string.unit_parent_none)) + units.map { it.id to it.name }).forEach { (id, name) ->
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.RadioButton) { onChange(id) },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            RadioButton(selected = parentId == id, onClick = null)
            Text(name, fontSize = 16.sp)
        }
    }
}

/**
 * Toiminnan kokoontumiset: viikonpäivät ja yksi aikaväli. Asukaskohtaiset ajat tehdään
 * jäsenen kohdalta (oma aika).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ActivitySessionsEditor(sessions: List<ActivitySession>, onChange: (List<ActivitySession>) -> Unit) {
    val context = LocalContext.current
    val days = sessions.map { it.day }.toSet()
    val start = sessions.firstOrNull()?.start ?: DEFAULT_START
    val end = sessions.firstOrNull()?.end ?: DEFAULT_END
    fun build(newDays: Set<Int>, s: String, e: String) =
        newDays.sorted().map { ActivitySession(it, s, e) }

    Text(stringResource(R.string.unit_sessions_title), fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        (1..7).forEach { d ->
            FilterChip(
                selected = d in days,
                onClick = { onChange(build(if (d in days) days - d else days + d, start, end)) },
                label = { Text(dayShort(d)) }
            )
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = { pickTime(context, start) { onChange(build(days, it, end)) } }, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.unit_start, formatClock(start)))
        }
        OutlinedButton(onClick = { pickTime(context, end) { onChange(build(days, start, it)) } }, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.unit_end, formatClock(end)))
        }
    }
    if (days.isNotEmpty() && WeekSchedule.minutesOf(end) <= WeekSchedule.minutesOf(start)) {
        Text(stringResource(R.string.unit_end_before_start), color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
    }
}

/** Siirtymäkortit (Y6): valmiina ehdotuksena, ohjaaja ottaa käyttöön. */
@Composable
internal fun TransitionCardsSwitch(group: Group, activityName: String, onChange: (leave: String, back: String) -> Unit) {
    val on = group.leaveCard.isNotBlank() || group.returnCard.isNotBlank()
    val leave = stringResource(R.string.unit_leave_card, activityName.ifBlank { stringResource(R.string.unit_kind_activity) })
    val back = stringResource(R.string.unit_return_card)
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.unit_transitions_title), fontSize = 16.sp)
            Text(stringResource(R.string.unit_transitions_info, leave, back), fontSize = 13.sp, color = AppColors.Muted)
        }
        Switch(checked = on, onCheckedChange = { onChange(if (it) leave else "", if (it) back else "") })
    }
}

/** Asukkaan oma aika toiminnassa (Y3). [current] null = ei omaa aikaa. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MemberTimeDialog(
    memberName: String,
    group: Group,
    current: MemberTime?,
    memberId: String,
    onSave: (MemberTime?) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val groupDays = group.sessions.map { it.day }.toSet()
    var start by remember { mutableStateOf(current?.start ?: group.sessions.firstOrNull()?.start ?: DEFAULT_START) }
    var end by remember { mutableStateOf(current?.end ?: group.sessions.firstOrNull()?.end ?: DEFAULT_END) }
    var days by remember { mutableStateOf(current?.days?.takeIf { it.isNotEmpty() } ?: groupDays) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.unit_member_time_title, memberName)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.unit_member_time_info, group.name), fontSize = 14.sp, color = AppColors.Muted)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    groupDays.sorted().forEach { d ->
                        FilterChip(selected = d in days, onClick = { days = if (d in days) days - d else days + d }, label = { Text(dayShort(d)) })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { pickTime(context, start) { start = it } }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.unit_start, formatClock(start)))
                    }
                    OutlinedButton(onClick = { pickTime(context, end) { end = it } }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.unit_end, formatClock(end)))
                    }
                }
                if (WeekSchedule.minutesOf(end) <= WeekSchedule.minutesOf(start)) {
                    Text(stringResource(R.string.unit_end_before_start), color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = days.isNotEmpty() && WeekSchedule.minutesOf(end) > WeekSchedule.minutesOf(start),
                onClick = { onSave(MemberTime(memberId, if (days == groupDays) emptySet() else days, start, end)) }
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            Row {
                if (current != null) TextButton(onClick = { onSave(null) }) { Text(stringResource(R.string.unit_member_time_remove)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        }
    )
}

/** Varoitus: asukkaalla kaksi toimintaa samaan aikaan. */
@Composable
internal fun ConflictWarning(conflicts: List<ActivityConflict>, nameOf: (String) -> String) {
    if (conflicts.isEmpty()) return
    Surface(color = AppColors.ChangeSoft, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.unit_conflict_title), fontWeight = FontWeight.Bold, color = AppColors.Change)
            conflicts.forEach { c ->
                Text(
                    stringResource(R.string.unit_conflict_line, nameOf(c.memberId), dayName(c.day), c.first.name, c.second.name),
                    fontSize = 14.sp, color = AppColors.Ink
                )
            }
        }
    }
}

/** Ryhmän lajin lyhyt kuvaus listaan. */
@Composable
internal fun kindLabel(group: Group, all: List<Group>): String = when (group.kind) {
    GroupKind.UNIT -> all.firstOrNull { it.id == group.parentId }?.let { stringResource(R.string.unit_row_sub, it.name) }
        ?: stringResource(R.string.unit_kind_unit)
    GroupKind.ACTIVITY -> group.sessions.firstOrNull()?.let {
        stringResource(R.string.unit_row_activity, sessionDaysLabel(group.sessions.map { s -> s.day }), rangeText(it.start, it.end))
    } ?: stringResource(R.string.unit_kind_activity)
    GroupKind.GROUP -> stringResource(R.string.unit_kind_group)
}

internal fun sessionDaysLabel(days: List<Int>): String {
    val sorted = days.distinct().sorted()
    return when {
        sorted == (1..5).toList() -> dayShort(1) + "–" + dayShort(5)
        sorted == (1..7).toList() -> dayShort(1) + "–" + dayShort(7)
        else -> sorted.joinToString(", ") { dayShort(it) }
    }
}

private const val DEFAULT_START = "09:00"
private const val DEFAULT_END = "14:00"
