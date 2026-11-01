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
        "hampaiden pesu" -> listOf("Ota hammasharja", "Pese hampaat", "Laita hammasharja takaisin")
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
        // P0-23: tiivis avaus, jotta Järjestä-vaiheen rivit pysyvät matalina.
        TextButton(
            onClick = { expanded = !expanded },
            modifier = Modifier.heightIn(min = 48.dp)
        ) {
            Text(
                if (step.subSteps.isEmpty()) "+ Osavaiheet" else "Osavaiheet (${step.subSteps.size})  ${if (expanded) "⌃" else "›"}",
                fontSize = 15.sp
            )
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

// P0-22a: Suoritusnäkymän kortti, apu-/taukopainikkeet ja viesti-ikkuna korvattiin
// tiedostolla RunStepScreen.kt (RunStepScreen, SupportMessageScreen).
