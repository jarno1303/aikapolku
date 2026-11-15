package fi.aikapolku.app.week

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Face
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fi.aikapolku.app.R
import fi.aikapolku.app.data.Group
import fi.aikapolku.app.data.GroupKind
import fi.aikapolku.app.data.GroupStore
import fi.aikapolku.app.data.Units
import fi.aikapolku.app.data.WeekSchedule
import fi.aikapolku.app.data.Profile
import fi.aikapolku.app.data.ProfileStore
import fi.aikapolku.app.data.WeekStore
import fi.aikapolku.app.profiles.ProfileAvatar
import fi.aikapolku.app.profiles.profileName
import fi.aikapolku.app.ui.AppColors
import fi.aikapolku.app.ui.BackOverride
import fi.aikapolku.app.ui.ChoiceRow
import fi.aikapolku.app.ui.OverflowMenu
import fi.aikapolku.app.ui.PrimaryButton
import fi.aikapolku.app.ui.RowIcon
import fi.aikapolku.app.ui.ScreenTitle
import fi.aikapolku.app.ui.SecondaryButton
import java.util.UUID

/**
 * Ryhmät (ohjaajalle): ryhmällä on asiakkaat ja yhteinen viikkorunko. Rungon muutos näkyy
 * heti kaikkien jäsenten päivässä. Asiakkaan poikkeukset tehdään asiakkaan viikossa.
 */
@Composable
internal fun GroupsScreen(canEdit: Boolean) {
    val context = LocalContext.current
    val app = context.applicationContext
    val store = remember(context) { GroupStore(app) }
    var groups by remember { mutableStateOf(store.load().sortedBy { it.name.lowercase() }) }
    val profiles = remember(GroupSession.revision, fi.aikapolku.app.profiles.ProfileSession.revision) { ProfileStore.list(app) }
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(false) }
    var wizard by rememberSaveable { mutableStateOf(false) }

    fun save(group: Group) {
        // Poistettujen kohtien poikkeukset pois.
        val ids = group.plan.events.map { it.id }.toSet()
        store.upsert(group.copy(exceptions = group.exceptions.filter { it.eventId in ids }))
        groups = store.load().sortedBy { it.name.lowercase() }
        EventReminders.reschedule(app)
        GroupSession.changed()
    }

    // Ohjattu aloitus (V4): koko yksikkö kerralla.
    if (wizard) {
        UnitWizard(
            onDone = {
                wizard = false
                groups = store.load().sortedBy { it.name.lowercase() }
            },
            onCancel = { wizard = false }
        )
        return
    }

    val open = groups.firstOrNull { it.id == openId }
    if (open != null) {
        BackOverride { openId = null }
        BackHandler { openId = null }
        ScreenTitle(open.name)
        GroupDetail(
            group = open,
            profiles = profiles,
            otherGroups = groups.filter { it.id != open.id },
            canEdit = canEdit,
            onChange = ::save,
            onDelete = {
                store.delete(open.id)
                groups = store.load().sortedBy { it.name.lowercase() }
                openId = null
                EventReminders.reschedule(app)
                GroupSession.changed()
            }
        )
        return
    }

    Text(stringResource(R.string.groups_info), fontSize = 15.sp, color = AppColors.Muted)
    if (canEdit) PrimaryButton(stringResource(R.string.wizard_open), { wizard = true })
    if (groups.isEmpty()) {
        Text(stringResource(R.string.groups_empty), fontSize = 15.sp, color = AppColors.Ink)
    } else {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                // Yksiköt ylimmästä alkaen, alayksiköt sisennettyinä; sitten toiminnat ja ryhmät.
                val ordered = remember(groups) { orderedGroups(groups) }
                ordered.forEach { (g, depth) ->
                    val days = g.plan.events.map { it.day }.toSet().sorted()
                    val count = if (g.kind == GroupKind.UNIT && g.members.isEmpty())
                        Units.children(g, groups).sumOf { it.members.size } else g.members.size
                    ChoiceRow(
                        title = g.name,
                        subtitle = kindLabel(g, groups) + " · " + stringResource(R.string.groups_row_info, count, daysLabel(days)),
                        onClick = { openId = g.id },
                        modifier = Modifier.padding(start = (6 + depth * 20).dp, end = 6.dp),
                        leading = { RowIcon(Icons.Rounded.Face) }
                    )
                }
            }
        }
    }
    if (canEdit) SecondaryButton(stringResource(R.string.groups_new), { creating = true }, modifier = Modifier.fillMaxWidth())

    if (creating) {
        GroupDialog(
            title = stringResource(R.string.groups_new),
            initial = Group(UUID.randomUUID().toString(), "", kind = GroupKind.UNIT),
            allGroups = groups,
            profiles = profiles,
            onSave = { group ->
                save(group.copy(members = profiles.map { it.id }.filter { it in group.members }))
                creating = false
                openId = group.id
            },
            onDismiss = { creating = false }
        )
    }
}

