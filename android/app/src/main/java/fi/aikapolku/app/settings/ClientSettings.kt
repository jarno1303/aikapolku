package fi.aikapolku.app.settings

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import fi.aikapolku.app.R
import fi.aikapolku.app.cards.CardArt
import fi.aikapolku.app.cards.CardPhoto
import fi.aikapolku.app.data.ClientViewPrefs
import fi.aikapolku.app.data.RunPreferences
import fi.aikapolku.app.data.TimeDisplayPrefs
import fi.aikapolku.app.data.TimelineMode
import fi.aikapolku.app.timer.TimeModeChoices
import fi.aikapolku.app.timer.playCompletionSound
import fi.aikapolku.app.ui.AppColors
import fi.aikapolku.app.ui.AppIcons
import fi.aikapolku.app.ui.ChoiceRow
import fi.aikapolku.app.ui.RowIcon
import fi.aikapolku.app.ui.SecondaryButton
import fi.aikapolku.app.ui.SegmentedChoice
import fi.aikapolku.app.voice.VoicePickerDialog
import fi.aikapolku.app.voice.VoicePlayer
import fi.aikapolku.app.voice.VoiceRecorder
import kotlinx.coroutines.launch

/*
 * Asiakkaan asetukset osioittain. Sama osio näkyy Asetukset-sivulla ja (osittain)
 * ajastimen ja päiväohjelman rataskuvakkeen takana, joten jokainen asetus on vain yhdessä
 * paikassa koodissa ja käyttöliittymässä. Kaikki asetukset ovat asiakaskohtaisia
 * (ProfileScope) ja tallentuvat heti; SettingsRevision kertoo muutoksesta muille näkymille.
 */

/** Avattava osio: otsikko, kuvake ja lyhyt kuvaus; sisältö näkyy avattuna. */
@Composable
internal fun SettingsSection(
    title: String,
    info: String,
    icon: ImageVector,
    initiallyOpen: Boolean = false,
    /** Avaa osion (esim. sähköpostista avattu varmuuskopio), vaikka se olisi suljettu. */
    forceOpen: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    var open by rememberSaveable(title) { mutableStateOf(initiallyOpen || forceOpen) }
    androidx.compose.runtime.LaunchedEffect(forceOpen) { if (forceOpen) open = true }
    val state = stringResource(if (open) R.string.settings_section_open else R.string.settings_section_closed)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            ChoiceRow(
                title = title,
                onClick = { open = !open },
                modifier = Modifier.padding(horizontal = 6.dp).semantics { stateDescription = state },
                subtitle = info,
                leading = { RowIcon(icon) },
                trailing = {
                    Icon(
                        if (open) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                        contentDescription = null,
                        tint = AppColors.Teal,
                        modifier = Modifier.size(28.dp)
                    )
                }
            )
            if (open) {
                Column(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    content = content
                )
            }
        }
    }
}

/** Asetuksen otsikko ja valinnainen selitys osion sisällä. */
@Composable
internal fun SettingLabel(title: String, info: String? = null) {
    Column(modifier = Modifier.padding(top = 6.dp)) {
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = AppColors.Ink, modifier = Modifier.semantics { heading() })
        if (!info.isNullOrBlank()) Text(info, fontSize = 13.sp, color = AppColors.Muted)
    }
}

@Composable
internal fun SwitchLine(label: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f), fontSize = 16.sp, color = if (enabled) AppColors.Ink else AppColors.Muted)
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled, modifier = Modifier.semantics { contentDescription = label })
    }
}

@Composable
private fun RadioLine(label: String, info: String?, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = if (info == null) Alignment.CenterVertically else Alignment.Top
    ) {
        RadioButton(selected = selected, onClick = null, modifier = Modifier.padding(top = if (info == null) 0.dp else 2.dp))
        Column(modifier = Modifier.padding(start = 8.dp)) {
            Text(label, fontSize = 16.sp, color = AppColors.Ink)
            if (info != null) Text(info, fontSize = 13.sp, color = AppColors.Muted)
        }
    }
}

