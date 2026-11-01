package fi.aikapolku.app.programs.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fi.aikapolku.app.R
import fi.aikapolku.app.cards.CardPhoto
import fi.aikapolku.app.data.DayProgram
import fi.aikapolku.app.data.ProgramTiming
import fi.aikapolku.app.data.TimeMode
import fi.aikapolku.app.ui.AppColors

@Composable
internal fun SavePage(draft: DayProgram, onDraftChange: (DayProgram) -> Unit) {
    val minutesText = stringResource(R.string.minutes_short)
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        OutlinedTextField(
            value = draft.name,
            onValueChange = { onDraftChange(draft.copy(name = it.take(80))) },
            label = { Text(stringResource(R.string.ohjelman_nimi)) },
            singleLine = true,
            isError = draft.name.isBlank(),
            supportingText = { if (draft.name.isBlank()) Text(stringResource(R.string.anna_ohjelmalle_nimi)) },
            modifier = Modifier.fillMaxWidth()
        )

        Text(stringResource(R.string.nain_ohjelma_nakyy), fontSize = 18.sp, fontWeight = FontWeight.Bold)
        val summary = when (draft.timeMode) {
            TimeMode.CLOCK -> {
                val end = ProgramTiming.endOf(draft.startClock, draft.steps)
                stringResource(R.string.kellonajat_alkaa_klo, draft.startClock) +
                    (end?.let { stringResource(R.string.paattyy_noin_klo, ProgramTiming.format(it)) } ?: "")
            }
            TimeMode.DURATION -> stringResource(R.string.kestot_jokaisella_toiminnolla_oma_ajastin)
            else -> stringResource(R.string.omaan_tahtiin_ei_aikoja)
        }
        Text(summary, fontSize = 14.sp, color = AppColors.Muted)

        val slots = if (draft.timeMode == TimeMode.CLOCK)
            ProgramTiming.schedule(draft.startClock, draft.steps) else emptyList()
        if (slots.any { it.overlap }) {
            Text(
                stringResource(R.string.huom_jokin_kiintea_aika_alkaa),
                color = MaterialTheme.colorScheme.error,
                fontSize = 13.sp
            )
        }

        draft.steps.forEachIndexed { index, step ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(8.dp)
            ) {
                val label = ProgramTiming.label(draft, index) { minutes -> minutesText.format(minutes) }
                if (draft.timeMode != TimeMode.FREE) {
                    Text(
                        label,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.Teal,
                        modifier = Modifier.width(62.dp)
                    )
                }
                CardPhoto(step.name, step.photo, Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)))
                Column(modifier = Modifier.weight(1f)) {
                    Text("${index + 1}. ${step.name}", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    if (step.subSteps.isNotEmpty()) {
                        Text(pluralStringResource(R.plurals.substeps_count_label, step.subSteps.size, step.subSteps.size), fontSize = 12.sp, color = AppColors.Muted)
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}
