package fi.aikapolku.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import fi.aikapolku.app.caregiver.PrivacyPrefs
import fi.aikapolku.app.link.LinkStore
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Tietosuojan puutelistan korjaukset (T4, T5): oletukset ja paikan laitteen vastaanotto. */
@RunWith(RobolectricTestRunner::class)
class TietosuojaTest {
    // Testi kirjoittaa Composen tilaa (revision) ilman näkymää: muutokset ilmoitetaan heti, jottei
    // seuraavien Compose-testien odotus jumitu (AppNotIdleException).
    @After
    fun flushSnapshots() = androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `ohjaajan nakymat piilossa ja paikan laite pois oletuksena`() {
        // Vain luku: asetusten kirjoitus päivittäisi Composen tilaa ilman näkymää.
        assertTrue(PrivacyPrefs.secureScreens(context))
        assertFalse(LinkStore(context).placeEnabled)
    }
}