/** Etusivu: mitä asiakas näkee aloitusnäkymässään. */
@Composable
internal fun HomeSettings() {
    val context = LocalContext.current
    var prefs by remember { mutableStateOf(ClientViewPrefs.load(context)) }
    fun update(next: ClientViewPrefs) {
        prefs = next
        ClientViewPrefs.save(context, next)
        SettingsRevision.bump()
    }
    SettingLabel(stringResource(R.string.client_view_timeline))
    listOf(
        Triple(TimelineMode.FULL, R.string.client_view_full, R.string.client_view_full_info),
        Triple(TimelineMode.NEXT, R.string.client_view_next, R.string.client_view_next_info),
        Triple(TimelineMode.NONE, R.string.client_view_none, R.string.client_view_none_info)
    ).forEach { (mode, title, info) ->
        RadioLine(stringResource(title), stringResource(info), prefs.timeline == mode) { update(prefs.copy(timeline = mode)) }
    }
    SwitchLine(stringResource(R.string.client_view_times), prefs.showTimes) { update(prefs.copy(showTimes = it)) }

    // Väliajan kortti: mitä NYT-kortti näyttää, kun seuraava ei ole vielä alkanut.
    SettingLabel(stringResource(R.string.client_view_gap), stringResource(R.string.client_view_gap_info))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        ClientViewPrefs.GAP_CHOICES.forEach { id ->
            val template = CardArt.templates.firstOrNull { it.id == id } ?: return@forEach
            val name = stringResource(template.nameRes)
            val selected = prefs.gapCard == id
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .then(if (selected) Modifier.border(3.dp, AppColors.Teal, RoundedCornerShape(14.dp)) else Modifier)
                    .selectable(selected = selected, role = Role.RadioButton) { update(prefs.copy(gapCard = id)) }
                    .padding(5.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CardPhoto(name, "builtin:$id", Modifier.size(54.dp).clip(RoundedCornerShape(12.dp)))
                Text(name, fontSize = 13.sp, maxLines = 1, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }

    SettingLabel(stringResource(R.string.client_view_tools), stringResource(R.string.client_view_tools_info))
    SwitchLine(stringResource(R.string.timer_title), prefs.showTimer) { update(prefs.copy(showTimer = it)) }
    SwitchLine(stringResource(R.string.ensin_sitten), prefs.showFirstThen) { update(prefs.copy(showFirstThen = it)) }
    SwitchLine(stringResource(R.string.paivaohjelmat), prefs.showPrograms) { update(prefs.copy(showPrograms = it)) }
}

/** Tehtävät: päiväohjelman kulku. */
@Composable
internal fun TaskSettings() {
    val context = LocalContext.current

    val timerModes = listOf(RunPreferences.TIMER_AUTO, RunPreferences.TIMER_BUTTON)
    var timerStart by remember { mutableStateOf(RunPreferences.timerStart(context)) }
    SettingLabel(stringResource(R.string.timer_start_title), stringResource(R.string.timer_start_info))
    SegmentedChoice(
        options = listOf(stringResource(R.string.timer_start_auto), stringResource(R.string.timer_start_button_short)),
        selected = timerModes.indexOf(timerStart).coerceAtLeast(0),
        onSelect = {
            timerStart = timerModes[it]
            RunPreferences.setTimerStart(context, timerModes[it])
            SettingsRevision.bump()
        }
    )

    val doneModes = listOf(RunPreferences.DONE_ALWAYS, RunPreferences.DONE_CONFIRM, RunPreferences.DONE_AFTER_TIME)
    var doneMode by remember { mutableStateOf(RunPreferences.doneMode(context)) }
    SettingLabel(stringResource(R.string.done_mode_title), stringResource(R.string.done_mode_info))
    SegmentedChoice(
        options = listOf(
            stringResource(R.string.done_mode_always),
            stringResource(R.string.done_mode_confirm),
            stringResource(R.string.done_mode_after)
        ),
        selected = doneModes.indexOf(doneMode).coerceAtLeast(0),
        onSelect = {
            doneMode = doneModes[it]
            RunPreferences.setDoneMode(context, doneModes[it])
            SettingsRevision.bump()
        }
    )

    val nextModes = listOf(RunPreferences.NEXT_HIDDEN, RunPreferences.NEXT_TEXT, RunPreferences.NEXT_CARD)
    var nextMode by remember { mutableStateOf(RunPreferences.nextMode(context)) }
    SettingLabel(stringResource(R.string.seuraavan_vaiheen_esikatselu), stringResource(R.string.next_mode_info))
    SegmentedChoice(
        options = listOf(stringResource(R.string.ei), stringResource(R.string.teksti), stringResource(R.string.kuva)),
        selected = nextModes.indexOf(nextMode).coerceAtLeast(0),
        onSelect = {
            nextMode = nextModes[it]
            RunPreferences.setNextMode(context, nextModes[it])
            SettingsRevision.bump()
        }
    )
}

/** Aika: ajan näyttötapa ja äänimerkki (ajastin ja päiväohjelma). */
@Composable
internal fun TimeSettings() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var mode by remember { mutableStateOf(TimeDisplayPrefs.load(context)) }
    var lastTen by remember { mutableStateOf(TimeDisplayPrefs.lastTen(context)) }
    var sound by remember { mutableStateOf(TimeDisplayPrefs.endSound(context)) }

    TimeModeChoices(mode) {
        mode = it
        TimeDisplayPrefs.save(context, it)
        SettingsRevision.bump()
    }
    SwitchLine(stringResource(R.string.viimeisten_10_sekunnin_laskenta), lastTen, enabled = mode == TimeDisplayPrefs.NUMBERS) {
        lastTen = it
        TimeDisplayPrefs.setLastTen(context, it)
        SettingsRevision.bump()
    }

    SettingLabel(stringResource(R.string.aanimerkki_ajan_paattyessa))
    listOf(
        TimeDisplayPrefs.SOUND_OFF to R.string.ei_aanta,
        TimeDisplayPrefs.SOUND_SHORT to R.string.lyhyt_merkkiaani,
        TimeDisplayPrefs.SOUND_CLEAR to R.string.selkea_piippaus
    ).forEach { (value, label) ->
        RadioLine(stringResource(label), null, sound == value) {
            sound = value
            TimeDisplayPrefs.setEndSound(context, value)
            SettingsRevision.bump()
        }
    }
    SecondaryButton(
        text = stringResource(R.string.kuuntele_aanimerkki),
        icon = AppIcons.Speaker,
        enabled = sound != TimeDisplayPrefs.SOUND_OFF,
        onClick = { scope.launch { playCompletionSound(sound) } },
        modifier = Modifier.fillMaxWidth()
    )
}

/** Viestit: Tarvitsen apua- ja Tarvitsen tauon -painikkeiden ääni ja omat äänitykset. */
@Composable
internal fun MessageSettings() {
    val context = LocalContext.current
    val recorder = remember(context) { VoiceRecorder(context.applicationContext, "support") }
    var recordingKind by remember { mutableStateOf<String?>(null) }
    var pendingKind by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf("") }
    var helpMode by remember { mutableStateOf(RunPreferences.voiceMode(context, RunPreferences.HELP)) }
    var breakMode by remember { mutableStateOf(RunPreferences.voiceMode(context, RunPreferences.BREAK)) }
    var helpFile by remember { mutableStateOf(RunPreferences.recording(context, RunPreferences.HELP)) }
    var breakFile by remember { mutableStateOf(RunPreferences.recording(context, RunPreferences.BREAK)) }
    var showVoicePicker by remember { mutableStateOf(false) }

    fun start(kind: String) {
        try {
            VoicePlayer.stop()
            recorder.start()
            recordingKind = kind
            message = context.getString(R.string.aanitys_kaynnissa_paina_lopeta_tallennus)
        } catch (e: Exception) {
            recordingKind = null
            message = context.getString(R.string.aanitys_ei_kaynnistynyt_tarkista_mikrofonin)
        }
    }

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val kind = pendingKind
        pendingKind = null
        if (granted && kind != null) start(kind)
        else message = context.getString(R.string.mikrofonin_kayttooikeutta_ei_annettu)
    }
    DisposableEffect(recorder) {
        onDispose { recorder.cancel() }
    }

    fun beginRecord(kind: String) {
        if (recordingKind != null) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) start(kind)
        else {
            pendingKind = kind
            fi.aikapolku.app.caregiver.CaregiverSession.keepOpenForOwnPicker()
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    fun stopRecord(kind: String) {
        if (recordingKind != kind) return
        try {
            val saved = recorder.finish()
            RunPreferences.setRecording(context, kind, saved)
            if (kind == RunPreferences.HELP) helpFile = saved else breakFile = saved
            RunPreferences.setVoiceMode(context, kind, RunPreferences.AUDIO_RECORDED)
            if (kind == RunPreferences.HELP) helpMode = RunPreferences.AUDIO_RECORDED else breakMode = RunPreferences.AUDIO_RECORDED
            message = context.getString(R.string.oma_aani_tallennettu)
        } catch (_: Exception) {
            message = context.getString(R.string.tallennus_epaonnistui)
        }
        recordingKind = null
    }

    VoiceRow(
        label = stringResource(R.string.support_help),
        selection = helpMode,
        path = helpFile,
        recording = recordingKind == RunPreferences.HELP,
        canStart = recordingKind == null,
        onSelection = {
            helpMode = it
            RunPreferences.setVoiceMode(context, RunPreferences.HELP, it)
        },
        onStart = { beginRecord(RunPreferences.HELP) },
        onFinish = { stopRecord(RunPreferences.HELP) },
        onPlay = { VoicePlayer.play(context, helpFile) }
    )
    VoiceRow(
        label = stringResource(R.string.support_break),
        selection = breakMode,
        path = breakFile,
        recording = recordingKind == RunPreferences.BREAK,
        canStart = recordingKind == null,
        onSelection = {
            breakMode = it
            RunPreferences.setVoiceMode(context, RunPreferences.BREAK, it)
        },
        onStart = { beginRecord(RunPreferences.BREAK) },
        onFinish = { stopRecord(RunPreferences.BREAK) },
        onPlay = { VoicePlayer.play(context, breakFile) }
    )
    SecondaryButton(
        text = stringResource(R.string.valitse_ja_kuuntele_puheaani),
        icon = AppIcons.Speaker,
        onClick = { showVoicePicker = true },
        enabled = recordingKind == null,
        modifier = Modifier.fillMaxWidth()
    )
    if (message.isNotBlank()) Text(message, fontSize = 13.sp, color = AppColors.Muted)

    if (showVoicePicker) VoicePickerDialog(onDismiss = { showVoicePicker = false })
}

