package fi.aikapolku.app.week

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fi.aikapolku.app.R
import fi.aikapolku.app.caregiver.AppAction
import fi.aikapolku.app.caregiver.LocalCaregiver
import fi.aikapolku.app.cards.CardPhoto
import fi.aikapolku.app.data.ChangeReveal
import fi.aikapolku.app.data.ChangeType
import fi.aikapolku.app.data.DayChanges
import fi.aikapolku.app.data.DayEntry
import fi.aikapolku.app.data.EventChange
import fi.aikapolku.app.data.EventKind
import fi.aikapolku.app.data.Group
import fi.aikapolku.app.data.GroupStore
import fi.aikapolku.app.data.Moves
import fi.aikapolku.app.data.ProfileStore
import fi.aikapolku.app.data.Runko
import fi.aikapolku.app.data.WeekSchedule
import fi.aikapolku.app.profiles.ProfileAvatar
import fi.aikapolku.app.profiles.profileName
import fi.aikapolku.app.ui.AppColors
import fi.aikapolku.app.ui.ChangeSymbol
import fi.aikapolku.app.ui.ChoiceRow
import fi.aikapolku.app.ui.RowIcon
import java.time.LocalDate
import java.util.UUID

/** Ryhmän tämän päivän muutokset ja poissaolot: puhdas logiikka testattavaksi. */
internal object GroupToday {
    /** Ryhmän päivä muutoksineen (vain kortit). */
    fun entries(group: Group, changes: List<EventChange>, date: LocalDate): List<DayEntry> =
        DayChanges.entries(WeekSchedule.eventsFor(group.plan, date).filter { it.kind == EventKind.CARD }, changes, date, null)

    fun cancel(eventId: String, date: LocalDate, newId: () -> String) =
        EventChange(newId(), date, eventId, ChangeType.CANCEL, reveal = ChangeReveal.NOW, showOriginal = true)

    fun replace(eventId: String, date: LocalDate, name: String, photo: String, newId: () -> String) =
        EventChange(newId(), date, eventId, ChangeType.REPLACE, cardName = name, cardPhoto = photo, reveal = ChangeReveal.NOW, showOriginal = true)

    /** Ryhmän rungon kohta uuteen aikaan joka viikko (koko ryhmälle). */
    fun moveWeekly(group: Group, eventId: String, time: String): Group =
        group.copy(plan = group.plan.copy(events = group.plan.events.map { if (it.id == eventId) it.copy(time = time) else it }))
}

/**
 * Ohjaajan etusivu: Ryhmä tänään. Asiakkaat (napautus: paikalla ⇄ poissa), ryhmän päivä ja
 * sen muutokset. Ryhmän muutos koskee kaikkia paikalla olevia, myös myöhemmin paikalle
 * merkittyjä. Näkyy vain, jos laitteessa on ryhmiä.
 */
