package fi.aikapolku.app.programs

import fi.aikapolku.app.ui.AppIcons
import fi.aikapolku.app.ui.SecondaryButton
import fi.aikapolku.app.ui.SegmentedChoice
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import fi.aikapolku.app.R
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
            message = context.getString(R.string.aanitys_kaynnissa_paina_lopeta_tallennus)
        } catch (e: Exception) {
            recordingKind = null
            message = context.getString(R.string.aanitys_ei_kaynnistynyt_tarkista_mikrofonin)
        }
    }

    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val kind = pendingKind
        pendingKind = null
        if (granted && kind != null) start(kind)
        else message = context.getString(R.string.mikrofonin_kayttooikeutta_ei_annettu)
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
            message = context.getString(R.string.oma_aani_tallennettu)
        } catch (_: Exception) {
            message = context.getString(R.string.tallennus_epaonnistui)
        }
        recordingKind = null
    }

    if (showVoicePicker) {
        VoicePickerDialog(onDismiss = { showVoicePicker = false })
    } else {
        AlertDialog(
            onDismissRequest = { if (recordingKind == null) onDismiss() },
            title = { Text(stringResource(R.string.nakyma_ja_viestiaanet)) },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 490.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(stringResource(R.string.seuraavan_vaiheen_esikatselu), fontWeight = FontWeight.Bold)
                    val nextModes = listOf(RunPreferences.NEXT_HIDDEN, RunPreferences.NEXT_TEXT, RunPreferences.NEXT_CARD)
                    SegmentedChoice(
                        options = listOf(stringResource(R.string.ei), stringResource(R.string.teksti), stringResource(R.string.kuva)),
                        selected = nextModes.indexOf(currentNextMode).coerceAtLeast(0),
                        onSelect = {
                            onNextModeChange(nextModes[it])
                            RunPreferences.setNextMode(context, nextModes[it])
                        }
                    )
                    // Kestotehtävän aika: itsestään vai asiakkaan Aloita aika -painalluksella (profiilikohtainen).
                    Text(stringResource(R.string.timer_start_title), fontWeight = FontWeight.Bold)
                    val timerModes = listOf(RunPreferences.TIMER_AUTO, RunPreferences.TIMER_BUTTON)
                    var timerStart by remember { mutableStateOf(RunPreferences.timerStart(context)) }
                    SegmentedChoice(
                        options = listOf(stringResource(R.string.timer_start_auto), stringResource(R.string.timer_start_button)),
                        selected = timerModes.indexOf(timerStart).coerceAtLeast(0),
                        onSelect = {
                            timerStart = timerModes[it]
                            RunPreferences.setTimerStart(context, timerModes[it])
                        }
                    )
                    // Valmis-painike, kun kestotehtävän aikaa on jäljellä (profiilikohtainen).
                    Text(stringResource(R.string.done_mode_title), fontWeight = FontWeight.Bold)
                    val doneModes = listOf(RunPreferences.DONE_ALWAYS, RunPreferences.DONE_CONFIRM, RunPreferences.DONE_AFTER_TIME)
                    var doneMode by remember { mutableStateOf(RunPreferences.doneMode(context)) }
                    SegmentedChoice(
                        options = listOf(
                            stringResource(R.string.done_mode_always),
                            stringResource(R.string.done_mode_confirm),
                            stringResource(R.string.done_mode_after)
                        ),
                        selected = doneModes.indexOf(doneMode).coerceAtLeast(0),
                        onSelect = {
                            doneMode = doneModes[it]
                            RunPreferences.setDoneMode(context, doneModes[it])
                        }
                    )
                    Text(stringResource(R.string.viestipainikkeiden_aani), fontWeight = FontWeight.Bold)
                    CompactVoiceRow(
                        label = stringResource(R.string.support_help),
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
                    CompactVoiceRow(
                        label = stringResource(R.string.support_break),
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
                    SecondaryButton(
                        text = stringResource(R.string.valitse_ja_kuuntele_puheaani),
                        icon = AppIcons.Speaker,
                        onClick = { showVoicePicker = true },
                        enabled = recordingKind == null,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (message.isNotBlank()) {
                        Text(message, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = recordingKind == null, onClick = onDismiss) {
                    Text(stringResource(R.string.dialog_done))
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
        // Oma ääni valittavissa vasta, kun se on äänitetty.
        val audioModes = listOf(RunPreferences.AUDIO_SILENT, RunPreferences.AUDIO_TTS, RunPreferences.AUDIO_RECORDED)
        SegmentedChoice(
            options = listOf(stringResource(R.string.ei_aanta), stringResource(R.string.puheaani), stringResource(R.string.oma_aani)),
            selected = audioModes.indexOf(selection).coerceAtLeast(0),
            onSelect = { index ->
                if (audioModes[index] != RunPreferences.AUDIO_RECORDED || path.isNotBlank()) onSelection(audioModes[index])
            }
        )
        if (recording) {
            Button(onClick = onFinish, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.lopeta_tallennus))
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SecondaryButton(
                    text = if (path.isEmpty()) stringResource(R.string.aanita_oma_viesti) else stringResource(R.string.aanita_uudelleen),
                    icon = AppIcons.Mic,
                    onClick = onStart,
                    enabled = canStart,
                    modifier = Modifier.weight(1f)
                )
                if (path.isNotBlank()) {
                    SecondaryButton(
                        text = stringResource(R.string.kuuntele),
                        icon = AppIcons.Speaker,
                        onClick = onPlay
                    )
                }
            }
        }
    }
}
