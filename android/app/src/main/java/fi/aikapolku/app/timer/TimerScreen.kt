package fi.aikapolku.app.timer

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import fi.aikapolku.app.ui.AppIcons
import fi.aikapolku.app.ui.RoundIconButton
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
            TimerTaskCards(
                first = activeTask,
                firstPhoto = firstPhoto,
                next = nextActivity,
                nextPhoto = nextPhoto
            )
        }
        // Otsikko on yläpalkissa. Aika valitaan kellotaulua napauttamalla tai vetämällä.

        val secs = ((remaining + 999L) / 1000L)
            .coerceAtLeast(0L)
            .toInt()

        val lastTen = showLastTen && secs in 1..10
        val screenHeight = LocalConfiguration.current.screenHeightDp
        val compact = screenHeight < 750

        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            // Kellotaulu käyttää ruudun leveyden (yläpalkki vapautti tilaa).
            // Korkeus rajaa myös: korttipari, kellotaulu ja painikkeet mahtuvat ilman vieritystä
            // (yläpalkki, ohjaajan palkki ja painikerivi vievät noin 330 dp, korttipari noin 150 dp).
            val byHeight = (screenHeight - if (guided) 490 else 340).coerceAtLeast(220).dp
            val target = minOf(
                byHeight,
                when {
                    compact -> 300.dp
                    guided -> 340.dp
                    else -> 420.dp
                }
            )

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
                    // Numerot suoraan renkaan valkoisen keskustan päällä: ei taustalaatikkoa,
                    // joka leikkaisi renkaaseen koloja. Koko suhteessa keskustan kokoon.
                    val innerDiameter = ((diameter / 2) - 44.dp) * 0.62f * 2
                    Text(
                        String.format(
                            Locale.getDefault(),
                            "%02d:%02d",
                            secs / 60,
                            secs % 60
                        ),
                        fontSize = (innerDiameter.value * 0.24f).sp,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.Ink,
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
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
                    RoundIconButton(
                        icon = Icons.Rounded.Refresh,
                        contentDescription = stringResource(R.string.nollaa),
                        onClick = { confirmReset = true }
                    )
                    Text(
                        stringResource(R.string.nollaa),
                        fontSize = 12.sp,
                        color = DialMuted
                    )
                }
            }
            RoundIconButton(
                icon = when {
                    pauseLocked -> Icons.Rounded.Lock
                    running -> AppIcons.Pause
                    else -> Icons.Rounded.PlayArrow
                },
                contentDescription = when {
                    pauseLocked -> stringResource(R.string.tauko_vaatii_ohjaajan_pin_koodin)
                    running -> stringResource(R.string.pysayta_aika)
                    else -> stringResource(R.string.aloita_aika)
                },
                onClick = startPause,
                size = 72.dp,
                container = AppColors.Ink,
                tint = Color.White
            )

            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (showSettings) {
                    RoundIconButton(
                        icon = Icons.Rounded.Settings,
                        contentDescription = stringResource(R.string.ajastimen_asetukset),
                        onClick = { settingsOpen = true }
                    )
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
                        Icon(AppIcons.Speaker, contentDescription = null, modifier = Modifier.size(20.dp))
                        Text(stringResource(R.string.kuuntele_aanimerkki), modifier = Modifier.padding(start = 6.dp))
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
    // Sama Ensin–Sitten-pari kuin valintanäkymässä, tiiviinä.
    fi.aikapolku.app.firstthen.FirstThenPair(
        first = first,
        firstPhoto = firstPhoto,
        firstDescription = "",
        next = next,
        nextPhoto = nextPhoto,
        nextDescription = "",
        editable = false,
        compact = true
    )
}
