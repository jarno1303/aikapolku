package fi.aikapolku.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import fi.aikapolku.app.caregiver.CaregiverLockHost
import fi.aikapolku.app.caregiver.CaregiverSession
import fi.aikapolku.app.ui.theme.AikapolkuTheme

internal val Teal = Color(0xFF0F766E)

internal val Background = Color(0xFFF8FAFC)

internal val Ink = Color(0xFF1F2937)

internal val Secondary = Color(0xFF475569)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AikapolkuTheme {
                CaregiverLockHost {
                    AikaPolkuApp()
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

