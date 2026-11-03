package fi.aikapolku.app.week

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fi.aikapolku.app.R
import fi.aikapolku.app.data.WeekSchedule
import fi.aikapolku.app.ui.AppColors
import fi.aikapolku.app.ui.AppIcons
import fi.aikapolku.app.ui.PrimaryButton
import fi.aikapolku.app.ui.RoundIconButton
import fi.aikapolku.app.ui.SecondaryButton

/**
 * Raahauksen jälkeen: uusi aika (hienosäätö 5 min) ja koskeeko siirto vain tätä päivää vai
 * asiakkaan viikko-ohjelmaa joka viikko. [onToday] null = vain viikko-ohjelma (viikkomuokkain).
 */
@Composable
internal fun MoveDialog(
    name: String,
    fromTime: String,
    initialTime: String,
    clientName: String,
    isGroup: Boolean,
    onToday: ((String) -> Unit)?,
    onWeekly: (String) -> Unit,
    onDismiss: () -> Unit,
    /** Ryhmän koko päivä: omat selitystekstit (muuten asiakasta koskevat). */
    todayInfo: String? = null,
    weeklyInfo: String? = null
) {
    var minutes by remember { mutableIntStateOf(WeekSchedule.minutesOf(initialTime)) }
    val time = WeekSchedule.formatTime(minutes / 60, minutes % 60)
    val shown = time.replace(':', '.')
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.move_title, name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(fromTime.replace(':', '.') + " →", fontSize = 18.sp, color = AppColors.Muted)
                    RoundIconButton(
                        icon = AppIcons.Minus,
                        contentDescription = stringResource(R.string.runko_time_earlier),
                        onClick = { minutes = (minutes - 5).coerceAtLeast(0) },
                        container = AppColors.TealSoft,
                        tint = AppColors.Teal
                    )
                    Text(
                        shown,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.Ink,
                        modifier = Modifier.semantics { contentDescription = shown }
                    )
                    RoundIconButton(
                        icon = Icons.Rounded.Add,
                        contentDescription = stringResource(R.string.runko_time_later),
                        onClick = { minutes = (minutes + 5).coerceAtMost(23 * 60 + 55) },
                        container = AppColors.TealSoft,
                        tint = AppColors.Teal
                    )
                }
                if (onToday != null) {
                    PrimaryButton(stringResource(R.string.move_today), { onToday(time) })
                    Text(todayInfo ?: stringResource(R.string.move_today_info, clientName), fontSize = 13.sp, color = AppColors.Muted)
                }
                if (onToday != null) {
                    SecondaryButton(stringResource(R.string.move_weekly), { onWeekly(time) }, modifier = Modifier.fillMaxWidth())
                } else {
                    PrimaryButton(stringResource(R.string.move_weekly), { onWeekly(time) })
                }
                Text(
                    weeklyInfo ?: stringResource(if (isGroup) R.string.move_weekly_group_info else R.string.move_weekly_info, clientName),
                    fontSize = 13.sp,
                    color = AppColors.Muted
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}
