package fi.aikapolku.app.programs

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import fi.aikapolku.app.ui.SecondaryButton
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fi.aikapolku.app.R
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
            Icon(
                when {
                    step.subSteps.isEmpty() -> Icons.Rounded.Add
                    expanded -> Icons.Rounded.KeyboardArrowUp
                    else -> Icons.Rounded.KeyboardArrowDown
                },
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Text(
                if (step.subSteps.isEmpty()) stringResource(R.string.substeps_add_first)
                else stringResource(R.string.substeps_count, step.subSteps.size),
                fontSize = 15.sp,
                modifier = Modifier.padding(start = 6.dp)
            )
        }
        if (expanded) {
            Text(
                stringResource(R.string.jaa_tehtava_pienempiin_osiin_ajastin),
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
                        label = { Text(stringResource(R.string.osavaihe, index + 1)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = { onUpdate(step.copy(subSteps = step.subSteps.filterIndexed { i, _ -> i != index })) },
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) { Text(stringResource(R.string.delete)) }
                }
            }
            SecondaryButton(
                text = stringResource(R.string.lisaa_osavaihe),
                icon = Icons.Rounded.Add,
                onClick = { onUpdate(step.copy(subSteps = step.subSteps + "")) },
                enabled = step.subSteps.size < 16,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
