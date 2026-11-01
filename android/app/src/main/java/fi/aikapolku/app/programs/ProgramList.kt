package fi.aikapolku.app.programs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
        "Päiväohjelmat",
        fontSize = 28.sp,
        fontWeight = FontWeight.Bold
    )

    if (!canEdit) {
        CaregiverLockButton(label = "Ohjaaja: luo ja muokkaa ohjelmia")
    }

    if (canEdit) {
    Button(
        onClick = onNew,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
    ) {
        Text("+ Uusi päiväohjelma")
    }

    Text("Aloita valmiista rutiinista", fontWeight = FontWeight.SemiBold)
    Text(
        "Pohjaa voi muokata ennen tallentamista.",
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
                        Text(template.name, fontSize = 13.sp)
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
    } // canEdit

    if (programs.isEmpty()) {
        Text(
            "Ei vielä päiväohjelmia. Luo ensimmäinen ohjelma.",
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
                Text("${program.steps.size} toimintoa")

                OutlinedButton(
                    onClick = { onPrint(program) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                ) {
                    Text("Tulosta / PDF")
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Button(
                        onClick = { onStart(program) }
                    ) {
                        Text("Käynnistä")
                    }
                    if (canEdit) {
                        TextButton(
                            onClick = { onEdit(program) }
                        ) {
                            Text("Muokkaa")
                        }
                        TextButton(
                            onClick = { onDelete(program) }
                        ) {
                            Text("Poista")
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
    Text("✓ Kaikki valmista!", fontSize = 29.sp, fontWeight = FontWeight.Bold)
    Text("$programName on päättynyt.")
    Button(
        onClick = onBack,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
    ) {
        Text("Takaisin päiväohjelmiin")
    }
}
