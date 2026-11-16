package fi.aikapolku.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import fi.aikapolku.app.data.CardClockMode
import fi.aikapolku.app.data.CardClockPrefs
import fi.aikapolku.app.data.ProfileStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Kello korteissa: ajan tulkinta, viisarien kulmat ja asukaskohtainen asetus (oletus pois). */
@RunWith(RobolectricTestRunner::class)
class KelloKorteissaTest {
    @get:org.junit.Rule
    val composeFlush = fi.aikapolku.app.ComposeStateFlush()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `kellonaika tulkitaan kaksoispisteella ja pisteella`() {
        assertEquals(8 * 60 + 30, CardClockPrefs.minutes("08:30"))
        assertEquals(12 * 60, CardClockPrefs.minutes("12.00"))
        assertEquals(7 * 60 + 5, CardClockPrefs.minutes(" 7:05 "))
        assertNull(CardClockPrefs.minutes(""))
        assertNull(CardClockPrefs.minutes(null))
        assertNull(CardClockPrefs.minutes("25:00"))
        assertNull(CardClockPrefs.minutes("kahdeksan"))
    }

    @Test
    fun `viisarit osoittavat oikein`() {
        assertEquals(0f to 0f, CardClockPrefs.handAngles(12 * 60))
        assertEquals(90f to 0f, CardClockPrefs.handAngles(15 * 60))
        // 8.30: tuntiviisari puolivälissä 8:n ja 9:n välillä, minuuttiviisari kuutosessa.
        assertEquals(255f to 180f, CardClockPrefs.handAngles(8 * 60 + 30))
    }

    @Test
    fun `asetus on asukaskohtainen ja oletuksena pois`() {
        val aino = ProfileStore.add(context, "Aino").id
        val eero = ProfileStore.add(context, "Eero").id
        ProfileStore.switchTo(context, aino)
        assertEquals(CardClockMode.OFF, CardClockPrefs.mode(context))
        CardClockPrefs.setMode(context, CardClockMode.BOTH)
        assertEquals(CardClockMode.BOTH, CardClockPrefs.mode(context, aino))
        assertEquals(CardClockMode.OFF, CardClockPrefs.mode(context, eero))
    }
}
