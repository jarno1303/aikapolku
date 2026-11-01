package fi.aikapolku.app.voice

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
internal fun VoiceControls(
    voicePath: String,
    autoPlay: Boolean,
    onVoicePathChange: (String) -> Unit,
    onAutoPlayChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val recorder = remember(context) { VoiceRecorder(context.applicationContext, "voice") }
    var recording by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }

    fun begin() {
        try {
            VoicePlayer.stop()
            recorder.start()
            recording = true
            message = ""
        } catch (_: Exception) {
            recording = false
            message = "Tallennus ei käynnistynyt. Tarkista mikrofonin käyttöoikeus."
        }
    }

    val permission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) begin() else message = "Mikrofonin lupa evättiin."
    }

    DisposableEffect(recorder) {
        onDispose { recorder.cancel() }
    }

    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        HorizontalDivider()
        Text("Oma puheviesti", style = MaterialTheme.typography.titleSmall)
        Text(
            "Tallenna lyhyt ohje tälle kortille. Puhe tallennetaan vain tähän puhelimeen.",
            style = MaterialTheme.typography.bodySmall
        )

        if (recording) {
            Text("● Tallennus käynnissä", color = MaterialTheme.colorScheme.error)
            Button(
                onClick = {
                    try {
                        val path = recorder.finish()
                        onVoicePathChange(path)
                        message = "Puheviesti tallennettu."
                    } catch (_: Exception) {
                        message = "Tallennus epäonnistui. Kokeile uudelleen."
                    }
                    recording = false
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            ) { Text("Lopeta tallennus") }
        } else {
            OutlinedButton(
                onClick = {
                    if (androidx.core.content.ContextCompat.checkSelfPermission(
                            context, Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                    ) begin()
                    else permission.launch(Manifest.permission.RECORD_AUDIO)
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            ) { Text(if (voicePath.isBlank()) "● Tallenna puheviesti" else "● Äänitä uusi viesti") }
        }

        if (voicePath.isNotBlank() && !recording) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = {
                        val result = VoicePlayer.play(
                            context,
                            voicePath
                        )

                        message = result
                            ?: "Puheviesti toistetaan."
                    }
                ) {
                    Text("▶ Kuuntele")
                }
                TextButton(onClick = {
                    VoicePlayer.stop()
                    onVoicePathChange("")
                    onAutoPlayChange(false)
                }) { Text("Poista ääni") }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = autoPlay, onCheckedChange = onAutoPlayChange)
                Text("Toista automaattisesti toiminnon alkaessa")
            }
        }

        if (message.isNotBlank()) {
            Text(message, style = MaterialTheme.typography.bodySmall)
        }
    }
}
