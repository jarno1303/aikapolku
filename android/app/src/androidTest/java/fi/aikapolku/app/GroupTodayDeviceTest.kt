package fi.aikapolku.app

import android.content.Context
import android.view.WindowManager
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import fi.aikapolku.app.caregiver.RoleSession
import fi.aikapolku.app.data.ChangeType
import fi.aikapolku.app.data.ClientWeek
import fi.aikapolku.app.data.EventKind
import fi.aikapolku.app.data.Group
import fi.aikapolku.app.data.GroupStore
import fi.aikapolku.app.data.ProfileStore
import fi.aikapolku.app.data.WeekEvent
import fi.aikapolku.app.data.WeekPlan
import fi.aikapolku.app.week.EventReminders
import java.time.LocalDate
import org.junit.After
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters

/**
 * Puhelimessa ajettava testi, joka näkyy ruudulla: ohjaajan etusivun Ryhmä tänään.
 * 1) Asiakas merkitään poissaolevaksi: ryhmän ohjelma poistuu hänen päivästään.
 * 2) Ryhmän kohta perutaan tälle päivälle: muutos näkyy ryhmän jäsenen päivässä.
 * Testi käyttää omaa väliaikaista asiakastaan ja ryhmäänsä. Aja skriptillä aikapolku-ryhmatesti.ps1.
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class GroupTodayDeviceTest {

    companion object {
        private const val PROFILE_NAME = "Ryhmätesti"
        private const val GROUP_ID = "ryhmatesti-ryhma"
        private lateinit var originalProfile: String
        private lateinit var testProfile: String
        private var originalTestMode = true

        private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

        @BeforeClass
        @JvmStatic
        fun setUp() {
            val ctx = context
            originalProfile = ProfileStore.currentId(ctx)
            ProfileStore.list(ctx).filter { it.name == PROFILE_NAME }.forEach { ProfileStore.delete(ctx, it.id) }
            GroupStore(ctx).delete(GROUP_ID)
            val profile = ProfileStore.add(ctx, PROFILE_NAME)
            testProfile = profile.id
            ProfileStore.switchTo(ctx, profile.id)
            RoleSession.load(ctx)
            originalTestMode = RoleSession.testMode
            RoleSession.setTestMode(ctx, true)
            RoleSession.toCaregiver()
        }

        @AfterClass
        @JvmStatic
        fun tearDown() {
            val ctx = context
            GroupStore(ctx).delete(GROUP_ID)
            ProfileStore.switchTo(ctx, originalProfile)
            ProfileStore.delete(ctx, testProfile)
            RoleSession.setTestMode(ctx, originalTestMode)
            RoleSession.toClient()
            EventReminders.reschedule(ctx)
        }
    }

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val today = LocalDate.now()
    private val weekday = today.dayOfWeek.value

    @Before
    fun prepare() {
        val activity = compose.activity
        compose.runOnUiThread { activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        GroupStore(context).delete(GROUP_ID)
        GroupStore(context).upsert(
            Group(
                GROUP_ID, "Testiryhmä", listOf(testProfile, "toinen-asiakas"),
                WeekPlan(listOf(
                    WeekEvent("uinti", weekday, 0, "10:00", EventKind.CARD, cardName = "Ryhmän uinti", cardPhoto = "builtin:exercise"),
                    WeekEvent("lounas", weekday, 0, "11:00", EventKind.CARD, cardName = "Ryhmän lounas", cardPhoto = "builtin:lunch_meal")
                ))
            )
        )
        reloadHome()
    }

    @After
    fun clean() {
        GroupStore(context).delete(GROUP_ID)
    }

    private fun t(id: Int, vararg args: Any) = compose.activity.getString(id, *args)

    private fun pause(ms: Long = 1_500L) {
        Thread.sleep(ms)
        compose.mainClock.advanceTimeBy(ms)
        compose.waitForIdle()
    }

    private fun waitForText(text: String) {
        compose.waitUntil(10_000L) { compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty() }
    }

    /** Etusivu luetaan uudelleen: käydään Asiakkaan viikossa ja palataan. */
    private fun reloadHome() {
        waitForText(t(R.string.home_week))
        compose.onAllNodesWithText(t(R.string.home_week)).onFirst().performClick()
        pause(800L)
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        pause(800L)
        waitForText(t(R.string.group_today_title))
    }

    @Test
    fun a_poissaolevalle_ei_tule_ryhman_ohjelmaa() {
        assertEquals(2, ClientWeek.load(context).eventsFor(today).size)
        // Sivu vieritetään Ryhmä tänään -korttiin (asiakkaiden rivi vierii myös sivusuunnassa).
        val lounas = compose.onAllNodesWithText("Ryhmän lounas")
        lounas[lounas.fetchSemanticsNodes().size - 1].performScrollTo()
        pause()
        val toggle = compose.onAllNodes(hasContentDescription(t(R.string.group_today_toggle, PROFILE_NAME))).onFirst()
        toggle.performClick()
        pause()

        assertTrue("poissaolo tallentui", GroupStore(context).isAbsent(GROUP_ID, testProfile, today))
        assertTrue("ryhmän ohjelma poistui asiakkaan päivästä", ClientWeek.load(context).eventsFor(today).isEmpty())
        waitForText(t(R.string.group_today_absent))
        pause(2_000L)

        // Takaisin paikalle.
        toggle.performClick()
        pause()
        assertEquals(2, ClientWeek.load(context).eventsFor(today).size)
    }

    @Test
    fun b_ryhman_kohta_perutaan_kaikilta_tanaan() {
        // Ryhmä tänään -kortin rivi on etusivulla alimpana (asiakkaan Tänään-listan jälkeen).
        val rows = compose.onAllNodesWithText("Ryhmän uinti")
        val count = rows.fetchSemanticsNodes().size
        val row = rows[count - 1]
        row.performScrollTo()
        pause()
        row.performClick()
        waitForText(t(R.string.group_change_cancel))
        compose.onAllNodesWithText(t(R.string.group_change_cancel)).onFirst().performClick()
        pause()

        val change = GroupStore(context).changes(GROUP_ID).single()
        assertEquals(ChangeType.CANCEL, change.type)
        assertEquals("uinti", change.eventId)
        val mine = ClientWeek.changes(context).firstOrNull { it.eventId == "grp:$GROUP_ID:uinti" }
        assertEquals("jäsenen päivässä uinti peruttu", ChangeType.CANCEL, mine?.type)
        pause(2_000L)
    }
}