@Composable
internal fun GroupTodayCard() {
    val context = LocalContext.current
    val app = context.applicationContext
    val revision = GroupSession.revision
    val store = remember { GroupStore(app) }
    val me = remember { ProfileStore.currentId(app) }
    val profiles = remember(revision) { ProfileStore.list(app) }
    val groups = remember(revision) {
        store.load().filter { it.members.isNotEmpty() && it.plan.events.isNotEmpty() }
            .sortedWith(compareBy({ if (me in it.members) 0 else 1 }, { it.name.lowercase() }))
    }
    if (groups.isEmpty()) return
    var selectedId by rememberSaveable { mutableStateOf(groups.first().id) }
    val group = groups.firstOrNull { it.id == selectedId } ?: groups.first()
    val today = remember { LocalDate.now() }
    val absent = remember(revision, group.id) { store.absences()[group.id]?.get(today).orEmpty() }
    val changes = remember(revision, group.id) { store.changes(group.id) }
    val entries = GroupToday.entries(group, changes, today)
    val caregiver = LocalCaregiver.current
    val canEdit = caregiver.allows(AppAction.EDIT_PROGRAMS)
    val haptic = LocalHapticFeedback.current
    val drag = rememberRowDrag()
    AutoScrollWhileDragging(drag)
    var acting by remember { mutableStateOf<DayEntry?>(null) }
    var moving by remember { mutableStateOf<Pair<DayEntry, String>?>(null) }
    val newId = { UUID.randomUUID().toString() }

    fun saved() {
        fi.aikapolku.app.week.EventReminders.reschedule(app)
        GroupSession.changed()
    }

    Text(
        stringResource(R.string.group_today_title),
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        color = AppColors.Ink,
        modifier = Modifier.padding(top = 6.dp).semantics { heading() }
    )
    if (groups.size > 1) {
        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            groups.forEach { g -> ChoiceButton(g.name, selected = g.id == group.id) { selectedId = g.id } }
        }
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val members = profiles.filter { it.id in group.members }
            val present = members.count { it.id !in absent }
            Text(
                (if (groups.size == 1) group.name + " · " else "") + stringResource(R.string.group_today_present, present, members.size),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = AppColors.Teal
            )
            // Asiakkaat: napautus vaihtaa paikalla ⇄ poissa.
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                members.forEach { p ->
                    val isAbsent = p.id in absent
                    val name = profileName(p)
                    val stateText = stringResource(if (isAbsent) R.string.group_today_absent else R.string.group_today_here)
                    val toggleText = stringResource(R.string.group_today_toggle, name)
                    Column(
                        modifier = Modifier
                            .width(72.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable(enabled = canEdit, role = Role.Switch) {
                                store.setAbsent(group.id, p.id, today, !isAbsent)
                                saved()
                            }
                            .semantics {
                                contentDescription = toggleText
                                stateDescription = stateText
                            }
                            .padding(4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(modifier = Modifier.alpha(if (isAbsent) 0.35f else 1f)) { ProfileAvatar(p, 48.dp) }
                        Text(name, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = AppColors.Ink)
                        Text(
                            stateText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isAbsent) Color(0xFFB45309) else AppColors.Teal
                        )
                    }
                }
            }

            // Ryhmän päivä: napautus avaa muutokset, pitkä painallus siirtää.
            val dragging = drag.dragId
            val others = if (dragging != null) entries.filter { it.original.id != dragging } else emptyList()
            val target = dragging?.let { drag.target(others.map { o -> o.original.id }) }
            val targetTime = target?.let { Runko.suggestTime(others.getOrNull(it - 1)?.event?.time, others.getOrNull(it)?.event?.time) }
            entries.forEach { entry ->
                val key = entry.original.id
                val lineAbove = target != null && others.getOrNull(target)?.original?.id == key
                val lineBelow = target != null && target == others.size && others.lastOrNull()?.original?.id == key
                Box(
                    modifier = Modifier.dragRow(
                        drag = drag,
                        id = key,
                        enabled = canEdit && !entry.cancelled && entries.size > 1,
                        haptic = haptic,
                        onDrop = {
                            val rest = entries.filter { it.original.id != key }
                            val to = drag.target(rest.map { it.original.id })
                            val time = Runko.suggestTime(rest.getOrNull(to - 1)?.event?.time, rest.getOrNull(to)?.event?.time)
                            drag.end()
                            moving = entry to time
                        }
                    )
                ) {
                    GroupEntryRow(entry, canEdit) { if (drag.clickAllowed()) acting = entry }
                    if (lineAbove && targetTime != null) DropLine(targetTime, Modifier.align(Alignment.TopCenter))
                    if (lineBelow && targetTime != null) DropLine(targetTime, Modifier.align(Alignment.BottomCenter))
                }
            }
            if (canEdit) Text(stringResource(R.string.group_today_hint), fontSize = 13.sp, color = AppColors.Muted)
        }
    }

    acting?.let { entry ->
        GroupEntryDialog(
            entry = entry,
            groupName = group.name,
            onCancel = {
                store.upsertChange(group.id, GroupToday.cancel(entry.original.id, today, newId))
                acting = null
                saved()
            },
            onReplace = { name, photo ->
                store.upsertChange(group.id, GroupToday.replace(entry.original.id, today, name, photo, newId))
                acting = null
                saved()
            },
            onMove = {
                acting = null
                moving = entry to entry.event.time
            },
            onRestore = entry.change?.let { change ->
                {
                    store.deleteChange(group.id, change.id)
                    acting = null
                    saved()
                }
            },
            onDismiss = { acting = null }
        )
    }
    moving?.let { (entry, time) ->
        MoveDialog(
            name = entry.event.cardName,
            fromTime = entry.event.time,
            initialTime = time,
            clientName = group.name,
            isGroup = true,
            onToday = { chosen ->
                Moves.dayChange(changes, entry.original.id, today, chosen, ChangeReveal.NOW, true, newId)
                    ?.let { store.upsertChange(group.id, it) }
                moving = null
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                saved()
            },
            onWeekly = { chosen ->
                store.upsert(GroupToday.moveWeekly(group, entry.original.id, chosen))
                // Tämän päivän siirto samaan kohtaan ei enää tarvita.
                DayChanges.wholeChange(changes, today, entry.original.id)
                    ?.takeIf { it.type == ChangeType.MOVE }
                    ?.let { store.deleteChange(group.id, it.id) }
                moving = null
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                saved()
            },
            onDismiss = { moving = null },
            todayInfo = stringResource(R.string.group_move_today_info, group.name),
            weeklyInfo = stringResource(R.string.group_move_weekly_info, group.name)
        )
    }
}

