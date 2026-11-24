package fi.aikapolku.app

import android.content.Context
import android.view.WindowManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import fi.aikapolku.app.KaksiLaitetta.AINO
import fi.aikapolku.app.KaksiLaitetta.AINO_EVENT
import fi.aikapolku.app.KaksiLaitetta.AINO_EVENT_2
import fi.aikapolku.app.KaksiLaitetta.CHANGED_NAME
import fi.aikapolku.app.KaksiLaitetta.EERO
import fi.aikapolku.app.KaksiLaitetta.EERO_SECRET
import fi.aikapolku.app.KaksiLaitetta.FIRST_NAME
import fi.aikapolku.app.KaksiLaitetta.MESSAGE_1
import fi.aikapolku.app.KaksiLaitetta.MESSAGE_2
import fi.aikapolku.app.KaksiLaitetta.PLACE_NAME
import fi.aikapolku.app.KaksiLaitetta.Report
import fi.aikapolku.app.KaksiLaitetta.WAIT_PEER_MS
import fi.aikapolku.app.KaksiLaitetta.WAIT_STEP_MS
import fi.aikapolku.app.KaksiLaitetta.log
import fi.aikapolku.app.KaksiLaitetta.photo
import fi.aikapolku.app.KaksiLaitetta.waitUntil
import fi.aikapolku.app.data.EventKind
import fi.aikapolku.app.data.GroupStore
import fi.aikapolku.app.data.ProfileStore
import fi.aikapolku.app.data.UnitSetup
import fi.aikapolku.app.data.UnitSpec
import fi.aikapolku.app.data.WeekEvent
import fi.aikapolku.app.data.WeekPlan
import fi.aikapolku.app.data.WeekSchedule
import fi.aikapolku.app.data.WeekStore
import fi.aikapolku.app.link.LinkInbox
import fi.aikapolku.app.link.LinkPairing
import fi.aikapolku.app.link.LinkReceiverService
import fi.aikapolku.app.link.LinkStore
import fi.aikapolku.app.link.PlaceStatus
import fi.aikapolku.app.link.ResidentPackage
import fi.aikapolku.app.profiles.ProfileSession
import fi.aikapolku.app.week.EventReminders
import fi.aikapolku.app.week.GroupSession
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Kahden laitteen siirtotesti, paikan laitteen puoli (ks. [KaksiLaitetta]).
 *
 * Laitteen omat tiedot otetaan talteen ja palautetaan lopuksi (myös keskeytyneen ajon jälkeen).
 * Luodaan Aino ja Eero yhteiseen yksikköön, Ainolle tapahtuma omalla kuvalla. Sitten:
 *  1. hyväksytään asukkaan puhelimen liitos Ainolle
 *  2. odotetaan, että Ainon tiedot ovat perillä
 *  3. muutetaan Ainon päivää (uusi nimi ja uusi kuva); vain uusi kuva lähtee
 *  4. odotetaan muutoksen perillä-tilaa (puhelin odottaa ensin käynnissä olevan ohjelman loppuun)
 *  6. odotetaan asukkaan viesti, 7. Wi-Fin katkon aikana jonoon jäänyt viesti
 *  8. poistetaan liitos ja palvellaan vielä hetki, jotta puhelin voi todeta, ettei tietoja enää saa
 */
@RunWith(AndroidJUnit4::class)
class KaksiLaitettaPaikkaDeviceTest {
    private val ctx: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private var scenario: ActivityScenario<MainActivity>? = null

    @Test
    fun paikan_laite() {
        assumeTrue("Ajetaan vain kahden laitteen testinä (-e kaksi true)", KaksiLaitetta.requested())
        val app = ctx.applicationContext
        val report = Report("PAIKAN LAITE")
        log("paikan laite: alku")
        PrefsSnapshot.restoreLeftover(app)
        PrefsSnapshot.save(app)
        try {
            run(app, report)
        } catch (e: Throwable) {
            report.check("ajo keskeytyi", false, "${e.javaClass.simpleName}: ${e.message?.take(200)}")
        } finally {
            runCatching { LinkReceiverService.stop(app) }
            runCatching { scenario?.close() }
            Thread.sleep(1_500)
            PrefsSnapshot.restore(app)
            File(app.filesDir, KaksiLaitetta.TEST_DIR).deleteRecursively()
            runCatching { EventReminders.reschedule(app) }
            GroupSession.changed()
            ProfileSession.changed()
            log("paikan laite: tiedot palautettu")
        }
        log(report.text())
        println(report.text())
        assertEquals(report.text(), 0, report.failed)
    }

