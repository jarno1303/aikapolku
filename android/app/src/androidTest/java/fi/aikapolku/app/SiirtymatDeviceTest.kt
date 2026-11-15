package fi.aikapolku.app

import android.content.Context
import android.os.SystemClock
import android.view.WindowManager
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import fi.aikapolku.app.caregiver.RoleSession
import fi.aikapolku.app.data.AckStore
import fi.aikapolku.app.data.CardStore
import fi.aikapolku.app.data.ChangeReveal
import fi.aikapolku.app.data.ChangeStore
import fi.aikapolku.app.data.ChangeType
import fi.aikapolku.app.data.DemoDay
import fi.aikapolku.app.data.EventChange
import fi.aikapolku.app.data.EventKind
import fi.aikapolku.app.data.FirstThenSelection
import fi.aikapolku.app.data.MoreTime
import fi.aikapolku.app.data.ProfileStore
import fi.aikapolku.app.data.TimeDisplayPrefs
import fi.aikapolku.app.data.WeekEvent
import fi.aikapolku.app.data.WeekPlan
import fi.aikapolku.app.data.WeekStore
import fi.aikapolku.app.timer.BackgroundTimer
import fi.aikapolku.app.week.EventReminders
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.AfterClass
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/*
 * Puhelimessa ajettavat siirtymätestit (tutkielma A1–A4), näkyvät ruudulla.
 * Jokainen luokka käyttää omaa väliaikaista asiakastaan tunnetuilla asetuksilla ja poistaa sen
 * lopuksi; muut tiedot eivät muutu. Aja: aja-aikapolku.ps1 -branch <haara> -laitetesti.
 */

private val targetContext: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

/** Yhteiset apufunktiot: odotus oikeassa ajassa (ajastimet laskevat laitteen kellolla). */
private class Ui(private val compose: AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>) {
    fun t(id: Int, vararg args: Any) = compose.activity.getString(id, *args)

    fun shown(matcher: SemanticsMatcher) = compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()
    fun shownText(text: String) = shown(hasText(text, substring = true))

