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
import androidx.compose.ui.platform.LocalContext
import fi.aikapolku.app.data.ProfileStore
import fi.aikapolku.app.profiles.ProfileAvatarButton
import fi.aikapolku.app.profiles.ProfilePickerDialog
import fi.aikapolku.app.profiles.ProfileSession
import fi.aikapolku.app.week.TodaySection
import fi.aikapolku.app.data.WeekEvent

@Composable
internal fun HomeScreen(
    open: (AppPage) -> Unit,
    onOpenProgram: (WeekEvent) -> Unit = {},
    onOpenCard: (WeekEvent) -> Unit = {},
    onOpenDay: () -> Unit = {}
) {
    // Aloitusnäkymässä vain päivä, valikko ja toiminnot. Oikean yläkulman valikosta profiilin
    // vaihto (vain, jos profiileja on useampi) ja ohjaajan asetukset (PIN-koodin takana).
    val context = LocalContext.current
    val caregiver = LocalCaregiver.current
    val revision = ProfileSession.revision
    val profiles = remember(revision) { ProfileStore.list(context) }
    val currentId = remember(revision) { ProfileStore.currentId(context) }
    var choosing by remember { mutableStateOf(false) }
    val menuButton: @Composable () -> Unit = {
        HomeMenu(
            profile = profiles.first { it.id == currentId }.takeIf { profiles.size > 1 },
            onSwitchProfile = {
                if (ProfileStore.selfSelect(context)) choosing = true
                else caregiver.run(AppAction.SETTINGS) { choosing = true }
            },
            onCaregiverSettings = { caregiver.run(AppAction.SETTINGS) { open(AppPage.CAREGIVER) } }
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
        trailing = menuButton
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

/**
 * Oikean yläkulman valikko: profiilikuva (kun profiileja on useampi) tai valikkokuvake.
 * Valikossa profiilin vaihto ja ohjaajan asetukset.
 */
@Composable
private fun HomeMenu(profile: Profile?, onSwitchProfile: () -> Unit, onCaregiverSettings: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val menuLabel = stringResource(R.string.menu_open)
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
            DropdownMenuItem(
                text = { Text(stringResource(R.string.menu_caregiver_settings), fontSize = 17.sp) },
                onClick = { expanded = false; onCaregiverSettings() }
            )
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
