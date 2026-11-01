package fi.aikapolku.app.programs

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import fi.aikapolku.app.data.RunPreferences
import fi.aikapolku.app.voice.VoicePickerDialog
import fi.aikapolku.app.voice.VoicePlayer
import fi.aikapolku.app.voice.VoiceRecorder

@Composable
internal fun RunSettingsDialog(
    currentNextMode: String,
    onNextModeChange: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val recorder = remember(context) { VoiceRecorder(context.applicationContext, "support") }
    var recordingKind by remember { mutableStateOf<String?>(null) }
    var pendingKind by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf("") }
    var helpMode by remember { mutableStateOf(RunPreferences.voiceMode(context, RunPreferences.HELP)) }
    var breakMode by remember { mutableStateOf(RunPreferences.voiceMode(context, RunPreferences.BREAK)) }
    var helpFile by remember { mutableStateOf(RunPreferences.recording(context, RunPreferences.HELP)) }
    var breakFile by remember { mutableStateOf(RunPreferences.recording(context, RunPreferences.BREAK)) }
    var showVoicePicker by remember { mutableStateOf(false) }

    fun start(kind: String) {
        try {
            VoicePlayer.stop()
            recorder.start()
            recordingKind = kind
            message = "Äänitys käynnissä. Paina Lopeta tallennus."
        } catch (e: Exception) {
            recordingKind = null
            message = "Äänitys ei käynnistynyt. Tarkista mikrofonin käyttöoikeus."
        }
    }

    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val kind = pendingKind
        pendingKind = null
        if (granted && kind != null) start(kind)
        else message = "Mikrofonin käyttöoikeutta ei annettu."
    }
    DisposableEffect(recorder) {
        onDispose { recorder.cancel() }
    }

    fun beginRecord(kind: String) {
        if (recordingKind != null) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED) start(kind)
        else {
            pendingKind = kind
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    fun stopRecord(kind: String) {
        if (recordingKind != kind) return
        try {
            val saved = recorder.finish()
            RunPreferences.setRecording(context, kind, saved)
            if (kind == RunPreferences.HELP) helpFile = saved else breakFile = saved
            RunPreferences.setVoiceMode(context, kind, RunPreferences.AUDIO_RECORDED)
            if (kind == RunPreferences.HELP) helpMode = RunPreferences.AUDIO_RECORDED
            else breakMode = RunPreferences.AUDIO_RECORDED
            message = "Oma ääni tallennettu."
        } catch (_: Exception) {
            message = "Tallennus epäonnistui."
        }
        recordingKind = null
    }

    if (showVoicePicker) {
        VoicePickerDialog(onDismiss = { showVoicePicker = false })
    } else {
        AlertDialog(
            onDismissRequest = { if (recordingKind == null) onDismiss() },
            title = { Text("Näkymä ja viestiäänet") },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 490.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Seuraavan vaiheen esikatselu", fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        listOf(
                            RunPreferences.NEXT_HIDDEN to "Ei",
                            RunPreferences.NEXT_TEXT to "Teksti",
                            RunPreferences.NEXT_CARD to "Kuva"
                        ).forEach { (value, label) ->
                            FilterChip(
                                selected = currentNextMode == value,
                                onClick = {
                                    onNextModeChange(value)
                                    RunPreferences.setNextMode(context, value)
                                },
                                label = { Text(label, fontSize = 12.sp) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    HorizontalDivider()
                    Text("Viestipainikkeiden ääni", fontWeight = FontWeight.Bold)
                    CompactVoiceRow(
                        label = "Tarvitsen apua",
                        selection = helpMode,
                        path = helpFile,
                        recording = recordingKind == RunPreferences.HELP,
                        canStart = recordingKind == null,
                        onSelection = {
                            helpMode = it
                            RunPreferences.setVoiceMode(context, RunPreferences.HELP, it)
                        },
                        onStart = { beginRecord(RunPreferences.HELP) },
                        onFinish = { stopRecord(RunPreferences.HELP) },
                        onPlay = { VoicePlayer.play(context, helpFile) }
                    )
                    HorizontalDivider()
                    CompactVoiceRow(
                        label = "Tarvitsen tauon",
                        selection = breakMode,
                        path = breakFile,
                        recording = recordingKind == RunPreferences.BREAK,
                        canStart = recordingKind == null,
                        onSelection = {
                            breakMode = it
                            RunPreferences.setVoiceMode(context, RunPreferences.BREAK, it)
                        },
                        onStart = { beginRecord(RunPreferences.BREAK) },
                        onFinish = { stopRecord(RunPreferences.BREAK) },
                        onPlay = { VoicePlayer.play(context, breakFile) }
                    )
                    HorizontalDivider()
                    OutlinedButton(
                        onClick = { showVoicePicker = true },
                        enabled = recordingKind == null,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) { Text("Valitse ja kuuntele puheääni") }
                    if (message.isNotBlank()) {
                        Text(message, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("Valitse laitteella käytettävissä oleva ääni kuuntelemalla. Oma tallenne toimii myös ilman puhemoottoria.",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                TextButton(enabled = recordingKind == null, onClick = onDismiss) {
                    Text("Valmis")
                }
            }
        )
    }
}

@Composable
private fun CompactVoiceRow(
    label: String,
    selection: String,
    path: String,
    recording: Boolean,
    canStart: Boolean,
    onSelection: (String) -> Unit,
    onStart: () -> Unit,
    onFinish: () -> Unit,
    onPlay: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(label, fontWeight = FontWeight.SemiBold)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            listOf(
                RunPreferences.AUDIO_SILENT to "Ei ääntä",
                RunPreferences.AUDIO_TTS to "Puheääni",
                RunPreferences.AUDIO_RECORDED to "Oma ääni"
            ).forEach { (value, title) ->
                FilterChip(
                    selected = selection == value,
                    enabled = value != RunPreferences.AUDIO_RECORDED || path.isNotBlank(),
                    onClick = { onSelection(value) },
                    label = { Text(title, fontSize = 11.sp, maxLines = 1) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        if (recording) {
            Button(onClick = onFinish, modifier = Modifier.fillMaxWidth()) {
                Text("Lopeta tallennus")
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedButton(
                    onClick = onStart,
                    enabled = canStart,
                    modifier = Modifier.weight(1f)
                ) { Text(if (path.isEmpty()) "Äänitä oma viesti" else "Äänitä uudelleen", fontSize = 12.sp) }
                if (path.isNotBlank()) {
                    TextButton(onClick = onPlay) { Text("Kuuntele", fontSize = 12.sp) }
                }
            }
        }
    }
}
