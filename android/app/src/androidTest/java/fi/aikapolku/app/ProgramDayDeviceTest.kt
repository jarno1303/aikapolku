package fi.aikapolku.app

import android.content.Context
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
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
import org.junit.Assert.assertNotNull
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Puhelimessa ajettava testi, joka näkyy ruudulla: asiakas käy esittelypäivän läpi.
 * NYT-kortti → Aloita → aloitusnäkymä → Aloita → Aamupala (Valmis) → Hampaiden pesu
 * (aika käyntiin, odotetaan minuutti, Valmis) → Pukeutuminen (Valmis) → Kaikki valmista
 * → Takaisin aloitukseen, jolloin NYT-kortissa on väliaika ja seuraavaksi Lukeminen.
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

        // 4. Hampaiden pesu: aika käyntiin, odotetaan minuutti.
        waitForText(t(R.string.card_brush_teeth))
        pause()
        compose.onNodeWithContentDescription(t(R.string.aloita_aika), substring = true).performClick()
        Thread.sleep(62_000L)
        compose.mainClock.advanceTimeBy(1_000L)
        compose.waitUntil(15_000L) {
            compose.onAllNodes(hasText(t(R.string.time_ended))).fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodes(androidx.compose.ui.test.hasContentDescription(t(R.string.a11y_time_ended), substring = true))
                    .fetchSemanticsNodes().isNotEmpty()
        }
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

        // 7. Takaisin omaan päivään: väliaika (Odota) ja seuraavaksi Lukeminen.
        compose.onNodeWithText(t(R.string.takaisin_aloitukseen)).performScrollTo().performClick()
        waitForText(t(R.string.card_waiting))
        compose.onAllNodesWithText(t(R.string.card_reading)).onFirst().assertExists()
        pause(3_000L)
    }
}
