package fi.aikapolku.app.link

import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.InetSocketAddress
import java.net.Socket
import java.util.Base64
import org.json.JSONObject

/** Tämä laite: pysyvä tunnus ja näkyvä nimi. */
internal data class Identity(val id: String, val name: String)

/**
 * Liitetty laite. [receiver] = ohjaajan puhelin, joka vastaanottaa viestejä (asiakkaan laitteessa);
 * false = asiakkaan laite, joka lähettää (ohjaajan puhelimessa). [host]/[port] viimeksi toimiva osoite.
 */
internal data class Peer(
    val id: String,
    val name: String,
    val key: ByteArray,
    val receiver: Boolean,
    val host: String = "",
    val port: Int = 0
) {
    override fun equals(other: Any?) = other is Peer && other.id == id && other.receiver == receiver
    override fun hashCode() = id.hashCode() * 31 + receiver.hashCode()
}

/** Asiakkaan viesti ohjaajalle. [kind] help / break / more_time / choice_none / state / choice. */
internal data class LinkMessage(val kind: String, val text: String, val who: String, val at: Long, val fromName: String)

/**
 * Yksinkertainen protokolla samassa verkossa (V5a): yksi TCP-yhteys = yksi pyyntö, JSON-rivit.
 *  - pair: asiakkaan laite → ohjaajan puhelin. Vastaus "pub" heti (molemmat näyttävät koodin),
 *    "result" kun ohjaaja on hyväksynyt tai hylännyt.
 *  - msg: salattu viesti; vastaus "ack" tai "err".
 * Ei Android-riippuvuuksia (org.json on mukana Robolectric-testeissä).
 */
internal object LinkProtocol {
    const val SERVICE_TYPE = "_aikapolku._tcp"
    const val CONNECT_TIMEOUT_MS = 4_000
    const val READ_TIMEOUT_MS = 8_000
    const val PAIR_TIMEOUT_MS = 120_000

    private val b64e = Base64.getEncoder()
    private val b64d = Base64.getDecoder()

    /** Vastaanottajan (ohjaajan puhelin) tiedot ja päätökset. */
    interface Host {
        val identity: Identity
        /** Liitetty lähettäjä (asiakkaan laite) tunnuksella. */
        fun sender(id: String): Peer?
        fun addSender(peer: Peer)
        /** Kysyy ohjaajalta (estävä, enintään [PAIR_TIMEOUT_MS]): liitetäänkö [name], koodi [code]. */
        fun confirmPair(name: String, code: String): Boolean
        fun onMessage(message: LinkMessage)
    }

    private fun Socket.reader() = BufferedReader(InputStreamReader(getInputStream(), Charsets.UTF_8))
    private fun Socket.writer() = PrintWriter(OutputStreamWriter(getOutputStream(), Charsets.UTF_8), true)

