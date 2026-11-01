package fi.aikapolku.app.voice

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fi.aikapolku.app.R
import java.util.Locale

@Composable
internal fun VoicePickerDialog(onDismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val speech = remember(context) { Speech(context.applicationContext) }
    DisposableEffect(speech) { onDispose { speech.close() } }
    var chosenId by remember { mutableStateOf(VoicePrefs.voiceId(context)) }
    var rate by remember { mutableFloatStateOf(VoicePrefs.speed(context)) }
    val available = speech.finnishVoices
    // A missing or uninstalled selected voice is not shown as a working choice.
    LaunchedEffect(available) {
        if (speech.ready && chosenId.isNotEmpty() && available.none { it.name == chosenId }) {
            chosenId = ""
            VoicePrefs.setVoiceId(context, "")
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.valitse_puheaani)) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(stringResource(R.string.kuuntele_eri_aania_android_ei), fontSize = 13.sp)
                Text(speech.status, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                VoiceChoice(
                    label = stringResource(R.string.laitteen_oletusaani),
                    selected = chosenId.isEmpty(),
                    enabled = speech.ready,
                    onSelect = {
                        chosenId = ""
                        VoicePrefs.setVoiceId(context, "")
                    },
                    onPreview = { speech.speak(context.getString(R.string.tarvitsen_apua_haluan_pitaa_tauon), "") }
                )
                available.forEachIndexed { index, voice ->
                    VoiceChoice(
                        label = stringResource(R.string.voice_label, index + 1,
                            stringResource(if (voice.isNetworkConnectionRequired) R.string.voice_needs_network else R.string.voice_offline)),
                        selected = chosenId == voice.name,
                        enabled = speech.ready,
                        onSelect = {
                            chosenId = voice.name
                            VoicePrefs.setVoiceId(context, voice.name)
                        },
                        onPreview = { speech.speak(context.getString(R.string.tarvitsen_apua_haluan_pitaa_tauon), voice.name) }
                    )
                }
                HorizontalDivider()
                Text(stringResource(R.string.puheen_nopeus), fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.speech_rate_value, "%.1f".format(Locale.getDefault(), rate)), fontSize = 13.sp)
                Slider(
                    value = rate,
                    onValueChange = {
                        rate = it
                        VoicePrefs.setSpeed(context, it)
                    },
                    valueRange = 0.8f..1.2f,
                    steps = 3
                )
                Text(stringResource(R.string.puheaanet_tulevat_puhelimen_android_puhemoottorista),
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.valmis_4)) }
        }
    )
}

@Composable
private fun VoiceChoice(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit,
    onPreview: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        RadioButton(selected = selected, onClick = onSelect, enabled = enabled)
        Text(label, modifier = Modifier.weight(1f), fontSize = 13.sp)
        TextButton(onClick = onPreview, enabled = enabled) { Text(stringResource(R.string.kuuntele)) }
    }
}
