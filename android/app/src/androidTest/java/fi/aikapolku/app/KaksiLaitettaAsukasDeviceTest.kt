package fi.aikapolku.app

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import fi.aikapolku.app.KaksiLaitetta.AINO
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
import fi.aikapolku.app.KaksiLaitetta.shell
import fi.aikapolku.app.KaksiLaitetta.waitUntil
import fi.aikapolku.app.data.ProfileStore
import fi.aikapolku.app.data.WeekStore
import fi.aikapolku.app.link.ClientMessages
import fi.aikapolku.app.link.FoundReceiver
import fi.aikapolku.app.link.LinkDiscovery
import fi.aikapolku.app.link.LinkProtocol
import fi.aikapolku.app.link.LinkStore
import fi.aikapolku.app.link.Peer
import fi.aikapolku.app.link.PlaceSync
import fi.aikapolku.app.link.ResidentPackage
import fi.aikapolku.app.link.SendResult
import fi.aikapolku.app.profiles.ProfileSession
import fi.aikapolku.app.programs.run.PrefsRunProgress
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Kahden laitteen siirtotesti, asukkaan puhelimen puoli (ks. [KaksiLaitetta]).
 *
 * Puhelimen omat tiedot otetaan talteen ja palautetaan lopuksi; testin tuomat kuvat poistetaan.
 *  1. löydetään paikan laite verkosta ja liitytään siihen
 *  2. haetaan Ainon tiedot: oma tapahtuma ja kuva tulevat, Eeron tietoja ei ole missään
 *  3–4. kun paikan laitteella on muutos, käynnissä oleva ohjelma ei vaihdu kesken; ohjelman
 *     jälkeen muutos ja uusi kuva tulevat
 *  6. viesti ohjaajalle menee perille
 *  7. Wi-Fi pois: haku ei kaadu, viesti jää jonoon; Wi-Fi takaisin: viesti lähtee, haku toimii
 *  8. paikan laite poistaa liitoksen: tietoja ei enää saa
 */
@RunWith(AndroidJUnit4::class)
class KaksiLaitettaAsukasDeviceTest {
    private val ctx: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun asukkaan_puhelin() {
        assumeTrue("Ajetaan vain kahden laitteen testinä (-e kaksi true)", KaksiLaitetta.requested())
        val app = ctx.applicationContext
        val report = Report("ASUKKAAN PUHELIN")
        log("asukkaan puhelin: alku")
        PrefsSnapshot.restoreLeftover(app)
        PrefsSnapshot.save(app)
        val syncDir = File(app.filesDir, "sync")
        val before = syncDir.list()?.toSet().orEmpty()
        wifi(true)
        try {
            run(app, report, syncDir, before)
        } catch (e: Throwable) {
            report.check("ajo keskeytyi", false, "${e.javaClass.simpleName}: ${e.message?.take(200)}")
        } finally {
            wifi(true)
            PlaceSync.stop()
            Thread.sleep(1_000)
            PrefsSnapshot.restore(app)
            syncDir.listFiles()?.filter { it.name !in before }?.forEach { it.delete() }
            // Muistutukset ja kotinäytön widget takaisin puhelimen omiin tietoihin.
            runCatching { fi.aikapolku.app.week.EventReminders.reschedule(app) }
            ProfileSession.changed()
            log("asukkaan puhelin: tiedot palautettu")
        }
        log(report.text())
        println(report.text())
        assertEquals(report.text(), 0, report.failed)
    }

    private fun wifi(on: Boolean) {
        val state = if (on) "enabled" else "disabled"
        val out = runCatching { shell("cmd wifi set-wifi-enabled $state") }.getOrDefault("")
        if (out.contains("Unknown", ignoreCase = true) || out.contains("Exception")) {
            runCatching { shell("svc wifi ${if (on) "enable" else "disable"}") }
        }
    }

    /** Onko Wi-Fi-yhteys (myös silloin, kun oletusverkko on mobiilidata). */
    @Suppress("DEPRECATION")
    private fun onWifi(): Boolean {
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        return cm.allNetworks.any { cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true }
    }

    /** Kaikki asetustiedostot tekstinä (vuototarkistus: Eeron tietoja ei saa olla). */
    private fun prefsText(app: Context): String =
        File(app.applicationInfo.dataDir, "shared_prefs").listFiles()
            // Laiteliitosten salattu tieto ohitetaan (satunnainen base64 voisi sisältää "Eero").
            ?.filterNot { it.name.startsWith(LinkStore.PREFS) }
            ?.joinToString("\n") { runCatching { it.readText() }.getOrDefault("") }.orEmpty()

    private fun newFiles(syncDir: File, before: Set<String>) =
        syncDir.listFiles()?.count { it.name !in before && !it.name.endsWith(".tmp") } ?: 0

    private fun findPlace(app: Context): FoundReceiver? =
        runBlocking { LinkDiscovery.discover(app) }.firstOrNull { it.name.contains(PLACE_NAME) }

