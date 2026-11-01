package fi.aikapolku.app

import android.os.Bundle
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

    override fun onStop() {
        super.onStop()
        // P0-21e: ohjaajan toiminnot lukittuvat aina, kun sovellus siirtyy taustalle.
        CaregiverSession.onAppBackground()
    }
}

