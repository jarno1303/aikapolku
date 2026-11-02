package fi.aikapolku.app.caregiver

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fi.aikapolku.app.R
import fi.aikapolku.app.backup.BackupSettingsCard
import fi.aikapolku.app.backup.ImportLaunch
import fi.aikapolku.app.profiles.ClientPickerCard
import fi.aikapolku.app.settings.ClientSettingsSections
import fi.aikapolku.app.settings.SettingsHeading
import fi.aikapolku.app.settings.SettingsSection
import fi.aikapolku.app.ui.AppColors
import fi.aikapolku.app.ui.PrimaryButton
import fi.aikapolku.app.ui.SecondaryButton
import fi.aikapolku.app.week.NotificationStatusCard

/**
 * Asetukset: ylhäällä valitun asiakkaan asetukset, alhaalla puhelimen yhteiset asetukset.
 * Jokainen asetus on vain tässä yhdessä paikassa (ajastimen ja päiväohjelman rataskuvake
 * avaa samat osiot). Sivulle pääsee vain ohjaajana (ks. MainActivity).
 */
@Composable
internal fun CaregiverSettingsScreen() {
    SettingsHeading(stringResource(R.string.settings_client_title))
    ClientPickerCard()
    ClientSettingsSections()

    SettingsHeading(stringResource(R.string.settings_device_title), stringResource(R.string.settings_device_info))
    SettingsSection(stringResource(R.string.ohjaajan_lukitus), stringResource(R.string.settings_lock_info), Icons.Rounded.Lock) {
        LockSettings()
    }
    SettingsSection(stringResource(R.string.reminder_status_title), stringResource(R.string.settings_reminders_info), Icons.Rounded.Notifications) {
        NotificationStatusCard(framed = false)
    }
    SettingsSection(
        stringResource(R.string.backup_title),
        stringResource(R.string.settings_backup_info),
        Icons.Rounded.Share,
        forceOpen = ImportLaunch.pending.value != null
    ) {
        BackupSettingsCard(framed = false)
    }
    SettingsSection(stringResource(R.string.role_settings_title), stringResource(R.string.settings_roles_info), Icons.Rounded.Build) {
        RoleSettings()
    }
}

/** Ohjaajan lukitus (PIN) ja vinkki näytön kiinnityksestä. */
@Composable
private fun LockSettings() {
    val caregiver = LocalCaregiver.current
    var confirmDisable by remember { mutableStateOf(false) }

    Text(
        if (caregiver.lockEnabled) stringResource(R.string.tila_kaytossa) else stringResource(R.string.tila_ei_kaytossa),
        fontWeight = FontWeight.SemiBold,
        color = AppColors.Teal
    )
    Text(stringResource(R.string.lukitus_piilottaa_pin_koodin_taakse), fontSize = 15.sp)
    Text(stringResource(R.string.aina_kaytettavissa_tarvitsen_apua_tarvitsen), fontSize = 15.sp)
    Text(stringResource(R.string.pin_koodilla_avatut_ohjaajan_toiminnot), fontSize = 13.sp, color = AppColors.Muted)

    if (!caregiver.lockEnabled) {
        PrimaryButton(stringResource(R.string.ota_lukitus_kayttoon), { caregiver.run(AppAction.SETTINGS) { caregiver.openSetup() } })
    } else {
        PrimaryButton(stringResource(R.string.lukitse_nyt), { caregiver.lockNow() })
        SecondaryButton(
            stringResource(R.string.vaihda_pin_koodi),
            { caregiver.run(AppAction.SETTINGS) { caregiver.openSetup() } },
            modifier = Modifier.fillMaxWidth()
        )
        TextButton(
            onClick = { caregiver.run(AppAction.SETTINGS) { confirmDisable = true } },
            modifier = Modifier.fillMaxWidth()
        ) { Text(stringResource(R.string.poista_lukitus)) }
    }
    if (!caregiver.deviceSecure) {
        Text(stringResource(R.string.puhelimessa_ei_ole_nayton_lukitusta), fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
    }
    Text(stringResource(R.string.vinkki_nayton_kiinnitys), fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
    Text(stringResource(R.string.androidin_nayton_kiinnitys_pitaa_aikapolun), fontSize = 14.sp)

    if (confirmDisable) {
        AlertDialog(
            onDismissRequest = { confirmDisable = false },
            title = { Text(stringResource(R.string.poistetaanko_ohjaajan_lukitus)) },
            text = { Text(stringResource(R.string.kaikki_toiminnot_ovat_taman_jalkeen)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDisable = false
                    caregiver.disableLock()
                }) { Text(stringResource(R.string.poista_lukitus)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDisable = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

/** Roolit: testitilan vapaa Asiakas / Ohjaaja -vaihto. */
@Composable
private fun RoleSettings() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val label = stringResource(R.string.role_test_switch)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Text(stringResource(R.string.role_test_switch_info), fontSize = 13.sp, color = AppColors.Muted)
        }
        Switch(
            checked = RoleSession.testMode,
            onCheckedChange = { RoleSession.setTestMode(context, it) },
            modifier = Modifier.semantics { contentDescription = label }
        )
    }
}
