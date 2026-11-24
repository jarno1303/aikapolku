package fi.aikapolku.app.link

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

/**
 * Vastaanotto vain lähiverkosta. Yhteys hyväksytään, kun se tulee yksikön omasta verkosta
 * (Wi-Fi, Ethernet tai puhelimen jakama yhteyspiste), ei koskaan mobiilidatan kautta.
 * Päätös on puhdas funktio ([allowed]) ja testattavissa JVM:ssä; Android-osa ([snapshot])
 * kerää laitteen verkot.
 */
internal object LinkNetwork {
    /** Verkon osoitealue, esim. 192.168.1.0/24. */
    data class Prefix(val address: InetAddress, val length: Int)

    /** Laitteen verkot hyväksymishetkellä. */
    data class Snapshot(
        /** Mobiilidataverkkojen omat osoitteet: niihin tulevia yhteyksiä ei hyväksytä. */
        val cellular: Set<InetAddress> = emptySet(),
        /** Wi-Fi- ja Ethernet-verkkojen osoitealueet (kattaa myös julkiset IPv6-osoitteet lähiverkossa). */
        val lan: List<Prefix> = emptyList()
    )

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
     * Hyväksytäänkö yhteys etäosoitteesta [remote], joka saapui tämän laitteen osoitteeseen [local].
     *  - oma laite (loopback): kyllä
     *  - saapui mobiilidataverkon osoitteeseen: ei (operaattorin verkossa olevat laitteet eivät saa yhteyttä)
     *  - muuten: etäosoite on yksityinen tai jonkin Wi-Fi- tai Ethernet-verkon alueella
     * Julkinen osoite, joka ei ole oman lähiverkon alueella (esim. internetistä), hylätään.
     */
    fun allowed(remote: InetAddress, local: InetAddress, snapshot: Snapshot): Boolean {
        if (remote.isLoopbackAddress) return true
        if (snapshot.cellular.any { sameAddress(it, local) }) return false
        return isPrivate(remote) || snapshot.lan.any { inPrefix(remote, it) }
    }

    /** Vertailu ilman IPv6-vyöhyketunnusta (fe80::1%wlan0 = fe80::1). */
    private fun sameAddress(a: InetAddress, b: InetAddress): Boolean = a.address.contentEquals(b.address)

    /** Laitteen nykyiset verkot. Virhetilanteessa tyhjä: silloin vain yksityiset osoitteet hyväksytään. */
    @Suppress("DEPRECATION")
    fun snapshot(context: Context): Snapshot = runCatching {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return Snapshot()
        val cellular = HashSet<InetAddress>()
        val lan = ArrayList<Prefix>()
        cm.allNetworks.forEach { network ->
            val caps = cm.getNetworkCapabilities(network) ?: return@forEach
            val links = cm.getLinkProperties(network)?.linkAddresses.orEmpty()
            when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> links.forEach { cellular.add(it.address) }
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ->
                    links.forEach { if (it.address is Inet4Address || it.address is Inet6Address) lan.add(Prefix(it.address, it.prefixLength)) }
            }
        }
        Snapshot(cellular, lan)
    }.getOrDefault(Snapshot())
}
