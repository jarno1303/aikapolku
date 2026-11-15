package fi.aikapolku.app.link

import java.nio.ByteBuffer
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Laitteiden välinen salaus (V5a). Pariliitos: ECDH (P-256) kertakäyttöisillä avaimilla ja
 * numerovertailu – ohjaaja tarkistaa, että molemmissa laitteissa näkyy sama kuusinumeroinen koodi
 * (sama periaate kuin Bluetooth-pariliitoksessa). Yhteinen avain johdetaan HKDF-SHA256:lla ja
 * viestit salataan AES-GCM:llä. Ei Android-riippuvuuksia: testattavissa JVM:ssä.
 */
internal object LinkCrypto {
    private const val INFO = "aikapolku-link-v1"
    private val random = SecureRandom()

    fun newKeyPair(): KeyPair = KeyPairGenerator.getInstance("EC").apply {
        initialize(ECGenParameterSpec("secp256r1"), random)
    }.generateKeyPair()

    fun publicBytes(pair: KeyPair): ByteArray = pair.public.encoded

    private fun publicKey(bytes: ByteArray): PublicKey =
        KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(bytes))

    /** Yhteinen 32 tavun avain. Molemmat osapuolet saavat saman tunnuksista riippumatta järjestyksestä. */
    fun sharedKey(own: KeyPair, peerPublic: ByteArray, idA: String, idB: String): ByteArray {
        val agreement = KeyAgreement.getInstance("ECDH")
        agreement.init(own.private)
        agreement.doPhase(publicKey(peerPublic), true)
        val secret = agreement.generateSecret()
        val salt = listOf(idA, idB).sorted().joinToString("|").toByteArray()
        return hkdf(secret, salt, INFO.toByteArray(), 32)
    }

    /** Kuusinumeroinen vertailukoodi molempien julkisista avaimista ("123 456"). */
    fun comparisonCode(publicA: ByteArray, publicB: ByteArray): String {
        val (first, second) = listOf(publicA, publicB).sortedWith { a, b -> compareBytes(a, b) }
        val digest = MessageDigest.getInstance("SHA-256").apply { update(first); update(second) }.digest()
        val number = (ByteBuffer.wrap(digest, 0, 4).int.toLong() and 0xffffffffL) % 1_000_000L
        val text = "%06d".format(number)
        return text.substring(0, 3) + " " + text.substring(3)
    }

    private fun compareBytes(a: ByteArray, b: ByteArray): Int {
        for (i in 0 until minOf(a.size, b.size)) {
            val d = (a[i].toInt() and 0xff) - (b[i].toInt() and 0xff)
            if (d != 0) return d
        }
        return a.size - b.size
    }

    fun hkdf(ikm: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray {
        val extract = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(if (salt.isEmpty()) ByteArray(32) else salt, "HmacSHA256")) }
        val prk = extract.doFinal(ikm)
        val expand = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(prk, "HmacSHA256")) }
        val out = ByteArray(length)
        var previous = ByteArray(0)
        var offset = 0
        var counter = 1
        while (offset < length) {
            expand.update(previous)
            expand.update(info)
            expand.update(counter.toByte())
            previous = expand.doFinal()
            val n = minOf(previous.size, length - offset)
            System.arraycopy(previous, 0, out, offset, n)
            offset += n
            counter++
        }
        return out
    }

    /** Salaa: palauttaa (nonce 12 tavua, salateksti + tunniste). [aad] sidotaan viestiin (lähettäjän tunnus). */
    fun seal(key: ByteArray, plaintext: ByteArray, aad: ByteArray): Pair<ByteArray, ByteArray> {
        val nonce = ByteArray(12).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        cipher.updateAAD(aad)
        return nonce to cipher.doFinal(plaintext)
    }

    /** Purkaa; null, jos avain, tunniste tai [aad] ei täsmää (väärennetty tai vieras viesti). */
    fun open(key: ByteArray, nonce: ByteArray, ciphertext: ByteArray, aad: ByteArray): ByteArray? = runCatching {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        cipher.updateAAD(aad)
        cipher.doFinal(ciphertext)
    }.getOrNull()
}
