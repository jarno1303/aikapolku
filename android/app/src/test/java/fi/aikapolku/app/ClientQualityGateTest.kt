package fi.aikapolku.app

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
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
import fi.aikapolku.app.programs.run.RunStepScreen
import fi.aikapolku.app.programs.run.SupportMessageScreen
import fi.aikapolku.app.timer.TimerScreen
import fi.aikapolku.app.week.ChangeScreen
import fi.aikapolku.app.week.EventCardScreen
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Asiakasnäkymän laatuportti (tutkielma docs/TUTKIELMA_OPTIMAALINEN_SOVELLUS.md, periaatteet P1–P9).
 *
 * Käy läpi asiakkaan näkymät lukittuna (asiakkaan rooli) ja tarkistaa säännöt, joista on sovittu:
 *  R1 kosketusalue vähintään 48 × 48 dp
 *  R2 enintään [MAX_ACTIONS] painettavaa toimintonäkymässä (vähän valintoja kerrallaan)
 *  R3 jokaisella painettavalla on nimi ruudunlukijalle
 *  R4 asiakkaan teksteissä ei hälyttäviä sanoja ([ALARMING])
 *  R5 "Tarvitsen tauon" on painettavissa toimintonäkymissä (tauko aina vapaa)
 *  R6 tekstikoko 200 %: painikkeet eivät valu ruudun ulkopuolelle sivusuunnassa
 *  R7 muutosnäkymän osat aina samassa järjestyksessä
 *  R8 muutos ei näy asiakkaalle ennen valittua näkymisaikaa
 *
 * Tunnetut poikkeamat ovat listassa [KNOWN_GAPS] tutkielman tunnisteineen: ne eivät pysäytä
 * käännöstä, mutta jokainen UUSI rike pysäyttää. Kun poikkeama korjataan, se poistetaan listalta
 * (testi kertoo, jos listalla on jo korjattu kohta).
 *
 * Automaattinen testi ei kerro, miltä sovellus tuntuu autistisesta ihmisestä: se vain pitää
 * sovitut periaatteet voimassa. Oikea käyttäjäpalaute ratkaisee.
 */