    fun keepScreenOn() {
        val activity = compose.activity
        compose.runOnUiThread { activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    /** Tauko, jotta kulun ehtii nähdä (ja Valmis-painikkeen 1,5 s esto ohittuu). */
    fun pause(ms: Long = 1_500L) {
        Thread.sleep(ms)
        compose.mainClock.advanceTimeBy(ms)
        compose.waitForIdle()
    }

    fun waitFor(timeoutMs: Long, failure: String, condition: () -> Boolean) {
        val end = SystemClock.elapsedRealtime() + timeoutMs
        while (true) {
            val ok = try {
                compose.mainClock.advanceTimeBy(500L)
                compose.waitForIdle()
                condition()
            } catch (e: IllegalStateException) {
                throw AssertionError(
                    "Sovellus poistui ruudulta kesken testin (näyttö sammui, puhelin lukittui tai " +
                        "lupakysely/asetussivu avautui päälle). Kohta: $failure", e
                )
            }
            if (ok) return
            if (SystemClock.elapsedRealtime() > end) throw AssertionError(failure)
            Thread.sleep(500L)
        }
    }

    fun waitForText(text: String, timeoutMs: Long = 10_000L) =
        waitFor(timeoutMs, "Tekstiä \"$text\" ei näkynyt ${timeoutMs / 1000} sekunnissa.") { shownText(text) }

    /** Napauttaa ensimmäistä painettavaa kohtaa, jossa on teksti. */
    fun click(text: String) {
        val node = compose.onAllNodes(hasText(text, substring = true) and hasClickAction()).onFirst()
        // Vieritys vain, jos näkymä vierii (päiväohjelman pystytilassa ei vieritystä).
        runCatching { node.performScrollTo() }
        node.performClick()
    }

    fun timerRunning() =
        shown(hasContentDescription(t(R.string.pysayta_aika), substring = true)) ||
            shown(hasContentDescription(t(R.string.timer_pause_locked), substring = true)) ||
            shown(hasContentDescription(t(R.string.tauko_vaatii_ohjaajan_pin_koodin), substring = true))
}

/** Väliaikainen asiakas ja testitila; palauttaa alkuperäiset lopuksi. */
private object TestProfile {
    var original = ""
    var id = ""
    var testMode = true

    fun create(name: String) {
        val ctx = targetContext
        original = ProfileStore.currentId(ctx)
        ProfileStore.list(ctx).filter { it.name == name }.forEach { ProfileStore.delete(ctx, it.id) }
        id = ProfileStore.add(ctx, name).id
        ProfileStore.switchTo(ctx, id)
        RoleSession.load(ctx)
        testMode = RoleSession.testMode
        RoleSession.setTestMode(ctx, true)
        // Ensin–Sitten-ajastimen tila on laitteen yhteinen: aloitetaan puhtaalta pöydältä.
        BackgroundTimer.stop(ctx, "timer", 300_000L)
    }

    fun remove() {
        val ctx = targetContext
        BackgroundTimer.stop(ctx, "timer", 300_000L)
        ProfileStore.switchTo(ctx, original)
        ProfileStore.delete(ctx, id)
        RoleSession.setTestMode(ctx, testMode)
        RoleSession.toClient()
        EventReminders.reschedule(ctx)
    }
}

/**
 * Ensin–Sitten-ajastin ja "Tarvitsen vielä hetken" (A1, A2) koko kulkuna:
 * ohjaaja valmistelee → valmistelu säilyy, kun Ajastin avataan → Anna asiakkaalle →
 * asiakas: Aloita (aika alkaa heti, ei lupakyselyä) → tauko ja jatka → ennakkovaroitus ja
 * lisäaika → toinen pyyntö: viesti aikuiselle (aika jatkuu) → aika loppuu → pyyntö: viesti
 * (ei uutta lisäaikaa) → Siirry seuraavaan. Asetukset: varoitus 2 min, lisäaika 2 min kerran.
 * Kesto noin 8 minuuttia (5 min ajastus + 2 min lisäaika).
 */
@RunWith(AndroidJUnit4::class)
class EnsinSittenLisaaikaDeviceTest {
    companion object {
        @BeforeClass
        @JvmStatic
        fun setUp() {
            val ctx = targetContext
            TestProfile.create("Automaattitesti lisäaika")
            CardStore(ctx, TestProfile.id).saveSelection(
                FirstThenSelection(
                    firstName = ctx.getString(R.string.card_crafts), firstPhoto = "builtin:crafts",
                    nextName = ctx.getString(R.string.card_lunch), nextPhoto = "builtin:lunch"
                )
            )
            TimeDisplayPrefs.setWarnMinutes(ctx, 2)
            MoreTime.setMinutes(ctx, 2)
            MoreTime.setCount(ctx, 1)
            RoleSession.toCaregiver()
        }

        @AfterClass
        @JvmStatic
        fun tearDown() = TestProfile.remove()
    }

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()
    private val ui by lazy { Ui(compose) }

    @Before
    fun screenOn() = ui.keepScreenOn()

    @Test
    fun ohjaaja_valmistelee_ja_asiakas_saa_lisaajan_rajoissa() {
        val first = ui.t(R.string.card_crafts)
        val next = ui.t(R.string.card_lunch)
        val moreTime = ui.t(R.string.support_more_time)

        // 1. Ohjaaja: Valmistele asiakkaalle → Ensin–Sitten → Siirry kellotauluun.
        ui.waitForText(ui.t(R.string.caregiver_prepare))
        ui.click(ui.t(R.string.ensin_sitten))
        ui.waitForText(ui.t(R.string.siirry_kellotauluun))
        ui.pause()
        ui.click(ui.t(R.string.siirry_kellotauluun))
        ui.waitForText(ui.t(R.string.timer_hand_to_client))
        assertTrue("Ensin-kortti näkyy kellotaulun yllä", ui.shownText(first))

        // 2. Valmistelu säilyy, vaikka ohjaaja käy etusivulla ja avaa Ajastimen (puhelintesti 15.11.).
        Espresso.pressBack()
        ui.pause()
        Espresso.pressBack()
        ui.waitForText(ui.t(R.string.caregiver_prepare))
        ui.click(ui.t(R.string.timer_title))
        ui.waitForText(ui.t(R.string.timer_hand_to_client))
        assertTrue("Valmisteltu Ensin–Sitten katosi, kun Ajastin avattiin", ui.shownText(first))

        // 3. Anna asiakkaalle: asiakkaan NYT-kortti ja Aloita.
        ui.pause()
        ui.click(ui.t(R.string.timer_hand_to_client))
        ui.waitForText(ui.t(R.string.now_label))
        assertTrue("NYT-kortissa valmisteltu toiminto", ui.shownText(first))
        ui.pause()
        ui.click(ui.t(R.string.today_start))

        // 4. Aika alkaa heti ensimmäisellä painalluksella ilman lupakyselyä.
        ui.waitFor(6_000L, "Aika ei alkanut asiakkaan ensimmäisellä Aloita-painalluksella.") { ui.timerRunning() }
        assertFalse("Tarvitsen vielä hetken näkyi heti alussa", ui.shownText(moreTime))

        // 5. Tarvitsen tauon pysäyttää ajan ilman PIN-koodia, Jatka käynnistää sen.
        ui.pause()
        ui.click(ui.t(R.string.support_break))
        ui.waitForText(ui.t(R.string.ajastin_on_pysaytetty))
        ui.pause()
        ui.click(ui.t(R.string.support_continue))
        ui.waitFor(6_000L, "Jatka ei käynnistänyt aikaa tauon jälkeen.") { ui.timerRunning() }

        // 6. Ennakkovaroitus ja lisäaikapainike, kun aikaa on 2 min (noin 3 min kuluttua).
        ui.waitFor(220_000L, "\"Pian vaihtuu\" ja \"$moreTime\" eivät tulleet näkyviin 2 minuuttia ennen loppua.") {
            ui.shownText(ui.t(R.string.transition_soon_title)) && ui.shownText(moreTime)
        }
        assertTrue("Ennakkovaroitus kertoo seuraavan", ui.shownText(ui.t(R.string.run_next_line, next)))

        // 7. Lisäaika ilman PIN-koodia: vahvistus, ja painike väistyy (aikaa taas yli 2 min).
        ui.click(moreTime)
        ui.waitForText(ui.t(R.string.more_time_granted, 2), 4_000L)
        ui.waitFor(9_000L, "Vahvistus ei hävinnyt 5 sekunnissa.") { !ui.shownText(ui.t(R.string.more_time_granted, 2)) }
        assertFalse("Painike jäi näkyviin, vaikka aikaa lisättiin", ui.shownText(moreTime))
        assertTrue("Aika pysähtyi lisäajasta", ui.timerRunning())

        // 8. Painike palaa lopussa; toinen pyyntö = viesti aikuiselle, aika jatkuu.
        ui.waitFor(150_000L, "\"$moreTime\" ei palannut näkyviin lisäajan lopussa.") { ui.shownText(moreTime) }
        ui.click(moreTime)
        ui.waitForText(ui.t(R.string.more_time_show_adult, ui.t(R.string.helper_caregiver_to)), 4_000L)
        ui.pause(2_000L)
        ui.click(ui.t(R.string.support_continue))
        ui.waitFor(6_000L, "Aika ei jatkunut viestin aikana.") { ui.timerRunning() || ui.shownText(ui.t(R.string.siirry_seuraavaan)) }

        // 9. Aika loppuu: valmis-näkymässä Siirry seuraavaan ja Tarvitsen vielä hetken.
        ui.waitFor(150_000L, "Valmis-näkymä ei tullut ajan loputtua.") { ui.shownText(ui.t(R.string.siirry_seuraavaan)) }
        assertTrue("Lisäaikapainike puuttuu valmis-näkymästä", ui.shownText(moreTime))

        // 10. Lisäaika on käytetty: pyyntö avaa viestin eikä käynnistä aikaa uudelleen.
        ui.pause()
        ui.click(moreTime)
        ui.waitFor(4_000L, "Käytetty lisäaika: viestiä aikuiselle ei tullut (aika käynnistyi uudelleen?).") {
            ui.shownText(ui.t(R.string.more_time_show_adult, ui.t(R.string.helper_caregiver_to)))
        }
        ui.pause(2_000L)
        ui.click(ui.t(R.string.support_continue))
        ui.waitForText(ui.t(R.string.siirry_seuraavaan))
        assertFalse("Aika käynnistyi uudelleen, vaikka lisäaika oli käytetty", ui.timerRunning())

        // 11. Siirry seuraavaan: Sitten-toiminto.
        ui.click(ui.t(R.string.siirry_seuraavaan))
        ui.waitForText(next)
        ui.pause(2_000L)
    }
}

/**
 * Päiväohjelman kestotehtävä ja lisäaika (A2): esittelypäivän hampaiden pesu (1 min).
 * Painike tulee viimeisellä puoliskolla, asiakkaan "Näytä koko ohjelma" väistyy, lisäaika
 * tulee ilman PIN-koodia ja Valmis etenee heti lisäajan jälkeen. Kesto noin 1,5 minuuttia.
 */
@RunWith(AndroidJUnit4::class)
class PaivaohjelmaLisaaikaDeviceTest {
    companion object {
        @BeforeClass
        @JvmStatic
        fun setUp() {
            val ctx = targetContext
            TestProfile.create("Automaattitesti ohjelma")
            DemoDay.fill(ctx, TestProfile.id)
            MoreTime.setMinutes(ctx, 2)
            MoreTime.setCount(ctx, 1)
            RoleSession.toClient()
        }

        @AfterClass
        @JvmStatic
        fun tearDown() = TestProfile.remove()
    }

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()
    private val ui by lazy { Ui(compose) }

    @Before
    fun screenOn() = ui.keepScreenOn()

    @Test
    fun kestotehtavan_lisaaika_ja_valmis_heti_sen_jalkeen() {
        val moreTime = ui.t(R.string.support_more_time)

        ui.waitForText(ui.t(R.string.demo_program_name))
        ui.pause()
        ui.click(ui.t(R.string.today_start))
        ui.waitForText(ui.t(R.string.program_start_count, 3))
        ui.pause()
        ui.click(ui.t(R.string.today_start))

        // Aamupala: Valmis.
        ui.waitForText(ui.t(R.string.card_breakfast))
        ui.pause(2_000L)
        ui.click(ui.t(R.string.run_done_button))

        // Hampaiden pesu: aika alkaa itsestään; painike viimeisellä puoliskolla (30 s).
        ui.waitForText(ui.t(R.string.card_brush_teeth))
        ui.waitFor(6_000L, "Hampaiden pesun aika ei alkanut itsestään.") { ui.timerRunning() }
        assertFalse("Lisäaikapainike näkyi heti alussa", ui.shownText(moreTime))
        ui.waitFor(50_000L, "\"$moreTime\" ei tullut näkyviin tehtävän lopussa.") { ui.shownText(moreTime) }
        assertFalse(
            "Asiakkaan Näytä koko ohjelma ei väistynyt (enintään viisi painiketta)",
            ui.shown(hasText(ui.t(R.string.nayta_koko_ohjelma)) and hasClickAction())
        )

        // Lisäaika ja Valmis heti perään: ei odotusta eikä "Aikaa on vielä" -kyselyä.
        ui.click(moreTime)
        ui.waitForText(ui.t(R.string.more_time_granted, 2), 4_000L)
        ui.pause(2_000L)
        ui.click(ui.t(R.string.run_done_button))
        ui.waitFor(5_000L, "Valmis ei edennyt heti lisäajan jälkeen.") { ui.shownText(ui.t(R.string.card_dress)) }

        // Pukeutuminen: Valmis → Kaikki valmista.
        ui.pause(2_000L)
        ui.click(ui.t(R.string.run_done_button))
        ui.waitForText(ui.t(R.string.kaikki_valmista))
        ui.pause(2_000L)
    }
}

/**
 * Muutoskortti asiakkaalle (A3): etusivun MUUTOS-rivi → muutosnäkymä, jossa syy ja
 * "Pysyy samana", tauko ja jatka, Selvä kuittaa ja palaa etusivulle. Kesto alle minuutti.
 */
@RunWith(AndroidJUnit4::class)
class MuutoskorttiDeviceTest {
    companion object {
        private const val CHANGE_ID = "laitetesti-muutos"
        private const val REASON = "Sataa"

        @BeforeClass
        @JvmStatic
        fun setUp() {
            val ctx = targetContext
            TestProfile.create("Automaattitesti muutos")
            // Tunnin päästä ulkoilu (muuttuu lepoon), kahden tunnin päästä iltapala (pysyy samana).
            val now = LocalDateTime.now()
            val day = now.dayOfWeek.value
            fun at(hours: Long): String {
                val t = now.plusHours(hours)
                return if (t.toLocalDate() == now.toLocalDate()) "%02d:%02d".format(t.hour, t.minute) else "23:5${hours}"
            }
            WeekStore(ctx).save(
                WeekPlan(
                    listOf(
                        WeekEvent("ulkoilu", day, 0, at(1), EventKind.CARD, cardName = ctx.getString(R.string.card_outdoors), cardPhoto = "builtin:outdoors"),
                        WeekEvent("iltapala", day, 0, at(2), EventKind.CARD, cardName = ctx.getString(R.string.card_evening_snack), cardPhoto = "builtin:evening_snack")
                    )
                )
            )
            ChangeStore(ctx).upsert(
                EventChange(
                    id = CHANGE_ID, date = LocalDate.now(), eventId = "ulkoilu", type = ChangeType.REPLACE,
                    cardName = ctx.getString(R.string.card_rest), cardPhoto = "builtin:rest",
                    reason = REASON, reveal = ChangeReveal.NOW
                )
            )
            RoleSession.toClient()
        }

        @AfterClass
        @JvmStatic
        fun tearDown() = TestProfile.remove()
    }

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()
    private val ui by lazy { Ui(compose) }

    @Before
    fun screenOn() = ui.keepScreenOn()

    @Test
    fun asiakas_avaa_muutoksen_pyytaa_tauon_ja_kuittaa() {
        val label = ui.t(R.string.change_label)
        ui.waitForText(label)
        ui.pause()
        ui.click(label)

        // Muutosnäkymä: syy ja Pysyy samana (iltapala).
        ui.waitForText(ui.t(R.string.change_ok))
        assertTrue("Syy puuttuu muutosnäkymästä", ui.shownText(REASON))
        assertTrue("Pysyy samana puuttuu", ui.shownText(ui.t(R.string.change_unchanged_title)))
        assertTrue("Ennallaan oleva iltapala puuttuu", ui.shownText(ui.t(R.string.card_evening_snack)))

        // Tarvitsen tauon ja Jatka palaavat muutokseen.
        ui.pause()
        ui.click(ui.t(R.string.support_break))
        ui.waitForText(ui.t(R.string.support_continue))
        ui.pause()
        ui.click(ui.t(R.string.support_continue))
        ui.waitForText(ui.t(R.string.change_ok))

        // Selvä kuittaa ja palaa etusivulle.
        ui.pause()
        ui.click(ui.t(R.string.change_ok))
        ui.waitForText(ui.t(R.string.now_label))
        ui.pause()
        assertNotNull(
            "Selvä ei kuitannut muutosta",
            AckStore(targetContext, TestProfile.id).ackTime(LocalDate.now(), "change:$CHANGE_ID")
        )
    }
}
