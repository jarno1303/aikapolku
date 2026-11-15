package fi.aikapolku.app

import android.content.Context
import android.graphics.Bitmap
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import fi.aikapolku.app.caregiver.CaregiverAccess
import fi.aikapolku.app.caregiver.CaregiverAccessState
import fi.aikapolku.app.caregiver.LocalCaregiver
import fi.aikapolku.app.data.ChangeReveal
import fi.aikapolku.app.data.ChangeStore
import fi.aikapolku.app.data.ChangeType
import fi.aikapolku.app.data.ChoiceOption
import fi.aikapolku.app.data.ClientViewPrefs
import fi.aikapolku.app.data.EventChange
import fi.aikapolku.app.data.EventKind
import fi.aikapolku.app.data.RunPreferences
import fi.aikapolku.app.data.SupportKind
import fi.aikapolku.app.data.TimelineMode
import fi.aikapolku.app.data.WaitState
import fi.aikapolku.app.data.WeekEvent
import fi.aikapolku.app.data.WeekPlan
import fi.aikapolku.app.data.WeekStore
import fi.aikapolku.app.programs.run.ChoiceScreen
import fi.aikapolku.app.programs.run.OwnStateFlow
import fi.aikapolku.app.programs.run.RunNext
import fi.aikapolku.app.programs.run.RunStepScreen
import fi.aikapolku.app.programs.run.RunTime
import fi.aikapolku.app.programs.run.SupportMessageScreen
import fi.aikapolku.app.timer.TimerScreen
import fi.aikapolku.app.week.ChangeScreen
import fi.aikapolku.app.week.EventCardScreen
import fi.aikapolku.app.week.WaitingView
import java.io.File
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Näkymät eri ruutukoissa (havainto 15.11.2026: vaakatila ja tabletit). Mittaus, ei vielä
 * laatuportti: jokaisesta asiakkaan näkymästä tallentuu kuva (build/nakymakuvat/<koko>/) ja
 * raportti siitä, jääkö painike ruudun ulkopuolelle tai onko painikkeita yli viisi.
 * Sisältöalue = ruutu − tilarivi 24 − järjestelmäpalkki 24 − yläpalkki 64 − roolipalkki 40 dp.
 */
abstract class NakymaKootBase(private val tag: String, private val width: Int, private val contentH: Int) {
    @get:Rule
    val compose = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private fun t(id: Int, vararg args: Any) = context.getString(id, *args)
    private val today = LocalDate.now()
    private val rest get() = t(R.string.card_rest)
    private val dress get() = t(R.string.card_dress)

    private class Screen(val name: String, val scrolls: Boolean, val caregiver: Boolean = false, val content: @Composable () -> Unit)

