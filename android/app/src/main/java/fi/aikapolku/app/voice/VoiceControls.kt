package fi.aikapolku.app.voice

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import fi.aikapolku.app.ui.AppIcons
import fi.aikapolku.app.ui.SecondaryButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fi.aikapolku.app.R

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
            message = context.getString(R.string.tallennus_ei_kaynnistynyt_tarkista_mikrofonin)
        }
    }

    val permission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) begin() else message = context.getString(R.string.mikrofonin_lupa_evattiin)
    }

    DisposableEffect(recorder) {
        onDispose { recorder.cancel() }
    }

    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        HorizontalDivider()
        Text(stringResource(R.string.oma_puheviesti), style = MaterialTheme.typography.titleSmall)

        if (recording) {
            Text(context.getString(R.string.tallennus_kaynnissa), color = MaterialTheme.colorScheme.error)
            Button(
                onClick = {
                    try {
                        val path = recorder.finish()
                        onVoicePathChange(path)
                        message = context.getString(R.string.puheviesti_tallennettu)
                    } catch (_: Exception) {
                        message = context.getString(R.string.tallennus_epaonnistui_kokeile_uudelleen)
                    }
                    recording = false
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            ) { Text(stringResource(R.string.lopeta_tallennus)) }
        } else {
            SecondaryButton(
                text = if (voicePath.isBlank()) stringResource(R.string.tallenna_puheviesti) else stringResource(R.string.aanita_uusi_viesti),
                icon = AppIcons.Mic,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    if (androidx.core.content.ContextCompat.checkSelfPermission(
                            context, Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                    ) begin()
                    else permission.launch(Manifest.permission.RECORD_AUDIO)
                }
            )
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
                            ?: context.getString(R.string.puheviesti_toistetaan)
                    }
                ) {
                    Icon(AppIcons.Speaker, contentDescription = null, modifier = Modifier.size(20.dp))
                    Text(stringResource(R.string.kuuntele), modifier = Modifier.padding(start = 6.dp))
                }
                TextButton(onClick = {
                    VoicePlayer.stop()
                    onVoicePathChange("")
                    onAutoPlayChange(false)
                }) {
                    Icon(Icons.Rounded.Delete, contentDescription = null, modifier = Modifier.size(20.dp))
                    Text(stringResource(R.string.poista_aani), modifier = Modifier.padding(start = 6.dp))
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = autoPlay, onCheckedChange = onAutoPlayChange)
                Text(stringResource(R.string.toista_automaattisesti_toiminnon_alkaessa))
            }
        }

        if (message.isNotBlank()) {
            Text(message, style = MaterialTheme.typography.bodySmall)
        }
    }
}
