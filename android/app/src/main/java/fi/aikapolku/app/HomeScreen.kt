package fi.aikapolku.app

import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.foundation.clickable
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
    onOpenWeek: () -> Unit = { open(AppPage.PROGRAMS) }
) {
    // Roolit selvästi erillään: asiakkaalla oma päivä, ohjaajalla oma etusivu ja työkalut.
    val context = LocalContext.current
    val caregiver = LocalCaregiver.current
    val isCaregiver = caregiver.isCaregiver
    val revision = ProfileSession.revision
    val profiles = remember(revision) { ProfileStore.list(context) }
    val currentId = remember(revision) { ProfileStore.currentId(context) }
    var choosing by remember { mutableStateOf(false) }
    var showCards by remember { mutableStateOf(false) }
    val menuButton: @Composable () -> Unit = {
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

    // Ylärivi: päivä ja (jos tapahtumia) tehty/kaikki sekä nyt- ja seuraavat tapahtumat.
    val todayShown = TodaySection(
        onOpenProgram = onOpenProgram,
        onOpenCard = onOpenCard,
        onOpenDay = onOpenDay,
        trailing = menuButton,
        // Asiakkaalle muutokset heti päivän otsikon alle; ohjaaja näkee ne riveillä.
        afterHeader = { if (!isCaregiver) ChangeAnnouncements(onOpenChange) },
        onChange = if (isCaregiver) { event -> onMakeChange(event.id) } else null
    )
    if (!todayShown) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(
                dayName(LocalDate.now().dayOfWeek.value),
                modifier = Modifier.weight(1f).semantics { heading() },
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = AppColors.Ink
            )
            menuButton()
        }
        if (!isCaregiver) ChangeAnnouncements(onOpenChange)
    }

    if (isCaregiver) {
        CaregiverHome(
            onMakeChange = { onMakeChange(null) },
            onOpenWeek = onOpenWeek,
            onOpenPrograms = { open(AppPage.PROGRAMS) },
            onOpenCards = { showCards = true },
            onOpenSettings = { open(AppPage.CAREGIVER) },
            onViewAsClient = { fi.aikapolku.app.caregiver.RoleSession.preview() }
        )
        if (showCards) {
            fi.aikapolku.app.cards.CardLibraryDialog(onDismiss = { showCards = false }, onSelect = { showCards = false })
        }
        return
    }

    Spacer(Modifier.height(2.dp))
    HomeCard(
        symbol = "timer",
        title = stringResource(R.string.timer_title),
        subtitle = stringResource(R.string.aseta_aika_ja_keskity),
        onClick = { open(AppPage.TIMER) }
    )
    HomeCard(
        symbol = "flow",
        title = stringResource(R.string.ensin_sitten),
        subtitle = stringResource(R.string.suunnittele_kaksi_askelta),
        onClick = { open(AppPage.FIRST_THEN) }
    )
    HomeCard(
        symbol = "programs",
        title = stringResource(R.string.paivaohjelmat),
        subtitle = stringResource(R.string.nae_paivan_kulku),
        onClick = { open(AppPage.PROGRAMS) }
    )
}

/** Ohjaajan etusivu: Tee muutos ja ohjaajan työkalut yhdessä paikassa. */
@Composable
private fun CaregiverHome(
    onMakeChange: () -> Unit,
    onOpenWeek: () -> Unit,
    onOpenPrograms: () -> Unit,
    onOpenCards: () -> Unit,
    onOpenSettings: () -> Unit,
    onViewAsClient: () -> Unit
) {
    OutlinedButton(
        onClick = onMakeChange,
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
    Text(
        stringResource(R.string.caregiver_tools),
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
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            ChoiceRow(stringResource(R.string.home_week), onOpenWeek, subtitle = stringResource(R.string.home_week_info), leading = { RowIcon(Icons.Rounded.DateRange) })
            ChoiceRow(stringResource(R.string.home_programs), onOpenPrograms, subtitle = stringResource(R.string.home_programs_info), leading = { RowIcon(Icons.AutoMirrored.Rounded.List) })
            ChoiceRow(stringResource(R.string.home_cards), onOpenCards, subtitle = stringResource(R.string.home_cards_info), leading = { RowIcon(AppIcons.Image) })
            ChoiceRow(stringResource(R.string.home_settings), onOpenSettings, subtitle = stringResource(R.string.home_settings_info), leading = { RowIcon(Icons.Rounded.Settings) })
            ChoiceRow(stringResource(R.string.home_view_as_client), onViewAsClient, subtitle = stringResource(R.string.home_view_as_client_info), leading = { RowIcon(Icons.Rounded.Face) })
        }
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
