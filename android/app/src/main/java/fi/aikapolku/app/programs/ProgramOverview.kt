package fi.aikapolku.app.programs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fi.aikapolku.app.cards.CardPhoto
import fi.aikapolku.app.data.ProgramStep

@Composable
internal fun ProgramOverview(
    steps: List<ProgramStep>,
    currentIndex: Int
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 280.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        steps.forEachIndexed { index, item ->
            val status = when {
                index < currentIndex -> "VALMIS"
                index == currentIndex -> "NYT"
                index == currentIndex + 1 -> "SEURAAVAKSI"
                else -> "TULOSSA"
            }

            val color = when {
                index == currentIndex ->
                    androidx.compose.ui.graphics.Color(0xFFE6F4F1)
                index < currentIndex ->
                    androidx.compose.ui.graphics.Color(0xFFF1F5F4)
                else ->
                    MaterialTheme.colorScheme.surfaceVariant
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = color
                )
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CardPhoto(
                        item.name,
                        item.photo,
                        Modifier.size(44.dp)
                    )

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "${index + 1}. ${item.name}",
                            fontWeight = if (index == currentIndex)
                                FontWeight.Bold
                            else FontWeight.Medium,
                            fontSize = 16.sp
                        )
                        Text(
                            status,
                            color = if (index == currentIndex)
                                programsTeal
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    if (item.mode == "clock") {
                        Text(
                            item.clock,
                            fontSize = 14.sp
                        )
                    } else if (item.mode == "duration") {
                        Text(
                            "${item.minutes} min",
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}
