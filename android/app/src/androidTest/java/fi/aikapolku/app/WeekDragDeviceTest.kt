package fi.aikapolku.app

import android.content.Context
import android.view.WindowManager
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import fi.aikapolku.app.caregiver.RoleSession
import fi.aikapolku.app.data.ChangeStore
import fi.aikapolku.app.data.ChangeType
import fi.aikapolku.app.data.EventKind
import fi.aikapolku.app.data.ExceptionKind
import fi.aikapolku.app.data.Group
import fi.aikapolku.app.data.GroupStore
import fi.aikapolku.app.data.ProfileStore
import fi.aikapolku.app.data.WeekEvent
import fi.aikapolku.app.data.WeekPlan
import fi.aikapolku.app.data.WeekSchedule
import fi.aikapolku.app.data.WeekStore
import fi.aikapolku.app.week.EventReminders
import java.time.LocalDate
import org.junit.After
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters

/**
 * Puhelimessa ajettava testi, joka näkyy ruudulla: ohjaaja siirtää kohtia pitkällä
 * painalluksella ja raahaamalla.
 * 1) Asiakkaan viikko, lyhyt lista: ylin oma kohta alimmaksi (ajat pysyvät, sisällöt vaihtuvat).
 * 2) Asiakkaan viikko, pitkä lista: kohta ruudun alareunaan, sivu vierii mukana.
 * 3) Tänään-lista: kohta raahataan myöhemmäksi ja valitaan "Vain tänään" (päivän muutos).
 * 4) Tänään-lista: ryhmän kohta raahataan aiemmaksi ja valitaan "Joka viikko" (vain tälle asiakkaalle).
 *
 * Testi käyttää omaa väliaikaista asiakastaan ja ryhmäänsä, jotka poistetaan lopuksi.
 * Aja skriptillä aikapolku-siirtotesti.ps1.
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class WeekDragDeviceTest {

    companion object {
        private const val PROFILE_NAME = "Siirtotesti"
        private const val GROUP_ID = "siirtotesti-ryhma"
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
    fun keepScreenOn() {
        val activity = compose.activity
        compose.runOnUiThread { activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    @After
    fun clean() {
        GroupStore(context).delete(GROUP_ID)
        ChangeStore(context, testProfile).save(emptyList())
        WeekStore(context, testProfile).save(WeekPlan())
    }

    private fun t(id: Int) = compose.activity.getString(id)

    private fun pause(ms: Long = 1_500L) {
        Thread.sleep(ms)
        compose.mainClock.advanceTimeBy(ms)
        compose.waitForIdle()
    }

    /** Kaikki ruudulla (myös ikkunoissa) näkyvät tekstit: virheilmoitukseen, kun jotain ei löydy. */
    private fun visibleTexts(): String = runCatching {
        compose.onAllNodes(isRoot(), useUnmergedTree = true).fetchSemanticsNodes().flatMap { root ->
            fun walk(n: SemanticsNode): List<String> =
                n.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } + n.children.flatMap { walk(it) }
            walk(root)
        }.distinct().joinToString(" | ")
    }.getOrElse { "(ei saatu: ${it.message})" }

    private fun waitForText(text: String) {
        try {
            compose.waitUntil(10_000L) { compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty() }
        } catch (e: Throwable) {
            throw AssertionError("Tekstiä \"$text\" ei löytynyt. Ruudulla: ${visibleTexts()}", e)
        }
    }

    /** Odottaa painikkeen ja painaa sitä heti (ei taukoa välissä). */
    private fun clickWhenShown(text: String) {
        waitForText(text)
        node(text).performClick()
        pause()
    }

    private fun node(text: String) = compose.onAllNodesWithText(text).onFirst()

    /** Tämän päivän omat kohdat: "Siirto 01" klo 08:00, "Siirto 02" klo 09:00 … */
    private fun savePlan(count: Int): List<String> {
        val names = (1..count).map { "Siirto %02d".format(it) }
        val events = names.mapIndexed { i, name ->
            WeekEvent("siirto-$i", weekday, 0, WeekSchedule.formatTime(8 + i, 0), EventKind.CARD, cardName = name, cardPhoto = "builtin:outdoors")
        }
        WeekStore(context, testProfile).save(WeekPlan(events))
        return names
    }

    private fun savedOrder(): List<Pair<String, String>> =
        WeekSchedule.eventsOn(WeekStore(context, testProfile).load(), weekday, 0).map { it.time to it.cardName }

    /** Etusivu luetaan uudelleen: käydään Asiakkaan viikossa ja palataan. */
    private fun reloadHome() {
        waitForText(t(R.string.home_week))
        node(t(R.string.home_week)).performClick()
        pause(800L)
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        pause(800L)
        waitForText(t(R.string.home_week))
    }

    private fun openClientWeek(first: String) {
        waitForText(t(R.string.home_week))
        pause()
        node(t(R.string.home_week)).performClick()
        waitForText(first)
        pause()
    }

    /** Pitkä painallus ja raahaus pystysuunnassa [distance] pikseliä pieninä askelina. */
    private fun longPressDrag(target: SemanticsNodeInteraction, distance: Float) {
        target.performTouchInput {
            down(center)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 300L)
            val steps = 25
            repeat(steps) { moveBy(Offset(0f, distance / steps)) }
        }
        pause(800L)
        target.performTouchInput { up() }
        pause()
    }

    @Test
    fun a_viikko_lyhyt_lista_ylin_kohta_alimmaksi() {
        val names = savePlan(3)
        openClientWeek(names.first())
        val start = node(names[0]).fetchSemanticsNode().boundsInRoot
        val last = node(names[2]).fetchSemanticsNode().boundsInRoot
        longPressDrag(node(names[0]), last.bottom - start.center.y + 40f)

        assertEquals(
            "ylin kohta siirtyi alimmaksi ja ajat pysyivät",
            listOf("08:00" to names[1], "09:00" to names[2], "10:00" to names[0]),
            savedOrder()
        )
        pause(2_000L)
    }

    @Test
    fun b_viikko_pitka_lista_vierii_kun_kohta_viedaan_alareunaan() {
        val names = savePlan(12)
        openClientWeek(names.first())

        val rootHeight = compose.onRoot().fetchSemanticsNode().size.height.toFloat()
        val first = node(names[0])
        val start = first.fetchSemanticsNode().boundsInRoot
        val visibleBefore = names.count { n ->
            compose.onAllNodesWithText(n).fetchSemanticsNodes().firstOrNull()?.boundsInRoot?.let { it.bottom <= rootHeight } == true
        }

        first.performTouchInput {
            down(center)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 300L)
            val targetY = rootHeight - 30f - start.top
            val steps = 30
            repeat(steps) { moveBy(Offset(0f, (targetY - height / 2f) / steps)) }
        }
        // Pidetään alareunassa: sivun pitää vieriä.
        repeat(30) {
            Thread.sleep(100L)
            compose.mainClock.advanceTimeBy(100L)
        }
        first.performTouchInput { up() }
        pause()

        val position = savedOrder().indexOfFirst { it.second == names[0] }
        assertTrue(
            "kohta siirtyi näkyvissä olleen alueen alapuolelle ($visibleBefore kohtaa näkyi, kohta nyt sijalla ${position + 1})",
            position >= visibleBefore
        )
        assertEquals((0 until 12).map { WeekSchedule.formatTime(8 + it, 0) }, savedOrder().map { it.first })
        pause(2_000L)
    }

    @Test
    fun c_tanaan_kohta_myohemmaksi_vain_tanaan() {
        val names = savePlan(3)
        reloadHome()
        waitForText(names[0])
        pause()
        val start = node(names[0]).fetchSemanticsNode().boundsInRoot
        val last = node(names[2]).fetchSemanticsNode().boundsInRoot
        longPressDrag(node(names[0]), last.bottom - start.center.y + 40f)

        // Siirtoikkuna: kohta saa ajan viimeisen jälkeen (10:00 + 1 h).
        clickWhenShown(t(R.string.move_today))

        val change = ChangeStore(context, testProfile).load().firstOrNull { it.eventId == "siirto-0" && it.date == today }
        assertNotNull("päivän muutos tallentui", change)
        assertEquals(ChangeType.MOVE, change!!.type)
        assertEquals("11:00", change.time)
        // Viikko-ohjelma ennallaan.
        assertEquals("08:00", savedOrder().first { it.second == names[0] }.first)
        pause(2_000L)
    }

    @Test
    fun d_tanaan_ryhman_kohta_aiemmaksi_joka_viikko() {
        val names = savePlan(2)
        val groupName = "Ryhmän lähtö"
        GroupStore(context).upsert(
            Group(
                GROUP_ID, "Siirtotestin ryhmä", listOf(testProfile, "toinen-asiakas"),
                WeekPlan(listOf(WeekEvent("lahto", weekday, 0, "15:00", EventKind.CARD, cardName = groupName, cardPhoto = "builtin:leaving")))
            )
        )
        reloadHome()
        waitForText(groupName)
        pause()
        val group = node(groupName).fetchSemanticsNode().boundsInRoot
        val first = node(names[0]).fetchSemanticsNode().boundsInRoot
        longPressDrag(node(groupName), first.top - group.center.y - 40f)

        // Ensimmäisen eteen: 08:00 - 1 h = 07:00.
        clickWhenShown(t(R.string.move_weekly))

        val g = GroupStore(context).load().first { it.id == GROUP_ID }
        assertEquals(
            "ryhmän kohdasta tuli poikkeus vain tälle asiakkaalle",
            listOf(ExceptionKind.SKIP),
            g.exceptions.filter { it.memberId == testProfile }.map { it.kind }
        )
        assertTrue("toiselle asiakkaalle ei poikkeusta", g.exceptions.none { it.memberId == "toinen-asiakas" })
        assertEquals("07:00", savedOrder().first { it.second == groupName }.first)
        pause(2_000L)
    }
}
