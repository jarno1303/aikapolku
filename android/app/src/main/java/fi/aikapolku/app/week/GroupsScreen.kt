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
import fi.aikapolku.app.data.GroupStore
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
    val profiles = remember { ProfileStore.list(app) }
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(false) }

    fun save(group: Group) {
        // Poistettujen kohtien poikkeukset pois.
        val ids = group.plan.events.map { it.id }.toSet()
        store.upsert(group.copy(exceptions = group.exceptions.filter { it.eventId in ids }))
        groups = store.load().sortedBy { it.name.lowercase() }
        EventReminders.reschedule(app)
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
            }
        )
        return
    }

    Text(stringResource(R.string.groups_info), fontSize = 15.sp, color = AppColors.Muted)
    if (groups.isEmpty()) {
        Text(stringResource(R.string.groups_empty), fontSize = 15.sp, color = AppColors.Ink)
    } else {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                groups.forEach { g ->
                    val days = g.plan.events.map { it.day }.toSet().sorted()
                    ChoiceRow(
                        title = g.name,
                        subtitle = stringResource(R.string.groups_row_info, g.members.size, daysLabel(days)),
                        onClick = { openId = g.id },
                        modifier = Modifier.padding(horizontal = 6.dp),
                        leading = { RowIcon(Icons.Rounded.Face) }
                    )
                }
            }
        }
    }
    if (canEdit) PrimaryButton(stringResource(R.string.groups_new), { creating = true })

    if (creating) {
        GroupDialog(
            title = stringResource(R.string.groups_new),
            initialName = "",
            initialMembers = emptySet(),
            profiles = profiles,
            onSave = { name, members ->
                val group = Group(UUID.randomUUID().toString(), name, profiles.map { it.id }.filter { it in members })
                save(group)
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
            if (members.isEmpty()) Text(stringResource(R.string.groups_no_members), fontSize = 14.sp, color = AppColors.Muted)
            members.forEach { p ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ProfileAvatar(p, 36.dp)
                    Text(profileName(p), fontSize = 16.sp, color = AppColors.Ink)
                }
            }
            if (canEdit) SecondaryButton(stringResource(R.string.groups_edit), { editing = true }, modifier = Modifier.fillMaxWidth())
        }
    }

    Text(
        stringResource(R.string.groups_runko),
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        color = AppColors.Ink,
        modifier = Modifier.padding(top = 6.dp).semantics { heading() }
    )
    Text(stringResource(R.string.groups_runko_info), fontSize = 14.sp, color = AppColors.Muted)

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
            initialName = group.name,
            initialMembers = group.members.toSet(),
            profiles = profiles,
            onSave = { name, chosen ->
                onChange(group.copy(name = name, members = profiles.map { it.id }.filter { it in chosen }))
                editing = false
            },
            onDismiss = { editing = false }
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

/** Ryhmän nimi ja jäsenet (asiakkaat valintaruuduin). */
@Composable
private fun GroupDialog(
    title: String,
    initialName: String,
    initialMembers: Set<String>,
    profiles: List<Profile>,
    onSave: (name: String, members: Set<String>) -> Unit,
    onDismiss: () -> Unit
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var members by remember { mutableStateOf(initialMembers) }
    var error by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(40); error = false },
                    label = { Text(stringResource(R.string.groups_name)) },
                    placeholder = { Text(stringResource(R.string.groups_name_hint)) },
                    singleLine = true,
                    isError = error,
                    modifier = Modifier.fillMaxWidth()
                )
                if (error) Text(stringResource(R.string.groups_name_needed), color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                Text(stringResource(R.string.groups_choose_members), fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
                profiles.forEach { p ->
                    val checked = p.id in members
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .clickable(role = Role.Checkbox) { members = if (checked) members - p.id else members + p.id },
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
            TextButton(onClick = { if (name.isBlank()) error = true else onSave(name.trim(), members) }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

/** Päivät lyhyesti: "ma–pe", "joka päivä" tai luettelo. */
@Composable
private fun daysLabel(days: List<Int>): String = when {
    days.isEmpty() -> stringResource(R.string.groups_no_runko)
    days == (1..7).toList() -> stringResource(R.string.groups_every_day)
    days == (1..5).toList() -> dayShort(1) + "–" + dayShort(5)
    else -> days.joinToString(", ") { dayShort(it) }
}
