package fi.aikapolku.app

import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.PlayArrow
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
    onOpenWeek: () -> Unit = { open(AppPage.WEEK) },
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
    // Ohjaajan kaksi tärkeintä toimintoa vierekkäin: asiakkaan viikko ja päivän muutos.
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(
            onClick = onOpenWeek,
            modifier = Modifier.weight(1f).heightIn(min = 76.dp),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(2.dp, AppColors.Teal),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 8.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                androidx.compose.material3.Icon(Icons.Rounded.DateRange, contentDescription = null, tint = AppColors.Teal, modifier = Modifier.size(28.dp))
                Text(
                    stringResource(R.string.home_week),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.Teal,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    maxLines = 2
                )
            }
        }
        OutlinedButton(
            onClick = { onMakeChange(null) },
            modifier = Modifier.weight(1f).heightIn(min = 76.dp),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(2.dp, AppColors.ChangeFill),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 8.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                ChangeSymbol(Modifier.size(28.dp))
                Text(
                    stringResource(R.string.quick_title),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.Change,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    maxLines = 2
                )
            }
        }
    }

    // Ryhmä tänään: paikalla/poissa ja ryhmän päivän muutokset (näkyy, jos ryhmiä on).
    fi.aikapolku.app.week.GroupTodayCard()

    ToolsCard(stringResource(R.string.caregiver_prepare)) {
        ChoiceRow(stringResource(R.string.ensin_sitten), { open(AppPage.FIRST_THEN) }, subtitle = stringResource(R.string.caregiver_first_then_info), leading = { RowIcon(Icons.AutoMirrored.Rounded.ArrowForward) })
        ChoiceRow(stringResource(R.string.timer_title), { open(AppPage.TIMER) }, subtitle = stringResource(R.string.caregiver_timer_info), leading = { RowIcon(Icons.Rounded.Refresh) })
    }
    ToolsCard(stringResource(R.string.caregiver_tools)) {
        ChoiceRow(stringResource(R.string.groups_title), { open(AppPage.GROUPS) }, subtitle = stringResource(R.string.groups_home_info), leading = { RowIcon(Icons.Rounded.Face) })
        ChoiceRow(stringResource(R.string.home_programs), { open(AppPage.PROGRAMS) }, subtitle = stringResource(R.string.home_programs_info), leading = { RowIcon(Icons.AutoMirrored.Rounded.List) })
        ChoiceRow(stringResource(R.string.home_cards), { showCards = true }, subtitle = stringResource(R.string.home_cards_info), leading = { RowIcon(AppIcons.Image) })
        ChoiceRow(stringResource(R.string.home_view_as_client), { fi.aikapolku.app.caregiver.RoleSession.preview() }, subtitle = stringResource(R.string.home_view_as_client_info), leading = { RowIcon(Icons.Rounded.Face) })
        ChoiceRow(stringResource(R.string.home_settings), { open(AppPage.CAREGIVER) }, subtitle = stringResource(R.string.home_settings_info), leading = { RowIcon(Icons.Rounded.Settings) })
        // Testitilassa: esittelypäivä omaan profiiliinsa yhdellä painalluksella.
        if (fi.aikapolku.app.caregiver.RoleSession.testMode) {
            val context = LocalContext.current
            ChoiceRow(stringResource(R.string.demo_create), {
                val name = context.getString(R.string.demo_profile_name)
                val profile = fi.aikapolku.app.data.DemoDay.profile(context, name)
                fi.aikapolku.app.data.DemoDay.fill(context, profile.id)
                fi.aikapolku.app.week.EventReminders.reschedule(context.applicationContext)
                android.widget.Toast.makeText(context, context.getString(R.string.demo_created, name), android.widget.Toast.LENGTH_LONG).show()
                ProfileSession.switchTo(context, profile.id)
                ProfileSession.changed()
            }, subtitle = stringResource(R.string.demo_create_info), leading = { RowIcon(Icons.Rounded.PlayArrow) })
        }
    }
    if (showCards) {
        fi.aikapolku.app.cards.CardLibraryDialog(onDismiss = { showCards = false }, onSelect = { showCards = false })
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
