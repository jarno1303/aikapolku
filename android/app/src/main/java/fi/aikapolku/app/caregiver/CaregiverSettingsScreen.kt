package fi.aikapolku.app.caregiver

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fi.aikapolku.app.MainActivity
import fi.aikapolku.app.R
import fi.aikapolku.app.ui.AppColors

/** Ohjaajan asetukset -sivu. Sivulle pääsee vain ohjaajana (ks. MainActivity). */
@Composable
internal fun CaregiverSettingsScreen() {
    val caregiver = LocalCaregiver.current
    var confirmDisable by remember { mutableStateOf(false) }

    Text(stringResource(R.string.caregiver_settings_title), fontSize = 28.sp, fontWeight = FontWeight.Bold)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFE6F4F1))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(stringResource(R.string.ohjaajan_lukitus), fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(
                if (caregiver.lockEnabled) stringResource(R.string.tila_kaytossa) else stringResource(R.string.tila_ei_kaytossa),
                fontWeight = FontWeight.SemiBold,
                color = AppColors.Teal
            )
            Text(
                stringResource(R.string.lukitus_piilottaa_pin_koodin_taakse)
            )
            Text(
                stringResource(R.string.aina_kaytettavissa_tarvitsen_apua_tarvitsen)
            )
            Text(
                stringResource(R.string.pin_koodilla_avatut_ohjaajan_toiminnot),
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (!caregiver.lockEnabled) {
                Button(
                    onClick = { caregiver.run(AppAction.SETTINGS) { caregiver.openSetup() } },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                ) { Text(stringResource(R.string.ota_lukitus_kayttoon), fontSize = 17.sp) }
            } else {
                Button(
                    onClick = { caregiver.lockNow() },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                ) { Text(stringResource(R.string.lukitse_nyt), fontSize = 17.sp) }
                OutlinedButton(
                    onClick = { caregiver.run(AppAction.SETTINGS) { caregiver.openSetup() } },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                ) { Text(stringResource(R.string.vaihda_pin_koodi)) }
                TextButton(
                    onClick = { caregiver.run(AppAction.SETTINGS) { confirmDisable = true } },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.poista_lukitus)) }
            }

            if (!caregiver.deviceSecure) {
                Text(
                    stringResource(R.string.puhelimessa_ei_ole_nayton_lukitusta),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(stringResource(R.string.vinkki_nayton_kiinnitys), fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(
                stringResource(R.string.androidin_nayton_kiinnitys_pitaa_aikapolun)
            )
        }
    }

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
