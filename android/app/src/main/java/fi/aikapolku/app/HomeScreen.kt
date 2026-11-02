package fi.aikapolku.app

import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import fi.aikapolku.app.data.Profile
import fi.aikapolku.app.week.dayName
import java.time.LocalDate
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fi.aikapolku.app.caregiver.AppAction
import fi.aikapolku.app.caregiver.LocalCaregiver
import fi.aikapolku.app.ui.AppColors
import fi.aikapolku.app.week.CaregiverDayList
import fi.aikapolku.app.week.ClientDayView
import fi.aikapolku.app.week.ClientActivity
import fi.aikapolku.app.data.TimelineMode
import fi.aikapolku.app.data.ClientViewPrefs
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Refresh
import fi.aikapolku.app.ui.AppIcons
import fi.aikapolku.app.ui.RowIcon
import fi.aikapolku.app.ui.ChoiceRow
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.Face
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.ui.platform.LocalContext
import fi.aikapolku.app.data.ProfileStore
import fi.aikapolku.app.profiles.ProfileAvatarButton
import fi.aikapolku.app.profiles.ProfilePickerDialog
import fi.aikapolku.app.profiles.ProfileSession
import fi.aikapolku.app.week.TodaySection
import fi.aikapolku.app.week.ChangeAnnouncements
import fi.aikapolku.app.ui.ChangeSymbol
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedButton
import androidx.compose.foundation.BorderStroke
import fi.aikapolku.app.data.EventChange
import fi.aikapolku.app.data.WeekEvent

@Composable
internal fun HomeScreen(
    open: (AppPage) -> Unit,
    onOpenProgram: (WeekEvent) -> Unit = {},
    onOpenCard: (WeekEvent) -> Unit = {},
    onOpenDay: () -> Unit = {},
    onOpenChange: (EventChange) -> Unit = {},
    /** Pikamuutos: tapahtuman tunnus (null = valitaan näkymässä). */
    onMakeChange: (String?) -> Unit = {},
    /** Ohjaajan etusivulta suoraan viikko-ohjelmaan. */
    onOpenWeek: () -> Unit = { open(AppPage.PROGRAMS) },
    /** Ohjaajan asiakkaalle valmistelema toiminta (Ensin–Sitten tai ajastin). */
    activity: ClientActivity? = null,
    onOpenActivity: () -> Unit = {}
) {
    // Kummallekin roolille oma etusivu: asiakkaalle oma päivä, ohjaajalle työkalut.
    if (LocalCaregiver.current.isCaregiver) {
        CaregiverHome(open, onOpenProgram, onOpenCard, onOpenDay, onOpenChange, onMakeChange, onOpenWeek)
    } else {
        ClientHome(open, onOpenProgram, onOpenCard, onOpenChange, activity, onOpenActivity)
    }
}

/** Profiilin vaihto ja (asiakkaalla) ohjaajan tila oikean yläkulman valikosta. */
@Composable
private fun rememberProfileMenu(isCaregiver: Boolean): @Composable () -> Unit {
    val context = LocalContext.current
    val caregiver = LocalCaregiver.current
    val revision = ProfileSession.revision
    val profiles = remember(revision) { ProfileStore.list(context) }
    val currentId = remember(revision) { ProfileStore.currentId(context) }
    var choosing by remember { mutableStateOf(false) }
    if (choosing) {
        ProfilePickerDialog(
            profiles = profiles,
            currentId = currentId,
            onPick = {
                choosing = false
                ProfileSession.switchTo(context, it.id)
            },
            onDismiss = { choosing = false }
        )
    }
    return {
        HomeMenu(
            profile = profiles.first { it.id == currentId }.takeIf { profiles.size > 1 },
            isCaregiver = isCaregiver,
            onSwitchProfile = {
                if (isCaregiver || ProfileStore.selfSelect(context)) choosing = true
                else caregiver.run(AppAction.SETTINGS) { choosing = true }
            },
            onCaregiverMode = { caregiver.unlock() }
        )
    }
}

/**
 * Asiakkaan etusivu: päivän nimi, muutokset, iso NYT-kortti ja päivän aikajana.
 * Työkalut (Ajastin, Ensin–Sitten, Päiväohjelmat) vain, jos ohjaaja on ne sallinut.
 */