    private fun run(app: Context, r: Report) {
        // --- Valmistelu ---
        val aino = ProfileStore.add(app, AINO).id
        val eero = ProfileStore.add(app, EERO).id
        ProfileStore.list(app).filter { it.name.isBlank() }.forEach { ProfileStore.delete(app, it.id) }
        ProfileStore.switchTo(app, aino)

        val today = LocalDate.now()
        val week = WeekSchedule.weekOf(WeekPlan(), today)
        val now = LocalTime.now()
        val first = (now.hour * 60 + now.minute + 10).coerceAtMost(23 * 60 + 58)
        val second = (first + 30).coerceAtMost(23 * 60 + 59)
        fun time(m: Int) = WeekSchedule.formatTime(m / 60, m % 60)
        val photo1 = photo(app, "kuva1.png", KaksiLaitetta.COLOR_1)
        val event1 = WeekEvent(AINO_EVENT, today.dayOfWeek.value, week, time(first), EventKind.CARD, cardName = FIRST_NAME, cardPhoto = photo1)
        WeekStore(app, aino).save(WeekPlan(listOf(event1)))
        WeekStore(app, eero).save(
            WeekPlan(listOf(WeekEvent("kaksi-eero-1", today.dayOfWeek.value, week, time(first), EventKind.CARD, cardName = EERO_SECRET, cardPhoto = "builtin:music")))
        )
        var n = 0
        GroupStore(app).save(
            UnitSetup.build(UnitSpec("Testiyksikkö KAKSI", emptyList(), listOf(aino, eero), UnitSetup.defaultMeals { it }, emptyList())) { "kaksi-${n++}" }
        )
        GroupSession.changed()
        ProfileSession.changed()

        val store = LinkStore(app)
        store.setName(PLACE_NAME)
        store.placeEnabled = true
        // Sovellus etualalle: vastaanottopalvelu on etualan palvelu.
        scenario = ActivityScenario.launch(MainActivity::class.java).also { s ->
            s.onActivity { it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        }
        LinkReceiverService.start(app)
        r.note("valmis: Aino, Eero ja yksikkö; odotetaan asukkaan puhelimen liitospyyntöä")

        // --- 1. Liitos ---
        val asked = waitUntil(WAIT_PEER_MS, 500L) { LinkPairing.pending != null }
        r.check("1 liitospyyntö saapui", asked)
        if (!asked) return
        val request = LinkPairing.pending!!
        log("KOODI ${request.code}")
        r.note("koodi paikan laitteessa: ${request.code}")
        r.check("1 paikan laite kysyy, kenen puhelin", request.place)
        LinkPairing.answer(true, aino)
        val paired = waitUntil(15_000L, 500L) { store.peers().any { !it.receiver && it.resident == aino } }
        r.check("1 liitos tallentui Ainolle", paired)
        if (!paired) return
        val phoneId = store.peers().first { !it.receiver && it.resident == aino }.id

        // --- 2. Ainon tiedot perillä ---
        // Verrataan aina nykyiseen versioon (sovellus voi tallentaa oletusasetuksia myöhemmin).
        fun deliveredNow() = PlaceStatus.delivered(app, aino)?.version == ResidentPackage.version(app, aino)
        r.check("2 Ainon tiedot perillä", waitUntil(WAIT_STEP_MS) { deliveredNow() })
        val v1 = ResidentPackage.version(app, aino)

        // --- 3. Muutos: uusi nimi ja uusi kuva; vain uusi kuva lähtee ---
        val photo2 = photo(app, "kuva2.png", KaksiLaitetta.COLOR_2)
        WeekStore(app, aino).save(
            WeekPlan(
                listOf(
                    event1.copy(cardName = CHANGED_NAME),
                    WeekEvent(AINO_EVENT_2, today.dayOfWeek.value, week, time(second), EventKind.CARD, cardName = "Musiikki KAKSI", cardPhoto = photo2)
                )
            )
        )
        ProfileSession.changed()
        val known1 = setOf(ResidentPackage.sha256(File(photo1).readBytes()))
        val built = ResidentPackage.build(app, aino, known1)
        r.check("5 vain uusi kuva lähtee", built.mediaSent == 1 && built.mediaTotal == 2, "lähtee ${built.mediaSent}/${built.mediaTotal}")
        val v2 = ResidentPackage.version(app, aino)
        r.check("3 versio muuttui", v2 != v1)

        // --- 4. Muutos perillä (puhelin odottaa ensin ohjelman loppuun) ---
        r.check("4 muutos perillä", waitUntil(WAIT_STEP_MS * 2) { deliveredNow() })

        // --- 6–7. Viestit ---
        r.check("6 asukkaan viesti saapui", waitUntil(WAIT_STEP_MS) { LinkInbox.today(app).any { it.text == MESSAGE_1 } })
        r.check(
            "7 Wi-Fin katkon aikana jonoon jäänyt viesti saapui",
            waitUntil(WAIT_STEP_MS * 2) { LinkInbox.today(app).any { it.text == MESSAGE_2 } }
        )
        val messages = LinkInbox.today(app).count { it.text == MESSAGE_1 || it.text == MESSAGE_2 }
        r.check("7 kumpikin viesti vain kerran", messages == 2, "$messages kpl")

        // --- 8. Liitoksen poisto ---
        r.check("8 liitos poistettu", store.removePeer(phoneId, receiver = false) && store.peers().none { it.id == phoneId })
        r.note("palvellaan vielä 2 min, jotta puhelin voi todeta poiston")
        Thread.sleep(120_000L)
    }
}
