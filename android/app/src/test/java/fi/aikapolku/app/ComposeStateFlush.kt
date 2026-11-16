package fi.aikapolku.app

import android.os.Looper
import androidx.compose.runtime.snapshots.Snapshot
import org.junit.rules.ExternalResource
import org.robolectric.Shadows.shadowOf

/**
 * Robolectric-testit, jotka eivät piirrä näkymää mutta kirjoittavat Composen tilaa (esim. tallennusten
 * revision-laskurit): Compose ilmoittaa muutoksesta lähettämällä viestin pääsäikeelle. Robolectric
 * tyhjentää pääsäikeen jonon testien välissä, jolloin viesti katoaa ja Composen pääsäikeen
 * lähettäjä jää odottamaan sitä. Seuraava Compose-testi ei silloin pääse lepotilaan
 * (AppNotIdleException, 60 s). Tämä sääntö ilmoittaa muutokset ja ajaa jonon tyhjäksi
 * jokaisen testin lopussa.
 */
class ComposeStateFlush : ExternalResource() {
    override fun after() {
        Snapshot.sendApplyNotifications()
        shadowOf(Looper.getMainLooper()).idle()
    }
}
