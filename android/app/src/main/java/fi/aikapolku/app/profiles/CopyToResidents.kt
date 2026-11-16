package fi.aikapolku.app.profiles

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fi.aikapolku.app.R
import fi.aikapolku.app.data.ProfileStore
import fi.aikapolku.app.ui.AppColors

/**
 * "Kopioi muille asukkaille": valitaan asukkaat rastilla (nykyinen ei listalla). Kukin saa oman
 * kopion, jota voi muokata vaikuttamatta muihin.
 */
@Composable
internal fun CopyToResidentsDialog(title: String, info: String, onDismiss: () -> Unit, onCopy: (Set<String>) -> Unit) {
    val context = LocalContext.current
    val current = remember { ProfileStore.currentId(context) }
    val others = remember { ProfileStore.list(context).filter { it.id != current } }
    var chosen by remember { mutableStateOf(setOf<String>()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(info, fontSize = 14.sp, color = AppColors.Muted)
                if (others.isEmpty()) Text(stringResource(R.string.copy_no_others))
                else TextButton(onClick = { chosen = if (chosen.size == others.size) emptySet() else others.map { it.id }.toSet() }) {
                    Text(stringResource(if (chosen.size == others.size) R.string.copy_select_none else R.string.copy_select_all))
                }
                others.forEach { p ->
                    val on = p.id in chosen
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Checkbox) { chosen = if (on) chosen - p.id else chosen + p.id },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = on, onCheckedChange = null)
                        Text(p.name.ifBlank { stringResource(R.string.profile_unnamed) }, modifier = Modifier.padding(start = 8.dp), fontSize = 16.sp)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = chosen.isNotEmpty(), onClick = { onCopy(chosen) }) { Text(stringResource(R.string.copy_confirm, chosen.size)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}
