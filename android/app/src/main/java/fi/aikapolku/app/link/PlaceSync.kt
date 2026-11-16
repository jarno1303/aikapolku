package fi.aikapolku.app.link

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Paikan laitteella (A5): mikä asukkaan tietojen versio on viimeksi toimitettu hänen puhelimeensa
 * ja milloin. Ohjaaja näkee asukkaittain, onko viimeisin muutos perillä.
 */
internal object PlaceStatus {
    private const val PREFS = "aikapolku_place_status_v1"

    internal data class Delivery(val version: String, val at: Long)

    var revision by mutableIntStateOf(0)
        private set

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun delivered(context: Context, profileId: String): Delivery? {
        val o = runCatching { JSONObject(prefs(context).getString(profileId, null) ?: return null) }.getOrNull() ?: return null
        return Delivery(o.optString("v"), o.optLong("at"))
    }

    fun markDelivered(context: Context, profileId: String, version: String, at: Long) {
        prefs(context).edit().putString(profileId, JSONObject().put("v", version).put("at", at).toString()).apply()
        revision++
    }

    /** Paikan laitteen vastaus asukkaan puhelimen hakuun (LinkReceiverService). */
    fun serve(context: Context, peer: Peer, version: String, known: Set<String>, now: Long = System.currentTimeMillis()): Pair<String, ByteArray?>? {
        val profileId = peer.resident.takeIf { it.isNotBlank() } ?: return null
        if (fi.aikapolku.app.data.ProfileStore.list(context).none { it.id == profileId }) return null
        val current = ResidentPackage.version(context, profileId)
        val bytes = if (version == current) null else ResidentPackage.build(context, profileId, known).bytes
        markDelivered(context, profileId, current, now)
        return current to bytes
    }
}

/**
 * Asukkaan puhelimessa (A5): hakee omat tietonsa liitetyiltä paikan laitteilta. Sovelluksen
 * ollessa auki noin minuutin välein ja heti avattaessa. Käynnissä oleva päiväohjelma ei vaihdu
 * kesken: uudet tiedot puretaan, kun ohjelma on päättynyt (enintään [MAX_DEFER] kierrosta).
 */
internal object PlaceSync {
    const val INTERVAL_MS = 60_000L
    private const val MAX_DEFER = 20
    private const val PREFS = "aikapolku_place_sync_v1"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val running = Mutex()
    @Volatile private var loop: Job? = null
    @Volatile private var deferred = 0

    /** Viimeisin onnistunut haku (näytetään ohjaajalle asukkaan puhelimen asetuksissa). */
    var revision by mutableIntStateOf(0)
        private set

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun version(context: Context, placeId: String): String = prefs(context).getString("v_$placeId", "") ?: ""
    fun lastSync(context: Context, placeId: String): Long = prefs(context).getLong("at_$placeId", 0L)

    private fun places(context: Context) = LinkStore(context).peers().filter { it.receiver && it.place }

    /** Sovellus avattiin: haku heti ja sitten minuutin välein, kunnes sovellus suljetaan. */
    @Synchronized
    fun start(context: Context) {
        val app = context.applicationContext
        if (places(app).isEmpty() || loop?.isActive == true) return
        loop = scope.launch {
            while (isActive) {
                runCatching { syncNow(app) }
                delay(INTERVAL_MS)
            }
        }
    }

    @Synchronized
    fun stop() {
        loop?.cancel()
        loop = null
    }

    /** Onko asukkaalla päiväohjelma tai ajastin kesken (uudet tiedot odottavat sen loppuun). */
    private fun busy(context: Context): Boolean =
        fi.aikapolku.app.programs.run.PrefsRunProgress(context).programId.isNotBlank() ||
            fi.aikapolku.app.timer.BackgroundTimer.anyRunning(context)

    /** Hakee kaikilta paikoilta; palauttaa purettujen pakettien määrän. */
    suspend fun syncNow(context: Context): Int = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        if (!running.tryLock()) return@withContext 0
        try {
            val store = LinkStore(app)
            val me = store.identity
            var applied = 0
            var found: List<FoundReceiver>? = null
            places(app).forEach { peer ->
                val known = ResidentPackage.known(app)
                fun attempt(host: String, port: Int) =
                    if (host.isBlank()) null
                    else runCatching { LinkProtocol.pull(host, port, me, peer, version(app, peer.id), known) }.getOrNull()
                var result = attempt(peer.host, peer.port)
                if (result == null) {
                    // Osoite voi vaihtua (DHCP): haetaan verkosta.
                    val at = (found ?: LinkDiscovery.discover(app).also { found = it }).firstOrNull { it.id == peer.id }
                    if (at != null) {
                        result = attempt(at.host, at.port)
                        if (result != null) store.updateAddress(peer.id, at.host, at.port)
                    }
                }
                val pulled = result ?: return@forEach
                val now = System.currentTimeMillis()
                if (pulled.bytes != null) {
                    if (busy(app) && deferred < MAX_DEFER) {
                        deferred++
                        return@forEach
                    }
                    deferred = 0
                    runCatching { ResidentPackage.apply(app, pulled.bytes, peer.id) }.getOrNull() ?: return@forEach
                    applied++
                }
                prefs(app).edit().putString("v_${peer.id}", pulled.version).putLong("at_${peer.id}", now).apply()
            }
            withContext(Dispatchers.Main) {
                revision++
                if (applied > 0) {
                    // Näkymät lukevat tiedot uudelleen (kuten profiilin vaihtuessa).
                    fi.aikapolku.app.profiles.ProfileSession.changed()
                    fi.aikapolku.app.settings.SettingsRevision.bump()
                }
            }
            applied
        } finally {
            running.unlock()
        }
    }
}
