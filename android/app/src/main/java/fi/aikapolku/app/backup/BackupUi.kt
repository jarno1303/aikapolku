package fi.aikapolku.app.backup

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fi.aikapolku.app.R
import fi.aikapolku.app.caregiver.CaregiverSession
import fi.aikapolku.app.data.ProfileStore
import fi.aikapolku.app.data.ProgramStore
import fi.aikapolku.app.profiles.ProfileSession
import fi.aikapolku.app.ui.AppColors
import fi.aikapolku.app.ui.PrimaryButton
import fi.aikapolku.app.ui.SecondaryButton
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Sähköpostin liitteestä tai tiedostosta avattu vientitiedosto (MainActivity asettaa). */
internal object ImportLaunch {
    val pending = mutableStateOf<Uri?>(null)

    fun from(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW) intent.data?.let { pending.value = it }
    }
}

private fun errorText(context: Context, reason: BackupException.Reason): String = context.getString(
    when (reason) {
        BackupException.Reason.NOT_AIKAPOLKU -> R.string.backup_err_not
        BackupException.Reason.TOO_NEW -> R.string.backup_err_new
        BackupException.Reason.WRONG_PASSWORD, BackupException.Reason.PASSWORD_NEEDED -> R.string.backup_err_password
        BackupException.Reason.TOO_LARGE -> R.string.backup_err_large
        BackupException.Reason.BROKEN -> R.string.backup_err_broken
    }
)

/** Ohjaajan asetusten kortti: vienti ja tuonti. */
@Composable
internal fun BackupSettingsCard() {
    var exporting by remember { mutableStateOf(false) }
    val importer = rememberImportFlow()
    // Sähköpostin liitteestä avattu tiedosto: esikatselu aukeaa heti.
    val launched = ImportLaunch.pending.value
    LaunchedEffect(launched) {
        if (launched != null) {
            ImportLaunch.pending.value = null
            importer.start(launched)
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.backup_title), fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.backup_info), fontSize = 15.sp)
            PrimaryButton(text = stringResource(R.string.backup_export), onClick = { exporting = true }, modifier = Modifier.fillMaxWidth())
            SecondaryButton(text = stringResource(R.string.backup_import), onClick = importer::pick, modifier = Modifier.fillMaxWidth())
        }
    }

    if (exporting) {
        ExportDialog(initialScope = BackupScope.PROFILE, initialProgramIds = emptySet()) { exporting = false }
    }
    importer.Dialogs()
}

private fun readAll(context: Context, uri: Uri): ByteArray {
    val input = context.contentResolver.openInputStream(uri) ?: throw BackupException(BackupException.Reason.BROKEN)
    return input.use { stream ->
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val n = stream.read(buffer)
            if (n < 0) break
            total += n
            if (total > BackupArchive.MAX_TOTAL_BYTES) throw BackupException(BackupException.Reason.TOO_LARGE)
            out.write(buffer, 0, n)
        }
        out.toByteArray()
    }
}

/** Tuonnin vaiheet: tiedoston valinta, salasana, esikatselu ja tulos. */
internal class ImportFlow(
    private val context: Context,
    private val scope: kotlinx.coroutines.CoroutineScope,
    private val launchPicker: () -> Unit
) {
    var bytes by mutableStateOf<ByteArray?>(null)
    var askPassword by mutableStateOf(false)
    var passwordError by mutableStateOf(false)
    var preview by mutableStateOf<ImportPreview?>(null)
    var message by mutableStateOf<Pair<String, String>?>(null)
    var busy by mutableStateOf(false)

    fun pick() {
        CaregiverSession.keepOpenForOwnPicker()
        launchPicker()
    }

    fun start(uri: Uri) {
        scope.launch {
            busy = true
            try {
                bytes = withContext(Dispatchers.IO) { readAll(context, uri) }
                open(null)
            } catch (e: BackupException) {
                fail(e.reason)
            } catch (e: Exception) {
                fail(BackupException.Reason.BROKEN)
            } finally {
                busy = false
            }
        }
    }

    fun open(password: String?) {
        val data = bytes ?: return
        scope.launch {
            busy = true
            try {
                preview = withContext(Dispatchers.Default) { BackupImporter.read(data, password) }
                askPassword = false
            } catch (e: BackupException) {
                when (e.reason) {
                    BackupException.Reason.PASSWORD_NEEDED -> askPassword = true
                    BackupException.Reason.WRONG_PASSWORD -> {
                        askPassword = true
                        passwordError = true
                    }
                    else -> fail(e.reason)
                }
            } finally {
                busy = false
            }
        }
    }

    fun apply(target: ImportTarget, replaceWeek: Boolean) {
        val p = preview ?: return
        scope.launch {
            busy = true
            try {
                val r = withContext(Dispatchers.IO) { BackupImporter.apply(context, p, target, replaceWeek) }
                ProfileSession.changed()
                message = context.getString(R.string.backup_done_title) to (
                    context.getString(R.string.backup_done_summary, r.profiles, r.programs, r.cards, r.skipped) +
                        if (r.week && r.profiles == 0) " " + context.getString(R.string.backup_done_week) else ""
                    )
            } catch (e: Exception) {
                fail(BackupException.Reason.BROKEN)
            } finally {
                preview = null
                bytes = null
                busy = false
            }
        }
    }

    private fun fail(reason: BackupException.Reason) {
        askPassword = false
        preview = null
        bytes = null
        message = context.getString(R.string.backup_title) to errorText(context, reason)
    }

    fun cancel() {
        askPassword = false
        passwordError = false
        preview = null
        bytes = null
    }

    @Composable
    fun Dialogs() {
        if (askPassword) PasswordDialog(this)
        preview?.let { PreviewDialog(this, it) }
        message?.let { (title, text) ->
            AlertDialog(
                onDismissRequest = { message = null },
                title = { Text(title) },
                text = { Text(text) },
                confirmButton = { TextButton(onClick = { message = null }) { Text(stringResource(R.string.selva)) } }
            )
        }
    }
}

