package fi.aikapolku.app

import fi.aikapolku.app.link.Identity
import fi.aikapolku.app.link.KeyVault
import fi.aikapolku.app.link.LinkMessage
import fi.aikapolku.app.link.LinkNetwork
import fi.aikapolku.app.link.LinkProtocol
import fi.aikapolku.app.link.Peer
import java.io.BufferedReader
import java.io.StringReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Laitteiden välisen yhteyden suojaukset (ajantasaistus 24.11.2026). */
@RunWith(RobolectricTestRunner::class)
class LinkSecurityTest {
    @get:org.junit.Rule
    val composeFlush = fi.aikapolku.app.ComposeStateFlush()

    private fun ip(text: String): InetAddress = InetAddress.getByName(text)

    // --- Rivin kokoraja ---

    @Test
    fun `rivi luetaan normaalisti ja rivinvaihto poistetaan`() {
        val reader = BufferedReader(StringReader("eka\r\ntoka\nkolmas"))
        assertEquals("eka", LinkProtocol.readLineLimited(reader, 100))
        assertEquals("toka", LinkProtocol.readLineLimited(reader, 100))
        assertEquals("kolmas", LinkProtocol.readLineLimited(reader, 100))
        assertNull(LinkProtocol.readLineLimited(reader, 100))
    }

    @Test
    fun `liian pitka rivi katkaistaan`() {
        val reader = BufferedReader(StringReader("x".repeat(1001) + "\n"))
        try {
            LinkProtocol.readLineLimited(reader, 1000)
            fail("Liian pitkä rivi hyväksyttiin")
        } catch (_: LinkProtocol.LineTooLongException) {
        }
        // Täsmälleen rajan mittainen kelpaa.
        assertEquals(1000, LinkProtocol.readLineLimited(BufferedReader(StringReader("y".repeat(1000) + "\n")), 1000)!!.length)
    }

    private class CountingHost : LinkProtocol.Host {
        override val identity = Identity("ohjaaja-id", "Ohjaajan puhelin")
        var paired = 0
        override fun sender(id: String): Peer? = null
        override fun addSender(peer: Peer): Boolean { paired++; return true }
        override fun confirmPair(name: String, code: String) = true
        override fun onMessage(message: LinkMessage) {}
    }

    @Test
    fun `vastaanotto sulkee yhteyden loputtomalla rivilla ilman muistin loppumista`() {
        val host = CountingHost()
        val server = ServerSocket(0, 5, InetAddress.getLoopbackAddress())
        val done = CountDownLatch(1)
        thread(isDaemon = true) {
            server.accept().let { LinkProtocol.serve(it, host) }
            done.countDown()
        }
        Socket(InetAddress.getLoopbackAddress(), server.localPort).use { s ->
            val out = s.getOutputStream()
            val chunk = ByteArray(64 * 1024) { 'a'.code.toByte() }
            // Yli kaksi kertaa sallittu pyynnön pituus ilman rivinvaihtoa.
            runCatching { repeat(LinkProtocol.MAX_REQUEST_CHARS * 2 / chunk.size) { out.write(chunk) } }
        }
        assertTrue("Palvelu ei lopettanut", done.await(10, TimeUnit.SECONDS))
        assertEquals(0, host.paired)
        server.close()
    }

    @Test
    fun `pariliitos epaonnistuu jos avainta ei voi tallentaa`() {
        val host = object : LinkProtocol.Host {
            override val identity = Identity("ohjaaja-id", "Ohjaajan puhelin")
            override fun sender(id: String): Peer? = null
            override fun addSender(peer: Peer): Boolean = false
            override fun confirmPair(name: String, code: String) = true
            override fun onMessage(message: LinkMessage) {}
        }
        val server = ServerSocket(0, 5, InetAddress.getLoopbackAddress())
        thread(isDaemon = true) { server.accept().let { LinkProtocol.serve(it, host) } }
        val peer = LinkProtocol.pair("127.0.0.1", server.localPort, Identity("asiakas-id", "Matin tabletti")) {}
        assertNull("Liitos ei saa onnistua asiakkaan puolella, jos ohjaaja ei voinut tallentaa avainta", peer)
        server.close()
    }

    // --- Vain lähiverkko ---

    private val wifi = LinkNetwork.Snapshot(
        cellular = setOf(ip("100.70.1.2"), ip("10.150.3.4")),
        lan = listOf(LinkNetwork.Prefix(ip("192.168.1.20"), 24), LinkNetwork.Prefix(ip("2001:db8:1:2::20"), 64))
    )

    @Test
    fun `yksityiset osoitteet hyvaksytaan wifissa`() {
        val local = ip("192.168.1.20")
        listOf("192.168.1.30", "10.0.0.5", "172.16.4.4", "fe80::1", "fd12:3456::7", "127.0.0.1").forEach {
            assertTrue(it, LinkNetwork.allowed(ip(it), local, wifi))
        }
    }

    @Test
    fun `julkinen IPv6 omassa lahiverkossa hyvaksytaan`() {
        assertTrue(LinkNetwork.allowed(ip("2001:db8:1:2::99"), ip("2001:db8:1:2::20"), wifi))
    }

    @Test
    fun `internetista tuleva hylataan`() {
        val local = ip("192.168.1.20")
        listOf("8.8.8.8", "2001:db8:9:9::1", "100.64.0.9").forEach {
            assertFalse(it, LinkNetwork.allowed(ip(it), local, wifi))
        }
    }

    @Test
    fun `mobiilidatan osoitteeseen tuleva hylataan vaikka lahettaja olisi yksityinen`() {
        // Operaattori voi antaa puhelimille 10.x-osoitteita: toinen liittymä samassa verkossa ei saa yhteyttä.
        assertFalse(LinkNetwork.allowed(ip("10.150.9.9"), ip("10.150.3.4"), wifi))
        assertFalse(LinkNetwork.allowed(ip("100.70.5.5"), ip("100.70.1.2"), wifi))
    }

    @Test
    fun `osoitealueen vertailu bittitarkasti`() {
        val p = LinkNetwork.Prefix(ip("10.1.2.128"), 25)
        assertTrue(LinkNetwork.inPrefix(ip("10.1.2.200"), p))
        assertFalse(LinkNetwork.inPrefix(ip("10.1.2.127"), p))
        assertFalse("eri osoiteperhe", LinkNetwork.inPrefix(ip("::1"), p))
        assertTrue(LinkNetwork.inPrefix(ip("1.2.3.4"), LinkNetwork.Prefix(ip("9.9.9.9"), 0)))
    }

    // --- Salaisuuksien tallennus ---

    @Test
    fun `testiymparistossa ilman Keystorea arvo sailyy kayttokelpoisena`() {
        // Oikeassa laitteessa Keystore on aina; jos salaus siellä epäonnistuu, seal palauttaa null
        // eikä arvoa tallenneta. Testiympäristössä Keystorea ei ole, joten arvo kulkee läpi.
        val sealed = KeyVault.seal("salasana123")
        assertEquals("salasana123", sealed)
        assertEquals("salasana123", KeyVault.open(sealed!!))
    }
}
