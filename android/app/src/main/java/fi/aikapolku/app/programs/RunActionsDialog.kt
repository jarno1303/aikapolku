package fi.aikapolku.app.programs

import androidx.compose.foundation.layout.size
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
import fi.aikapolku.app.caregiver.LocalCaregiver
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Settings
import fi.aikapolku.app.ui.AppIcons
import fi.aikapolku.app.ui.ChoiceRow
import fi.aikapolku.app.ui.RowIcon

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
    onSkip: () -> Unit,
    onEnd: () -> Unit,
    onDismiss: () -> Unit,
    /** Ohjaajalle: muutos nykyiseen vaiheeseen (null = ei näytetä). */
    onStepChange: (() -> Unit)? = null
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.muut_toiminnot)) },
        text = {
            // Toiminnot kuvakeriveinä; ohjaajan toiminnot lukossa yhtenä lukkorivinä.
            val caregiver = LocalCaregiver.current
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (canExtend) {
                    ChoiceRow(stringResource(R.string.extend_5_min), onExtend, leading = { RowIcon(Icons.Rounded.Add) })
                }
                if (canChangeSettings) {
                    ChoiceRow(stringResource(R.string.ajan_nayttotapa), onTimeSettings, leading = { RowIcon(Icons.Rounded.Settings) })
                    ChoiceRow(stringResource(R.string.nakyma_ja_viestiaanet), onViewSettings, leading = { RowIcon(AppIcons.Speaker) })
                }
                if (onStepChange != null) {
                    ChoiceRow(stringResource(R.string.quick_step_change), onStepChange, leading = {
                        fi.aikapolku.app.ui.ChangeSymbol(Modifier.size(36.dp))
                    })
                }
                // Ohitus on aina vapaa; ohitettu tehtävä näkyy himmeänä lopussa.
                ChoiceRow(stringResource(R.string.ohita_tehtava), onSkip, leading = { RowIcon(Icons.AutoMirrored.Rounded.ArrowForward) })
                ChoiceRow(stringResource(R.string.nayta_koko_ohjelma), onShowProgram, leading = { RowIcon(Icons.AutoMirrored.Rounded.List) })
                if (canEnd) {
                    ChoiceRow(stringResource(R.string.lopeta_ohjelma), onEnd, leading = { RowIcon(Icons.Rounded.Close) })
                } else {
                    ChoiceRow(stringResource(R.string.caregiver_actions), { caregiver.unlock() }, leading = { RowIcon(Icons.Rounded.Lock) })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        }
    )
}
