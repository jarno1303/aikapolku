package fi.aikapolku.app

import androidx.compose.foundation.Image
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
import fi.aikapolku.app.profiles.CurrentProfileRow
import fi.aikapolku.app.profiles.ProfilePickerDialog
import fi.aikapolku.app.profiles.ProfileSession

@Composable
internal fun HomeScreen(open: (AppPage) -> Unit) {
    Spacer(Modifier.height(10.dp))

    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(R.drawable.ap_home_brand),
            contentDescription = null,
            modifier = Modifier.size(66.dp).clip(RoundedCornerShape(17.dp))
        )
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("AikaPolku", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = AppColors.Ink)
            Text(stringResource(R.string.selkeytta_arkeen_askel_kerrallaan), fontSize = 14.sp, color = AppColors.Muted)
        }
    }

    // Profiilit: kuka käyttää nyt. Vaihto vaatii ohjaajan PIN-koodin, ellei ohjaaja ole sallinut omaa valintaa.
    val context = LocalContext.current
    val caregiverAccess = LocalCaregiver.current
    val revision = ProfileSession.revision
    val profiles = remember(revision) { ProfileStore.list(context) }
    val currentId = remember(revision) { ProfileStore.currentId(context) }
    var choosing by remember { mutableStateOf(false) }
    if (profiles.size > 1) {
        Spacer(Modifier.height(12.dp))
        CurrentProfileRow(profiles.first { it.id == currentId }) {
            if (ProfileStore.selfSelect(context)) choosing = true
            else caregiverAccess.run(AppAction.SETTINGS) { choosing = true }
        }
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

    Spacer(Modifier.height(18.dp))
    Text(stringResource(R.string.mita_haluat_tehda), fontSize = 22.sp,
        fontWeight = FontWeight.Bold, color = AppColors.Ink)
    Spacer(Modifier.height(6.dp))

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

    val caregiver = LocalCaregiver.current
    TextButton(
        onClick = { caregiver.run(AppAction.SETTINGS) { open(AppPage.CAREGIVER) } },
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
    ) {
        Text(
            if (caregiver.isLocked) stringResource(R.string.home_caregiver_settings_locked) else stringResource(R.string.home_caregiver_settings_open),
            color = AppColors.Teal,
            fontSize = 15.sp
        )
    }

    Spacer(Modifier.height(14.dp))
    Surface(
        color = Color(0xFFE6F4F1),
        shape = RoundedCornerShape(17.dp)
    ) {
        Text(
            stringResource(R.string.nae_mita_teet_nyt_ja),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 17.dp, vertical = 14.dp),
            fontSize = 14.sp,
            color = Color(0xFF145B55)
        )
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
