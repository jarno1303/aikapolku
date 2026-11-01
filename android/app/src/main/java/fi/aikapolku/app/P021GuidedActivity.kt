package fi.aikapolku.app

import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

/** P0-21: Simple editable substeps, kept separate from the parent step timer. */
internal object P021SubstepDefaults {
    fun forCard(cardName: String): List<String> = when (cardName.trim().lowercase(Locale.ROOT)) {
        "pukeutuminen" -> listOf("Sukat", "Housut", "Paita", "Takki", "Kengät")
        "hampaiden pesu" -> listOf("Ota hammasharja", "Pese hampaat", "Laita välineet takaisin")
        else -> emptyList()
    }
}

@Composable
internal fun P021SubstepEditor(
    step: AP16Step,
    onUpdate: (AP16Step) -> Unit
) {
    var expanded by remember(step.id) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        OutlinedButton(
            onClick = { expanded = !expanded },
            modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)
        ) {
            Text("Osavaiheet (${step.subSteps.size})  ${if (expanded) "−" else "+"}")
        }
        if (expanded) {
            Text(
                "Jaa tehtävä pienempiin osiin. Ajastin koskee koko tehtävää.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            step.subSteps.forEachIndexed { index, item ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedTextField(
                        value = item,
                        onValueChange = { edited ->
                            val changed = step.subSteps.toMutableList()
                            changed[index] = edited.take(72)
                            onUpdate(step.copy(subSteps = changed))
                        },
                        label = { Text("Osavaihe ${index + 1}") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = { onUpdate(step.copy(subSteps = step.subSteps.filterIndexed { i, _ -> i != index })) },
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) { Text("Poista") }
                }
            }
            OutlinedButton(
                onClick = { onUpdate(step.copy(subSteps = step.subSteps + "")) },
                enabled = step.subSteps.size < 16,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            ) { Text("+ Lisää osavaihe") }
        }
    }
}

@Composable
internal fun P021CurrentSubstep(
    subSteps: List<String>,
    currentIndex: Int
) {
    if (subSteps.isEmpty()) return
    val safeIndex = currentIndex.coerceIn(0, subSteps.lastIndex)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color(0xFFE6F4F1))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                "VAIHE ${safeIndex + 1}/${subSteps.size}",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = androidx.compose.ui.graphics.Color(0xFF0F766E)
            )
            Text(
                subSteps[safeIndex],
                fontSize = 25.sp,
                lineHeight = 30.sp,
                fontWeight = FontWeight.Bold,
                color = androidx.compose.ui.graphics.Color(0xFF1F2937)
            )
            LinearProgressIndicator(
                progress = { (safeIndex + 1).toFloat() / subSteps.size.toFloat() },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
internal fun P021SupportButtons(onHelp: () -> Unit, onBreak: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedButton(
            onClick = onHelp,
            modifier = Modifier.weight(1f).heightIn(min = 62.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("Tarvitsen\napua", textAlign = TextAlign.Center, fontSize = 15.sp, lineHeight = 18.sp)
        }
        OutlinedButton(
            onClick = onBreak,
            modifier = Modifier.weight(1f).heightIn(min = 62.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("Tarvitsen\ntauon", textAlign = TextAlign.Center, fontSize = 15.sp, lineHeight = 18.sp)
        }
    }
}

@Composable
internal fun P021SupportMessageDialog(
    message: String,
    onDismiss: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val kind = if (message == "Tarvitsen tauon") P021cPreferences.BREAK
        else P021cPreferences.HELP
    val voiceMode = remember(message) { P021cPreferences.voiceMode(context, kind) }
    val recording = remember(message) { P021cPreferences.recording(context, kind) }
    val speech = remember(context) { P021dSpeech(context.applicationContext) }
    DisposableEffect(speech) {
        onDispose {
            speech.close()
            P017VoicePlayer.stop()
        }
    }
    fun playConfigured() {
        when (voiceMode) {
            P021cPreferences.AUDIO_TTS -> speech.speak(message)
            P021cPreferences.AUDIO_RECORDED -> {
                if (recording.isNotBlank()) P017VoicePlayer.play(context, recording)
            }
            else -> Unit
        }
    }
    // The user explicitly chose audio in settings; a single press can then communicate.
    LaunchedEffect(message, voiceMode, recording) { playConfigured() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Viestini") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(message, fontSize = 26.sp, lineHeight = 33.sp, fontWeight = FontWeight.Bold)
                Text("Ajastin on tauolla. Näytä viesti tarvittaessa toiselle henkilölle.",
                    fontSize = 14.sp)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Sulje") } },
        dismissButton = {
            if (voiceMode != P021cPreferences.AUDIO_SILENT) {
                TextButton(onClick = { playConfigured() }) { Text("Toista ääni") }
            }
        }
    )
}

/** Compact single focus card for programme playback. One picture, one current action. */
@Composable
internal fun P021FocusedActivityCard(
    title: String,
    photo: String,
    subSteps: List<String>,
    currentIndex: Int
) {
    val safeIndex = currentIndex.coerceIn(0, maxOf(0, subSteps.lastIndex))
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = androidx.compose.ui.graphics.Color(0xFFE6F4F1)
        )
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                P011Photo(title, photo, Modifier.size(110.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "NYT",
                        color = androidx.compose.ui.graphics.Color(0xFF0F766E),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                    Text(
                        title,
                        color = androidx.compose.ui.graphics.Color(0xFF1F2937),
                        fontWeight = FontWeight.Bold,
                        fontSize = 21.sp,
                        lineHeight = 26.sp
                    )
                    if (subSteps.isNotEmpty()) {
                        Text(
                            "Osavaihe ${safeIndex + 1}/${subSteps.size}",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            if (subSteps.isNotEmpty()) {
                Text(
                    subSteps[safeIndex],
                    fontSize = 26.sp,
                    lineHeight = 31.sp,
                    fontWeight = FontWeight.Bold,
                    color = androidx.compose.ui.graphics.Color(0xFF1F2937)
                )
                LinearProgressIndicator(
                    progress = { (safeIndex + 1).toFloat() / subSteps.size.toFloat() },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