@Composable
private fun ClientHome(
    open: (AppPage) -> Unit,
    onOpenProgram: (WeekEvent) -> Unit,
    onOpenCard: (WeekEvent) -> Unit,
    onOpenChange: (EventChange) -> Unit,
    activity: ClientActivity?,
    onOpenActivity: () -> Unit
) {
    val context = LocalContext.current
    val revision = ProfileSession.revision
    val prefs = remember(revision) { ClientViewPrefs.load(context) }
    val menu = rememberProfileMenu(isCaregiver = false)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Text(
            dayName(LocalDate.now().dayOfWeek.value),
            modifier = Modifier.weight(1f).semantics { heading() },
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = AppColors.Ink
        )
        menu()
    }
    ChangeAnnouncements(onOpenChange)
    ClientDayView(prefs, activity, onOpenActivity, onOpenProgram, onOpenCard, onOpenChange)
    if (prefs.anyTool) Spacer(Modifier.height(6.dp))
    if (prefs.showTimer) {
        HomeCard("timer", stringResource(R.string.timer_title), stringResource(R.string.aseta_aika_ja_keskity)) { open(AppPage.TIMER) }
    }
    if (prefs.showFirstThen) {
        HomeCard("flow", stringResource(R.string.ensin_sitten), stringResource(R.string.suunnittele_kaksi_askelta)) { open(AppPage.FIRST_THEN) }
    }
    if (prefs.showPrograms) {
        HomeCard("programs", stringResource(R.string.paivaohjelmat), stringResource(R.string.nae_paivan_kulku)) { open(AppPage.PROGRAMS) }
    }
}

/**
 * Ohjaajan etusivu: koko päivä muutosmerkein, Tee muutos, asiakkaalle valmisteltavat
 * toiminnot ja ohjaajan työkalut.
 */
@Composable
private fun CaregiverHome(
    open: (AppPage) -> Unit,
    onOpenProgram: (WeekEvent) -> Unit,
    onOpenCard: (WeekEvent) -> Unit,
    onOpenDay: () -> Unit,
    onOpenChange: (EventChange) -> Unit,
    onMakeChange: (String?) -> Unit,
    onOpenWeek: () -> Unit
) {
    var showCards by remember { mutableStateOf(false) }
    var showClientView by remember { mutableStateOf(false) }
    val menu = rememberProfileMenu(isCaregiver = true)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Text(
            stringResource(R.string.caregiver_home_title),
            modifier = Modifier.weight(1f).semantics { heading() },
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = AppColors.Ink
        )
        menu()
    }
    CaregiverDayList(
        onOpenProgram = onOpenProgram,
        onOpenCard = onOpenCard,
        onOpenChange = onOpenChange,
        onChange = { onMakeChange(it.id) },
        onOpenDay = onOpenDay
    )
    OutlinedButton(
        onClick = { onMakeChange(null) },
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(2.dp, AppColors.ChangeFill)
    ) {
        ChangeSymbol(Modifier.size(28.dp))
        Text(
            stringResource(R.string.quick_title),
            modifier = Modifier.padding(start = 10.dp),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = AppColors.Change
        )
    }

    ToolsCard(stringResource(R.string.caregiver_prepare)) {
        ChoiceRow(stringResource(R.string.ensin_sitten), { open(AppPage.FIRST_THEN) }, subtitle = stringResource(R.string.caregiver_first_then_info), leading = { RowIcon(Icons.AutoMirrored.Rounded.ArrowForward) })
        ChoiceRow(stringResource(R.string.timer_title), { open(AppPage.TIMER) }, subtitle = stringResource(R.string.caregiver_timer_info), leading = { RowIcon(Icons.Rounded.Refresh) })
    }
    ToolsCard(stringResource(R.string.caregiver_tools)) {
        ChoiceRow(stringResource(R.string.home_week), onOpenWeek, subtitle = stringResource(R.string.home_week_info), leading = { RowIcon(Icons.Rounded.DateRange) })
        ChoiceRow(stringResource(R.string.home_programs), { open(AppPage.PROGRAMS) }, subtitle = stringResource(R.string.home_programs_info), leading = { RowIcon(Icons.AutoMirrored.Rounded.List) })
        ChoiceRow(stringResource(R.string.home_cards), { showCards = true }, subtitle = stringResource(R.string.home_cards_info), leading = { RowIcon(AppIcons.Image) })
        ChoiceRow(stringResource(R.string.client_view_title), { showClientView = true }, subtitle = stringResource(R.string.client_view_info), leading = { RowIcon(Icons.Rounded.Person) })
        ChoiceRow(stringResource(R.string.home_view_as_client), { fi.aikapolku.app.caregiver.RoleSession.preview() }, subtitle = stringResource(R.string.home_view_as_client_info), leading = { RowIcon(Icons.Rounded.Face) })
        ChoiceRow(stringResource(R.string.home_settings), { open(AppPage.CAREGIVER) }, subtitle = stringResource(R.string.home_settings_info), leading = { RowIcon(Icons.Rounded.Settings) })
    }
    if (showCards) {
        fi.aikapolku.app.cards.CardLibraryDialog(onDismiss = { showCards = false }, onSelect = { showCards = false })
    }
    if (showClientView) {
        ClientViewDialog { showClientView = false }
    }
}

@Composable
private fun ToolsCard(title: String, content: @Composable () -> Unit) {
    Text(
        title,
        modifier = Modifier.padding(top = 6.dp).semantics { heading() },
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        color = AppColors.Ink
    )
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) { content() }
    }
}

