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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fi.aikapolku.app.R
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
        title = { Text(stringResource(R.string.muut_toiminnot)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (canExtend) {
                    TextButton(onClick = onExtend, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.extend_5_min)) }
                }
                if (canChangeSettings) {
                    TextButton(onClick = onTimeSettings, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.ajan_nayttotapa)) }
                    TextButton(onClick = onViewSettings, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.nakyma_ja_viestiaanet)) }
                }
                TextButton(onClick = onShowProgram, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.nayta_koko_ohjelma)) }
                HorizontalDivider()
                if (canEnd) {
                    TextButton(onClick = onEnd, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.lopeta_ohjelma)) }
                } else {
                    CaregiverLockButton(modifier = Modifier.fillMaxWidth(), label = stringResource(R.string.ohjaajan_toiminnot_2))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.sulje)) }
        }
    )
}
