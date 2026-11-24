package fi.aikapolku.app.link

import android.content.Context
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

/**
 * Laitteen yhteystiedot (laitekohtaiset, eivät asiakaskohtaisia eivätkä siirry tietojen viennissä):
 * oma tunnus ja nimi, liitetyt laitteet avaimineen ja onko vastaanotto päällä (ohjaajan puhelin).
 * Huom (V5a): avaimet sovelluksen yksityisessä tallennuksessa; ennen tuotantoa Android Keystore.
 */
internal class LinkStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    val identity: Identity
        get() {
            val id = prefs.getString(KEY_ID, null) ?: UUID.randomUUID().toString().also { prefs.edit().putString(KEY_ID, it).apply() }
            val name = prefs.getString(KEY_NAME, null)?.takeIf { it.isNotBlank() } ?: Build.MODEL ?: "AikaPolku"
            return Identity(id, name)
        }

    fun setName(name: String) {
        prefs.edit().putString(KEY_NAME, name.trim().take(40)).apply()
        revision++
    }

    /**
     * Paikan laite (A5, docs/PAIKAN_LAITE_SUUNNITELMA.md): pitää paikan tiedot ja jakaa kunkin
     * asukkaan omat tiedot hänen puhelimeensa. Paikan laite on aina myös vastaanottaja.
     */
    var placeEnabled: Boolean
        get() = prefs.getBoolean(KEY_PLACE, false)
        set(value) {
            prefs.edit().putBoolean(KEY_PLACE, value).apply()
            if (value) prefs.edit().putBoolean(KEY_RECEIVER, true).apply()
            revision++
        }

    var receiverEnabled: Boolean
        get() = prefs.getBoolean(KEY_RECEIVER, false)
        set(value) {
            prefs.edit().putBoolean(KEY_RECEIVER, value).apply()
            revision++
        }

    /** Liitokset; avaimet tallennetaan Android Keystoren avaimella salattuina (T1). */
    fun peers(): List<Peer> {
        val raw = prefs.getString(KEY_PEERS, null) ?: return emptyList()
        val plain = KeyVault.open(raw)
        if (plain == null) {
            // Keystoren avain menetetty: salattu tieto talteen (ei ylikirjoiteta), liitokset tehdään uudelleen.
            prefs.edit().putString(KEY_BROKEN, raw).remove(KEY_PEERS).commit()
            revision++
            return emptyList()
        }
        // Vanha salaamaton tallennus salataan heti (T1).
        if (!KeyVault.isSealed(raw)) KeyVault.seal(raw)?.takeIf { it != raw }?.let { prefs.edit().putString(KEY_PEERS, it).apply() }
        return decode(plain)
    }

    /** Liitokset jouduttiin tekemään uudelleen (salausavain menetetty). */
    val peersLost: Boolean get() = prefs.contains(KEY_BROKEN)

    /**
     * Tallentaa liitokset salattuina. false = salaus epäonnistui: mitään ei tallennettu ja
     * edellinen (salattu) tila on ennallaan. Avaimia ei koskaan tallenneta salaamatta.
     */
    private fun savePeers(list: List<Peer>, now: Boolean = true): Boolean {
        val sealed = KeyVault.seal(encode(list)) ?: return false
        val editor = prefs.edit().putString(KEY_PEERS, sealed)
        if (now) editor.commit() else editor.apply()
        return true
    }

    /** false = liitosta ei voitu tallentaa (salaus epäonnistui); pariliitos on silloin epäonnistunut. */
    @Synchronized
    fun addPeer(peer: Peer): Boolean {
        val list = peers().filterNot { it.id == peer.id && it.receiver == peer.receiver } + peer
        val ok = savePeers(list)
        revision++
        return ok
    }

    @Synchronized
    fun removePeer(id: String, receiver: Boolean): Boolean {
        val ok = savePeers(peers().filterNot { it.id == id && it.receiver == receiver })
        revision++
        return ok
    }

    /**
     * Palautuksen jälkeen: avaimet salataan tämän laitteen Keystoren avaimella. Jos salaus ei
     * onnistu, salaamattomat avaimet poistetaan ja liitokset tehdään uudelleen.
     */
    @Synchronized
    fun resealAll(): Boolean {
        val list = peers()
        if (list.isEmpty()) return true
        if (savePeers(list)) return true
        prefs.edit().remove(KEY_PEERS).putString(KEY_BROKEN, "").commit()
        revision++
        return false
    }

    @Synchronized
    fun updateAddress(id: String, host: String, port: Int) {
        val list = peers().map { if (it.id == id && it.receiver) it.copy(host = host, port = port) else it }
        savePeers(list, now = false)
    }

    /** Milloin liitetty asiakkaan laite viimeksi lähetti viestin (ohjaajan puhelimessa). */
    fun lastSeen(id: String): Long = prefs.getLong(KEY_SEEN + id, 0L)

    fun markSeen(id: String, at: Long) {
        prefs.edit().putLong(KEY_SEEN + id, at).apply()
        // Oma laskuri: viestin saapuminen ei nollaa Yksikön yhteys -näkymän muokkausta.
        seenRevision++
    }

    companion object {
        const val PREFS = "aikapolku_link_v1"
        private const val KEY_SEEN = "seen_"
        private const val KEY_ID = "device_id"
        private const val KEY_NAME = "device_name"
        private const val KEY_RECEIVER = "receiver"
        private const val KEY_PLACE = "place"
        private const val KEY_PEERS = "peers"
        private const val KEY_BROKEN = "peers_broken"

        /** Näkymät päivittyvät, kun liitokset muuttuvat. */
        var revision by mutableIntStateOf(0)
            private set

        /** Viimeksi nähty -tiedot muuttuivat (vain lähettäjärivit lukevat tämän). */
        var seenRevision by mutableIntStateOf(0)
            private set

        fun encode(list: List<Peer>): String = JSONArray().also { arr ->
            list.forEach {
                arr.put(
                    JSONObject().put("id", it.id).put("name", it.name).put("key", LinkProtocol.encodeKey(it.key))
                        .put("receiver", it.receiver).put("host", it.host).put("port", it.port)
                        .put("place", it.place).put("resident", it.resident)
                )
            }
        }.toString()

        fun decode(text: String?): List<Peer> = runCatching {
            val arr = JSONArray(text ?: return emptyList())
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Peer(
                    o.getString("id"), o.optString("name"), LinkProtocol.decodeKey(o.getString("key")), o.optBoolean("receiver"),
                    o.optString("host"), o.optInt("port"), o.optBoolean("place"), o.optString("resident")
                )
            }
        }.getOrDefault(emptyList())
    }
}