    @Composable
    private fun Host(screen: Screen) {
        val access = CaregiverAccess(
            state = if (screen.caregiver) CaregiverAccessState(lockEnabled = false, sessionOpen = true)
            else CaregiverAccessState(lockEnabled = true, sessionOpen = false),
            requestUnlock = {}, openSetup = {}, offerLockOnce = {}, disableLock = {}, lockNow = {}, forgotPin = {},
            deviceSecure = true
        )
        CompositionLocalProvider(LocalCaregiver provides access) {
            Box(Modifier.size(width.dp, contentH.dp).then(Modifier)) {
                androidx.compose.material3.Surface(color = fi.aikapolku.app.ui.AppColors.Background, modifier = Modifier.fillMaxSize()) {
                    if (screen.scrolls) {
                        Column(
                            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalArrangement = Arrangement.spacedBy(9.dp)
                        ) { screen.content() }
                    } else {
                        Box(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 9.dp)) { screen.content() }
                    }
                }
            }
        }
    }

    private fun prepareDay(): String {
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
        val change = EventChange(
            id = "koot-c1", date = today, eventId = "ulkoilu", type = ChangeType.REPLACE,
            cardName = rest, cardPhoto = "builtin:rest", reason = "Sataa", reveal = ChangeReveal.NOW
        )
        ChangeStore(context).upsert(change)
        return change.id
    }

    private fun timer(remaining: Long, running: Boolean = true, handOff: Boolean = false) = @Composable {
        TimerScreen(
            remaining = remaining, duration = 10 * 60_000L, running = running,
            activeTask = dress, firstPhoto = "builtin:dress", nextPhoto = "builtin:rest", nextActivity = rest,
            onGoNext = {}, onPreset = {}, onStartPause = {}, onReset = {}, moreTimeUsed = 0, onMoreTime = { true },
            onHandToClient = if (handOff) ({}) else null
        )
    }

    @Composable
    private fun Home(mode: TimelineMode) {
        remember(mode) { ClientViewPrefs.save(context, ClientViewPrefs(timeline = mode)); true }
        HomeScreen(open = {})
    }

    private fun step(time: RunTime?, more: Boolean = false) = @Composable {
        RunStepScreen(
            taskName = dress, taskPhoto = "builtin:dress", description = "",
            subSteps = if (time == null) listOf(t(R.string.substep_socks), t(R.string.substep_trousers)) else emptyList(),
            subStepIndex = 0, taskIndex = 0, taskCount = 3, time = time,
            next = RunNext(rest, rest, "builtin:rest", isSubStep = false),
            nextMode = RunPreferences.NEXT_CARD,
            onToggleTimer = {}, onHelp = {}, onBreak = {}, onDone = {}, onMore = {}, moreIsProgram = true,
            moreTimeEnabled = more, moreTimeVisible = more
        )
    }

    private fun screens(changeId: String): List<Screen> = listOf(
        Screen("etusivu-lahimmat", true) { Home(TimelineMode.NEXT) },
        Screen("etusivu-koko-paiva", true) { Home(TimelineMode.FULL) },
        Screen("paivaohjelman-vaihe", false, content = step(null)),
        Screen("paivaohjelma-ennakkovaroitus", false, content = step(RunTime.Countdown(2 * 60_000L, 10 * 60_000L, running = true, displayMode = "numbers", pauseLocked = true))),
        Screen("paivaohjelma-lisaaika", false, content = step(RunTime.Countdown(60_000L, 10 * 60_000L, running = true, displayMode = "numbers", pauseLocked = true), more = true)),
        Screen("taukoviesti", false) { SupportMessageScreen(kind = SupportKind.BREAK, timerPaused = true, onContinue = {}, onClose = {}) },
        Screen("apuviesti", false) { SupportMessageScreen(kind = SupportKind.HELP, timerPaused = false, onContinue = {}, onClose = {}) },
        Screen("valinta", false) {
            ChoiceScreen(
                options = listOf(ChoiceOption(t(R.string.card_outdoors), "builtin:outdoors"), ChoiceOption(t(R.string.card_music), "builtin:music")),
                allowNone = true, onStart = {}, onNone = {}
            )
        },
        Screen("oma-tila", false) { OwnStateFlow(onClose = {}) },
        Screen("odotetaan", true) { WaitingView(WaitState(t(R.string.card_leaving), "builtin:leaving", System.currentTimeMillis(), 0)) },
        Screen("muutos", true) { ChangeScreen(changeId) {} },
        Screen("tapahtumakortti", true) { EventCardScreen(name = t(R.string.card_outdoors), photo = "builtin:outdoors", doneAt = null, onDone = {}) },
        Screen("ajastin", true, content = timer(6 * 60_000L)),
        Screen("ajastin-ennakkovaroitus", true, content = timer(3 * 60_000L)),
        Screen("ajastin-aika-loppui", true, content = timer(0L, running = false)),
        Screen("ajastin-ohjaaja-valmistelu", true, caregiver = true, content = timer(10 * 60_000L, running = false, handOff = true))
    )

    private fun SemanticsNode.label(): String {
        val text = config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty()
        val desc = config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ").orEmpty()
        return listOf(text, desc).filter { it.isNotBlank() }.joinToString(" / ").take(40)
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
    fun nakymat_tassa_koossa() {
        val list = screens(prepareDay())
        val dir = File("build/nakymakuvat/$tag").apply { mkdirs() }
        // Rauhalliset animaatiot (odotus) eivät pysähdy: kello siirretään käsin.
        compose.mainClock.autoAdvance = false
        var index by mutableIntStateOf(0)
        compose.setContent {
            val screen = list[index]
            androidx.compose.runtime.key(index) { Host(screen) }
        }
        val density = compose.density.density
        val bottom = contentH * density
        val right = width * density
        val lines = mutableListOf("Koko $tag: ${width} × ${contentH} dp sisältöalue")
        list.indices.forEach { i ->
            compose.runOnIdle { index = i }
            // Uusi näkymä piirtyy vasta kehyksillä (kello on käsin ohjattu).
            repeat(6) { compose.mainClock.advanceTimeByFrame() }
            compose.mainClock.advanceTimeBy(1_000L)
            compose.waitForIdle()
            val screen = list[i]
            val nodes = clickables()
            val hidden = nodes.filter { n -> n.positionInRoot.y + n.size.height > bottom + 1f }
            val wide = nodes.filter { n -> n.positionInRoot.x + n.size.width > right + 1f }
            val findings = buildList {
                if (nodes.size > 5) add("R2 painikkeita ${nodes.size}")
                hidden.forEach { n -> add("R11 '${n.label()}' ${((n.positionInRoot.y + n.size.height - bottom) / density).toInt()} dp alareunan alla") }
                wide.forEach { n -> add("leveys '${n.label()}' yli oikean reunan") }
            }
            lines += "[${screen.name}] " + (if (findings.isEmpty()) "OK" else findings.joinToString("; "))
            runCatching {
                // Piirto suoraan näkymästä (captureToImage odottaa kehystä, jota käsin ohjattu kello ei anna).
                var bmp: Bitmap? = null
                compose.runOnUiThread {
                    val view = compose.activity.window.decorView
                    val w = (width * density).toInt()
                    val h = (contentH * density).toInt()
                    bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { view.draw(android.graphics.Canvas(it)) }
                }
                File(dir, "${screen.name}.png").outputStream().use { bmp!!.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }.onFailure { lines += "  (kuva ei onnistunut: ${it.javaClass.simpleName}: ${it.message?.take(120)})" }
        }
        File(dir, "raportti.txt").writeText(lines.joinToString("\n"))
        println(lines.joinToString("\n"))
    }
}

/** Puhelin pystyssä (vertailukohta; laatuportti ClientScreenFitTest). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h851dp-port")
class NakymaKootPuhelinPystyTest : NakymaKootBase("puhelin-pysty", 411, 851 - 152)

/** Puhelin vaakatasossa. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w851dp-h411dp-land")
class NakymaKootPuhelinVaakaTest : NakymaKootBase("puhelin-vaaka", 851, 411 - 152)

/** Tabletti pystyssä. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w800dp-h1280dp-port")
class NakymaKootTablettiPystyTest : NakymaKootBase("tabletti-pysty", 800, 1280 - 152)

/** Tabletti vaakatasossa. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w1280dp-h800dp-land")
class NakymaKootTablettiVaakaTest : NakymaKootBase("tabletti-vaaka", 1280, 800 - 152)