    private fun run(app: Context, r: Report, syncDir: File, before: Set<String>) {
        val store = LinkStore(app)
        store.setName("Ainon puhelin KAKSI")
        r.check("0 puhelin on Wi-Fissä", waitUntil(60_000L) { onWifi() })

        // --- 1. Paikan laite verkosta ja liitos ---
        var place: FoundReceiver? = null
        val found = waitUntil(WAIT_PEER_MS, 3_000L) { findPlace(app)?.also { place = it } != null }
        r.check("1 paikan laite löytyi verkosta", found, place?.host.orEmpty())
        val at = place ?: return
        val peer = runCatching {
            LinkProtocol.pair(at.host, at.port, store.identity) { code ->
                log("KOODI $code")
                r.note("koodi asukkaan puhelimessa: $code")
            }
        }.onFailure { r.note("liitosvirhe: ${it.javaClass.simpleName}: ${it.message}") }.getOrNull()
        val saved = peer != null && store.addPeer(peer)
        r.check("1 liitos hyväksyttiin ja tallentui", saved)
        r.check("1 liitetty laite on paikan laite", peer?.place == true)
        if (!saved || peer == null) return

        // --- 2. Ainon tiedot ---
        val applied = runBlocking { PlaceSync.syncNow(app) }
        r.check("2 Ainon tiedot haettiin", applied >= 1, "purettu $applied")
        val events = WeekStore(app).load().events
        r.check("2 Ainon tapahtuma näkyy", events.any { it.cardName == FIRST_NAME })
        r.check("2 profiilin nimi on Aino", ProfileStore.current(app).name == AINO, ProfileStore.current(app).name)
        val photo = events.firstOrNull { it.cardName == FIRST_NAME }?.cardPhoto.orEmpty()
        r.check("2 kuva siirtyi ja tapahtuma viittaa siihen", photo.isNotBlank() && File(photo).exists(), "uusia tiedostoja ${newFiles(syncDir, before)}")
        val text = prefsText(app)
        r.check("2 Eeron tietoja ei ole puhelimessa", !text.contains(EERO_SECRET) && !text.contains(EERO))
        // Vahvistushaku: paikan laite merkitsee perillä-tilan, kun puhelin kertoo omistavansa version.
        runBlocking { PlaceSync.syncNow(app) }

        // --- 3–4. Käynnissä oleva ohjelma ei vaihdu kesken ---
        val progress = PrefsRunProgress(app)
        progress.save("kaksi-kesken", 0, 0)
        fun current(): Peer = store.peers().first { it.receiver && it.place }
        val changed = waitUntil(WAIT_STEP_MS, 5_000L) {
            val p = current()
            val pulled = LinkProtocol.pull(p.host, p.port, store.identity, p, PlaceSync.version(app, p.id), ResidentPackage.known(app))
            pulled?.bytes != null
        }
        r.check("3 paikan laitteen muutos havaittiin", changed)
        val whileBusy = runBlocking { PlaceSync.syncNow(app) }
        r.check(
            "4 käynnissä oleva ohjelma: muutos ei tullut kesken",
            whileBusy == 0 && WeekStore(app).load().events.none { it.cardName == CHANGED_NAME }
        )
        progress.clear()
        val afterProgram = runBlocking { PlaceSync.syncNow(app) }
        r.check(
            "4 ohjelman jälkeen muutos tuli",
            afterProgram >= 1 && WeekStore(app).load().events.any { it.cardName == CHANGED_NAME },
            "purettu $afterProgram"
        )
        r.check("5 uusi kuva siirtyi (yhteensä 2)", newFiles(syncDir, before) == 2, "uusia tiedostoja ${newFiles(syncDir, before)}")
        runBlocking { PlaceSync.syncNow(app) }

        // --- 6. Viesti ohjaajalle ---
        val sent = runBlocking { ClientMessages.send(app, "help", MESSAGE_1) }
        r.check("6 viesti meni perille", sent == SendResult.DELIVERED, sent.name)

        // --- 7. Wi-Fi pois ja takaisin ---
        wifi(false)
        r.check("7 Wi-Fi pois", waitUntil(30_000L, 1_000L) { !onWifi() })
        val offline = runCatching { runBlocking { PlaceSync.syncNow(app) } }
        r.check("7 haku ilman Wi-Fiä ei kaadu eikä tuo mitään", offline.isSuccess && offline.getOrNull() == 0)
        val queued = runBlocking { ClientMessages.send(app, "help", MESSAGE_2) }
        r.check("7 viesti jäi jonoon", queued == SendResult.QUEUED, queued.name)
        wifi(true)
        r.check("7 Wi-Fi takaisin", waitUntil(90_000L, 1_000L) { onWifi() })
        val flushed = waitUntil(WAIT_STEP_MS, 5_000L) { runBlocking { ClientMessages.flush(app) } == 0 }
        r.check("7 jonossa ollut viesti lähti", flushed)
        r.check("7 haku toimii taas", runCatching { runBlocking { PlaceSync.syncNow(app) } }.isSuccess)

        // --- 8. Liitoksen poisto paikan laitteella ---
        val removed = waitUntil(WAIT_STEP_MS, 5_000L) {
            val visible = findPlace(app) != null
            val p = current()
            val pulled = runCatching { LinkProtocol.pull(p.host, p.port, store.identity, p, "", ResidentPackage.known(app)) }.getOrNull()
            visible && pulled == null
        }
        r.check("8 poistettu liitos: tietoja ei enää saa", removed)
    }
}
