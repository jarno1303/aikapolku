package fi.aikapolku.app.programs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fi.aikapolku.app.R
import fi.aikapolku.app.cards.CardPhoto
import fi.aikapolku.app.data.DayProgram
import fi.aikapolku.app.ui.AppColors
import fi.aikapolku.app.ui.ChangeSymbol

/**
 * Päiväohjelman aloitus: nimi, tehtävät kuvina ja iso Aloita. Asiakas näkee, mitä tulee,
 * ja aloittaa itse; ensimmäinen tehtävä tulee vasta Aloita-painalluksesta.
 * Tänään perutut vaiheet eivät näy, muutetuissa on muutosmerkki.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ProgramStartView(program: DayProgram, onStart: () -> Unit) {
    val steps = program.steps.filterNot { it.cancelledToday }
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            program.name,
            modifier = Modifier.semantics { heading() },
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            color = AppColors.Ink,
            textAlign = TextAlign.Center
        )
        Text(stringResource(R.string.program_start_count, steps.size), fontSize = 18.sp, color = AppColors.Muted)
        if (program.steps.any { it.changedToday }) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChangeSymbol(Modifier.size(28.dp))
                Text(stringResource(R.string.program_start_changes), fontSize = 17.sp, fontWeight = FontWeight.Bold, color = AppColors.Change)
            }
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            steps.forEachIndexed { index, step ->
                Column(
                    modifier = Modifier.width(84.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    CardPhoto(step.name, step.photo, Modifier.size(72.dp).clip(RoundedCornerShape(16.dp)))
                    Text(
                        "${index + 1}. ${step.name}",
                        fontSize = 13.sp,
                        lineHeight = 16.sp,
                        color = AppColors.Ink,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        Button(
            onClick = onStart,
            modifier = Modifier.fillMaxWidth().heightIn(min = 68.dp),
            shape = RoundedCornerShape(20.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AppColors.Teal)
        ) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(32.dp))
            Text(stringResource(R.string.today_start), fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
        }
    }
}
