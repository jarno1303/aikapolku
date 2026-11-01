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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fi.aikapolku.app.cards.CardPhoto
import fi.aikapolku.app.data.DayProgram
import fi.aikapolku.app.data.ProgramTiming

@Composable
internal fun SavePage(draft: DayProgram, onDraftChange: (DayProgram) -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        OutlinedTextField(
            value = draft.name,
            onValueChange = { onDraftChange(draft.copy(name = it.take(80))) },
            label = { Text("Ohjelman nimi") },
            singleLine = true,
            isError = draft.name.isBlank(),
            supportingText = { if (draft.name.isBlank()) Text("Anna ohjelmalle nimi.") },
            modifier = Modifier.fillMaxWidth()
        )

        Text("Näin ohjelma näkyy", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        val summary = when (draft.timeMode) {
            ProgramTiming.CLOCK -> {
                val end = ProgramTiming.endOf(draft.startClock, draft.steps)
                "Kellonajat · alkaa klo ${draft.startClock}" +
                    (end?.let { " · päättyy noin klo ${ProgramTiming.format(it)}" } ?: "")
            }
            ProgramTiming.DURATION -> "Kestot · jokaisella toiminnolla oma ajastin"
            else -> "Omaan tahtiin · ei aikoja"
        }
        Text(summary, fontSize = 14.sp, color = edMuted)

        val slots = if (draft.timeMode == ProgramTiming.CLOCK)
            ProgramTiming.schedule(draft.startClock, draft.steps) else emptyList()
        if (slots.any { it.overlap }) {
            Text(
                "Huom: jokin kiinteä aika alkaa ennen kuin edellinen toiminto päättyy. Voit tallentaa silti.",
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
                val label = ProgramTiming.label(draft, index)
                if (draft.timeMode != ProgramTiming.FREE) {
                    Text(
                        label,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = edTeal,
                        modifier = Modifier.width(62.dp)
                    )
                }
                CardPhoto(step.name, step.photo, Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)))
                Column(modifier = Modifier.weight(1f)) {
                    Text("${index + 1}. ${step.name}", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    if (step.subSteps.isNotEmpty()) {
                        Text("${step.subSteps.size} osavaihetta", fontSize = 12.sp, color = edMuted)
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}
