package fi.aikapolku.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import java.io.File
import java.util.UUID

/** P0-21c: Local presentation and support-message settings; no account or network. */
internal object P021cPreferences {
    const val NEXT_HIDDEN = "hidden"
    const val NEXT_TEXT = "text"
    const val NEXT_CARD = "card"
    const val AUDIO_SILENT = "silent"
    const val AUDIO_TTS = "tts"
    const val AUDIO_RECORDED = "recorded"
    const val HELP = "help"
    const val BREAK = "break"

    private const val NAME = "aikapolku_p021c_preferences"
    private fun prefs(context: Context) = context.applicationContext
        .getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun nextMode(context: Context): String =
        (prefs(context).getString("next_mode", NEXT_CARD) ?: NEXT_CARD)
            .takeIf { it in setOf(NEXT_HIDDEN, NEXT_TEXT, NEXT_CARD) } ?: NEXT_CARD

    fun setNextMode(context: Context, mode: String) {
        if (mode in setOf(NEXT_HIDDEN, NEXT_TEXT, NEXT_CARD))
            prefs(context).edit().putString("next_mode", mode).apply()
    }

    fun voiceMode(context: Context, kind: String): String =
        (prefs(context).getString("audio_$kind", AUDIO_SILENT) ?: AUDIO_SILENT)
            .takeIf { it in setOf(AUDIO_SILENT, AUDIO_TTS, AUDIO_RECORDED) } ?: AUDIO_SILENT

    fun setVoiceMode(context: Context, kind: String, mode: String) {
        if (kind in setOf(HELP, BREAK) && mode in setOf(AUDIO_SILENT, AUDIO_TTS, AUDIO_RECORDED)) {
            prefs(context).edit().putString("audio_$kind", mode).apply()
        }
    }

    fun recording(context: Context, kind: String): String =
        if (kind in setOf(HELP, BREAK)) prefs(context).getString("recording_$kind", "") ?: "" else ""

    fun setRecording(context: Context, kind: String, path: String) {
        if (kind in setOf(HELP, BREAK))
            prefs(context).edit().putString("recording_$kind", path).apply()
    }
}

/** Does not modify the card recorder. Files stay within the app's private directory. */
private class P021cRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var file: File? = null

    fun start() {
        cancel()
        val dir = File(context.filesDir, "voice_messages")
        check(dir.exists() || dir.mkdirs()) { "Tallennuskansiota ei voitu luoda" }
        val output = File(dir, "support_${UUID.randomUUID()}.m4a")
        @Suppress("DEPRECATION")
        val next = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else MediaRecorder()
        try {
            next.setAudioSource(MediaRecorder.AudioSource.MIC)
            next.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            next.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            next.setAudioSamplingRate(22050)
            next.setAudioEncodingBitRate(64000)
            next.setOutputFile(output.absolutePath)
            next.prepare()
            next.start()
            recorder = next
            file = output
        } catch (e: Exception) {
            runCatching { next.reset() }
            runCatching { next.release() }
            output.delete()
            throw e
        }
    }

    fun finish(): String {
        val current = recorder ?: error("Äänitys ei ole käynnissä")
        val output = file ?: error("Tallennetiedosto puuttuu")
        recorder = null
        file = null
        return try {
            current.stop()
            check(output.length() > 0L) { "Äänitallenne on tyhjä" }
            output.absolutePath
        } catch (e: Exception) {
            output.delete()
            throw e
        } finally {
            runCatching { current.release() }
        }
    }

    fun cancel() {
        recorder?.let { current ->
            runCatching { current.stop() }
            runCatching { current.release() }
        }
        recorder = null
        file?.delete()
        file = null
    }
}

// P0-22a: Seuraavan vaiheen esikatselu siirtyi tiedostoon RunStepScreen.kt (RunNextRow).

@Composable
internal fun P021cSettingsDialog(
    currentNextMode: String,
    onNextModeChange: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val recorder = remember(context) { P021cRecorder(context.applicationContext) }
    var recordingKind by remember { mutableStateOf<String?>(null) }
    var pendingKind by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf("") }
    var helpMode by remember { mutableStateOf(P021cPreferences.voiceMode(context, P021cPreferences.HELP)) }
    var breakMode by remember { mutableStateOf(P021cPreferences.voiceMode(context, P021cPreferences.BREAK)) }
    var helpFile by remember { mutableStateOf(P021cPreferences.recording(context, P021cPreferences.HELP)) }
    var breakFile by remember { mutableStateOf(P021cPreferences.recording(context, P021cPreferences.BREAK)) }
    var showVoicePicker by remember { mutableStateOf(false) }

    fun start(kind: String) {
        try {
            P017VoicePlayer.stop()
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
            P021cPreferences.setRecording(context, kind, saved)
            if (kind == P021cPreferences.HELP) helpFile = saved else breakFile = saved
            P021cPreferences.setVoiceMode(context, kind, P021cPreferences.AUDIO_RECORDED)
            if (kind == P021cPreferences.HELP) helpMode = P021cPreferences.AUDIO_RECORDED
            else breakMode = P021cPreferences.AUDIO_RECORDED
            message = "Oma ääni tallennettu."
        } catch (_: Exception) {
            message = "Tallennus epäonnistui."
        }
        recordingKind = null
    }

    if (showVoicePicker) {
        P021dVoicePickerDialog(onDismiss = { showVoicePicker = false })
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
                            P021cPreferences.NEXT_HIDDEN to "Ei",
                            P021cPreferences.NEXT_TEXT to "Teksti",
                            P021cPreferences.NEXT_CARD to "Kuva"
                        ).forEach { (value, label) ->
                            FilterChip(
                                selected = currentNextMode == value,
                                onClick = {
                                    onNextModeChange(value)
                                    P021cPreferences.setNextMode(context, value)
                                },
                                label = { Text(label, fontSize = 12.sp) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    HorizontalDivider()
                    Text("Viestipainikkeiden ääni", fontWeight = FontWeight.Bold)
                    P021dCompactVoiceRow(
                        label = "Tarvitsen apua",
                        selection = helpMode,
                        path = helpFile,
                        recording = recordingKind == P021cPreferences.HELP,
                        canStart = recordingKind == null,
                        onSelection = {
                            helpMode = it
                            P021cPreferences.setVoiceMode(context, P021cPreferences.HELP, it)
                        },
                        onStart = { beginRecord(P021cPreferences.HELP) },
                        onFinish = { stopRecord(P021cPreferences.HELP) },
                        onPlay = { P017VoicePlayer.play(context, helpFile) }
                    )
                    HorizontalDivider()
                    P021dCompactVoiceRow(
                        label = "Tarvitsen tauon",
                        selection = breakMode,
                        path = breakFile,
                        recording = recordingKind == P021cPreferences.BREAK,
                        canStart = recordingKind == null,
                        onSelection = {
                            breakMode = it
                            P021cPreferences.setVoiceMode(context, P021cPreferences.BREAK, it)
                        },
                        onStart = { beginRecord(P021cPreferences.BREAK) },
                        onFinish = { stopRecord(P021cPreferences.BREAK) },
                        onPlay = { P017VoicePlayer.play(context, breakFile) }
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
private fun P021dCompactVoiceRow(
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
                P021cPreferences.AUDIO_SILENT to "Ei ääntä",
                P021cPreferences.AUDIO_TTS to "Puheääni",
                P021cPreferences.AUDIO_RECORDED to "Oma ääni"
            ).forEach { (value, title) ->
                FilterChip(
                    selected = selection == value,
                    enabled = value != P021cPreferences.AUDIO_RECORDED || path.isNotBlank(),
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