@Composable
private fun VoiceRow(
    label: String,
    selection: String,
    path: String,
    recording: Boolean,
    canStart: Boolean,
    onSelection: (String) -> Unit,
    onStart: () -> Unit,
    onFinish: () -> Unit,
    onPlay: () -> Unit
) {
    SettingLabel(label)
    // Oma ääni valittavissa vasta, kun se on äänitetty.
    val audioModes = listOf(RunPreferences.AUDIO_SILENT, RunPreferences.AUDIO_TTS, RunPreferences.AUDIO_RECORDED)
    SegmentedChoice(
        options = listOf(stringResource(R.string.ei_aanta), stringResource(R.string.puheaani), stringResource(R.string.oma_aani)),
        selected = audioModes.indexOf(selection).coerceAtLeast(0),
        onSelect = { index ->
            if (audioModes[index] != RunPreferences.AUDIO_RECORDED || path.isNotBlank()) onSelection(audioModes[index])
        }
    )
    if (recording) {
        Button(onClick = onFinish, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            Text(stringResource(R.string.lopeta_tallennus))
        }
    } else {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SecondaryButton(
                text = if (path.isEmpty()) stringResource(R.string.aanita_oma_viesti) else stringResource(R.string.aanita_uudelleen),
                icon = AppIcons.Mic,
                onClick = onStart,
                enabled = canStart,
                modifier = Modifier.weight(1f)
            )
            if (path.isNotBlank()) {
                SecondaryButton(text = stringResource(R.string.kuuntele), icon = AppIcons.Speaker, onClick = onPlay)
            }
        }
    }
}

/** Kortti otsikolla tai ([framed] = false) pelkkä sisältö avattavan osion sisään. */
@Composable
internal fun OptionalCard(framed: Boolean, title: String, content: @Composable ColumnScope.() -> Unit) {
    if (framed) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                content()
            }
        }
    } else {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}
