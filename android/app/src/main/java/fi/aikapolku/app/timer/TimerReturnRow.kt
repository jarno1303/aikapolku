package fi.aikapolku.app.timer

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fi.aikapolku.app.R
import fi.aikapolku.app.cards.CardPhoto
import fi.aikapolku.app.data.TimeDisplayPrefs
import fi.aikapolku.app.ui.AppColors
import java.util.Locale

/**
 * Paluu kesken olevaan Ensin-ajastukseen (aloitus- ja Ensin–Sitten-näkymässä). Takaisin-ele ei
 * pysäytä aikaa, joten käyttäjä pääsee tästä aina samaan ajastukseen.
 */
@Composable
internal fun TimerReturnRow(
    task: String,
    photo: String,
    remaining: Long,
    running: Boolean,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    // Rauhallisessa tilassa ei näytetä vähenevää aikaa numeroina.
    val calm = remember(context) { TimeDisplayPrefs.load(context) == TimeDisplayPrefs.CALM }
    val status = when {
        remaining <= 0L -> stringResource(R.string.time_ended)
        running -> stringResource(R.string.timer_running_row)
        else -> stringResource(R.string.timer_paused_row)
    }
    val secs = ((remaining + 999L) / 1000L).coerceAtLeast(0L)
    val time = if (calm || remaining <= 0L) "" else String.format(Locale.getDefault(), "%d.%02d", secs / 60, secs % 60)
    val a11y = listOf(status, task, time).filter { it.isNotBlank() }.joinToString(", ")
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = AppColors.TealSoft,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .border(2.dp, AppColors.Teal, RoundedCornerShape(16.dp))
            .semantics(mergeDescendants = true) { contentDescription = a11y }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CardPhoto(task, photo, Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)))
            Column(modifier = Modifier.weight(1f)) {
                Text(status, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = AppColors.Teal)
                Text(
                    task,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AppColors.Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (time.isNotBlank()) {
                Text(time, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = AppColors.Ink)
            }
            Text("›", fontSize = 26.sp, color = AppColors.Teal)
        }
    }
}
