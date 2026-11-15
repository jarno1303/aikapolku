package fi.aikapolku.app

import fi.aikapolku.app.link.Identity
import fi.aikapolku.app.link.LinkCrypto
import fi.aikapolku.app.link.LinkInbox
import fi.aikapolku.app.link.LinkMessage
import fi.aikapolku.app.link.LinkProtocol
import fi.aikapolku.app.link.LinkStore
import fi.aikapolku.app.link.Peer
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Yksikön yhteys (V5a): salaus, pariliitos numerovertailulla ja salatut viestit paikallisesti. */
@RunWith(RobolectricTestRunner::class)
class LinkTest {
    @Test
    fun `molemmat laitteet saavat saman avaimen ja koodin`() {
        val a = LinkCrypto.newKeyPair()
        val b = LinkCrypto.newKeyPair()
        val keyA = LinkCrypto.sharedKey(a, LinkCrypto.publicBytes(b), "asiakas", "ohjaaja")
        val keyB = LinkCrypto.sharedKey(b, LinkCrypto.publicBytes(a), "ohjaaja", "asiakas")
        assertArrayEquals(keyA, keyB)
        assertEquals(32, keyA.size)
        val n1 = LinkCrypto.randomBytes(16)
        val n2 = LinkCrypto.randomBytes(16)
        val code = LinkCrypto.comparisonCode(LinkCrypto.publicBytes(a), LinkCrypto.publicBytes(b), n1, n2)
        assertEquals(code, LinkCrypto.comparisonCode(LinkCrypto.publicBytes(a), LinkCrypto.publicBytes(b), n1, n2))
        assertTrue(code.matches(Regex("\\d{3} \\d{3}")))
        // Asiakkaan satunnaisluku vaikuttaa koodiin: hyökkääjä ei voi valita sitä jälkikäteen.
        assertTrue(code != LinkCrypto.comparisonCode(LinkCrypto.publicBytes(a), LinkCrypto.publicBytes(b), LinkCrypto.randomBytes(16), n2))
    }

    @Test
    fun `vaarennetty tai vieras viesti ei aukea`() {
        val key = ByteArray(32) { it.toByte() }
        val (nonce, sealed) = LinkCrypto.seal(key, "Tarvitsen apua".toByteArray(), "asiakas".toByteArray())
        assertEquals("Tarvitsen apua", String(LinkCrypto.open(key, nonce, sealed, "asiakas".toByteArray())!!))
        assertNull("väärä lähettäjä", LinkCrypto.open(key, nonce, sealed, "joku".toByteArray()))
        val tampered = sealed.copyOf().also { it[0] = (it[0] + 1).toByte() }
        assertNull("muutettu viesti", LinkCrypto.open(key, nonce, tampered, "asiakas".toByteArray()))
        assertNull("väärä avain", LinkCrypto.open(ByteArray(32), nonce, sealed, "asiakas".toByteArray()))
    }

    private class TestHost(private val accept: Boolean) : LinkProtocol.Host {
        override val identity = Identity("ohjaaja-id", "Ohjaajan puhelin")
        val senders = CopyOnWriteArrayList<Peer>()
        val messages = CopyOnWriteArrayList<LinkMessage>()
        var shownCode: String? = null
        override fun sender(id: String) = senders.firstOrNull { it.id == id }
        override fun addSender(peer: Peer) { senders.add(peer) }
        override fun confirmPair(name: String, code: String): Boolean { shownCode = code; return accept }
        override fun onMessage(message: LinkMessage) { messages.add(message) }
    }

    private fun serve(host: TestHost): ServerSocket {
        val server = ServerSocket(0, 5, InetAddress.getLoopbackAddress())
        thread(isDaemon = true) {
            while (!server.isClosed) {
                val socket = runCatching { server.accept() }.getOrNull() ?: break
                thread(isDaemon = true) { LinkProtocol.serve(socket, host) }
            }
        }
        return server
    }

    @Test
    fun `pariliitos ja viesti ohjaajan puhelimeen`() {
        val host = TestHost(accept = true)
        val server = serve(host)
        val me = Identity("asiakas-id", "Matin tabletti")
        var clientCode: String? = null
        val peer = LinkProtocol.pair("127.0.0.1", server.localPort, me) { clientCode = it }
        assertNotNull(peer)
        assertEquals("Molemmissa sama koodi", host.shownCode, clientCode)
        assertTrue(peer!!.receiver)
        assertEquals(1, host.senders.size)
        assertArrayEquals(peer.key, host.senders.first().key)

        val message = LinkMessage("help", "Tarvitsen apua", "Matti", System.currentTimeMillis(), me.name)
        val sent = LinkProtocol.send("127.0.0.1", server.localPort, me, peer, message)
        assertTrue(sent)
        assertEquals("Tarvitsen apua", host.messages.single().text)
        assertEquals("Matti", host.messages.single().who)
        assertEquals("Matin tabletti", host.messages.single().fromName)
        // Vanha viesti (yli 30 min) hylätään.
        assertFalse(LinkProtocol.send("127.0.0.1", server.localPort, me, peer, message.copy(at = 1L)))
        assertEquals(1, host.messages.size)
        server.close()
    }

    @Test
    fun `hylatty liitos ja liittamaton laite eivat paase lapi`() {
        val host = TestHost(accept = false)
        val server = serve(host)
        val me = Identity("asiakas-id", "Tabletti")
        assertNull(LinkProtocol.pair("127.0.0.1", server.localPort, me) {})
        assertTrue(host.senders.isEmpty())
        val stranger = Peer("ohjaaja-id", "x", ByteArray(32), receiver = true)
        assertFalse(LinkProtocol.send("127.0.0.1", server.localPort, me, stranger, LinkMessage("help", "x", "y", System.currentTimeMillis(), "z")))
        assertTrue(host.messages.isEmpty())
        server.close()
    }

    @Test
    fun `liitokset ja viestit tallentuvat`() {
        val peers = listOf(Peer("a", "Ohjaaja", ByteArray(32) { 1 }, receiver = true, host = "10.0.0.2", port = 4000))
        val decoded = LinkStore.decode(LinkStore.encode(peers))
        assertEquals("10.0.0.2", decoded.single().host)
        assertArrayEquals(peers.single().key, decoded.single().key)
        val day = 24L * 60 * 60 * 1000
        val now = System.currentTimeMillis()
        val old = LinkMessage("help", "vanha", "M", now - 3 * day, "t")
        val recent = LinkMessage("help", "uusi", "M", now, "t")
        assertEquals(listOf(recent), LinkInbox.prune(listOf(old, recent), now))
    }
}
