package fi.aikapolku.app.link

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.net.Inet6Address
import java.net.InetAddress
import java.net.NetworkInterface

/**
 * Vastaanotto vain lähiverkosta. Yhteys hyväksytään, kun se tulee yksikön omasta verkosta
 * (Wi-Fi, Ethernet tai puhelimen jakama yhteyspiste), ei koskaan mobiilidatan tai VPN:n kautta.
 * Päätös on puhdas funktio ([allowed]) ja testattavissa JVM:ssä; Android-osa ([snapshot],
 * [localPrefixes]) kerää laitteen verkot.
 */
internal object LinkNetwork {
    /** Verkon osoitealue, esim. 192.168.1.0/24. */
    data class Prefix(val address: InetAddress, val length: Int)

    /** Laitteen verkot hyväksymishetkellä. */
    data class Snapshot(
        /** Mobiilidata- ja VPN-verkkojen omat osoitteet: niihin tulevia yhteyksiä ei hyväksytä. */
        val denied: Set<InetAddress> = emptySet(),
        /** Mobiilidata- ja VPN-verkkojen liitäntöjen nimet (esim. rmnet0, tun0). */
        val deniedInterfaces: Set<String> = emptySet(),
        /** Wi-Fi- ja Ethernet-verkkojen osoitealueet (kattaa myös julkiset IPv6-osoitteet lähiverkossa). */
        val lan: List<Prefix> = emptyList()
    )

    /** Liitäntä, johon yhteys saapui: nimi ja sen osoitealueet (yhteyspiste ei näy Androidin verkkolistassa). */
    data class LocalInterface(val name: String = "", val prefixes: List<Prefix> = emptyList())

    /** Yksityinen tai paikallinen osoite: 10/8, 172.16/12, 192.168/16, linkin paikallinen, IPv6 ULA fc00::/7. */
    fun isPrivate(address: InetAddress): Boolean =
        address.isLoopbackAddress || address.isLinkLocalAddress || address.isSiteLocalAddress ||
            (address is Inet6Address && (address.address[0].toInt() and 0xfe) == 0xfc)

    fun inPrefix(address: InetAddress, prefix: Prefix): Boolean {
        val a = address.address
        val p = prefix.address.address
        if (a.size != p.size) return false
        val bits = prefix.length.coerceIn(0, a.size * 8)
        val full = bits / 8
        for (i in 0 until full) if (a[i] != p[i]) return false
        val rest = bits % 8
        if (rest == 0) return true
        val mask = (0xff shl (8 - rest)) and 0xff
        return (a[full].toInt() and mask) == (p[full].toInt() and mask)
    }

    /**
     * Hyväksytäänkö yhteys etäosoitteesta [remote], joka saapui tämän laitteen osoitteeseen [local]
     * liitännässä [iface].
     *  - oma laite (loopback): kyllä
     *  - saapui mobiilidata- tai VPN-verkon osoitteeseen tai liitäntään: ei
     *  - muuten: etäosoite on yksityinen, jonkin Wi-Fi- tai Ethernet-verkon alueella tai saman
     *    liitännän alueella (puhelimen yhteyspiste, myös IPv6)
     * Julkinen osoite, joka ei ole oman lähiverkon alueella (esim. internetistä), hylätään.
     */
    fun allowed(remote: InetAddress, local: InetAddress, snapshot: Snapshot, iface: LocalInterface = LocalInterface()): Boolean {
        if (remote.isLoopbackAddress) return true
        if (snapshot.denied.any { sameAddress(it, local) }) return false
        if (iface.name.isNotEmpty() && iface.name in snapshot.deniedInterfaces) return false
        return isPrivate(remote) || snapshot.lan.any { inPrefix(remote, it) } || iface.prefixes.any { inPrefix(remote, it) }
    }

    /** Vertailu ilman IPv6-vyöhyketunnusta (fe80::1%wlan0 = fe80::1). */
    private fun sameAddress(a: InetAddress, b: InetAddress): Boolean = a.address.contentEquals(b.address)

    @Volatile private var cached: Pair<Long, Snapshot>? = null
    private const val CACHE_MS = 1_000L

    /**
     * Laitteen nykyiset verkot (välimuisti 1 s, ettei yhteystulva hidasta vastaanottoa).
     * Virhetilanteessa tyhjä: silloin vain yksityiset osoitteet hyväksytään.
     */
    fun snapshot(context: Context, now: Long = System.currentTimeMillis()): Snapshot {
        cached?.let { (at, snap) -> if (now - at in 0 until CACHE_MS) return snap }
        val fresh = read(context)
        cached = now to fresh
        return fresh
    }

    @Suppress("DEPRECATION")
    private fun read(context: Context): Snapshot = runCatching {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return Snapshot()
        val denied = HashSet<InetAddress>()
        val deniedIfaces = HashSet<String>()
        val lan = ArrayList<Prefix>()
        cm.allNetworks.forEach { network ->
            val caps = cm.getNetworkCapabilities(network) ?: return@forEach
            val props = cm.getLinkProperties(network) ?: return@forEach
            // VPN tarkistetaan ensin: VPN-verkolla on myös alla olevan verkon kuljetus (Wi-Fi tai mobiili).
            when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) ||
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                    props.linkAddresses.forEach { denied.add(it.address) }
                    props.interfaceName?.let { deniedIfaces.add(it) }
                }
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ->
                    props.linkAddresses.forEach { lan.add(Prefix(it.address, it.prefixLength)) }
            }
        }
        Snapshot(denied, deniedIfaces, lan)
    }.onFailure {
        runCatching { android.util.Log.w("AikaPolku", "Verkkojen luku epäonnistui: vain yksityiset osoitteet hyväksytään") }
    }.getOrDefault(Snapshot())

    /** Liitäntä, jonka osoite on [local], ja sen osoitealueet. */
    fun localPrefixes(local: InetAddress): LocalInterface = runCatching {
        val ni = NetworkInterface.getByInetAddress(local) ?: return LocalInterface()
        LocalInterface(ni.name.orEmpty(), ni.interfaceAddresses.mapNotNull { a -> a.address?.let { Prefix(it, a.networkPrefixLength.toInt()) } })
    }.getOrDefault(LocalInterface())
}
