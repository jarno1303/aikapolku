package fi.aikapolku.app

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import fi.aikapolku.app.caregiver.CaregiverAccess
import fi.aikapolku.app.caregiver.CaregiverAccessState
import fi.aikapolku.app.caregiver.LocalCaregiver
import fi.aikapolku.app.data.ChangeReveal
import fi.aikapolku.app.data.ChangeStore
import fi.aikapolku.app.data.ChangeType
import fi.aikapolku.app.data.EventChange
import fi.aikapolku.app.data.EventKind
import fi.aikapolku.app.data.RunPreferences
import fi.aikapolku.app.data.SupportKind
import fi.aikapolku.app.data.WeekEvent
import fi.aikapolku.app.data.WeekPlan
import fi.aikapolku.app.data.WeekStore
import fi.aikapolku.app.programs.run.RunNext
import fi.aikapolku.app.programs.run.RunStepScreen
import fi.aikapolku.app.programs.run.RunTime
import fi.aikapolku.app.programs.run.SupportMessageScreen
import fi.aikapolku.app.timer.TimerScreen
import fi.aikapolku.app.week.ChangeScreen
import fi.aikapolku.app.week.EventCardScreen
import java.time.LocalDate
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Laatuportin sääntö R11 (puhelintesti 15.11.2026, JarnoL): asiakkaan toimintonäkymässä kaikki
 * painikkeet näkyvät ruudulla ilman vieritystä. Mitataan tavallisen puhelimen sisältöalueella
 * (411 × 851 dp; yläpalkin, roolipalkin ja järjestelmäpalkkien jälkeen 411 × [CONTENT_H] dp)
 * samoin kuin sovellus piirtää: sivu vierivässä sarakkeessa, päiväohjelma omana näkymänään.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h851dp")
class ClientScreenFitTest {
    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private fun t(id: Int, vararg args: Any) = context.getString(id, *args)
    private val today = LocalDate.now()

    companion object {
        const val WIDTH = 411
        /** 851 − tilarivi 32 − alapalkki 24 − yläpalkki 64 − roolipalkki 40. */
        const val CONTENT_H = 691
    }

    /** [scrolls] = sivu on sovelluksen vierivässä sarakkeessa (kaikki paitsi päiväohjelma). */
    private class Screen(val name: String, val scrolls: Boolean, val content: @Composable () -> Unit)

