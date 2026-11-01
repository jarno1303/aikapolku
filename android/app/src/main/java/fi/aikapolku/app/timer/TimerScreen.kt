package fi.aikapolku.app.timer

import fi.aikapolku.app.data.ProfileScope
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fi.aikapolku.app.R
import fi.aikapolku.app.cards.CardPhoto
import fi.aikapolku.app.caregiver.AppAction
import fi.aikapolku.app.caregiver.CaregiverLockButton
import fi.aikapolku.app.caregiver.LocalCaregiver
import fi.aikapolku.app.data.TimeDisplayPrefs
import fi.aikapolku.app.firstthen.CompletedCard
import fi.aikapolku.app.ui.AppColors
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
internal fun TimerScreen(
    remaining: Long,
    duration: Long,
    running: Boolean,
    showLastTen: Boolean,
    activeTask: String,
    firstPhoto: String,
    nextPhoto: String,
    nextActivity: String,
    onGoNext: () -> Unit,
    onShowLastTen: (Boolean) -> Unit,
    onPreset: (Int) -> Unit,
    onStartPause: () -> Unit,
    onReset: () -> Unit
) {
    val guided = activeTask.isNotBlank()
    val finished = guided && remaining == 0L

    // P0-21e: ajan säätö vain pysäytettynä ja ohjaajana; nollaus ja asetukset ohjaajalle.
    val caregiver = LocalCaregiver.current
    val canAdjust = !running && caregiver.allows(AppAction.ADJUST_TIME)
    val showReset = caregiver.allows(AppAction.RESET_TIMER)
    val showSettings = caregiver.allows(AppAction.SETTINGS)
    // Tauko vaatii lukittuna PIN-koodin; Aloita ja Jatka ovat aina vapaita.
    val pauseLocked = running && !caregiver.allows(AppAction.PAUSE_TIMER)
    val currentRunning by rememberUpdatedState(running)
    val startPause: () -> Unit = {
        if (pauseLocked) {
            caregiver.run(AppAction.PAUSE_TIMER) { if (currentRunning) onStartPause() }
        } else {
            onStartPause()
        }
    }

    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var confirmReset by rememberSaveable { mutableStateOf(false) }

    val context = LocalContext.current
    val preferences = remember(context) {
        ProfileScope.prefs(context, ProfileScope.TIMER_PREFS)
    }

    var endSound by rememberSaveable {
        mutableStateOf(
            preferences.getString("end_sound", "off") ?: "off"
        )
    }

    val soundScope = rememberCoroutineScope()
    var timeMode by rememberSaveable {
        mutableStateOf(TimeDisplayPrefs.load(context))
    }

    val landscape = LocalConfiguration.current.orientation ==
        android.content.res.Configuration.ORIENTATION_LANDSCAPE

    if (!finished && landscape) {
        LandscapeTimer(
            remaining = remaining,
            duration = duration,
            timeMode = timeMode,
            running = running,
            showLastTen = showLastTen,
            activeTask = activeTask,
            firstPhoto = firstPhoto,
            nextActivity = nextActivity,
            nextPhoto = nextPhoto,
            onPreset = onPreset,
            onStartPause = startPause,
            pauseLocked = pauseLocked,
            onResetClick = { confirmReset = true },
            onSettingsClick = { settingsOpen = true },
            canAdjust = canAdjust,
            showReset = showReset,
            showSettings = showSettings
        )
    } else if (finished) {
        CompletedCard(
            first = activeTask,
            next = nextActivity,
            nextPhoto = nextPhoto
        )

        Button(
            onClick = onGoNext,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = AppColors.Teal
            )
        ) {
            Text(
                if (nextActivity == stringResource(R.string.choose_activity))
                    stringResource(R.string.dialog_done)
                else stringResource(R.string.siirry_seuraavaan),
                fontSize = 18.sp
            )
        }
    } else {
        if (guided) {
            Text(
                stringResource(R.string.toimintopolku),
                fontSize = 13.sp,
                letterSpacing = 1.sp,
                fontWeight = FontWeight.Bold,
                color = DialMuted
            )
            TimerTaskCards(
                first = activeTask,
                firstPhoto = firstPhoto,
                next = nextActivity,
                nextPhoto = nextPhoto
            )
        } else {
            Text(
                stringResource(R.string.timer_title),
                fontSize = 25.sp,
                fontWeight = FontWeight.Bold,
                color = AppColors.Ink
            )

            // Pikavalinnat poistettu (puhelintesti 1.11.2026): aika valitaan kellotaulua
            // vetämällä, eikä näkymä hypi ajastimen tilan tai lukituksen mukana.
        }

        val secs = ((remaining + 999L) / 1000L)
            .coerceAtLeast(0L)
            .toInt()

        val lastTen = showLastTen && secs in 1..10
        val compact = LocalConfiguration.current.screenHeightDp < 750

        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            val target = when {
                compact -> 258.dp
                guided -> 330.dp
                else -> 345.dp
            }

            val diameter = minOf(maxWidth - 8.dp, target)

            Box(
                modifier = Modifier.size(diameter),
                contentAlignment = Alignment.Center
            ) {
                if (timeMode == TimeDisplayPrefs.NUMBERS) {
                    ClockDial(
                        remaining = remaining,
                        seconds = secs,
                        lastTen = lastTen,
                        onAdjustMinutes = { minutes -> if (canAdjust) onPreset(minutes) },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    TimeVisual(
                        remaining = remaining,
                        duration = duration,
                        mode = timeMode,
                        running = running,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                if (timeMode == TimeDisplayPrefs.NUMBERS && !lastTen) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = Color.White.copy(alpha = 0.96f)
                    ) {
                        Text(
                            String.format(
                                Locale.getDefault(),
                                "%02d:%02d",
                                secs / 60,
                                secs % 60
                            ),
                            modifier = Modifier.padding(horizontal = 15.dp, vertical = 5.dp),
                            fontSize = 35.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppColors.Ink,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (showReset) {
                    IconButton(
                        onClick = { confirmReset = true },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Text(
                            "↺",
                            fontSize = 32.sp,
                            color = AppColors.Ink
                        )
                    }
                    Text(
                        stringResource(R.string.nollaa),
                        fontSize = 12.sp,
                        color = DialMuted
                    )
                } else {
                    CaregiverLockButton()
                }
            }
            androidx.compose.material3.IconButton(
                onClick = startPause,
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(AppColors.Ink)
            ) {
                Text(
                    when {
                        pauseLocked -> "🔒"
                        running -> "Ⅱ"
                        else -> "▶"
                    },
                    fontSize = if (pauseLocked) 28.sp else if (running) 39.sp else 30.sp,
                    color = Color.White
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (showSettings) {
                    TextButton(
                        onClick = { settingsOpen = true },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Text(
                            "⚙",
                            fontSize = 25.sp,
                            color = AppColors.Ink
                        )
                    }
                    Text(
                        stringResource(R.string.asetukset),
                        fontSize = 11.sp,
                        color = DialMuted
                    )
                }
            }
        }
    }

    // Jos lukitus menee päälle ikkunan ollessa auki, ikkuna suljetaan.
    LaunchedEffect(showSettings, showReset) {
        if (!showSettings) settingsOpen = false
        if (!showReset) confirmReset = false
    }

    if (settingsOpen) {
        AlertDialog(
            onDismissRequest = { settingsOpen = false },
            title = { Text(stringResource(R.string.ajastimen_asetukset)) },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 460.dp)
                        .verticalScroll(androidx.compose.foundation.rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TimeModeChoices(timeMode) { mode ->
                        timeMode = mode
                        TimeDisplayPrefs.save(context, mode)
                    }
                    HorizontalDivider()

                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            stringResource(R.string.viimeisten_10_sekunnin_laskenta),
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = showLastTen,
                            onCheckedChange = onShowLastTen,
                            enabled = timeMode == TimeDisplayPrefs.NUMBERS
                        )
                    }

                    HorizontalDivider()

                    Text(
                        stringResource(R.string.aanimerkki_ajan_paattyessa),
                        fontWeight = FontWeight.SemiBold
                    )

                    listOf(
                        "off" to stringResource(R.string.ei_aanta),
                        "short" to stringResource(R.string.lyhyt_merkkiaani),
                        "clear" to stringResource(R.string.selkea_piippaus)
                    ).forEach { (value, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .clickable {
                                    endSound = value
                                    preferences.edit()
                                        .putString("end_sound", value)
                                        .apply()
                                },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = endSound == value,
                                onClick = null
                            )
                            Text(label)
                        }
                    }

                    TextButton(
                        onClick = {
                            soundScope.launch {
                                playCompletionSound(endSound)
                            }
                        },
                        enabled = endSound != "off"
                    ) {
                        Text(stringResource(R.string.kuuntele_aanimerkki))
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { settingsOpen = false }
                ) {
                    Text(stringResource(R.string.dialog_done))
                }
            }
        )
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(stringResource(R.string.nollataanko_ajastin)) },
            text = {
                Text(stringResource(R.string.ajastin_palautetaan_valittuun_aloitusaikaan))
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    onReset()
                }) {
                    Text(stringResource(R.string.nollaa))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun TimerTaskCards(
    first: String,
    firstPhoto: String,
    next: String,
    nextPhoto: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        TimerTaskCard(
            label = stringResource(R.string.first_label),
            name = first,
            photo = firstPhoto,
            modifier = Modifier.weight(1f)
        )
        TimerTaskCard(
            label = stringResource(R.string.then_label),
            name = next,
            photo = nextPhoto,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun TimerTaskCard(
    label: String,
    name: String,
    photo: String,
    modifier: Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(17.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(50.dp),
                color = if (label == stringResource(R.string.first_label))
                    AppColors.Teal else Color(0xFFE7F5F1)
            ) {
                Text(
                    label,
                    modifier = Modifier.padding(
                        horizontal = 14.dp,
                        vertical = 5.dp
                    ),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (label == stringResource(R.string.first_label))
                        Color.White else AppColors.Teal
                )
            }

            CardPhoto(
                name = name,
                imagePath = photo,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(88.dp)
                    .clip(RoundedCornerShape(12.dp))
            )

            Text(
                name,
                modifier = Modifier.fillMaxWidth(),
                color = AppColors.Ink,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
