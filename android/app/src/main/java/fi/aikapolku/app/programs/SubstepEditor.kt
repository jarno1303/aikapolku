package fi.aikapolku.app.programs

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fi.aikapolku.app.data.ProgramStep

@Composable
internal fun SubstepEditor(
    step: ProgramStep,
    onUpdate: (ProgramStep) -> Unit
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
