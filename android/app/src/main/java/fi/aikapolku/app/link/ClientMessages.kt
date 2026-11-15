package fi.aikapolku.app.link

import android.content.Context
import fi.aikapolku.app.R
import fi.aikapolku.app.data.ProfileScope
import fi.aikapolku.app.data.ProfileStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
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

    /** Lähettää viestin; true, jos ainakin yksi ohjaajan puhelin kuittasi. */
    suspend fun send(context: Context, kind: String, text: String): Boolean = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        if (!canSend(app)) return@withContext false
        val store = LinkStore(app)
        val me = store.identity
        val message = LinkMessage(kind, text, who(app), System.currentTimeMillis(), me.name)
        val receivers = store.peers().filter { it.receiver }
        var delivered = false
        val unreached = mutableListOf<Peer>()
        receivers.forEach { peer ->
            val ok = peer.host.isNotBlank() && runCatching { LinkProtocol.send(peer.host, peer.port, me, peer, message) }.getOrDefault(false)
            if (ok) delivered = true else unreached += peer
        }
        if (unreached.isNotEmpty()) {
            val found = LinkDiscovery.discover(app)
            unreached.forEach { peer ->
                val at = found.firstOrNull { it.id == peer.id } ?: return@forEach
                val ok = runCatching { LinkProtocol.send(at.host, at.port, me, peer, message) }.getOrDefault(false)
                if (ok) {
                    delivered = true
                    store.updateAddress(peer.id, at.host, at.port)
                }
            }
        }
        delivered
    }

    /** Valinta tiedoksi ohjaajalle (taustalla, ei odoteta). */
    fun choice(context: Context, chosen: String) {
        val app = context.applicationContext
        if (!canSend(app) || chosen.isBlank()) return
        val text = app.getString(R.string.link_choice_text, chosen)
        scope.launch { runCatching { send(app, KIND_CHOICE, text) } }
    }
}
