package fi.aikapolku.app.programs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fi.aikapolku.app.R
import fi.aikapolku.app.caregiver.CaregiverLockButton
import fi.aikapolku.app.data.DayProgram
import fi.aikapolku.app.data.RoutineTemplates

/** Päiväohjelmien lista: uusi ohjelma, valmiit pohjat ja tallennetut ohjelmat. Piirtyy kutsujan Columniin. */
@Composable
internal fun ProgramListView(
    programs: List<DayProgram>,
    canEdit: Boolean,
    onNew: () -> Unit,
    onTemplate: (RoutineTemplates.Template) -> Unit,
    onStart: (DayProgram) -> Unit,
    onPrint: (DayProgram) -> Unit,
    onEdit: (DayProgram) -> Unit,
    onDelete: (DayProgram) -> Unit
) {
    Text(
        stringResource(R.string.paivaohjelmat),
        fontSize = 28.sp,
        fontWeight = FontWeight.Bold
    )

    if (!canEdit) {
        CaregiverLockButton(label = stringResource(R.string.ohjaaja_luo_ja_muokkaa_ohjelmia))
    }

    if (canEdit) {
    Button(
        onClick = onNew,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
    ) {
        Text(stringResource(R.string.program_new_button))
    }

    Text(stringResource(R.string.aloita_valmiista_rutiinista), fontWeight = FontWeight.SemiBold)
    Text(
        stringResource(R.string.pohjaa_voi_muokata_ennen_tallentamista),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 13.sp
    )
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        RoutineTemplates.all.chunked(2).forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                row.forEach { template ->
                    OutlinedButton(
                        onClick = { onTemplate(template) },
                        modifier = Modifier.weight(1f).heightIn(min = 60.dp)
                    ) {
                        Text(stringResource(template.nameRes), fontSize = 13.sp)
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
    } // canEdit

    if (programs.isEmpty()) {
        Text(
            stringResource(R.string.ei_viela_paivaohjelmia_luo_ensimmainen),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    programs.forEach { program ->
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    program.name,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(pluralStringResource(R.plurals.activities_count, program.steps.size, program.steps.size))

                OutlinedButton(
                    onClick = { onPrint(program) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                ) {
                    Text(stringResource(R.string.tulosta_pdf))
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Button(
                        onClick = { onStart(program) }
                    ) {
                        Text(stringResource(R.string.kaynnista))
                    }
                    if (canEdit) {
                        TextButton(
                            onClick = { onEdit(program) }
                        ) {
                            Text(stringResource(R.string.edit))
                        }
                        TextButton(
                            onClick = { onDelete(program) }
                        ) {
                            Text(stringResource(R.string.delete))
                        }
                    }
                }
            }
        }
    }
}

/** Ohjelma suoritettu loppuun. */
@Composable
internal fun ProgramDoneView(programName: String, onBack: () -> Unit) {
    Spacer(Modifier.height(30.dp))
    Text(stringResource(R.string.program_all_done), fontSize = 29.sp, fontWeight = FontWeight.Bold)
    Text(stringResource(R.string.on_paattynyt, programName))
    Button(
        onClick = onBack,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
    ) {
        Text(stringResource(R.string.takaisin_paivaohjelmiin))
    }
}
