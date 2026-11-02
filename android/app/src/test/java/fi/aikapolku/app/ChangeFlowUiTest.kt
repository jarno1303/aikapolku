package fi.aikapolku.app

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import fi.aikapolku.app.caregiver.CaregiverAccess
import fi.aikapolku.app.caregiver.CaregiverAccessState
import fi.aikapolku.app.caregiver.LocalCaregiver
import fi.aikapolku.app.data.AckStore
import fi.aikapolku.app.data.ChangeReveal
import fi.aikapolku.app.data.ChangeStore
import fi.aikapolku.app.data.ChangeType
import fi.aikapolku.app.data.DayChanges
import fi.aikapolku.app.data.DayProgram
import fi.aikapolku.app.data.EventChange
import fi.aikapolku.app.data.EventKind
import fi.aikapolku.app.data.ProgramStep
import fi.aikapolku.app.data.ProgramStore
import fi.aikapolku.app.data.WeekEvent
import fi.aikapolku.app.data.WeekPlan
import fi.aikapolku.app.data.WeekStore
import fi.aikapolku.app.programs.run.ProgramRunController
import fi.aikapolku.app.programs.run.RunProgress
import fi.aikapolku.app.programs.withTodayChanges
import fi.aikapolku.app.timer.TimerPort
import fi.aikapolku.app.week.ChangeScreen
import fi.aikapolku.app.week.QuickChangeScreen
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Muutoskortin käyttö ilman puhelinta: muutosmerkit aloitusnäkymässä, pikamuutos kortille,
 * päiväohjelman vaiheelle ja huomiselle sekä lukittu näkymä ja "Selvä".
 * Tekstit haetaan resursseista, joten testi toimii myös käännetyillä kielillä.
 */