@Composable
private fun rememberImportFlow(): ImportFlow {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var flow: ImportFlow? = null
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) flow?.start(uri)
    }
    flow = remember(context) { ImportFlow(context, scope) { picker.launch(arrayOf("*/*")) } }
    return flow
}

@Composable
private fun PasswordDialog(flow: ImportFlow) {
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = flow::cancel,
        title = { Text(stringResource(R.string.backup_import_password_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.backup_import_password_text))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; flow.passwordError = false },
                    label = { Text(stringResource(R.string.backup_password_field)) },
                    singleLine = true,
                    isError = flow.passwordError,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                )
                if (flow.passwordError) Text(stringResource(R.string.backup_err_password), color = MaterialTheme.colorScheme.error)
                if (flow.busy) CircularProgressIndicator()
            }
        },
        confirmButton = {
            TextButton(enabled = password.isNotEmpty() && !flow.busy, onClick = { flow.open(password) }) {
                Text(stringResource(R.string.backup_open))
            }
        },
        dismissButton = { TextButton(onClick = flow::cancel) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
private fun PreviewDialog(flow: ImportFlow, preview: ImportPreview) {
    val forcedNew = preview.scope == BackupScope.ALL
    val programsOnly = preview.scope == BackupScope.PROGRAMS
    var target by remember { mutableStateOf(if (programsOnly) ImportTarget.CURRENT_PROFILE else ImportTarget.NEW_PROFILE) }
    var replaceWeek by remember { mutableStateOf(false) }
    val unnamed = stringResource(R.string.backup_unnamed)
    AlertDialog(
        onDismissRequest = flow::cancel,
        title = { Text(stringResource(R.string.backup_preview_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (programsOnly) {
                    Text(stringResource(R.string.backup_preview_programs, preview.programCount), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    if (preview.anonymized) Text(stringResource(R.string.backup_preview_anonymized), fontSize = 14.sp, color = AppColors.Muted)
                } else {
                    preview.profiles.forEach { p ->
                        Text(
                            stringResource(R.string.backup_preview_profile, p.name.ifBlank { unnamed }, p.programs, p.cards),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (p.weekEvents > 0) Text(stringResource(R.string.backup_preview_week, p.weekEvents), fontSize = 14.sp, color = AppColors.Muted)
                    }
                }
                if (!programsOnly && !forcedNew) {
                    listOf(
                        Triple(ImportTarget.NEW_PROFILE, R.string.backup_target_new, R.string.backup_target_new_info),
                        Triple(ImportTarget.CURRENT_PROFILE, R.string.backup_target_current, R.string.backup_target_current_info)
                    ).forEach { (option, title, info) ->
                        OptionRow(stringResource(title), stringResource(info), target == option) { target = option }
                    }
                    if (target == ImportTarget.CURRENT_PROFILE && preview.hasWeek) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .toggleable(value = replaceWeek, role = Role.Checkbox) { replaceWeek = it },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = replaceWeek, onCheckedChange = null)
                            Text(stringResource(R.string.backup_replace_week), modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                } else if (programsOnly) {
                    Text(stringResource(R.string.backup_target_current_info), fontSize = 14.sp, color = AppColors.Muted)
                } else {
                    Text(stringResource(R.string.backup_target_new_info), fontSize = 14.sp, color = AppColors.Muted)
                }
                if (flow.busy) CircularProgressIndicator()
            }
        },
        confirmButton = {
            TextButton(enabled = !flow.busy, onClick = { flow.apply(target, replaceWeek) }) {
                Text(stringResource(R.string.backup_import_button))
            }
        },
        dismissButton = { TextButton(onClick = flow::cancel) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
private fun OptionRow(title: String, info: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(modifier = Modifier.padding(start = 8.dp)) {
            Text(title, fontSize = 16.sp)
            Text(info, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * Vienti: mitä viedään, henkilötiedot ja salasana, sitten Jaa (sähköposti, Teams, Drive …)
 * tai Tallenna laitteelle.
 */
@Composable
internal fun ExportDialog(initialScope: BackupScope, initialProgramIds: Set<String>, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val coroutines = rememberCoroutineScope()
    val profiles = remember { ProfileStore.list(context) }
    val currentName = remember { ProfileStore.current(context).name }.ifBlank { stringResource(R.string.backup_unnamed) }
    val programs = remember { ProgramStore(context).load() }
    var scope by remember { mutableStateOf(initialScope) }
    var selected by remember { mutableStateOf(initialProgramIds) }
    var anonymize by remember { mutableStateOf(true) }
    var protect by remember { mutableStateOf(initialScope != BackupScope.PROGRAMS) }
    var protectTouched by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var again by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var pendingFile by remember { mutableStateOf<File?>(null) }

    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val file = pendingFile
        if (uri != null && file != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
            }.onSuccess {
                Toast.makeText(context, context.getString(R.string.backup_saved), Toast.LENGTH_LONG).show()
                onDismiss()
            }.onFailure {
                Toast.makeText(context, context.getString(R.string.backup_failed), Toast.LENGTH_LONG).show()
            }
        }
        pendingFile = null
    }

    val tooShort = stringResource(R.string.backup_password_short, BackupArchive.MIN_PASSWORD)
    val mismatch = stringResource(R.string.backup_password_mismatch)
    val needProgram = stringResource(R.string.backup_need_program)

    fun run(share: Boolean) {
        error = when {
            scope == BackupScope.PROGRAMS && selected.none { id -> programs.any { it.id == id } } -> needProgram
            protect && password.length < BackupArchive.MIN_PASSWORD -> tooShort
            protect && password != again -> mismatch
            else -> ""
        }
        if (error.isNotEmpty()) return
        busy = true
        coroutines.launch {
            val file = runCatching {
                withContext(Dispatchers.IO) {
                    BackupExporter.export(context, scope, selected, anonymize, if (protect) password else null)
                }
            }.getOrNull()
            busy = false
            if (file == null) {
                error = context.getString(R.string.backup_failed)
                return@launch
            }
            CaregiverSession.keepOpenForOwnPicker()
            if (share) {
                val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.files", file)
                val send = Intent(Intent.ACTION_SEND)
                    .setType("application/octet-stream")
                    .putExtra(Intent.EXTRA_STREAM, uri)
                    .putExtra(Intent.EXTRA_SUBJECT, file.nameWithoutExtension.replace('_', ' '))
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                val chooser = Intent.createChooser(send, context.getString(R.string.backup_share))
                if (context !is Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(chooser) }
                onDismiss()
            } else {
                pendingFile = file
                saver.launch(file.name)
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.backup_export)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                fun choose(option: BackupScope) {
                    scope = option
                    if (!protectTouched) protect = option != BackupScope.PROGRAMS
                    error = ""
                }
                if (profiles.size > 1) {
                    OptionRow(stringResource(R.string.backup_scope_all), stringResource(R.string.backup_scope_all_info), scope == BackupScope.ALL) { choose(BackupScope.ALL) }
                }
                OptionRow(stringResource(R.string.backup_scope_profile, currentName), stringResource(R.string.backup_scope_profile_info), scope == BackupScope.PROFILE) { choose(BackupScope.PROFILE) }
                OptionRow(stringResource(R.string.backup_scope_programs), stringResource(R.string.backup_scope_programs_info), scope == BackupScope.PROGRAMS) { choose(BackupScope.PROGRAMS) }

                if (scope == BackupScope.PROGRAMS) {
                    programs.forEach { p ->
                        val on = p.id in selected
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .padding(start = 16.dp)
                                .toggleable(value = on, role = Role.Checkbox) { selected = if (it) selected + p.id else selected - p.id; error = "" },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = on, onCheckedChange = null)
                            Text(p.name, modifier = Modifier.padding(start = 8.dp), fontSize = 16.sp)
                        }
                    }
                    SwitchRow(stringResource(R.string.backup_anonymize), stringResource(R.string.backup_anonymize_info), anonymize) { anonymize = it }
                }

                SwitchRow(stringResource(R.string.backup_password), stringResource(R.string.backup_password_info), protect) {
                    protect = it
                    protectTouched = true
                    error = ""
                }
                if (protect) {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it; error = "" },
                        label = { Text(stringResource(R.string.backup_password_field)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = again,
                        onValueChange = { again = it; error = "" },
                        label = { Text(stringResource(R.string.backup_password_again)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (!(scope == BackupScope.PROGRAMS && anonymize)) {
                    Text(stringResource(R.string.backup_privacy), fontSize = 13.sp, color = AppColors.Change)
                }
                Text(stringResource(R.string.backup_not_included), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)
                if (busy) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CircularProgressIndicator()
                        Text(stringResource(R.string.backup_working))
                    }
                }
                OutlinedButton(enabled = !busy, onClick = { run(share = false) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.backup_save))
                }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy, onClick = { run(share = true) }) { Text(stringResource(R.string.backup_share)) }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@Composable
private fun SwitchRow(title: String, info: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(info, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange, modifier = Modifier.semantics { contentDescription = title })
    }
}
