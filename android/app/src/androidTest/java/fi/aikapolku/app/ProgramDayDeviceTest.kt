package fi.aikapolku.app

import android.content.Context
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import fi.aikapolku.app.caregiver.RoleSession
import fi.aikapolku.app.data.AckStore
import fi.aikapolku.app.data.DemoDay
import fi.aikapolku.app.data.ProfileStore
import fi.aikapolku.app.week.EventReminders
import java.time.LocalDate
import org.junit.AfterClass
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Puhelimessa ajettava testi, joka näkyy ruudulla: asiakas käy esittelypäivän läpi.
 * NYT-kortti → Aloita → aloitusnäkymä → Aloita → Aamupala (Valmis) → Hampaiden pesu
 * (aika alkaa itsestään, odotetaan minuutti, Valmis) → Pukeutuminen (Valmis) → Kaikki valmista
 * → Takaisin aloitukseen, jolloin NYT-kortissa on "✓ Esittelypäivä tehty",
 * Vapaa-aika ja seuraavaksi Lukeminen.
 *
 * Testi käyttää omaa väliaikaista profiiliaan eikä koske muihin tietoihin; profiili
 * poistetaan lopuksi. Aja skriptillä aikapolku-laitetesti.ps1 (ei connectedAndroidTest,
 * joka poistaisi sovelluksen ja sen tiedot testin jälkeen).
 */
@RunWith(AndroidJUnit4::class)
class ProgramDayDeviceTest {

