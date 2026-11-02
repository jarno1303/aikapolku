package fi.aikapolku.app.programs

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fi.aikapolku.app.R
import fi.aikapolku.app.cards.CardPhoto
import fi.aikapolku.app.data.DayProgram
import fi.aikapolku.app.data.RoutineTemplates
import fi.aikapolku.app.ui.AllDoneView
import fi.aikapolku.app.ui.AppColors
import fi.aikapolku.app.ui.ChoiceRow
import fi.aikapolku.app.ui.OverflowMenu
import fi.aikapolku.app.ui.PrimaryButton
import fi.aikapolku.app.ui.RowIcon
import fi.aikapolku.app.ui.RoundIconButton

/**
 * Päiväohjelmien lista. Ohjaajalle "Uusi päiväohjelma" (tyhjä tai valmis pohja samasta
 * ikkunasta), jokaisessa ohjelmassa Käynnistä ja ⋮-valikko. Piirtyy kutsujan Columniin.
 */
@Composable
internal fun ProgramListView(
    programs: List<DayProgram>,
    canEdit: Boolean,
    onNew: () -> Unit,
    onTemplate: (RoutineTemplates.Template) -> Unit,
    onStart: (DayProgram) -> Unit,
    onPrint: (DayProgram) -> Unit,
    onShare: (DayProgram) -> Unit,
    onEdit: (DayProgram) -> Unit,
    onDelete: (DayProgram) -> Unit
) {
    var choosingNew by remember { mutableStateOf(false) }

    if (programs.isEmpty()) {
        // Tyhjä tila: kuva ja lyhyt teksti keskellä.
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 32.dp, bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Image(
                painter = painterResource(R.drawable.ap_home_programs),
                contentDescription = null,
                modifier = Modifier.size(96.dp).clip(RoundedCornerShape(24.dp))
            )
            Text(
                stringResource(R.string.ei_viela_paivaohjelmia_luo_ensimmainen),
                fontSize = 17.sp,
                color = AppColors.Muted,
                textAlign = TextAlign.Center
            )
        }
    }

    if (canEdit) {
        PrimaryButton(
            text = stringResource(R.string.program_new_button),
            icon = Icons.Rounded.Add,
            onClick = { choosingNew = true }
        )
    }

    val menuLabel = stringResource(R.string.muut_toiminnot)
    val printLabel = stringResource(R.string.tulosta_pdf)
    val shareLabel = stringResource(R.string.share_as_image)
    val editLabel = stringResource(R.string.edit)
    val deleteLabel = stringResource(R.string.delete)
    programs.forEach { program ->
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(1.dp)
        ) {
            Row(
                modifier = Modifier.padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val first = program.steps.firstOrNull()
                CardPhoto(first?.name ?: program.name, first?.photo ?: "", Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)))
                Column(modifier = Modifier.weight(1f)) {
                    Text(program.name, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = AppColors.Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        pluralStringResource(R.plurals.activities_count, program.steps.size, program.steps.size),
                        fontSize = 14.sp,
                        color = AppColors.Muted
                    )
                }
                // Pyöreä käynnistyspainike, jotta nimelle jää tilaa.
                RoundIconButton(
                    icon = Icons.Rounded.PlayArrow,
                    contentDescription = stringResource(R.string.kaynnista) + ": " + program.name,
                    onClick = { onStart(program) },
                    size = 52.dp,
                    container = AppColors.Teal,
                    tint = Color.White
                )
                OverflowMenu(
                    contentDescription = "$menuLabel: ${program.name}",
                    items = buildList {
                        add(printLabel to { onPrint(program) })
                        add(shareLabel to { onShare(program) })
                        if (canEdit) {
                            add(editLabel to { onEdit(program) })
                            add(deleteLabel to { onDelete(program) })
                        }
                    }
                )
            }
        }
    }

    if (choosingNew) {
        AlertDialog(
            onDismissRequest = { choosingNew = false },
            title = { Text(stringResource(R.string.editor_title_new)) },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    ChoiceRow(
                        title = stringResource(R.string.program_empty),
                        subtitle = stringResource(R.string.program_empty_hint),
                        onClick = { choosingNew = false; onNew() },
                        leading = { RowIcon(Icons.Rounded.Add) }
                    )
                    Text(
                        stringResource(R.string.aloita_valmiista_rutiinista),
                        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp, start = 6.dp),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AppColors.Muted
                    )
                    RoutineTemplates.all.forEach { template ->
                        ChoiceRow(
                            title = stringResource(template.nameRes),
                            subtitle = stringResource(template.descriptionRes),
                            onClick = { choosingNew = false; onTemplate(template) },
                            leading = {
                                val firstCard = template.steps.firstOrNull()?.cardId ?: ""
                                CardPhoto(firstCard, "builtin:$firstCard", Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)))
                            }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { choosingNew = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

/** Ohjelma suoritettu loppuun: yhteinen Kaikki valmista -näkymä. */
@Composable
internal fun ProgramDoneView(program: DayProgram, skipped: Set<Int>, onBack: () -> Unit) {
    AllDoneView(
        subtitle = stringResource(R.string.on_paattynyt, program.name),
        images = program.steps.map { it.name to it.photo },
        dimmed = skipped,
        buttonText = stringResource(R.string.takaisin_paivaohjelmiin),
        onButton = onBack
    )
}