@RunWith(RobolectricTestRunner::class)
class ChangeFlowUiTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private fun t(id: Int, vararg args: Any) = context.getString(id, *args)

    private val today = LocalDate.now()
    private val outdoors get() = t(R.string.card_outdoors)
    private val rest get() = t(R.string.card_rest)

    /** Korttitapahtuma joka päivälle klo 00.00, jolloin se on aina päivän "nyt"-tapahtuma. */
    private fun cardEvents() = (1..7).map { d ->
        WeekEvent("ulkoilu-$d", d, 0, "00:00", EventKind.CARD, cardName = outdoors, cardPhoto = "builtin:outdoors")
    }

    private val program = DayProgram(
        "koulu", "Koulupäivä",
        listOf(
            ProgramStep("s1", "Aamupiiri", "", ""),
            ProgramStep("s2", "Uinti", "", ""),
            ProgramStep("s3", "Kotiinlähtö", "", "")
        )
    )

    private fun todayEventId() = "ulkoilu-${today.dayOfWeek.value}"

    @Before
    fun setUp() {
        WeekStore(context).save(WeekPlan(cardEvents()))
    }

    /** Ohjaajan lukitus päällä; [open] = lukko avattu (ohjaaja) vai lukittu (käyttäjä). */
    @Composable
    private fun AsCaregiver(open: Boolean, content: @Composable () -> Unit) {
        val access = CaregiverAccess(
            state = CaregiverAccessState(lockEnabled = true, sessionOpen = open),
            requestUnlock = {},
            openSetup = {}, offerLockOnce = {}, disableLock = {}, lockNow = {}, forgotPin = {},
            deviceSecure = true
        )
        CompositionLocalProvider(LocalCaregiver provides access) {
            Column(Modifier.verticalScroll(rememberScrollState())) { content() }
        }
    }

    @Test
    fun `ohjaaja nakee muutosmerkin rivilla ja Tee muutos -painikkeen`() {
        var chosen: String? = "ei"
        compose.setContent {
            AsCaregiver(open = true) {
                HomeScreen(open = {}, onMakeChange = { chosen = it })
            }
        }
        // Ohjaajalla oma etusivu työkaluineen, ei asiakkaan aloituskortteja.
        compose.onNodeWithText(t(R.string.caregiver_tools)).assertExists()
        compose.onNodeWithText(t(R.string.home_view_as_client)).assertExists()
        compose.onNodeWithText(t(R.string.suunnittele_kaksi_askelta)).assertDoesNotExist()
        compose.onNodeWithText(t(R.string.quick_title)).performScrollTo().performClick()
        assertEquals("Tee muutos -painike avaa pikamuutoksen alusta", null, chosen)
        compose.onNodeWithContentDescription(t(R.string.quick_title) + ": " + outdoors).performScrollTo().performClick()
        assertEquals("rivin merkki avaa pikamuutoksen tälle tapahtumalle", todayEventId(), chosen)
    }

    @Test
    fun `pikamuutos korvaa kortin kolmella napautuksella`() {
        var done = false
        compose.setContent {
            AsCaregiver(open = true) {
                QuickChangeScreen(initialDayOffset = 0, initialEventId = todayEventId()) { done = true }
            }
        }
        compose.onNodeWithText(rest).performScrollTo().performClick()
        compose.onNodeWithText(t(R.string.quick_save)).performScrollTo().performClick()
        assertTrue(done)
        val change = ChangeStore(context).load().single()
        assertEquals(ChangeType.REPLACE, change.type)
        assertEquals(rest, change.cardName)
        assertEquals(today, change.date)
        assertEquals(todayEventId(), change.eventId)
    }

    @Test
    fun `paivaohjelman vaihe perutaan etukateen ja ohjelma ohittaa sen`() {
        ProgramStore(context).save(program)
        val schoolEvents = (1..7).map { d -> WeekEvent("koulu-$d", d, 0, "08:00", EventKind.PROGRAM, programId = "koulu") }
        WeekStore(context).save(WeekPlan(cardEvents() + schoolEvents))
        val eventId = "koulu-${today.dayOfWeek.value}"

        var done = false
        compose.setContent {
            AsCaregiver(open = true) {
                QuickChangeScreen(initialDayOffset = 0, initialEventId = eventId) { done = true }
            }
        }
        compose.onNodeWithText("Uinti").performScrollTo().performClick()
        compose.onNodeWithText(t(R.string.change_type_cancel)).performScrollTo().performClick()
        compose.onNodeWithText(t(R.string.quick_save)).performScrollTo().performClick()
        assertTrue(done)
        val change = ChangeStore(context).load().single()
        assertEquals("s2", change.stepId)
        assertEquals(ChangeType.CANCEL, change.type)

        // Ohjelma käynnistyy: Uinti ohitetaan, laskurissa 2 tehtävää.
        val run = ProgramRunController(FakeTimer(), FakeProgress()) {}
        run.start(withTodayChanges(context, program))
        assertEquals(2, run.activeCount)
        assertEquals("s1", run.currentStep?.id)
        assertEquals("s3", run.nextStep?.id)
        run.next()
        assertEquals("s3", run.currentStep?.id)
        assertEquals(1, run.activeIndex)
    }

    @Test
    fun `Tee muutos -painikkeella muutos huomiselle`() {
        var done = false
        compose.setContent {
            AsCaregiver(open = true) {
                QuickChangeScreen(initialDayOffset = 0, initialEventId = null) { done = true }
            }
        }
        compose.onNodeWithText(t(R.string.change_day_tomorrow)).performClick()
        compose.onNodeWithText("00.00  $outdoors").performScrollTo().performClick()
        compose.onNodeWithText(rest).performScrollTo().performClick()
        compose.onNodeWithText(t(R.string.quick_save)).performScrollTo().performClick()
        assertTrue(done)
        val change = ChangeStore(context).load().single()
        assertEquals(today.plusDays(1), change.date)
        assertEquals("ulkoilu-${today.plusDays(1).dayOfWeek.value}", change.eventId)
    }

    private fun savedChange(): EventChange {
        val change = EventChange(
            id = "c1", date = today, eventId = todayEventId(), type = ChangeType.REPLACE,
            cardName = rest, cardPhoto = "builtin:rest", reveal = ChangeReveal.NOW
        )
        ChangeStore(context).upsert(change)
        return change
    }

    @Test
    fun `lukittuna muutosmerkit ovat piilossa mutta muutos nakyy kayttajalle`() {
        savedChange()
        compose.setContent {
            AsCaregiver(open = false) {
                HomeScreen(open = {})
            }
        }
        compose.onNodeWithContentDescription(t(R.string.quick_title) + ": " + outdoors).assertDoesNotExist()
        compose.onNodeWithText(t(R.string.quick_title)).assertDoesNotExist()
        compose.onNodeWithText(t(R.string.change_label) + " · " + t(R.string.change_day_today), substring = true).assertExists()
        // Asiakkaalla vain oma päivä: ei ohjaajan työkaluja.
        compose.onNodeWithText(t(R.string.caregiver_tools)).assertDoesNotExist()
        compose.onNodeWithText(t(R.string.suunnittele_kaksi_askelta)).assertExists()
    }

    @Test
    fun `Selva kuittaa muutoksen eika sita enaa kerrota aloitusnakymassa`() {
        val change = savedChange()
        var done = false
        compose.setContent {
            AsCaregiver(open = false) {
                ChangeScreen(change.id) { done = true }
            }
        }
        compose.onNodeWithText(t(R.string.change_ok)).performScrollTo().performClick()
        assertTrue(done)
        assertNotNull(AckStore(context).ackTime(today, change.ackId))
        val acks = AckStore(context)
        assertTrue(
            DayChanges.announced(WeekStore(context).load(), ChangeStore(context).load(), LocalDateTime.now()) {
                acks.ackTime(it.date, it.ackId) != null
            }.isEmpty()
        )
    }

    private class FakeTimer : TimerPort {
        override fun now() = 1_000L
        override fun allowed() = true
        override fun requestPermissions() {}
        override fun start(durationMs: Long, title: String) = 1_000L + durationMs
        override fun stop(remainingMs: Long) {}
        override fun remaining(fallbackMs: Long) = fallbackMs
        override fun isRunning() = false
        override fun complete() = false
    }

    private class FakeProgress : RunProgress {
        override var programId = ""
        override var stepIndex = 0
        override var subStepIndex = 0
        override fun save(programId: String, stepIndex: Int, subStepIndex: Int) {
            this.programId = programId; this.stepIndex = stepIndex; this.subStepIndex = subStepIndex
        }
        override fun clear() {}
    }
}
