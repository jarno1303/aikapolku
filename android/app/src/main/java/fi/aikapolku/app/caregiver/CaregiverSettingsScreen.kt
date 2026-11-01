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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fi.aikapolku.app.MainActivity
import fi.aikapolku.app.ui.AppColors

/** Ohjaajan asetukset -sivu. Sivulle pääsee vain ohjaajana (ks. MainActivity). */
@Composable
internal fun CaregiverSettingsScreen() {
    val caregiver = LocalCaregiver.current
    var confirmDisable by remember { mutableStateOf(false) }

    Text("Ohjaajan asetukset", fontSize = 28.sp, fontWeight = FontWeight.Bold)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFE6F4F1))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Ohjaajan lukitus", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(
                if (caregiver.lockEnabled) "Tila: käytössä" else "Tila: ei käytössä",
                fontWeight = FontWeight.SemiBold,
                color = AppColors.Teal
            )
            Text(
                "Lukitus piilottaa PIN-koodin taakse ajan muuttamisen, ajastimen tauon ja nollauksen, " +
                    "lisäajan, päiväohjelman lopettamisen, ohjelmien ja korttien muokkauksen sekä asetukset."
            )
            Text(
                "Aina käytettävissä: Tarvitsen apua, Tarvitsen tauon, Valmis, osavaiheen ohitus " +
                    "sekä ajastimen Aloita ja Jatka."
            )
            Text(
                "PIN-koodilla avatut ohjaajan toiminnot ovat auki 2 minuuttia, minkä ajan " +
                    "yläreunassa näkyy muistutus. Lukitus menee päälle myös, kun sovellus " +
                    "suljetaan tai siirtyy taustalle.",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (!caregiver.lockEnabled) {
                Button(
                    onClick = { caregiver.run(AppAction.SETTINGS) { caregiver.openSetup() } },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                ) { Text("Ota lukitus käyttöön", fontSize = 17.sp) }
            } else {
                Button(
                    onClick = { caregiver.lockNow() },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                ) { Text("🔒 Lukitse nyt", fontSize = 17.sp) }
                OutlinedButton(
                    onClick = { caregiver.run(AppAction.SETTINGS) { caregiver.openSetup() } },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                ) { Text("Vaihda PIN-koodi") }
                TextButton(
                    onClick = { caregiver.run(AppAction.SETTINGS) { confirmDisable = true } },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Poista lukitus") }
            }

            if (!caregiver.deviceSecure) {
                Text(
                    "Puhelimessa ei ole näytön lukitusta. Unohtunut PIN-koodi nollataan puhelimen " +
                        "omalla lukituksella, joten sen käyttöönotto on suositeltavaa.",
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
            Text("Vinkki: näytön kiinnitys", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(
                "Androidin näytön kiinnitys pitää AikaPolun näkyvissä niin, ettei sovelluksesta " +
                    "voi siirtyä muihin sovelluksiin. Ota se käyttöön puhelimen asetuksista hakemalla " +
                    "\"Näytön kiinnitys\" tai \"Sovelluksen kiinnitys\"."
            )
        }
    }

    if (confirmDisable) {
        AlertDialog(
            onDismissRequest = { confirmDisable = false },
            title = { Text("Poistetaanko ohjaajan lukitus?") },
            text = { Text("Kaikki toiminnot ovat tämän jälkeen kenen tahansa käytettävissä.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDisable = false
                    caregiver.disableLock()
                }) { Text("Poista lukitus") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDisable = false }) { Text("Peruuta") }
            }
        )
    }
}
