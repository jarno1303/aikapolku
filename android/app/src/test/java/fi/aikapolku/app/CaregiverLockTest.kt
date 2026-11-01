package fi.aikapolku.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import fi.aikapolku.app.caregiver.AppAction
import fi.aikapolku.app.caregiver.CaregiverAccessState
import fi.aikapolku.app.caregiver.CaregiverLockStore
import fi.aikapolku.app.caregiver.CaregiverSession
import fi.aikapolku.app.caregiver.PinProblem
import fi.aikapolku.app.caregiver.PinResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** P0-21e – Ohjaajan lukitus: linjaus, PIN-koodi, väärien yritysten esto ja avauksen kesto. */
@RunWith(RobolectricTestRunner::class)
class CaregiverLockTest {

    private lateinit var context: Context
    private var clock = 1_000_000_000L
    private lateinit var store: CaregiverLockStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        store = CaregiverLockStore(context) { clock }
        CaregiverSession.close()
    }

    private fun prefsText(): String =
        context.getSharedPreferences("aikapolku_caregiver_lock_v1", Context.MODE_PRIVATE)
            .all.values.joinToString("|")

    // --- Linjaus: käyttäjän omat toiminnot eivät ole koskaan lukossa ---

    @Test
    fun `apu tarvitsen tauon valmis ohitus ja aloita-jatka eivat ole koskaan ohjaajan toimintoja`() {
        val alwaysFree = listOf(
            AppAction.HELP, AppAction.BREAK, AppAction.DONE, AppAction.SKIP_STEP,
            AppAction.START_RESUME, AppAction.START_PROGRAM, AppAction.VIEW_PROGRAM, AppAction.PRINT
        )
        alwaysFree.forEach { assertFalse("$it ei saa olla lukittu", it.caregiverOnly) }
    }

    @Test
    fun `lukittuna kayttajan toiminnot sallitaan ja ohjaajan toiminnot estetaan`() {
        val locked = CaregiverAccessState(lockEnabled = true, sessionOpen = false)

        assertTrue(locked.isLocked)
        AppAction.values().forEach { action ->
            assertEquals("$action", !action.caregiverOnly, locked.allows(action))
        }
    }

    @Test
    fun `ohjaajan toiminnot ovat ajan muutos tauko nollaus lisaaika lopetus muokkaus ja asetukset`() {
        assertEquals(
            setOf(
                AppAction.ADJUST_TIME, AppAction.PAUSE_TIMER, AppAction.RESET_TIMER, AppAction.EXTEND_TIME,
                AppAction.END_PROGRAM, AppAction.EDIT_PROGRAMS, AppAction.EDIT_CARDS, AppAction.SETTINGS
            ),
            AppAction.values().filter { it.caregiverOnly }.toSet()
        )
    }

    @Test
    fun `lukittuna tauko vaatii PIN-koodin mutta Tarvitsen tauon toimii aina`() {
        // Itsemääräämisoikeus: käyttäjän oma taukopyyntö ei ole koskaan lukossa (päätös 1.11.2026).
        val locked = CaregiverAccessState(lockEnabled = true, sessionOpen = false)

        assertFalse(locked.allows(AppAction.PAUSE_TIMER))
        assertTrue(locked.allows(AppAction.BREAK))
        assertTrue(locked.allows(AppAction.START_RESUME))
    }

    @Test
    fun `ilman lukitusta kaikki on sallittua`() {
        val off = CaregiverAccessState(lockEnabled = false, sessionOpen = false)

        assertFalse(off.isLocked)
        AppAction.values().forEach { assertTrue("$it", off.allows(it)) }
    }

    @Test
    fun `ohjaajan avaamana kaikki on sallittua`() {
        val open = CaregiverAccessState(lockEnabled = true, sessionOpen = true)

        assertFalse(open.isLocked)
        AppAction.values().forEach { assertTrue("$it", open.allows(it)) }
    }

    // --- PIN-koodi ---

    @Test
    fun `lukitus on oletuksena pois`() {
        assertFalse(store.isEnabled)
        assertEquals(PinResult.Ok, store.verify("0000"))
    }

    @Test
    fun `oikea PIN avaa`() {
        store.enable("2580")

        assertTrue(store.isEnabled)
        assertEquals(PinResult.Ok, store.verify("2580"))
    }

    @Test
    fun `vaara PIN kertoo jaljella olevat yritykset`() {
        store.enable("2580")

        assertEquals(PinResult.Wrong(4), store.verify("1111"))
        assertEquals(PinResult.Wrong(3), store.verify("0852"))
        assertEquals(PinResult.Wrong(2), store.verify("12"))
    }

    @Test
    fun `viides vaara yritys estaa 30 sekunniksi eika oikeakaan PIN silloin kelpaa`() {
        store.enable("2580")
        repeat(4) { store.verify("9999") }

        assertEquals(PinResult.LockedOut(30_000L), store.verify("9999"))
        assertTrue(store.verify("2580") is PinResult.LockedOut)
    }

    @Test
    fun `eston jalkeen oikea PIN toimii ja laskurit nollautuvat`() {
        store.enable("2580")
        repeat(5) { store.verify("9999") }

        clock += 30_001L

        assertEquals(PinResult.Ok, store.verify("2580"))
        assertEquals(PinResult.Wrong(4), store.verify("9999"))
    }

    @Test
    fun `toistuvat estot pitenevat`() {
        store.enable("2580")
        repeat(5) { store.verify("9999") }
        clock += 30_001L

        repeat(4) { store.verify("9999") }
        assertEquals(PinResult.LockedOut(60_000L), store.verify("9999"))
    }

    @Test
    fun `eston kesto kasvaa ja on enintaan 15 minuuttia`() {
        assertEquals(30_000L, CaregiverLockStore.lockoutDurationMs(1))
        assertEquals(60_000L, CaregiverLockStore.lockoutDurationMs(2))
        assertEquals(120_000L, CaregiverLockStore.lockoutDurationMs(3))
        assertEquals(15 * 60_000L, CaregiverLockStore.lockoutDurationMs(10))
        assertEquals(15 * 60_000L, CaregiverLockStore.lockoutDurationMs(1000))
    }

    @Test
    fun `onnistunut avaus nollaa vaarien yritysten laskurin`() {
        store.enable("2580")
        repeat(4) { store.verify("9999") }
        assertEquals(PinResult.Ok, store.verify("2580"))

        assertEquals(PinResult.Wrong(4), store.verify("9999"))
    }

    @Test
    fun `kellon siirto taaksepain ei pidenna estoa yli enimmaisajan`() {
        store.enable("2580")
        repeat(5) { store.verify("9999") }

        clock -= 365L * 24 * 60 * 60_000L // käyttäjä siirtää kelloa vuoden taaksepäin

        assertTrue(store.lockoutRemainingMs() <= CaregiverLockStore.MAX_LOCKOUT_MS)
    }

    @Test
    fun `PIN-koodia ei tallenneta selvakielisena`() {
        store.enable("2580")

        assertFalse(prefsText().contains("2580"))
    }

    @Test
    fun `sama PIN tuottaa eri tiivisteen uudella suolalla`() {
        store.enable("2580")
        val first = prefsText()
        store.enable("2580")

        assertNotEquals(first, prefsText())
        assertEquals(PinResult.Ok, store.verify("2580"))
    }

    @Test
    fun `PIN-koodin vaihdon jalkeen vain uusi kelpaa`() {
        store.enable("2580")
        store.enable("7193")

        assertTrue(store.verify("2580") is PinResult.Wrong)
        assertEquals(PinResult.Ok, store.verify("7193"))
    }

    @Test
    fun `lukitus ja PIN sailyvat sovelluksen uudelleenkaynnistyksessa`() {
        store.enable("2580")
        val restarted = CaregiverLockStore(context) { clock }

        assertTrue(restarted.isEnabled)
        assertEquals(PinResult.Ok, restarted.verify("2580"))
    }

    @Test
    fun `eston tila sailyy sovelluksen uudelleenkaynnistyksessa`() {
        store.enable("2580")
        repeat(5) { store.verify("9999") }
        val restarted = CaregiverLockStore(context) { clock }

        assertTrue(restarted.verify("2580") is PinResult.LockedOut)
    }

    @Test
    fun `lukituksen poisto poistaa PIN-koodin mutta muistaa kertakysymyksen`() {
        store.enable("2580")
        store.lockOfferShown = true

        store.disable()

        assertFalse(store.isEnabled)
        assertEquals(PinResult.Ok, store.verify("0000"))
        assertTrue(store.lockOfferShown)
        assertFalse(prefsText().contains("pin"))
    }

    @Test
    fun `kertakysymys on oletuksena nayttamatta ja tila sailyy`() {
        assertFalse(store.lockOfferShown)
        store.lockOfferShown = true

        assertTrue(CaregiverLockStore(context) { clock }.lockOfferShown)
    }

    @Test
    fun `PIN-koodin kelpoisuus`() {
        assertNull(CaregiverLockStore.pinProblem("2580"))
        assertNull(CaregiverLockStore.pinProblem("1357"))
        assertNull(CaregiverLockStore.pinProblem("0912"))
        assertEquals(PinProblem.LENGTH, CaregiverLockStore.pinProblem("123"))
        assertEquals(PinProblem.LENGTH, CaregiverLockStore.pinProblem("12345"))
        assertEquals(PinProblem.LENGTH, CaregiverLockStore.pinProblem("12a4"))
        assertEquals(PinProblem.SAME_DIGITS, CaregiverLockStore.pinProblem("1111"))
        assertEquals(PinProblem.SEQUENCE, CaregiverLockStore.pinProblem("1234"))
        assertEquals(PinProblem.SEQUENCE, CaregiverLockStore.pinProblem("6543"))
    }

    @Test
    fun `heikkoa PIN-koodia ei voi ottaa kayttoon`() {
        assertThrows(IllegalArgumentException::class.java) { store.enable("1234") }
        assertFalse(store.isEnabled)
    }

    // --- Avauksen kesto (CaregiverSession) ---

    @Test
    fun `avaus on voimassa 2 minuuttia`() {
        CaregiverSession.open(0L)

        assertTrue(CaregiverSession.isOpen(119_999L))
        assertFalse(CaregiverSession.isOpen(120_000L))
    }

    @Test
    fun `avaus ei pitene kaytossa vaan paattyy 2 minuutin kuluttua PIN-koodista`() {
        // Puhelintesti 1.11.2026: kosketukset pitivät lukon auki. Avaus on nyt kiinteä.
        CaregiverSession.open(0L)

        assertEquals(60_000L, CaregiverSession.remainingMs(60_000L))
        assertFalse(CaregiverSession.isOpen(CaregiverSession.TIMEOUT_MS))
        assertEquals(0L, CaregiverSession.remainingMs(CaregiverSession.TIMEOUT_MS + 1))
    }

    @Test
    fun `lukko on oletuksena kiinni`() {
        assertFalse(CaregiverSession.isOpen(0L))
        assertEquals(0L, CaregiverSession.remainingMs(0L))
    }

    @Test
    fun `taustalle siirtyminen lukitsee`() {
        CaregiverSession.open(0L)

        CaregiverSession.onAppBackground()

        assertFalse(CaregiverSession.isOpen(1L))
    }

    @Test
    fun `oma valokuvanvalitsin ei lukitse mutta vain kerran`() {
        CaregiverSession.open(0L)

        CaregiverSession.keepOpenForOwnPicker()
        CaregiverSession.onAppBackground() // valitsin aukeaa
        assertTrue(CaregiverSession.isOpen(1L))

        CaregiverSession.onAppBackground() // seuraava oikea taustalle siirto
        assertFalse(CaregiverSession.isOpen(2L))
    }

    @Test
    fun `sulkeminen lukitsee heti`() {
        CaregiverSession.open(0L)

        CaregiverSession.close()

        assertFalse(CaregiverSession.isOpen(1L))
    }
}
