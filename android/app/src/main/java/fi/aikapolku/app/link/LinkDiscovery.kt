package fi.aikapolku.app.link

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import java.util.Collections
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Samassa verkossa löytynyt ohjaajan puhelin. */
internal data class FoundReceiver(val name: String, val host: String, val port: Int, val id: String)

/** Ohjaajan puhelinten haku samasta verkosta (DNS-SD / NSD). */
internal object LinkDiscovery {
    @Suppress("DEPRECATION")
    suspend fun discover(context: Context, timeoutMs: Long = 3_500L): List<FoundReceiver> = withContext(Dispatchers.IO) {
        val nsd = context.getSystemService(NsdManager::class.java) ?: return@withContext emptyList()
        val wifi = context.applicationContext.getSystemService(WifiManager::class.java)
        val lock = runCatching { wifi?.createMulticastLock("aikapolku-link")?.apply { setReferenceCounted(false); acquire() } }.getOrNull()
        val found = Collections.synchronizedList(mutableListOf<NsdServiceInfo>())
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onServiceLost(info: NsdServiceInfo) {}
            override fun onServiceFound(info: NsdServiceInfo) {
                if (info.serviceType.contains("_aikapolku")) found.add(info)
            }
        }
        val started = runCatching { nsd.discoverServices(LinkProtocol.SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener) }.isSuccess
        try {
            if (started) delay(timeoutMs)
        } finally {
            if (started) runCatching { nsd.stopServiceDiscovery(listener) }
            runCatching { lock?.release() }
        }
        val unique = synchronized(found) { found.distinctBy { it.serviceName } }
        unique.mapNotNull { resolve(nsd, it) }
    }

    @Suppress("DEPRECATION")
    private suspend fun resolve(nsd: NsdManager, info: NsdServiceInfo): FoundReceiver? = withTimeoutOrNull(3_000L) {
        suspendCancellableCoroutine { cont ->
            runCatching {
                nsd.resolveService(info, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                        if (cont.isActive) cont.resume(null)
                    }

                    override fun onServiceResolved(resolved: NsdServiceInfo) {
                        val host = resolved.host?.hostAddress
                        val id = resolved.attributes["id"]?.let { String(it, Charsets.UTF_8) } ?: ""
                        if (cont.isActive) cont.resume(host?.let { FoundReceiver(resolved.serviceName, it, resolved.port, id) })
                    }
                })
            }.onFailure { if (cont.isActive) cont.resume(null) }
        }
    }
}
