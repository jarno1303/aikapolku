package fi.aikapolku.app.timer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fi.aikapolku.app.R
import fi.aikapolku.app.cards.CardPhoto
import fi.aikapolku.app.caregiver.CaregiverLockButton
import fi.aikapolku.app.data.TimeDisplayPrefs
import fi.aikapolku.app.ui.AppColors
import java.util.Locale

private val LandscapeInk = Color(0xFF24282B)

/** On landscape the clock is placed at the top right, not after the picture cards. */
@Composable
internal fun LandscapeTimer(
    remaining: Long,
    duration: Long,
    timeMode: String,
    running: Boolean,
    showLastTen: Boolean,
    activeTask: String,
    firstPhoto: String,
    nextActivity: String,
    nextPhoto: String,
    onPreset: (Int) -> Unit,
    onStartPause: () -> Unit,
    /** P0-21e: Tauko on lukittu (näytetään 🔒; painallus kysyy PIN-koodin). */
    pauseLocked: Boolean,
    onResetClick: () -> Unit,
    onSettingsClick: () -> Unit,
    /** P0-21e: ajan saa muuttaa (pysähdyksissä ja ohjaajana). */
    canAdjust: Boolean,
    showReset: Boolean,
    showSettings: Boolean
) {
    val guided = activeTask.isNotBlank()
    val seconds = ((remaining + 999L) / 1000L).coerceAtLeast(0L).toInt()

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Column(
            modifier = Modifier.weight(0.43f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (guided) {
                LandscapeActivityCard(stringResource(R.string.first_label), activeTask, firstPhoto)
                LandscapeActivityCard(stringResource(R.string.then_label), nextActivity, nextPhoto)
            } else {
                Text(stringResource(R.string.timer_title), fontSize = 24.sp, fontWeight = FontWeight.Bold)
                if (canAdjust) {
                    Text(
                        stringResource(R.string.valitse_aika_vetamalla_kellotaulua_kaynnissa),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        Column(
            modifier = Modifier.weight(0.57f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            BoxWithConstraints(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.TopCenter
            ) {
                val dialSize = minOf(maxWidth - 4.dp, 212.dp)
                if (timeMode == TimeDisplayPrefs.NUMBERS) {
                    ClockDial(
                        remaining = remaining,
                        seconds = seconds,
                        lastTen = showLastTen && seconds in 1..10,
                        onAdjustMinutes = { minutes ->
                            if (canAdjust) onPreset(minutes)
                        },
                        modifier = Modifier.size(dialSize)
                    )
                } else {
                    TimeVisual(
                        remaining = remaining, duration = duration,
                        mode = timeMode, running = running,
                        modifier = Modifier.size(dialSize)
                    )
                }
            }

            if (timeMode == TimeDisplayPrefs.NUMBERS) {
                Text(
                    String.format(Locale.getDefault(), "%02d:%02d", seconds / 60, seconds % 60),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = LandscapeInk
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                if (showReset) {
                    IconButton(onClick = onResetClick, modifier = Modifier.size(48.dp)) {
                        Text("↺", fontSize = 29.sp, color = LandscapeInk)
                    }
                } else {
                    CaregiverLockButton()
                }
                IconButton(
                    onClick = onStartPause,
                    modifier = Modifier
                        .size(58.dp)
                        .clip(CircleShape)
                        .background(LandscapeInk)
                ) {
                    Text(
                        when {
                            pauseLocked -> "🔒"
                            running -> "Ⅱ"
                            else -> "▶"
                        },
                        color = Color.White,
                        fontSize = if (pauseLocked) 22.sp else 26.sp
                    )
                }
                if (showSettings) {
                    IconButton(onClick = onSettingsClick, modifier = Modifier.size(48.dp)) {
                        Text("⚙", fontSize = 25.sp, color = LandscapeInk)
                    }
                } else {
                    Spacer(Modifier.size(48.dp))
                }
            }
        }
    }
}

@Composable
private fun LandscapeActivityCard(label: String, name: String, imagePath: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Row(
            modifier = Modifier.padding(7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            CardPhoto(
                name = name,
                imagePath = imagePath,
                modifier = Modifier
                    .size(85.dp)
                    .clip(RoundedCornerShape(10.dp))
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    label,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.Teal
                )
                Text(
                    name,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = LandscapeInk
                )
            }
        }
    }
}