/** Ohjaaja säätää asiakkaan etusivun: aikajana, kellonajat ja asiakkaan omat työkalut. */
@Composable
private fun ClientViewDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var prefs by remember { mutableStateOf(ClientViewPrefs.load(context)) }
    fun update(next: ClientViewPrefs) {
        prefs = next
        ClientViewPrefs.save(context, next)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.client_view_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(stringResource(R.string.client_view_timeline), fontWeight = FontWeight.SemiBold)
                listOf(
                    Triple(TimelineMode.FULL, R.string.client_view_full, R.string.client_view_full_info),
                    Triple(TimelineMode.NEXT, R.string.client_view_next, R.string.client_view_next_info),
                    Triple(TimelineMode.NONE, R.string.client_view_none, R.string.client_view_none_info)
                ).forEach { (mode, title, info) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .selectable(selected = prefs.timeline == mode, role = Role.RadioButton) { update(prefs.copy(timeline = mode)) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        RadioButton(selected = prefs.timeline == mode, onClick = null, modifier = Modifier.padding(top = 2.dp))
                        Column(modifier = Modifier.padding(start = 8.dp)) {
                            Text(stringResource(title), fontSize = 16.sp)
                            Text(stringResource(info), fontSize = 13.sp, color = AppColors.Muted)
                        }
                    }
                }
                SwitchLine(stringResource(R.string.client_view_times), prefs.showTimes) { update(prefs.copy(showTimes = it)) }
                // Väliajan kortti: mitä NYT-kortti näyttää, kun seuraava ei ole vielä alkanut.
                Text(stringResource(R.string.client_view_gap), fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
                Text(stringResource(R.string.client_view_gap_info), fontSize = 13.sp, color = AppColors.Muted)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ClientViewPrefs.GAP_CHOICES.forEach { id ->
                        val template = fi.aikapolku.app.cards.CardArt.templates.firstOrNull { it.id == id } ?: return@forEach
                        val name = stringResource(template.nameRes)
                        val selected = prefs.gapCard == id
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .then(if (selected) Modifier.border(3.dp, AppColors.Teal, RoundedCornerShape(14.dp)) else Modifier)
                                .selectable(selected = selected, role = Role.RadioButton) { update(prefs.copy(gapCard = id)) }
                                .padding(6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            fi.aikapolku.app.cards.CardPhoto(name, "builtin:$id", Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)))
                            Text(name, fontSize = 14.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }
                Text(stringResource(R.string.client_view_tools), fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
                Text(stringResource(R.string.client_view_tools_info), fontSize = 13.sp, color = AppColors.Muted)
                SwitchLine(stringResource(R.string.timer_title), prefs.showTimer) { update(prefs.copy(showTimer = it)) }
                SwitchLine(stringResource(R.string.ensin_sitten), prefs.showFirstThen) { update(prefs.copy(showFirstThen = it)) }
                SwitchLine(stringResource(R.string.paivaohjelmat), prefs.showPrograms) { update(prefs.copy(showPrograms = it)) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } }
    )
}

@Composable
private fun SwitchLine(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f), fontSize = 16.sp)
        Switch(checked = checked, onCheckedChange = onChange, modifier = Modifier.semantics { contentDescription = label })
    }
}

/**
 * Oikean yläkulman valikko: profiilikuva (kun profiileja on useampi) tai valikkokuvake.
 * Asiakkaalla profiilin vaihto (jos sallittu) ja Ohjaajan tila; ohjaajalla profiilin vaihto.
 */
@Composable
private fun HomeMenu(profile: Profile?, isCaregiver: Boolean, onSwitchProfile: () -> Unit, onCaregiverMode: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val menuLabel = stringResource(R.string.menu_open)
    if (isCaregiver && profile == null) return
    Box {
        if (profile != null) {
            ProfileAvatarButton(profile) { expanded = true }
        } else {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button) { expanded = true }
                    .semantics { contentDescription = menuLabel },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Menu, contentDescription = null, tint = AppColors.Ink, modifier = Modifier.size(26.dp))
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (profile != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.menu_switch_profile), fontSize = 17.sp) },
                    onClick = { expanded = false; onSwitchProfile() }
                )
            }
            if (!isCaregiver) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.menu_caregiver_mode), fontSize = 17.sp) },
                    onClick = { expanded = false; onCaregiverMode() }
                )
            }
        }
    }
}

@Composable
private fun HomeCard(
    symbol: String,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    val icon = when (symbol) {
        "flow" -> R.drawable.ap_home_flow
        "programs" -> R.drawable.ap_home_programs
        else -> R.drawable.ap_home_timer
    }
    val surfaceColor = when (symbol) {
        "flow" -> Color(0xFFF1ECFB)
        "programs" -> Color(0xFFE8F3FA)
        else -> Color(0xFFE4F4EF)
    }
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 94.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = surfaceColor),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(15.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(13.dp)
        ) {
            Image(
                painter = painterResource(icon),
                contentDescription = null,
                modifier = Modifier.size(60.dp).clip(RoundedCornerShape(16.dp))
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = AppColors.Ink)
                Text(subtitle, fontSize = 14.sp, color = AppColors.Muted)
            }
            Text("›", fontSize = 28.sp, color = AppColors.Teal)
        }
    }
}
