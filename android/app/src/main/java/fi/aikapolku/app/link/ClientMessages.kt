package fi.aikapolku.app.link

import android.content.Context
import fi.aikapolku.app.R
import fi.aikapolku.app.data.ProfileScope
import fi.aikapolku.app.data.ProfileStore
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext

/**
 * Asiakkaan viesti ohjaajan puhelimeen ("Kerro ohjaajalle", V5a). Asiakas päättää joka kerta
 * itse: mikään ei lähde automaattisesti, paitsi valinta tiedoksi (ei arkaluonteinen).
 * Viesti menee kaikkiin liitettyihin ohjaajan puhelimiin, jotka ovat samassa verkossa.
 */
internal object ClientMessages {
    const val KIND_STATE = "state"
    const val KIND_CHOICE = "choice"
    private const val KEY_ENABLED = "link_send"
    private const val RETRY_MS = 30_000L
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Asiakaskohtainen: näytetäänkö Kerro ohjaajalle -painike (oletus kyllä, kun laite on liitetty). */
    fun enabledForClient(context: Context): Boolean =
        ProfileScope.prefs(context, ProfileScope.RUN_PREFS).getBoolean(KEY_ENABLED, true)

    fun setEnabledForClient(context: Context, on: Boolean) {
        ProfileScope.prefs(context, ProfileScope.RUN_PREFS).edit().putBoolean(KEY_ENABLED, on).apply()
    }

    fun canSend(context: Context): Boolean =
        enabledForClient(context) && LinkStore(context).peers().any { it.receiver }

    private fun who(context: Context): String =
        runCatching { ProfileStore.current(context).name }.getOrNull()?.takeIf { it.isNotBlank() } ?: LinkStore(context).identity.name

    /**
     * Lähettää viestin kaikkiin liitettyihin ohjaajan puhelimiin. Puhelimet, joita ei tavoitettu,
     * jäävät jonoon ja niille yritetään uudelleen (ks. [LinkOutbox]).
     */
    suspend fun send(context: Context, kind: String, text: String): SendResult = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        if (!canSend(app)) return@withContext SendResult.FAILED
        val store = LinkStore(app)
        val message = LinkMessage(kind, text, who(app), System.currentTimeMillis(), store.identity.name)
        val receivers = store.peers().filter { it.receiver }
        val unreached = deliver(app, message, receivers)
        if (unreached.isEmpty()) return@withContext SendResult.DELIVERED
        LinkOutbox.add(app, QueuedMessage(UUID.randomUUID().toString(), kind, text, message.who, message.at, unreached.map { it.id }))
        ensureRetry(app)
        if (unreached.size < receivers.size) SendResult.DELIVERED else SendResult.QUEUED
    }

    /** Toimittaa viestin; palauttaa ohjaajan puhelimet, joita ei tavoitettu. */
    private suspend fun deliver(app: Context, message: LinkMessage, receivers: List<Peer>): List<Peer> {
        val store = LinkStore(app)
        val me = store.identity
        val unreached = mutableListOf<Peer>()
        receivers.forEach { peer ->
            val ok = peer.host.isNotBlank() && runCatching { LinkProtocol.send(peer.host, peer.port, me, peer, message) }.getOrDefault(false)
            if (!ok) unreached += peer
        }
        if (unreached.isEmpty()) return emptyList()
        // Osoite voi vaihtua (DHCP): haetaan verkosta ja yritetään uudella osoitteella.
        val found = LinkDiscovery.discover(app)
        return unreached.filterNot { peer ->
            val at = found.firstOrNull { it.id == peer.id } ?: return@filterNot false
            val ok = runCatching { LinkProtocol.send(at.host, at.port, me, peer, message) }.getOrDefault(false)
            if (ok) store.updateAddress(peer.id, at.host, at.port)
            ok
        }
    }

    private val flushing = Mutex()

    /** Yrittää jonossa olevia viestejä uudelleen; palauttaa jonoon jääneiden määrän. */
    suspend fun flush(context: Context): Int {
        val app = context.applicationContext
        // Yksi uusinta kerrallaan (avaus ja silmukka voivat osua samaan aikaan).
        if (!flushing.tryLock()) return LinkOutbox.pending(app).size
        try {
            val queue = LinkOutbox.pending(app)
            if (queue.isEmpty()) {
                LinkOutbox.purgeExpired(app)
                return 0
            }
            val store = LinkStore(app)
            val receivers = store.peers().filter { it.receiver }
            val left = queue.map { q ->
                val targets = receivers.filter { it.id in q.peers }
                val message = LinkMessage(q.kind, q.text, q.who, q.at, store.identity.name)
                q.copy(peers = if (targets.isEmpty()) emptyList() else deliver(app, message, targets).map { it.id })
            }
            // Lähetyksen aikana lisätyt viestit säilyvät (luku ja tallennus samassa lukossa).
            synchronized(LinkOutbox) {
                val added = LinkOutbox.pending(app).filter { n -> queue.none { it.id == n.id } }
                LinkOutbox.save(app, left + added)
            }
            return LinkOutbox.pending(app).size
        } finally {
            flushing.unlock()
        }
    }

    @Volatile private var retryJob: Job? = null

    /** Uusintasilmukka (yksi kerrallaan) niin kauan kuin jonossa on viestejä ja sovellus on käynnissä. */
    @Synchronized
    fun ensureRetry(context: Context) {
        val app = context.applicationContext
        if (retryJob?.isActive == true) return
        retryJob = scope.launch {
            while (true) {
                delay(RETRY_MS)
                val left = runCatching { flush(app) }.getOrElse { LinkOutbox.pending(app).size }
                if (left == 0) break
            }
        }.also { job ->
            // Silmukan päättyessä juuri lisätty viesti ei jää odottamaan seuraavaa avausta.
            job.invokeOnCompletion { if (LinkOutbox.pending(app).isNotEmpty()) ensureRetry(app) }
        }
    }

    /** Sovellus avattiin: jonossa olevat heti matkaan; vanhentuneet pois laitteelta. */
    fun resume(context: Context) {
        val app = context.applicationContext
        if (LinkOutbox.pending(app).isEmpty()) {
            LinkOutbox.purgeExpired(app)
            return
        }
        scope.launch {
            val left = runCatching { flush(app) }.getOrElse { LinkOutbox.pending(app).size }
            if (left > 0) ensureRetry(app)
        }
    }

    /** Valinta tiedoksi ohjaajalle (taustalla, ei odoteta). */
    fun choice(context: Context, chosen: String) {
        val app = context.applicationContext
        if (!canSend(app) || chosen.isBlank()) return
        val text = app.getString(R.string.link_choice_text, chosen)
        scope.launch { runCatching { send(app, KIND_CHOICE, text) } }
    }
}
