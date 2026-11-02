package fi.aikapolku.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import fi.aikapolku.app.data.ProfileStore
import fi.aikapolku.app.data.TimeDisplayPrefs
import fi.aikapolku.app.profiles.ProfileSession
import fi.aikapolku.app.timer.BackgroundTimer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Yhteinen Asetukset-näkymä: ajan asetukset tallentuvat asiakaskohtaisesti.
 *
 * Huom. testi ei kutsu ProfileSessionin vaihtoa tai poistoa: ne muuttavat Composen jaettua
 * tilaa, joka jää Robolectricin yhteisessä ympäristössä käsittelemättä ja jumittaa
 * myöhemmin ajettavat Compose-testit ("Compose did not get idle"). Vaihto tehdään suoraan
 * ProfileStoren kautta.
 */
@RunWith(RobolectricTestRunner::class)
class SettingsTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun `aanimerkki ja 10 sekunnin laskenta tallentuvat ja ajastin kayttaa samaa aanta`() {
        assertEquals(TimeDisplayPrefs.SOUND_OFF, TimeDisplayPrefs.endSound(context))
        assertTrue(TimeDisplayPrefs.lastTen(context))
        TimeDisplayPrefs.setEndSound(context, TimeDisplayPrefs.SOUND_CLEAR)
        TimeDisplayPrefs.setEndSound(context, "kova")
        TimeDisplayPrefs.setLastTen(context, false)
        assertEquals(TimeDisplayPrefs.SOUND_CLEAR, TimeDisplayPrefs.endSound(context))
        assertEquals(TimeDisplayPrefs.SOUND_CLEAR, BackgroundTimer.selectedSound(context))
        assertFalse(TimeDisplayPrefs.lastTen(context))
    }

    @Test
    fun `asetukset ovat asiakaskohtaiset`() {
        TimeDisplayPrefs.setEndSound(context, TimeDisplayPrefs.SOUND_SHORT)
        TimeDisplayPrefs.setLastTen(context, false)
        val first = ProfileStore.currentId(context)
        val other = ProfileStore.add(context, "Toinen")
        ProfileStore.switchTo(context, other.id)
        assertEquals(TimeDisplayPrefs.SOUND_OFF, TimeDisplayPrefs.endSound(context))
        assertTrue(TimeDisplayPrefs.lastTen(context))
        ProfileStore.switchTo(context, first)
        assertEquals(TimeDisplayPrefs.SOUND_SHORT, TimeDisplayPrefs.endSound(context))
    }

    @Test
    fun `saman asiakkaan valinta asetuksista ei tee mitaan`() {
        ProfileSession.switchFromSettings(context, ProfileStore.currentId(context))
        assertFalse(ProfileSession.takeReopenSettings())
    }
}
