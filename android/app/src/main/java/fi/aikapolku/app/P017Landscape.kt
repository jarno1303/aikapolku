package fi.aikapolku.app

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

private val P017Ink = Color(0xFF24282B)
private val P017Teal = Color(0xFF0F766E)

/** On landscape the clock is placed at the top right, not after the picture cards. */
@Composable
internal fun P017LandscapeTimer(
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
                P017SmallActivity("ENSIN", activeTask, firstPhoto)
                P017SmallActivity("SITTEN", nextActivity, nextPhoto)
            } else {
                Text("Ajastin", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                if (canAdjust) {
                    Text(
                        "Valitse aika pyörittämällä kellotaulua. " +
                            "Käynnissä olevan ajastimen aika on lukittu.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        listOf(1, 5, 10).forEach { min ->
                            OutlinedButton(
                                onClick = { if (canAdjust) onPreset(min) },
                                contentPadding = PaddingValues(horizontal = 7.dp)
                            ) { Text("$min min", fontSize = 12.sp) }
                        }
                    }
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
                if (timeMode == P020TimeDisplay.NUMBERS) {
                    P013ClockDial(
                        remaining = remaining,
                        seconds = seconds,
                        lastTen = showLastTen && seconds in 1..10,
                        onAdjustMinutes = { minutes ->
                            if (canAdjust) onPreset(minutes)
                        },
                        modifier = Modifier.size(dialSize)
                    )
                } else {
                    P020TimeVisual(
                        remaining = remaining, duration = duration,
                        mode = timeMode, running = running,
                        modifier = Modifier.size(dialSize)
                    )
                }
            }

            if (timeMode == P020TimeDisplay.NUMBERS) {
                Text(
                    String.format(Locale.getDefault(), "%02d:%02d", seconds / 60, seconds % 60),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = P017Ink
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                if (showReset) {
                    IconButton(onClick = onResetClick, modifier = Modifier.size(48.dp)) {
                        Text("↺", fontSize = 29.sp, color = P017Ink)
                    }
                } else {
                    CaregiverLockButton()
                }
                IconButton(
                    onClick = onStartPause,
                    modifier = Modifier
                        .size(58.dp)
                        .clip(CircleShape)
                        .background(P017Ink)
                ) {
                    Text(
                        if (running) "Ⅱ" else "▶",
                        color = Color.White,
                        fontSize = 26.sp
                    )
                }
                if (showSettings) {
                    IconButton(onClick = onSettingsClick, modifier = Modifier.size(48.dp)) {
                        Text("⚙", fontSize = 25.sp, color = P017Ink)
                    }
                } else {
                    Spacer(Modifier.size(48.dp))
                }
            }
        }
    }
}

@Composable
private fun P017SmallActivity(label: String, name: String, imagePath: String) {
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
            P011Photo(
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
                    color = P017Teal
                )
                Text(
                    name,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = P017Ink
                )
            }
        }
    }
}