    /** Palvelee yhden yhteyden (vastaanottajan puolella). */
    fun serve(socket: Socket, host: Host) {
        socket.use { s ->
            s.soTimeout = PAIR_TIMEOUT_MS
            val reader = s.reader()
            val writer = s.writer()
            val request = runCatching { JSONObject(reader.readLine() ?: return) }.getOrNull() ?: return
            when (request.optString("t")) {
                "pair" -> {
                    val clientId = request.optString("id")
                    val clientName = request.optString("name").take(60)
                    val clientPub = runCatching { b64d.decode(request.optString("pub")) }.getOrNull() ?: return
                    if (clientId.isBlank()) return
                    val pair = LinkCrypto.newKeyPair()
                    val ownPub = LinkCrypto.publicBytes(pair)
                    val key = runCatching { LinkCrypto.sharedKey(pair, clientPub, host.identity.id, clientId) }.getOrNull() ?: return
                    val code = LinkCrypto.comparisonCode(clientPub, ownPub)
                    writer.println(
                        JSONObject().put("t", "pub").put("id", host.identity.id).put("name", host.identity.name)
                            .put("pub", b64e.encodeToString(ownPub)).toString()
                    )
                    val ok = host.confirmPair(clientName, code)
                    if (ok) host.addSender(Peer(clientId, clientName, key, receiver = false))
                    writer.println(JSONObject().put("t", "result").put("ok", ok).toString())
                }
                "msg" -> {
                    val from = request.optString("id")
                    val peer = host.sender(from)
                    val plain = peer?.let {
                        runCatching {
                            LinkCrypto.open(it.key, b64d.decode(request.optString("n")), b64d.decode(request.optString("c")), from.toByteArray())
                        }.getOrNull()
                    }
                    val message = plain?.let { runCatching { JSONObject(String(it, Charsets.UTF_8)) }.getOrNull() }?.let {
                        LinkMessage(
                            kind = it.optString("k").take(20),
                            text = it.optString("x").take(200),
                            who = it.optString("w").take(60),
                            at = it.optLong("a"),
                            fromName = peer.name
                        )
                    }
                    if (message == null) {
                        writer.println(JSONObject().put("t", "err").put("e", if (peer == null) "unknown" else "invalid").toString())
                    } else {
                        host.onMessage(message)
                        writer.println(JSONObject().put("t", "ack").toString())
                    }
                }
            }
        }
    }

    private fun connect(host: String, port: Int, readTimeout: Int): Socket = Socket().apply {
        connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
        soTimeout = readTimeout
    }

    /**
     * Pariliitos asiakkaan laitteesta (estävä). [onCode] saa vertailukoodin heti, kun se on
     * laskettu (näytetään asiakkaan laitteessa). Palauttaa liitetyn ohjaajan puhelimen tai null.
     */
    fun pair(host: String, port: Int, me: Identity, onCode: (String) -> Unit): Peer? {
        connect(host, port, PAIR_TIMEOUT_MS).use { s ->
            val reader = s.reader()
            val writer = s.writer()
            val pair = LinkCrypto.newKeyPair()
            val ownPub = LinkCrypto.publicBytes(pair)
            writer.println(
                JSONObject().put("t", "pair").put("id", me.id).put("name", me.name).put("pub", b64e.encodeToString(ownPub)).toString()
            )
            val pub = JSONObject(reader.readLine() ?: return null)
            if (pub.optString("t") != "pub") return null
            val serverId = pub.optString("id")
            val serverPub = b64d.decode(pub.optString("pub"))
            val key = LinkCrypto.sharedKey(pair, serverPub, me.id, serverId)
            onCode(LinkCrypto.comparisonCode(ownPub, serverPub))
            val result = JSONObject(reader.readLine() ?: return null)
            if (result.optString("t") != "result" || !result.optBoolean("ok")) return null
            return Peer(serverId, pub.optString("name").take(60), key, receiver = true, host = host, port = port)
        }
    }

    /** Lähettää salatun viestin; true, jos ohjaajan puhelin kuittasi. */
    fun send(host: String, port: Int, me: Identity, peer: Peer, message: LinkMessage): Boolean {
        val payload = JSONObject().put("k", message.kind).put("x", message.text).put("w", message.who).put("a", message.at)
            .toString().toByteArray(Charsets.UTF_8)
        val (nonce, ciphertext) = LinkCrypto.seal(peer.key, payload, me.id.toByteArray())
        connect(host, port, READ_TIMEOUT_MS).use { s ->
            s.writer().println(
                JSONObject().put("t", "msg").put("id", me.id).put("n", b64e.encodeToString(nonce)).put("c", b64e.encodeToString(ciphertext)).toString()
            )
            val reply = JSONObject(s.reader().readLine() ?: return false)
            return reply.optString("t") == "ack"
        }
    }

    fun encodeKey(key: ByteArray): String = b64e.encodeToString(key)
    fun decodeKey(text: String): ByteArray = b64d.decode(text)
}
