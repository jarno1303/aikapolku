package fi.aikapolku.app.programs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fi.aikapolku.app.caregiver.CaregiverLockButton

/** Suorituksen Lisää-valikko. Ohjaajan toiminnot näkyvät vain avattuna (P0-21e). */
@Composable
internal fun RunActionsDialog(
    canExtend: Boolean,
    canChangeSettings: Boolean,
    canEnd: Boolean,
    onExtend: () -> Unit,
    onTimeSettings: () -> Unit,
    onViewSettings: () -> Unit,
    onShowProgram: () -> Unit,
    onEnd: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Muut toiminnot") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (canExtend) {
                    TextButton(onClick = onExtend, modifier = Modifier.fillMaxWidth()) { Text("+ 5 min lisäaikaa") }
                }
                if (canChangeSettings) {
                    TextButton(onClick = onTimeSettings, modifier = Modifier.fillMaxWidth()) { Text("Ajan näyttötapa") }
                    TextButton(onClick = onViewSettings, modifier = Modifier.fillMaxWidth()) { Text("Näkymä ja viestiäänet") }
                }
                TextButton(onClick = onShowProgram, modifier = Modifier.fillMaxWidth()) { Text("Näytä koko ohjelma") }
                HorizontalDivider()
                if (canEnd) {
                    TextButton(onClick = onEnd, modifier = Modifier.fillMaxWidth()) { Text("Lopeta ohjelma") }
                } else {
                    CaregiverLockButton(modifier = Modifier.fillMaxWidth(), label = "Ohjaajan toiminnot")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Sulje") }
        }
    )
}