    companion object {
        private const val PROFILE_NAME = "Automaattitesti"
        private lateinit var originalProfile: String
        private lateinit var testProfile: String
        private lateinit var programEventId: String
        private var originalTestMode = true

        private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

        @BeforeClass
        @JvmStatic
        fun setUp() {
            val ctx = context
            originalProfile = ProfileStore.currentId(ctx)
            // Aiemmin keskeytyneen ajon jäänteet pois.
            ProfileStore.list(ctx).filter { it.name == PROFILE_NAME }.forEach { ProfileStore.delete(ctx, it.id) }
            val profile = ProfileStore.add(ctx, PROFILE_NAME)
            testProfile = profile.id
            ProfileStore.switchTo(ctx, profile.id)
            programEventId = DemoDay.fill(ctx, profile.id).programEventId
            RoleSession.load(ctx)
            originalTestMode = RoleSession.testMode
            RoleSession.setTestMode(ctx, true)
            RoleSession.toClient()
        }

        @AfterClass
        @JvmStatic
        fun tearDown() {
            val ctx = context
            ProfileStore.switchTo(ctx, originalProfile)
            ProfileStore.delete(ctx, testProfile)
            RoleSession.setTestMode(ctx, originalTestMode)
            RoleSession.toClient()
            EventReminders.reschedule(ctx)
        }
    }

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    /** Näyttö ei saa sammua minuutin odotuksen aikana (puhelimen oma aikakatkaisu voi olla 30 s). */
    @Before
    fun keepScreenOn() {
        val activity = compose.activity
        compose.runOnUiThread { activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    private fun t(id: Int, vararg args: Any) = compose.activity.getString(id, *args)

    /** Tauko, jotta kulun ehtii nähdä ruudulta (ja Valmis-painikkeen 1,5 s esto ohittuu). */
    private fun pause(ms: Long = 2_000L) {
        Thread.sleep(ms)
        compose.mainClock.advanceTimeBy(ms)
        compose.waitForIdle()
    }

    private fun waitForText(text: String, timeoutMs: Long = 10_000L) {
        compose.waitUntil(timeoutMs) { compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun shown(matcher: SemanticsMatcher) = compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()

    /**
     * Odottaa oikeaa aikaa (ohjelman ajastin laskee laitteen kellolla) ja siirtää samalla
     * Compose-testikelloa perässä, jotta ajastimen päivityssilmukka ehtii pyöriä.
     */
    private fun waitRealTime(timeoutMs: Long, failure: String, condition: () -> Boolean) {
        val end = SystemClock.elapsedRealtime() + timeoutMs
        while (true) {
            val ok = try {
                compose.mainClock.advanceTimeBy(500L)
                compose.waitForIdle()
                condition()
            } catch (e: IllegalStateException) {
                throw AssertionError(
                    "Sovellus poistui ruudulta kesken odotuksen (näyttö sammui, puhelin lukittui " +
                        "tai toinen sovellus/asetussivu avautui päälle).", e
                )
            }
            if (ok) return
            if (SystemClock.elapsedRealtime() > end) throw AssertionError(failure)
            Thread.sleep(500L)
        }
    }

    /** Pitääkö sovellus itse näytön päällä (jokin näkymä keepScreenOn; testin oma ikkunalippu ei lasketa). */
    private fun appKeepsScreenOn(): Boolean {
        val activity = compose.activity
        var found = false
        compose.runOnUiThread {
            fun walk(v: View) {
                if (v.keepScreenOn) found = true
                if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i))
            }
            walk(activity.window.decorView)
        }
        return found
    }

    private fun clickDone() {
        pause()
        compose.onNodeWithText(t(R.string.run_done_button)).performClick()
    }

    @Test
    fun asiakas_kay_paivaohjelman_lapi_alusta_loppuun() {
        val programName = t(R.string.demo_program_name)

        // 1. Asiakkaan etusivu: NYT-kortissa esittelypäivä.
        waitForText(programName)
        compose.onNodeWithText(t(R.string.now_label)).assertExists()
        pause()
        compose.onAllNodesWithText(t(R.string.today_start)).onFirst().performScrollTo().performClick()

        // 2. Aloitusnäkymä: tehtävät kuvina ja Aloita.
        waitForText(t(R.string.program_start_count, 3))
        pause()
        compose.onNodeWithText(t(R.string.today_start)).performScrollTo().performClick()

        // 3. Aamupala: Valmis.
        waitForText(t(R.string.card_breakfast))
        clickDone()

        // 4. Hampaiden pesu: aika alkaa itsestään (oletus), odotetaan minuutti.
        waitForText(t(R.string.card_brush_teeth))
        waitRealTime(
            5_000L,
            "Hampaiden pesun aika ei alkanut itsestään (asetus: Tehtävän aika alkaa = Itsestään)."
        ) {
            shown(hasContentDescription(t(R.string.pysayta_aika), substring = true)) ||
                shown(hasContentDescription(t(R.string.tauko_vaatii_ohjaajan_pin_koodin), substring = true))
        }
        assertTrue("näyttö pidetään päällä, kun tehtävän aika on käynnissä", appKeepsScreenOn())
        waitRealTime(
            75_000L,
            "Aika käynnistyi, mutta \"${t(R.string.time_ended)}\" ei tullut näkyviin 75 sekunnissa."
        ) {
            shown(hasText(t(R.string.time_ended))) ||
                shown(hasContentDescription(t(R.string.a11y_time_ended), substring = true))
        }
        assertFalse("näyttö vapautuu, kun aika on päättynyt", appKeepsScreenOn())
        clickDone()

        // 5. Pukeutuminen: Valmis.
        waitForText(t(R.string.card_dress))
        clickDone()

        // 6. Kaikki valmista ja tapahtuma kuitattu.
        waitForText(t(R.string.kaikki_valmista))
        pause(3_000L)
        assertNotNull(
            "esittelypäivän tapahtuma kuitattu tehdyksi",
            AckStore(context, testProfile).ackTime(LocalDate.now(), programEventId)
        )

        // 7. Takaisin omaan päivään: kuittaus tehdystä, Vapaa-aika ja seuraavaksi Lukeminen.
        compose.onNodeWithText(t(R.string.takaisin_aloitukseen)).performScrollTo().performClick()
        waitForText(t(R.string.card_free_time))
        compose.onNodeWithText(t(R.string.client_gap_done, programName)).assertExists()
        compose.onAllNodesWithText(t(R.string.card_reading)).onFirst().assertExists()
        pause(3_000L)
    }
}