@RunWith(RobolectricTestRunner::class)
class ClientQualityGateTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private fun t(id: Int, vararg args: Any) = context.getString(id, *args)
    private val today = LocalDate.now()

    private data class Violation(val screen: String, val rule: String, val detail: String) {
        val key get() = "$screen/$rule"
    }

    private data class KnownGap(val screen: String, val rule: String, val id: String, val reason: String) {
        val key get() = "$screen/$rule"
    }

    companion object {
        const val MAX_ACTIONS = 5
        const val MIN_TOUCH_DP = 48f
        /** Sanat, jotka luovat kiirettä tai epäonnistumisen tunnetta asiakkaalle. */
        val ALARMING = listOf("myöhässä", "virhe", "epäonnistui", "väärin", "kiire", "nopeasti", "huono")

        /**
         * Tunnetut poikkeamat (laatuportin ensimmäinen ajo 14.11.2026). Korjataan tutkielman
         * tunnisteen mukaan; poista rivi, kun korjattu.
         */
        private val KNOWN_GAPS = listOf(
            KnownGap("ajastin", "R5", "A1/A2", "Ajastimessa ei ole Tarvitsen tauon -painiketta; tulee siirtymien yhteydessä"),
            KnownGap("tapahtumakortti", "R5", "A1/A2", "Kestävän tapahtuman kortissa ei ole Tarvitsen tauon -painiketta")
        )
    }

    // --- Näkymät asiakkaan roolissa ---------------------------------------------------------

    @Composable
    private fun AsClient(fontScale: Float = 1f, content: @Composable () -> Unit) {
        val access = CaregiverAccess(
            state = CaregiverAccessState(lockEnabled = true, sessionOpen = false),
            requestUnlock = {},
            openSetup = {}, offerLockOnce = {}, disableLock = {}, lockNow = {}, forgotPin = {},
            deviceSecure = true
        )
        val base = LocalDensity.current
        CompositionLocalProvider(
            LocalCaregiver provides access,
            LocalDensity provides Density(base.density, fontScale)
        ) {
            Column(Modifier.verticalScroll(rememberScrollState())) { content() }
        }
    }

    private val outdoors get() = t(R.string.card_outdoors)
    private val rest get() = t(R.string.card_rest)

    /** Tänään klo 00.00 ulkoilu (muuttuu) ja klo 23.30 ennallaan oleva tapahtuma. */
    private fun dayWithChange(): EventChange {
        val day = today.dayOfWeek.value
        WeekStore(context).save(
            WeekPlan(
                listOf(
                    WeekEvent("ulkoilu", day, 0, "00:00", EventKind.CARD, cardName = outdoors, cardPhoto = "builtin:outdoors"),
                    WeekEvent("ilta", day, 0, "23:30", EventKind.CARD, cardName = rest, cardPhoto = "builtin:rest")
                )
            )
        )
        val change = EventChange(
            id = "gate-c1", date = today, eventId = "ulkoilu", type = ChangeType.REPLACE,
            cardName = rest, cardPhoto = "builtin:rest", reason = "Sataa", reveal = ChangeReveal.NOW
        )
        ChangeStore(context).upsert(change)
        return change
    }

    /** Toimintonäkymä: nimi, onko tauko vaadittu, ja sisältö. */
    private class Screen(val name: String, val needsBreak: Boolean, val content: @Composable () -> Unit)

    private fun screens(change: EventChange): List<Screen> =
        listOf(
            Screen("muutos", true) { ChangeScreen(change.id) {} },
            Screen("paivaohjelman-vaihe", true) {
                RunStepScreen(
                    taskName = t(R.string.card_dress), taskPhoto = "builtin:dress", description = "",
                    subSteps = listOf(t(R.string.substep_socks), t(R.string.substep_trousers)),
                    subStepIndex = 0, taskIndex = 0, taskCount = 3, time = null, next = null,
                    nextMode = RunPreferences.NEXT_CARD,
                    onToggleTimer = {}, onHelp = {}, onBreak = {}, onDone = {}, onMore = {}
                )
            },
            Screen("taukoviesti", false) {
                SupportMessageScreen(kind = SupportKind.BREAK, timerPaused = false, onContinue = {}, onClose = {})
            },
            Screen("ajastin", true) {
                TimerScreen(
                    remaining = 5 * 60_000L, duration = 10 * 60_000L, running = true,
                    activeTask = t(R.string.card_dress), firstPhoto = "builtin:dress", nextPhoto = "builtin:rest",
                    nextActivity = rest, onGoNext = {}, onPreset = {}, onStartPause = {}, onReset = {}
                )
            },
            Screen("tapahtumakortti", true) {
                EventCardScreen(name = outdoors, photo = "builtin:outdoors", doneAt = null, onDone = {}, ongoing = true)
            }
        )

    // --- Mittaus ----------------------------------------------------------------------------

    private fun SemanticsNode.label(): String {
        val text = config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty()
        val desc = config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ").orEmpty()
        return listOf(text, desc).filter { it.isNotBlank() }.joinToString(" / ")
    }

    private fun SemanticsNode.isClickable() = config.getOrNull(SemanticsActions.OnClick) != null

    private fun allNodes(): List<SemanticsNode> {
        val root = compose.onRoot().fetchSemanticsNode()
        val out = mutableListOf<SemanticsNode>()
        fun walk(n: SemanticsNode) { out += n; n.children.forEach(::walk) }
        walk(root)
        return out
    }

    /** Mittaa piirretyn näkymän rikkeet säännöistä R1–R6. */
    private fun measure(name: String, needsBreak: Boolean, fontScale: Float): List<Violation> {
        compose.waitForIdle()
        val nodes = allNodes()
        val density = compose.density.density
        val rootWidth = compose.onRoot().fetchSemanticsNode().size.width
        val clickable = nodes.filter { it.isClickable() }
        val v = mutableListOf<Violation>()
        val tag = if (fontScale > 1f) "$name@${(fontScale * 100).toInt()}%" else name

        if (fontScale == 1f) {
            clickable.forEach { n ->
                val w = n.size.width / density
                val h = n.size.height / density
                if (w < MIN_TOUCH_DP || h < MIN_TOUCH_DP) {
                    v += Violation(name, "R1", "'${n.label()}' ${w.toInt()}×${h.toInt()} dp")
                }
            }
            if (clickable.size > MAX_ACTIONS) {
                v += Violation(name, "R2", "${clickable.size} painettavaa: " + clickable.joinToString { "'${it.label()}'" })
            }
            clickable.filter { it.label().isBlank() }.forEach {
                v += Violation(name, "R3", "painettava ilman nimeä kohdassa ${it.boundsInRoot}")
            }
            val texts = nodes.flatMap { n -> n.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } }
            texts.forEach { text ->
                ALARMING.firstOrNull { text.contains(it, ignoreCase = true) }?.let {
                    v += Violation(name, "R4", "'$it' tekstissä '$text'")
                }
            }
            if (needsBreak) {
                val breakText = t(R.string.support_break)
                if (clickable.none { it.label().contains(breakText, ignoreCase = true) }) {
                    v += Violation(name, "R5", "ei painettavaa '$breakText'")
                }
            }
        } else {
            clickable.forEach { n ->
                val b = n.boundsInRoot
                if (b.left < -1f || b.right > rootWidth + 1f) {
                    v += Violation(tag, "R6", "'${n.label()}' valuu ruudun ulkopuolelle (${b.left.toInt()}–${b.right.toInt()} / $rootWidth px)")
                }
            }
        }
        return v
    }

    // --- Testit -----------------------------------------------------------------------------

    @Test
    fun `asiakkaan toimintonakymat noudattavat sovittuja periaatteita`() {
        // Compose-testissä piirretään kerran: näkymä ja tekstikoko vaihdetaan tilalla.
        val list = screens(dayWithChange())
        var index by mutableIntStateOf(0)
        var scale by mutableFloatStateOf(1f)
        compose.setContent {
            val screen = list[index]
            androidx.compose.runtime.key(index, scale) { AsClient(scale) { screen.content() } }
        }
        val found = list.indices.flatMap { i ->
            listOf(1f, 2f).flatMap { s ->
                compose.runOnIdle { index = i; scale = s }
                measure(list[i].name, list[i].needsBreak, s)
            }
        }
        val known = KNOWN_GAPS.map { it.key }.toSet()
        val fresh = found.filter { it.key !in known }
        val fixed = KNOWN_GAPS.filter { gap -> found.none { it.key == gap.key } }
        val report = buildString {
            appendLine("Laatuportti: ${found.size} havaintoa, joista uusia ${fresh.size}.")
            found.forEach { appendLine("  [${it.screen} ${it.rule}] ${it.detail}" + if (it.key in known) "  (tunnettu)" else "") }
            if (fixed.isNotEmpty()) appendLine("Korjattu, poista KNOWN_GAPS-listalta: " + fixed.joinToString { it.key })
        }
        println(report)
        assertTrue(report, fresh.isEmpty() && fixed.isEmpty())
    }

    @Test
    fun `muutosnakyman osat ovat aina samassa jarjestyksessa`() {
        val change = dayWithChange()
        compose.setContent { AsClient { ChangeScreen(change.id) {} } }
        compose.waitForIdle()
        val nodes = allNodes()
        fun topOf(text: String): Float = nodes.filter { it.label().contains(text) }.minOfOrNull { it.boundsInRoot.top }
            ?: error("Muutosnäkymästä puuttuu '$text'")
        val order = listOf(
            t(R.string.change_label),          // Mikä muuttuu
            "Sataa",                            // Miksi
            t(R.string.change_unchanged_title), // Mikä pysyy samana
            t(R.string.change_listen),          // Mitä voin tehdä: kuuntele,
            t(R.string.change_ok),              // kuittaa
            t(R.string.support_break)           // tai pyytää taukoa
        )
        val tops = order.map(::topOf)
        assertTrue("Järjestys rikki: " + order.zip(tops).joinToString(), tops.zipWithNext().all { (a, b) -> a < b })
    }

    @Test
    fun `muutos ei nay asiakkaalle ennen valittua aikaa`() {
        val later = LocalDateTime.now().plusHours(2)
        val day = later.dayOfWeek.value
        val time = "%02d:%02d".format(later.hour, later.minute)
        WeekStore(context).save(WeekPlan(listOf(WeekEvent("uinti", day, 0, time, EventKind.CARD, cardName = outdoors))))
        ChangeStore(context).upsert(
            EventChange(
                id = "piilossa", date = later.toLocalDate(), eventId = "uinti", type = ChangeType.CANCEL,
                reveal = ChangeReveal.AT_EVENT
            )
        )
        compose.setContent { AsClient { HomeScreen(open = {}) } }
        compose.waitForIdle()
        val visible = allNodes().any { it.label().contains(t(R.string.change_label)) }
        assertTrue("Tapahtuman aikaan näkyvä muutos näkyi jo etukäteen", !visible)
    }
}
