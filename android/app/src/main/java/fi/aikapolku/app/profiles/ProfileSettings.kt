package fi.aikapolku.app.profiles

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fi.aikapolku.app.R
import fi.aikapolku.app.caregiver.CaregiverSession
import fi.aikapolku.app.data.Profile
import fi.aikapolku.app.data.ProfileStore
import fi.aikapolku.app.data.importCardPhoto
import fi.aikapolku.app.ui.AppColors

/** Ohjaajan asetusten Profiilit-osio: luettelo, lisäys, muokkaus, käyttöönotto ja valinta-asetus. */
@Composable
internal fun ProfilesSettingsCard() {
    val context = LocalContext.current
    val revision = ProfileSession.revision
    val profiles = remember(revision) { ProfileStore.list(context) }
    val currentId = remember(revision) { ProfileStore.currentId(context) }
    var selfSelect by remember(revision) { mutableStateOf(ProfileStore.selfSelect(context)) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var creating by rememberSaveable { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F3FA))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(stringResource(R.string.profiles_title), fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.profiles_info), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

            profiles.forEach { profile ->
                val current = profile.id == currentId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .clickable(role = Role.Button) { editingId = profile.id },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ProfileAvatar(profile, 44.dp)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            profileName(profile),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (current) Text(stringResource(R.string.profile_in_use), fontSize = 14.sp, color = AppColors.Teal)
                    }
                    Text("›", fontSize = 26.sp, color = AppColors.Teal)
                }
            }

            OutlinedButton(
                onClick = { creating = true },
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
            ) { Text(stringResource(R.string.profile_add)) }

            if (profiles.size > 1) {
                val switchLabel = stringResource(R.string.profile_self_select)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(switchLabel, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            stringResource(if (selfSelect) R.string.profile_self_select_on else R.string.profile_self_select_off),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = selfSelect,
                        onCheckedChange = {
                            ProfileStore.setSelfSelect(context, it)
                            selfSelect = it
                        },
                        modifier = Modifier.semantics { contentDescription = switchLabel }
                    )
                }
            }
        }
    }

    if (creating) {
        ProfileEditDialog(
            initial = null,
            isCurrent = false,
            canDelete = false,
            onSave = { name, photo ->
                ProfileStore.add(context, name, photo)
                ProfileSession.changed()
                creating = false
            },
            onUse = {},
            onDelete = {},
            onDismiss = { creating = false }
        )
    }

    profiles.firstOrNull { it.id == editingId }?.let { profile ->
        ProfileEditDialog(
            initial = profile,
            isCurrent = profile.id == currentId,
            canDelete = profiles.size > 1,
            onSave = { name, photo ->
                ProfileStore.update(context, profile.copy(name = name, photo = photo))
                ProfileSession.changed()
                editingId = null
            },
            onUse = {
                editingId = null
                ProfileSession.switchTo(context, profile.id)
            },
            onDelete = {
                editingId = null
                ProfileSession.delete(context, profile.id)
            },
            onDismiss = { editingId = null }
        )
    }
}

/** Uuden profiilin luonti tai olemassa olevan muokkaus. */
@Composable
private fun ProfileEditDialog(
    initial: Profile?,
    isCurrent: Boolean,
    canDelete: Boolean,
    onSave: (name: String, photo: String) -> Unit,
    onUse: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var name by rememberSaveable { mutableStateOf(initial?.name ?: "") }
    var photo by rememberSaveable { mutableStateOf(initial?.photo ?: "") }
    var error by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            try {
                photo = importCardPhoto(context, uri)
                error = ""
            } catch (_: Exception) {
                error = context.getString(R.string.kuvan_tallentaminen_ei_onnistunut)
            }
        }
    }

    val nameMissing = stringResource(R.string.profile_name_needed)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(if (initial == null) R.string.profile_new_title else R.string.profile_edit_title))
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    ProfileAvatar(Profile(initial?.id ?: "new", name, photo), 72.dp)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedButton(onClick = {
                            CaregiverSession.keepOpenForOwnPicker()
                            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }) { Text(stringResource(R.string.valitse_valokuva)) }
                        if (photo.isNotBlank()) {
                            TextButton(onClick = { photo = "" }) { Text(stringResource(R.string.profile_photo_remove)) }
                        }
                    }
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(40); error = "" },
                    label = { Text(stringResource(R.string.profile_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)

                if (initial != null && !isCurrent) {
                    OutlinedButton(
                        onClick = onUse,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                    ) { Text(stringResource(R.string.profile_use)) }
                }
                if (initial != null && canDelete) {
                    TextButton(
                        onClick = { confirmDelete = true },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.profile_delete), color = MaterialTheme.colorScheme.error) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isBlank()) error = nameMissing else onSave(name.trim(), photo)
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )

    if (confirmDelete && initial != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.profile_delete_title, profileName(initial))) },
            text = { Text(stringResource(R.string.profile_delete_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text(stringResource(R.string.profile_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}
