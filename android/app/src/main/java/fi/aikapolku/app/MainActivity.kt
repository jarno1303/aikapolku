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
import fi.aikapolku.app.caregiver.CaregiverSession
import androidx.compose.ui.platform.LocalContext
import fi.aikapolku.app.data.ProfileStore
import fi.aikapolku.app.profiles.ProfileSession
import fi.aikapolku.app.ui.theme.AikapolkuTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Ilmoituksesta avattu tapahtuma (vain ensimmäisellä kerralla, ei esim. näytön kääntyessä).
        if (savedInstanceState == null) EventLaunch.from(intent)
        EventReminders.onAppStart(this)
        setContent {
            AikapolkuTheme {
                CaregiverLockHost {
                    // Profiilin vaihtuessa koko sovelluksen tila luodaan uudelleen uuden profiilin tiedoilla.
                    val context = LocalContext.current
                    val revision = ProfileSession.revision
                    val profileId = remember(revision) { ProfileStore.currentId(context) }
                    key(profileId) {
                        AikaPolkuApp()
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        EventLaunch.from(intent)
    }

    override fun onStop() {
        super.onStop()
        // Viikko-ohjelman muutokset ajan tasalle myös taustalla oleville ilmoituksille.
        EventReminders.reschedule(this)
        // P0-21e: ohjaajan toiminnot lukittuvat aina, kun sovellus siirtyy taustalle.
        CaregiverSession.onAppBackground()
    }
}

