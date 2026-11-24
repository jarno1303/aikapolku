package fi.aikapolku.app.backup

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fi.aikapolku.app.R
import fi.aikapolku.app.caregiver.CaregiverSession
import fi.aikapolku.app.ui.AppColors
import fi.aikapolku.app.ui.PrimaryButton
import fi.aikapolku.app.ui.SecondaryButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Asetukset → Laitteen tiedot: automaattinen varmuuskopio, koko laitteen palautus ja kaikkien
 * tietojen poisto (T6). Vain ohjaajalle (asetukset ovat ohjaajan tilassa).
 */
@Composable
internal fun DeviceDataSettings() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val revision = AutoBackup.revision
    val enabled = remember(revision) { AutoBackup.enabled(context) }
    val last = remember(revision) { AutoBackup.last(context) }
    val error = remember(revision) { AutoBackup.lastError(context) }
    var pendingTree by remember { mutableStateOf<Uri?>(null) }
    var busy by remember { mutableStateOf(false) }
    var restoreBytes by remember { mutableStateOf<ByteArray?>(null) }
    var confirmErase by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }

    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            pendingTree = uri
        }
    }
    val pickBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            restoreBytes = runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
            if (restoreBytes == null) message = context.getString(R.string.backup_failed)
        }
    }

    Text(stringResource(R.string.autobackup_title), fontWeight = FontWeight.Bold)
    Text(stringResource(R.string.autobackup_info), fontSize = 14.sp, color = AppColors.Muted)
    if (enabled) {
        val status = if (last > 0L) stringResource(R.string.autobackup_last, fi.aikapolku.app.link.seenText(context, last))
        else stringResource(R.string.autobackup_none_yet)
        Text(status, fontWeight = FontWeight.SemiBold, color = AppColors.Teal)
        if (error.isNotBlank()) Text(stringResource(R.string.autobackup_error), color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
        SecondaryButton(
            text = stringResource(R.string.autobackup_now),
            onClick = {
                busy = true
                scope.launch {
                    val ok = withContext(Dispatchers.IO) { AutoBackup.runNow(context) }
                    busy = false
                    message = context.getString(if (ok) R.string.autobackup_done else R.string.autobackup_error)
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        TextButton(onClick = { AutoBackup.disable(context) }) { Text(stringResource(R.string.autobackup_disable)) }
    } else {
        PrimaryButton(
            text = stringResource(R.string.autobackup_enable),
            onClick = {
                CaregiverSession.keepOpenForOwnPicker()
                pickFolder.launch(null)
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
    SecondaryButton(
        text = stringResource(R.string.device_restore),
        onClick = {
            CaregiverSession.keepOpenForOwnPicker()
            pickBackup.launch(arrayOf("*/*"))
        },
        modifier = Modifier.fillMaxWidth()
    )
    TextButton(onClick = { confirmErase = true }) {
        Text(stringResource(R.string.device_erase), color = MaterialTheme.colorScheme.error)
    }
    if (busy) Text(stringResource(R.string.backup_working))
    if (message.isNotBlank()) Text(message, fontWeight = FontWeight.SemiBold)

    pendingTree?.let { tree ->
        PasswordTwiceDialog(
            title = stringResource(R.string.autobackup_password_title),
            onDismiss = { pendingTree = null },
            onOk = { password ->
                pendingTree = null
                if (!AutoBackup.configure(context, tree, password)) {
                    runCatching {
                        context.contentResolver.releasePersistableUriPermission(
                            tree, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        )
                    }
                    message = context.getString(R.string.secret_store_failed)
                    return@PasswordTwiceDialog
                }
                busy = true
                scope.launch {
                    val ok = withContext(Dispatchers.IO) { AutoBackup.runNow(context) }
                    busy = false
                    message = context.getString(if (ok) R.string.autobackup_done else R.string.autobackup_error)
                }
            }
        )
    }

    restoreBytes?.let { bytes ->
        RestoreDialog(bytes, onDismiss = { restoreBytes = null })
    }

    if (confirmErase) {
        var typed by remember { mutableStateOf("") }
        val word = stringResource(R.string.device_erase_word)
        AlertDialog(
            onDismissRequest = { confirmErase = false },
            title = { Text(stringResource(R.string.device_erase)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.device_erase_text, word))
                    OutlinedTextField(value = typed, onValueChange = { typed = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(enabled = typed.trim().equals(word, ignoreCase = true), onClick = { DeviceBackup.eraseAll(context) }) {
                    Text(stringResource(R.string.device_erase_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmErase = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

@Composable
private fun PasswordTwiceDialog(title: String, onDismiss: () -> Unit, onOk: (String) -> Unit) {
    var password by remember { mutableStateOf("") }
    var again by remember { mutableStateOf("") }
    val short = password.length < BackupArchive.MIN_PASSWORD
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.autobackup_password_text))
                OutlinedTextField(
                    value = password, onValueChange = { password = it }, singleLine = true,
                    label = { Text(stringResource(R.string.backup_password_field)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = again, onValueChange = { again = it }, singleLine = true,
                    label = { Text(stringResource(R.string.backup_password_again)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth()
                )
                if (password.isNotEmpty() && short) Text(stringResource(R.string.backup_password_short, BackupArchive.MIN_PASSWORD), color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = { TextButton(enabled = !short && password == again, onClick = { onOk(password) }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
private fun RestoreDialog(bytes: ByteArray, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.device_restore)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.device_restore_text), color = MaterialTheme.colorScheme.error)
                OutlinedTextField(
                    value = password, onValueChange = { password = it; error = "" }, singleLine = true,
                    label = { Text(stringResource(R.string.backup_password_field)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth()
                )
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
                if (busy) Text(stringResource(R.string.backup_working))
            }
        },
        confirmButton = {
            TextButton(enabled = password.isNotEmpty() && !busy, onClick = {
                busy = true
                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        runCatching { DeviceBackup.restore(context, DeviceBackup.read(bytes, password)) }
                    }
                    busy = false
                    val e = result.exceptionOrNull()
                    if (e == null) DeviceBackup.restart(context)
                    else error = context.getString(
                        if ((e as? BackupException)?.reason == BackupException.Reason.WRONG_PASSWORD) R.string.backup_err_password
                        else R.string.device_restore_failed
                    )
                }
            }) { Text(stringResource(R.string.device_restore_confirm), color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}