    @Composable
    private fun AsClient(scrolls: Boolean, content: @Composable () -> Unit) {
        val access = CaregiverAccess(
            state = CaregiverAccessState(lockEnabled = true, sessionOpen = false),
            requestUnlock = {}, openSetup = {}, offerLockOnce = {}, disableLock = {}, lockNow = {}, forgotPin = {},
            deviceSecure = true
        )
        CompositionLocalProvider(LocalCaregiver provides access) {
            Box(Modifier.size(WIDTH.dp, CONTENT_H.dp)) {
                if (scrolls) {
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalArrangement = Arrangement.spacedBy(9.dp)
                    ) { content() }
                } else {
                    Box(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 9.dp)) { content() }
                }
            }
        }
    }

    private val rest get() = t(R.string.card_rest)
    private val dress get() = t(R.string.card_dress)

    private fun changeId(): String {
        val day = today.dayOfWeek.value
        WeekStore(context).save(
            WeekPlan(
                listOf(
                    WeekEvent("ulkoilu", day, 0, "00:00", EventKind.CARD, cardName = t(R.string.card_outdoors), cardPhoto = "builtin:outdoors"),
                    WeekEvent("ilta", day, 0, "23:30", EventKind.CARD, cardName = rest, cardPhoto = "builtin:rest")
                ) + listOf("06:00", "07:00", "08:00", "09:00", "10:00", "11:00").mapIndexed { i, time ->
                    WeekEvent("e$i", day, 0, time, EventKind.CARD, cardName = t(R.string.card_reading), cardPhoto = "builtin:reading")
                }
            )
        )
        // Etusivu mitataan oletusnäkymällä "Lähimmät" (koko päivä voi vaatia vieritystä, ks. avoin kysymys).
        fi.aikapolku.app.data.ClientViewPrefs.save(
            context, fi.aikapolku.app.data.ClientViewPrefs(timeline = fi.aikapolku.app.data.TimelineMode.NEXT)
        )
        val change = EventChange(
            id = "fit-c1", date = today, eventId = "ulkoilu", type = ChangeType.REPLACE,
            cardName = rest, cardPhoto = "builtin:rest", reason = "Sataa", reveal = ChangeReveal.NOW
        )
        ChangeStore(context).upsert(change)
        return change.id
    }

    private fun timer(remaining: Long, running: Boolean = true) = @Composable {
        TimerScreen(
            remaining = remaining, duration = 10 * 60_000L, running = running,
            activeTask = dress, firstPhoto = "builtin:dress", nextPhoto = "builtin:rest", nextActivity = rest,
            onGoNext = {}, onPreset = {}, onStartPause = {}, onReset = {}, moreTimeUsed = 0, onMoreTime = { true }
        )
    }

    private fun screens(changeId: String): List<Screen> = listOf(
        Screen("ajastin", true, timer(6 * 60_000L)),
        Screen("ajastin-lopussa", true, timer(60_000L)),
        Screen("ajastin-aika-loppui", true, timer(0L, running = false)),
        Screen("paivaohjelman-vaihe", false) {
            RunStepScreen(
                taskName = dress, taskPhoto = "builtin:dress", description = "",
                subSteps = listOf(t(R.string.substep_socks), t(R.string.substep_trousers)),
                subStepIndex = 0, taskIndex = 0, taskCount = 3, time = null, next = null,
                nextMode = RunPreferences.NEXT_CARD,
                onToggleTimer = {}, onHelp = {}, onBreak = {}, onDone = {}, onMore = {}, moreIsProgram = true
            )
        },
        Screen("paivaohjelma-lisaaika", false) {
            RunStepScreen(
                taskName = dress, taskPhoto = "builtin:dress", description = "",
                subSteps = emptyList(), subStepIndex = 0, taskIndex = 0, taskCount = 3,
                time = RunTime.Countdown(60_000L, 10 * 60_000L, running = true, displayMode = "numbers", pauseLocked = false),
                next = RunNext(rest, rest, "builtin:rest", isSubStep = false),
                nextMode = RunPreferences.NEXT_CARD,
                onToggleTimer = {}, onHelp = {}, onBreak = {}, onDone = {}, onMore = {}, moreIsProgram = true,
                moreTimeEnabled = true, moreTimeVisible = true
            )
        },
        Screen("taukoviesti", false) { SupportMessageScreen(kind = SupportKind.BREAK, timerPaused = true, onContinue = {}, onClose = {}) },
        Screen("lisaaikaviesti", false) { SupportMessageScreen(kind = SupportKind.MORE_TIME, timerPaused = false, onContinue = {}, onClose = {}) },
        Screen("tapahtumakortti", true) { EventCardScreen(name = t(R.string.card_outdoors), photo = "builtin:outdoors", doneAt = null, onDone = {}) },
        Screen("muutos", true) { ChangeScreen(changeId) {} },
        Screen("etusivu", true) { HomeScreen(open = {}) }
    )

    private fun SemanticsNode.label(): String {
        val text = config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty()
        val desc = config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ").orEmpty()
        return listOf(text, desc).filter { it.isNotBlank() }.joinToString(" / ")
    }

    private fun clickables(): List<SemanticsNode> {
        val out = mutableListOf<SemanticsNode>()
        fun walk(n: SemanticsNode) {
            if (n.config.getOrNull(SemanticsActions.OnClick) != null) out += n
            n.children.forEach(::walk)
        }
        walk(compose.onRoot().fetchSemanticsNode())
        return out
    }

    @Test
    fun `asiakkaan nakymissa kaikki painikkeet nakyvat ilman vieritysta`() {
        val list = screens(changeId())
        var index by mutableIntStateOf(0)
        compose.setContent {
            val screen = list[index]
            androidx.compose.runtime.key(index) { AsClient(screen.scrolls) { screen.content() } }
        }
        val density = compose.density.density
        val limit = CONTENT_H * density
        val problems = list.indices.flatMap { i ->
            compose.runOnIdle { index = i }
            compose.waitForIdle()
            clickables().filter { n -> n.positionInRoot.y + n.size.height > limit + 1f }.map { n ->
                val below = ((n.positionInRoot.y + n.size.height - limit) / density).toInt()
                "[${list[i].name}] '${n.label()}' jää ${below} dp ruudun alareunan alle"
            }
        }
        val report = "R11: ${problems.size} piilossa olevaa painiketta.\n" + problems.joinToString("\n")
        println(report)
        assertTrue(report, problems.isEmpty())
    }
}
