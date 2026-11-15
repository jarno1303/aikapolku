package fi.aikapolku.app

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import fi.aikapolku.app.link.Identity
import fi.aikapolku.app.link.LinkDiscovery
import fi.aikapolku.app.link.LinkMessage
import fi.aikapolku.app.link.LinkProtocol
import fi.aikapolku.app.link.Peer
import java.net.ServerSocket
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Yksikön yhteys (V5a) oikeassa puhelimessa: palvelu ilmoitetaan verkkoon (DNS-SD), haku löytää
 * sen, pariliitos onnistuu numerovertailulla ja salattu viesti menee perille. Puhelin toimii
 * sekä ohjaajan puhelimena että asiakkaan laitteena (sama laite, oikea verkko). Vaatii wifin.
 */
@RunWith(AndroidJUnit4::class)
class YhteysDeviceTest {
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private class Host(override val identity: Identity) : LinkProtocol.Host {
        val senders = CopyOnWriteArrayList<Peer>()
        val messages = CopyOnWriteArrayList<LinkMessage>()
        var code: String? = null
        override fun sender(id: String) = senders.firstOrNull { it.id == id }
        override fun addSender(peer: Peer) { senders.add(peer) }
        override fun confirmPair(name: String, code: String): Boolean { this.code = code; return true }
        override fun onMessage(message: LinkMessage) { messages.add(message) }
    }

    @Test
    fun haku_pariliitos_ja_viesti_samassa_verkossa() = runBlocking {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val wifi = cm.getNetworkCapabilities(cm.activeNetwork)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        assumeTrue("Puhelin ei ole wifissä: yhteystesti ohitetaan", wifi)

        val host = Host(Identity("testi-" + UUID.randomUUID().toString().take(8), "Testipuhelin"))
        val server = ServerSocket(0)
        thread(isDaemon = true) {
            while (!server.isClosed) {
                val socket = runCatching { server.accept() }.getOrNull() ?: break
                thread(isDaemon = true) { runCatching { LinkProtocol.serve(socket, host) } }
            }
        }
        val nsd = context.getSystemService(NsdManager::class.java)
        val registration = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {}
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {}
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
        }
        nsd.registerService(NsdServiceInfo().apply {
            serviceName = "AikaPolku ${host.identity.id}"
            serviceType = LinkProtocol.SERVICE_TYPE
            port = server.localPort
            setAttribute("id", host.identity.id)
        }, NsdManager.PROTOCOL_DNS_SD, registration)
        try {
            Thread.sleep(1_500)
            val found = LinkDiscovery.discover(context, 5_000L).firstOrNull { it.id == host.identity.id }
            assertNotNull("Haku löysi ohjaajan puhelimen verkosta", found)

            val me = Identity("asiakas-" + UUID.randomUUID().toString().take(8), "Testitabletti")
            var clientCode: String? = null
            val peer = LinkProtocol.pair(found!!.host, found.port, me) { clientCode = it }
            assertNotNull("Pariliitos onnistui", peer)
            assertEquals("Sama koodi molemmissa", host.code, clientCode)

            val sent = LinkProtocol.send(found.host, found.port, me, peer!!, LinkMessage("help", "Tarvitsen apua", "Testi", System.currentTimeMillis(), me.name))
            assertTrue("Viesti kuitattiin", sent)
            assertEquals("Tarvitsen apua", host.messages.single().text)
        } finally {
            runCatching { nsd.unregisterService(registration) }
            server.close()
        }
    }
}