@Composable
private fun GroupEntryRow(entry: DayEntry, canEdit: Boolean, onClick: () -> Unit) {
    val resolved = resolveEvent(entry.event, emptyList())
    val original = resolveEvent(entry.original, emptyList())
    val summary = entry.change?.let { changeSummary(it, original, resolved) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(12.dp))
            .then(if (canEdit) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(entry.event.time.replace(':', '.'), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = AppColors.Ink, modifier = Modifier.width(52.dp))
        CardPhoto(resolved.imageName, resolved.imagePath, Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                resolved.title,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = AppColors.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textDecoration = if (entry.cancelled) TextDecoration.LineThrough else null
            )
            if (summary != null) Text(summary, fontSize = 13.sp, color = AppColors.Change, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (entry.change != null) ChangeSymbol(Modifier.size(26.dp))
    }
}

/** Ryhmän kohdan muutos tälle päivälle: peru, vaihda, siirrä tai palauta. */
@Composable
private fun GroupEntryDialog(
    entry: DayEntry,
    groupName: String,
    onCancel: () -> Unit,
    onReplace: (String, String) -> Unit,
    onMove: () -> Unit,
    onRestore: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    var picking by remember { mutableStateOf(false) }
    if (picking) {
        QuickCardDialog(
            initialTime = entry.event.time,
            allowPrograms = false,
            showTime = false,
            onCard = { _, name, photo -> picking = false; onReplace(name, photo) },
            onProgram = {},
            onDismiss = { picking = false }
        )
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${entry.original.time.replace(':', '.')} ${entry.original.cardName}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.group_change_info, groupName), fontSize = 14.sp, color = AppColors.Muted)
                if (!entry.cancelled) {
                    ChoiceRow(stringResource(R.string.group_change_cancel), onCancel, leading = { RowIcon(Icons.Rounded.Close) })
                }
                ChoiceRow(stringResource(R.string.group_change_replace), { picking = true }, leading = { ChangeSymbol(Modifier.size(36.dp)) })
                if (!entry.cancelled) {
                    ChoiceRow(stringResource(R.string.group_change_move), onMove, leading = { RowIcon(Icons.Rounded.Refresh) })
                }
                if (onRestore != null) {
                    ChoiceRow(stringResource(R.string.group_change_restore), onRestore, leading = { RowIcon(Icons.Rounded.Refresh) })
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}
