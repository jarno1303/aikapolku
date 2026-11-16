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
    val port: Int = 0,
    /** Asukkaan puhelimessa: liitetty laite on paikan laite (A5), josta haetaan asukkaan tiedot. */
    val place: Boolean = false,
    /** Paikan laitteella: kenen asukkaan (profiilin tunnus) puhelin tämä lähettäjä on. */
    val resident: String = ""
) {
    override fun equals(other: Any?) = other is Peer && other.id == id && other.receiver == receiver
    override fun hashCode() = id.hashCode() * 31 + receiver.hashCode()
}

/** Asiakkaan viesti ohjaajalle. [kind] help / break / more_time / choice_none / state / choice. */
/** [at] = lähetysaika (vastaanotossa saapumisaika), [sentAt] = alkuperäinen lähetysaika (0 = sama). */
internal data class LinkMessage(val kind: String, val text: String, val who: String, val at: Long, val fromName: String, val sentAt: Long = 0L)

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
        /** Liitetty lähettäjä oli yhteydessä (ohjaaja näkee, milloin laite viimeksi tavoitettiin). */
        fun seen(id: String, at: Long) {}
        /** Onko tämä paikan laite (A5): kerrotaan liitoksen tuloksessa asukkaan puhelimelle. */
        val isPlace: Boolean get() = false
        /**
         * Paikan laitteella (A5): asukkaan puhelimen [peer] paketti. Palauttaa nykyisen version ja
         * paketin, tai paketin tilalla null, jos puhelimella on jo sama [version]. null = ei palvella.
         */
        fun residentPackage(peer: Peer, version: String, known: Set<String>): Pair<String, ByteArray?>? = null
    }

    private fun Socket.reader() = BufferedReader(InputStreamReader(getInputStream(), Charsets.UTF_8))
    private fun Socket.writer() = PrintWriter(OutputStreamWriter(getOutputStream(), Charsets.UTF_8), true)

    /** Ensimmäinen ja paljastusrivi on luettava nopeasti; odotus koskee vain ohjaajan päätöstä. */
    const val LINE_TIMEOUT_MS = 10_000
    /** Viestin aikaleiman sallittu ero (laitteiden kellot verkon ajassa). */
    const val MAX_SKEW_MS = 30L * 60 * 1000

    /** Jo vastaanotetut viestit (lähettäjä + nonce): toistettua viestiä ei hyväksytä uudelleen. */
    private val seen = object : LinkedHashMap<String, Long>(256, 0.75f, false) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?) = size > 500
    }

    private fun firstSeen(key: String, now: Long): Boolean = synchronized(seen) {
        if (seen.containsKey(key)) false else { seen[key] = now; true }
    }

    /** Hakujen oma välimuisti: minuutin välein hakevat puhelimet eivät työnnä viestien tunnisteita pois. */
    private val seenPulls = object : LinkedHashMap<String, Long>(256, 0.75f, false) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?) = size > 5000
    }

    private fun firstPull(key: String, now: Long): Boolean = synchronized(seenPulls) {
        if (seenPulls.containsKey(key)) false else { seenPulls[key] = now; true }
    }

    /** Palvelee yhden yhteyden (vastaanottajan puolella). [now] testejä varten. */
    fun serve(socket: Socket, host: Host, now: () -> Long = System::currentTimeMillis) {
        socket.use { s ->
            s.soTimeout = LINE_TIMEOUT_MS
            val reader = s.reader()
            val writer = s.writer()
            val request = runCatching { JSONObject(reader.readLine() ?: return) }.getOrNull() ?: return
            when (request.optString("t")) {
                "pair" -> {
                    // 1) asiakas sitoutuu avaimeensa (tiiviste), 2) ohjaaja kertoo omansa,
                    // 3) asiakas paljastaa avaimensa, 4) molemmat näyttävät koodin.
                    val clientId = request.optString("id")
                    val clientName = request.optString("name").take(60)
                    val commit = runCatching { b64d.decode(request.optString("commit")) }.getOrNull() ?: return
                    if (clientId.isBlank() || commit.size != 32) return
                    val pair = LinkCrypto.newKeyPair()
                    val ownPub = LinkCrypto.publicBytes(pair)
                    val ownNonce = LinkCrypto.randomBytes(16)
                    writer.println(
                        JSONObject().put("t", "pub").put("id", host.identity.id).put("name", host.identity.name)
                            .put("pub", b64e.encodeToString(ownPub)).put("n", b64e.encodeToString(ownNonce)).toString()
                    )
                    val reveal = runCatching { JSONObject(reader.readLine() ?: return) }.getOrNull() ?: return
                    if (reveal.optString("t") != "reveal") return
                    val clientPub = runCatching { b64d.decode(reveal.optString("pub")) }.getOrNull() ?: return
                    val clientNonce = runCatching { b64d.decode(reveal.optString("n")) }.getOrNull() ?: return
                    if (!LinkCrypto.commitment(clientPub, clientNonce).contentEquals(commit)) return
                    val key = runCatching { LinkCrypto.sharedKey(pair, clientPub, host.identity.id, clientId) }.getOrNull() ?: return
                    val code = LinkCrypto.comparisonCode(clientPub, ownPub, clientNonce, ownNonce)
                    val ok = host.confirmPair(clientName, code)
                    if (ok) host.addSender(Peer(clientId, clientName, key, receiver = false))
                    writer.println(JSONObject().put("t", "result").put("ok", ok).put("place", ok && host.isPlace).toString())
                }
                "pull" -> {
                    // Asukkaan puhelin hakee omat tietonsa (A5). Pyyntö ja vastaus salattuina; vain
                    // liitetty puhelin ja vain sen asukkaan tiedot. Vastaus voi olla iso: pidempi aikaraja.
                    val from = request.optString("id")
                    val peer = host.sender(from)
                    val nonce = runCatching { b64d.decode(request.optString("n")) }.getOrNull()
                    val plain = if (peer != null && nonce != null) runCatching {
                        LinkCrypto.open(peer.key, nonce, b64d.decode(request.optString("c")), from.toByteArray())
                    }.getOrNull() else null
                    val json = plain?.let { runCatching { JSONObject(String(it, Charsets.UTF_8)) }.getOrNull() }
                    val time = now()
                    val fresh = json != null && kotlin.math.abs(time - json.optLong("a")) <= MAX_SKEW_MS &&
                        firstPull(from + ":" + request.optString("n"), time)
                    val known = json?.optJSONArray("k")?.let { a -> (0 until minOf(a.length(), 10_000)).map { a.optString(it) }.toSet() }.orEmpty()
                    val result = if (peer != null && fresh) runCatching { host.residentPackage(peer, json!!.optString("v"), known) }.getOrNull() else null
                    if (peer == null || result == null) {
                        writer.println(JSONObject().put("t", "err").put("e", if (peer == null) "unknown" else "invalid").toString())
                    } else {
                        host.seen(from, time)
                        s.soTimeout = PULL_TIMEOUT_MS
                        val body = JSONObject().put("v", result.first).put("r", request.optString("n"))
                            .put("p", result.second?.let { b64e.encodeToString(it) } ?: "")
                        val (replyNonce, sealed) = LinkCrypto.seal(peer.key, body.toString().toByteArray(Charsets.UTF_8), host.identity.id.toByteArray())
                        writer.println(JSONObject().put("t", "pkg").put("n", b64e.encodeToString(replyNonce)).put("c", b64e.encodeToString(sealed)).toString())
                    }
                }
                "msg" -> {
                    val from = request.optString("id")
                    val peer = host.sender(from)
                    val nonce = runCatching { b64d.decode(request.optString("n")) }.getOrNull()
                    val plain = if (peer != null && nonce != null) runCatching {
                        LinkCrypto.open(peer.key, nonce, b64d.decode(request.optString("c")), from.toByteArray())
                    }.getOrNull() else null
                    val json = plain?.let { runCatching { JSONObject(String(it, Charsets.UTF_8)) }.getOrNull() }
                    val time = now()
                    val fresh = json != null && kotlin.math.abs(time - json.optLong("a")) <= MAX_SKEW_MS &&
                        firstSeen(from + ":" + request.optString("n"), time)
                    if (peer == null || json == null || !fresh) {
                        writer.println(JSONObject().put("t", "err").put("e", if (peer == null) "unknown" else "invalid").toString())
                    } else {
                        host.seen(from, time)
                        host.onMessage(
                            LinkMessage(
                                kind = json.optString("k").take(20),
                                text = json.optString("x").take(200),
                                who = json.optString("w").take(60),
                                at = json.optLong("a"),
                                fromName = peer.name
                            )
                        )
                        // Kuittaus salattuna: lähettäjä tietää, että juuri tämä ohjaajan puhelin sai viestin.
                        val (ackNonce, ack) = LinkCrypto.seal(peer.key, ("ack:" + request.optString("n")).toByteArray(), host.identity.id.toByteArray())
                        writer.println(
                            JSONObject().put("t", "ack").put("n", b64e.encodeToString(ackNonce)).put("c", b64e.encodeToString(ack)).toString()
                        )
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
            val ownNonce = LinkCrypto.randomBytes(16)
            writer.println(
                JSONObject().put("t", "pair").put("id", me.id).put("name", me.name)
                    .put("commit", b64e.encodeToString(LinkCrypto.commitment(ownPub, ownNonce))).toString()
            )
            val pub = JSONObject(reader.readLine() ?: return null)
            if (pub.optString("t") != "pub") return null
            val serverId = pub.optString("id")
            val serverPub = b64d.decode(pub.optString("pub"))
            val serverNonce = b64d.decode(pub.optString("n"))
            writer.println(
                JSONObject().put("t", "reveal").put("pub", b64e.encodeToString(ownPub)).put("n", b64e.encodeToString(ownNonce)).toString()
            )
            val key = LinkCrypto.sharedKey(pair, serverPub, me.id, serverId)
            onCode(LinkCrypto.comparisonCode(ownPub, serverPub, ownNonce, serverNonce))
            val result = JSONObject(reader.readLine() ?: return null)
            if (result.optString("t") != "result" || !result.optBoolean("ok")) return null
            return Peer(serverId, pub.optString("name").take(60), key, receiver = true, host = host, port = port, place = result.optBoolean("place"))
        }
    }

    /** Lähettää salatun viestin; true vain, jos juuri liitetty ohjaajan puhelin kuittasi (salattu kuittaus). */
    fun send(host: String, port: Int, me: Identity, peer: Peer, message: LinkMessage): Boolean {
        val payload = JSONObject().put("k", message.kind).put("x", message.text).put("w", message.who).put("a", message.at)
            .toString().toByteArray(Charsets.UTF_8)
        val (nonce, ciphertext) = LinkCrypto.seal(peer.key, payload, me.id.toByteArray())
        val nonceText = b64e.encodeToString(nonce)
        connect(host, port, READ_TIMEOUT_MS).use { s ->
            s.writer().println(
                JSONObject().put("t", "msg").put("id", me.id).put("n", nonceText).put("c", b64e.encodeToString(ciphertext)).toString()
            )
            val reply = JSONObject(s.reader().readLine() ?: return false)
            if (reply.optString("t") != "ack") return false
            val ack = runCatching {
                LinkCrypto.open(peer.key, b64d.decode(reply.optString("n")), b64d.decode(reply.optString("c")), peer.id.toByteArray())
            }.getOrNull() ?: return false
            return String(ack) == "ack:$nonceText"
        }
    }

    /** Paketin siirron aikaraja (kuvat ja äänet ensimmäisellä kerralla). */
    const val PULL_TIMEOUT_MS = 60_000

    /** Asukkaan tietopaketin vastaus: nykyinen versio ja paketti (null = puhelimella jo sama versio). */
    internal data class Pulled(val version: String, val bytes: ByteArray?)

    /**
     * Asukkaan puhelimesta paikan laitteeseen (A5): hakee asukkaan paketin, jos [version] on
     * vanhentunut. [known] = laitteella jo olevat kuvat ja äänet (tiivisteet). null = ei vastausta.
     */
    fun pull(host: String, port: Int, me: Identity, peer: Peer, version: String, known: Set<String>, now: Long = System.currentTimeMillis()): Pulled? {
        val payload = JSONObject().put("a", now).put("v", version).put("k", org.json.JSONArray(known.toList()))
            .toString().toByteArray(Charsets.UTF_8)
        val (nonce, ciphertext) = LinkCrypto.seal(peer.key, payload, me.id.toByteArray())
        val nonceText = b64e.encodeToString(nonce)
        connect(host, port, PULL_TIMEOUT_MS).use { s ->
            s.writer().println(
                JSONObject().put("t", "pull").put("id", me.id).put("n", nonceText).put("c", b64e.encodeToString(ciphertext)).toString()
            )
            val reply = JSONObject(s.reader().readLine() ?: return null)
            if (reply.optString("t") != "pkg") return null
            val plain = LinkCrypto.open(peer.key, b64d.decode(reply.optString("n")), b64d.decode(reply.optString("c")), peer.id.toByteArray())
                ?: return null
            val body = JSONObject(String(plain, Charsets.UTF_8))
            // Vastaus juuri tähän pyyntöön (ei toistettu vanha vastaus).
            if (body.optString("r") != nonceText) return null
            val p = body.optString("p")
            return Pulled(body.optString("v"), if (p.isEmpty()) null else b64d.decode(p))
        }
    }

    fun encodeKey(key: ByteArray): String = b64e.encodeToString(key)
    fun decodeKey(text: String): ByteArray = b64d.decode(text)
}