@Composable
private fun GroupDetail(
    group: Group,
    profiles: List<Profile>,
    otherGroups: List<Group>,
    canEdit: Boolean,
    onChange: (Group) -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext
    var editing by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var timeFor by remember { mutableStateOf<Profile?>(null) }
    val members = profiles.filter { it.id in group.members }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = AppColors.TealSoft)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.groups_members, members.size),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.Ink,
                    modifier = Modifier.weight(1f).semantics { heading() }
                )
                if (canEdit) {
                    OverflowMenu(
                        contentDescription = stringResource(R.string.groups_actions, group.name),
                        items = listOf(
                            context.getString(R.string.groups_edit) to { editing = true },
                            context.getString(R.string.groups_delete) to { confirmDelete = true }
                        )
                    )
                }
            }
            Text(kindLabel(group, otherGroups + group), fontSize = 14.sp, color = AppColors.Muted)
            val subUnits = Units.children(group, otherGroups)
            if (subUnits.isNotEmpty()) {
                Text(stringResource(R.string.unit_children, subUnits.joinToString(", ") { it.name }), fontSize = 14.sp, color = AppColors.Ink)
            }
            if (members.isEmpty() && subUnits.isEmpty()) Text(stringResource(R.string.groups_no_members), fontSize = 14.sp, color = AppColors.Muted)
            members.forEach { p ->
                val own = group.memberTimes.firstOrNull { it.memberId == p.id }
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .then(if (canEdit && group.kind == GroupKind.ACTIVITY) Modifier.clickable { timeFor = p } else Modifier),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ProfileAvatar(p, 36.dp)
                    Column(Modifier.weight(1f)) {
                        Text(profileName(p), fontSize = 16.sp, color = AppColors.Ink)
                        if (group.kind == GroupKind.ACTIVITY) {
                            Text(
                                if (own != null) stringResource(R.string.unit_member_own_time, rangeText(own.start, own.end))
                                else stringResource(R.string.unit_member_group_time),
                                fontSize = 13.sp, color = if (own != null) AppColors.Teal else AppColors.Muted
                            )
                        }
                    }
                }
            }
            if (group.kind == GroupKind.ACTIVITY) {
                val all = otherGroups + group
                ConflictWarning(members.flatMap { p -> Units.conflicts(all, p.id).filter { it.first.id == group.id || it.second.id == group.id } }) { id ->
                    profiles.firstOrNull { it.id == id }?.let { profileNameText(it, context) } ?: id
                }
            }
            if (canEdit) SecondaryButton(stringResource(R.string.groups_edit), { editing = true }, modifier = Modifier.fillMaxWidth())
        }
    }

    Text(
        stringResource(if (group.kind == GroupKind.ACTIVITY) R.string.unit_activity_runko else R.string.groups_runko),
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        color = AppColors.Ink,
        modifier = Modifier.padding(top = 6.dp).semantics { heading() }
    )
    Text(
        stringResource(if (group.kind == GroupKind.ACTIVITY) R.string.unit_activity_runko_info else R.string.groups_runko_info),
        fontSize = 14.sp, color = AppColors.Muted
    )

    val copySources = remember(group.id, otherGroups) {
        otherGroups.filter { it.plan.events.isNotEmpty() }.map { context.getString(R.string.runko_group_source, it.name) to it.plan } +
            profiles.mapNotNull { p ->
                val plan = WeekStore(app, p.id).load()
                if (plan.events.isEmpty()) null else profileNameText(p, context) to plan
            }
    }
    RunkoEditor(
        plan = group.plan,
        onChange = { onChange(group.copy(plan = it)) },
        programs = emptyList(),
        canEdit = canEdit,
        allowPrograms = false,
        copySources = copySources
    )

    if (editing) {
        GroupDialog(
            title = stringResource(R.string.groups_edit),
            initial = group,
            allGroups = otherGroups + group,
            profiles = profiles,
            onSave = { edited ->
                val chosen = profiles.map { it.id }.filter { it in edited.members }
                onChange(edited.copy(members = chosen, memberTimes = edited.memberTimes.filter { it.memberId in chosen }))
                editing = false
            },
            onDismiss = { editing = false }
        )
    }
    timeFor?.let { p ->
        MemberTimeDialog(
            memberName = profileNameText(p, context),
            group = group,
            current = group.memberTimes.firstOrNull { it.memberId == p.id },
            memberId = p.id,
            onSave = { time ->
                onChange(group.copy(memberTimes = group.memberTimes.filterNot { it.memberId == p.id } + listOfNotNull(time)))
                timeFor = null
            },
            onDismiss = { timeFor = null }
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.groups_delete_title, group.name)) },
            text = { Text(stringResource(R.string.groups_delete_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text(stringResource(R.string.groups_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

private fun profileNameText(p: Profile, context: android.content.Context): String =
    p.name.ifBlank { context.getString(R.string.profile_unnamed) }

/** Ryhmän laji, nimi, yläyksikkö tai kokoontumiset ja jäsenet (asiakkaat valintaruuduin). */
@Composable
private fun GroupDialog(
    title: String,
    initial: Group,
    allGroups: List<Group>,
    profiles: List<Profile>,
    onSave: (Group) -> Unit,
    onDismiss: () -> Unit
) {
    var draft by remember { mutableStateOf(initial) }
    var error by remember { mutableStateOf(false) }
    // Yläyksiköksi kelpaa muu yksikkö, joka ei ole tämän alayksikkö (ei silmukoita).
    val parents = allGroups.filter { g ->
        g.kind == GroupKind.UNIT && g.id != initial.id && Units.ancestors(g, allGroups).none { it.id == initial.id }
    }
    val sessionsOk = draft.kind != GroupKind.ACTIVITY || (draft.sessions.isNotEmpty() &&
        draft.sessions.all { WeekSchedule.minutesOf(it.end) > WeekSchedule.minutesOf(it.start) })
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = draft.name,
                    onValueChange = { draft = draft.copy(name = it.take(40)); error = false },
                    label = { Text(stringResource(R.string.groups_name)) },
                    placeholder = { Text(stringResource(R.string.unit_name_hint)) },
                    singleLine = true,
                    isError = error,
                    modifier = Modifier.fillMaxWidth()
                )
                if (error) Text(stringResource(R.string.groups_name_needed), color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                GroupKindChoice(draft.kind) { draft = draft.copy(kind = it) }
                when (draft.kind) {
                    GroupKind.UNIT -> ParentUnitChoice(draft.parentId, parents) { draft = draft.copy(parentId = it) }
                    GroupKind.ACTIVITY -> {
                        ActivitySessionsEditor(draft.sessions) { draft = draft.copy(sessions = it) }
                        TransitionCardsSwitch(draft, draft.name) { leave, back -> draft = draft.copy(leaveCard = leave, returnCard = back) }
                    }
                    GroupKind.GROUP -> Unit
                }
                Text(stringResource(R.string.groups_choose_members), fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
                if (draft.kind == GroupKind.UNIT && parents.isNotEmpty() && draft.parentId.isBlank()) {
                    Text(stringResource(R.string.unit_members_in_sub), fontSize = 13.sp, color = AppColors.Muted)
                }
                profiles.forEach { p ->
                    val checked = p.id in draft.members
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .clickable(role = Role.Checkbox) {
                                draft = draft.copy(members = if (checked) draft.members - p.id else draft.members + p.id)
                            },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Checkbox(checked = checked, onCheckedChange = null)
                        ProfileAvatar(p, 36.dp)
                        Text(profileName(p), fontSize = 16.sp)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = sessionsOk, onClick = {
                if (draft.name.isBlank()) error = true
                else onSave(
                    draft.copy(
                        name = draft.name.trim(),
                        parentId = if (draft.kind == GroupKind.UNIT) draft.parentId else "",
                        sessions = if (draft.kind == GroupKind.ACTIVITY) draft.sessions else emptyList(),
                        cardPhoto = if (draft.kind == GroupKind.ACTIVITY) draft.cardPhoto.ifBlank { DEFAULT_ACTIVITY_PHOTO } else draft.cardPhoto,
                        leaveCard = if (draft.kind == GroupKind.ACTIVITY) draft.leaveCard else "",
                        returnCard = if (draft.kind == GroupKind.ACTIVITY) draft.returnCard else ""
                    )
                )
            }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

private const val DEFAULT_ACTIVITY_PHOTO = "builtin:schoolwork"

/** Yksiköt puuna (alayksiköt yläyksikön alla), sitten toiminnat ja ryhmät. Pari = ryhmä ja syvyys. */
internal fun orderedGroups(groups: List<Group>): List<Pair<Group, Int>> {
    val byName = groups.sortedBy { it.name.lowercase() }
    val units = byName.filter { it.kind == GroupKind.UNIT }
    val out = mutableListOf<Pair<Group, Int>>()
    fun add(g: Group, depth: Int) {
        if (out.any { it.first.id == g.id }) return
        out += g to depth
        Units.children(g, units).forEach { add(it, depth + 1) }
    }
    units.filter { u -> u.parentId.isBlank() || units.none { it.id == u.parentId } }.forEach { add(it, 0) }
    units.forEach { add(it, 0) } // silmukassa olevat
    byName.filter { it.kind == GroupKind.ACTIVITY }.forEach { add(it, 0) }
    byName.filter { it.kind == GroupKind.GROUP }.forEach { add(it, 0) }
    return out
}

/** Päivät lyhyesti: "ma–pe", "joka päivä" tai luettelo. */
@Composable
private fun daysLabel(days: List<Int>): String = when {
    days.isEmpty() -> stringResource(R.string.groups_no_runko)
    days == (1..7).toList() -> stringResource(R.string.groups_every_day)
    days == (1..5).toList() -> dayShort(1) + "–" + dayShort(5)
    else -> days.joinToString(", ") { dayShort(it) }
}
