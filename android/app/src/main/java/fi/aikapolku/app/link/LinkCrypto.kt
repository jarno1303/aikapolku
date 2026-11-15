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

    /**
     * Kuusinumeroinen vertailukoodi ("123 456") asiakkaan ja ohjaajan julkisista avaimista ja
     * satunnaisluvuista. Asiakas on sitoutunut omiin arvoihinsa (tiiviste) ennen kuin näkee ohjaajan
     * arvot, joten välissä oleva hyökkääjä ei voi kokeilla avaimia, kunnes koodit täsmäävät.
     */
    fun comparisonCode(clientPublic: ByteArray, serverPublic: ByteArray, clientNonce: ByteArray, serverNonce: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").apply {
            update(clientPublic); update(serverPublic); update(clientNonce); update(serverNonce)
        }.digest()
        val number = (ByteBuffer.wrap(digest, 0, 4).int.toLong() and 0xffffffffL) % 1_000_000L
        val text = "%06d".format(number)
        return text.substring(0, 3) + " " + text.substring(3)
    }

    fun randomBytes(size: Int): ByteArray = ByteArray(size).also { random.nextBytes(it) }

    /** Sitoumus: SHA-256(julkinen avain ‖ satunnaisluku). */
    fun commitment(publicKey: ByteArray, nonce: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").apply { update(publicKey); update(nonce) }.digest()

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
