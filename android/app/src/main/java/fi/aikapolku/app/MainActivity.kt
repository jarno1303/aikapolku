package fi.aikapolku.app

import android.content.Intent
import android.os.Bundle
import fi.aikapolku.app.week.EventLaunch
import fi.aikapolku.app.week.EventReminders
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import fi.aikapolku.app.caregiver.CaregiverLockHost
import fi.aikapolku.app.backup.ImportLaunch
import fi.aikapolku.app.caregiver.CaregiverSession
import androidx.compose.ui.platform.LocalContext
import fi.aikapolku.app.data.ProfileStore
import fi.aikapolku.app.profiles.ProfileSession
import fi.aikapolku.app.ui.theme.AikapolkuTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Ilmoituksesta avattu tapahtuma (vain ensimmäisellä kerralla, ei esim. näytön kääntyessä).
        if (savedInstanceState == null) {
            EventLaunch.from(intent)
            ImportLaunch.from(intent)
        }
        EventReminders.onAppStart(this)
        // Ohjaajan puhelin: asiakkaiden viestien vastaanotto, jos valittu (Yksikön yhteys).
        fi.aikapolku.app.link.LinkReceiverService.ensure(this)
        setContent {
            AikapolkuTheme {
                CaregiverLockHost {
                    // Profiilin vaihtuessa koko sovelluksen tila luodaan uudelleen uuden profiilin tiedoilla.
                    val context = LocalContext.current
                    val revision = ProfileSession.revision
                    val profileId = remember(revision) { ProfileStore.currentId(context) }
                    // T4: paikan laitteella ohjaajan tila vaatii näytön lukituksen.
                    fi.aikapolku.app.caregiver.PlaceLockGate {
                        key(profileId) {
                            AikaPolkuApp()
                        }
                    }
                    // T5: ohjaajan näkymät piiloon kuvakaappauksilta ja sovellusvaihtajasta.
                    fi.aikapolku.app.caregiver.SecureWindow()
                    // Ohjaajan puhelimessa: asiakkaan laitteen liitospyyntö missä tahansa näkymässä.
                    fi.aikapolku.app.link.PairingRequestDialog()
                    // Näytön asento asiakkaan asetuksen mukaan (puhelimessa oletuksena pysty).
                    fi.aikapolku.app.ui.OrientationLock()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        EventLaunch.from(intent)
        ImportLaunch.from(intent)
    }

    override fun onStart() {
        super.onStart()
        // Asiakkaan laite: jonossa odottavat viestit ohjaajalle, kun sovellus avataan.
        fi.aikapolku.app.link.ClientMessages.resume(this)
        // Asukkaan puhelin: omat tiedot paikan laitteelta heti ja minuutin välein (A5).
        fi.aikapolku.app.link.PlaceSync.start(this)
        // Paikan laite: automaattinen varmuuskopio kerran vuorokaudessa (taustalla).
        fi.aikapolku.app.backup.AutoBackup.runIfDue(this)
    }

    override fun onStop() {
        super.onStop()
        fi.aikapolku.app.link.PlaceSync.stop()
        // Viikko-ohjelman muutokset ajan tasalle myös taustalla oleville ilmoituksille ja
        // kotinäytön widgetille (kuittaukset näkyvät heti, kun sovellus suljetaan).
        EventReminders.reschedule(this)
        // P0-21e: ohjaajan toiminnot lukittuvat aina, kun sovellus siirtyy taustalle.
        CaregiverSession.onAppBackground()
    }
}

