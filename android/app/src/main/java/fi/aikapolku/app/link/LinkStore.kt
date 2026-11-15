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

    var receiverEnabled: Boolean
        get() = prefs.getBoolean(KEY_RECEIVER, false)
        set(value) {
            prefs.edit().putBoolean(KEY_RECEIVER, value).apply()
            revision++
        }

    fun peers(): List<Peer> = decode(prefs.getString(KEY_PEERS, null))

    @Synchronized
    fun addPeer(peer: Peer) {
        val list = peers().filterNot { it.id == peer.id && it.receiver == peer.receiver } + peer
        prefs.edit().putString(KEY_PEERS, encode(list)).commit()
        revision++
    }

    @Synchronized
    fun removePeer(id: String, receiver: Boolean) {
        prefs.edit().putString(KEY_PEERS, encode(peers().filterNot { it.id == id && it.receiver == receiver })).commit()
        revision++
    }

    @Synchronized
    fun updateAddress(id: String, host: String, port: Int) {
        val list = peers().map { if (it.id == id && it.receiver) it.copy(host = host, port = port) else it }
        prefs.edit().putString(KEY_PEERS, encode(list)).apply()
    }

    companion object {
        const val PREFS = "aikapolku_link_v1"
        private const val KEY_ID = "device_id"
        private const val KEY_NAME = "device_name"
        private const val KEY_RECEIVER = "receiver"
        private const val KEY_PEERS = "peers"

        /** Näkymät päivittyvät, kun liitokset muuttuvat. */
        var revision by mutableIntStateOf(0)
            private set

        fun encode(list: List<Peer>): String = JSONArray().also { arr ->
            list.forEach {
                arr.put(
                    JSONObject().put("id", it.id).put("name", it.name).put("key", LinkProtocol.encodeKey(it.key))
                        .put("receiver", it.receiver).put("host", it.host).put("port", it.port)
                )
            }
        }.toString()

        fun decode(text: String?): List<Peer> = runCatching {
            val arr = JSONArray(text ?: return emptyList())
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Peer(o.getString("id"), o.optString("name"), LinkProtocol.decodeKey(o.getString("key")), o.optBoolean("receiver"), o.optString("host"), o.optInt("port"))
            }
        }.getOrDefault(emptyList())
    }
}
