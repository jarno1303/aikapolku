package fi.aikapolku.app.link

import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * T1: salaisuudet (laiteliitosten avaimet, varmuuskopion salasana) Android Keystoren avaimella
 * salattuna. Keystoren avain ei koskaan poistu laitteen suojatusta tallennuksesta.
 * Jos salaus epäonnistuu, arvoa ei tallenneta lainkaan (ei salaamatonta varakopiota).
 * Vain testiympäristössä, jossa Keystorea ei ole, arvo jää salaamatta.
 */
internal object KeyVault {
    private const val ALIAS = "aikapolku_vault_v1"
    private const val PREFIX = "kv1:"

    /** Yksi luonti kerrallaan: kaksi säiettä ei saa luoda kahta eri avainta. */
    @Synchronized
    private fun key(): SecretKey? = runCatching {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey ?: run {
            val generator = KeyGenerator.getInstance("AES", "AndroidKeyStore")
            generator.init(
                android.security.keystore.KeyGenParameterSpec.Builder(
                    ALIAS,
                    android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or android.security.keystore.KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            )
            generator.generateKey()
        }
    }.getOrNull()

    fun isSealed(text: String): Boolean = text.startsWith(PREFIX)

    /**
     * Onko Android Keystore tässä ympäristössä lainkaan olemassa. Oikeassa laitteessa aina on;
     * puuttuu vain JVM- ja Robolectric-testeissä, joissa arvo saa jäädä salaamatta.
     */
    private val keystoreMissing: Boolean by lazy { java.security.Security.getProvider("AndroidKeyStore") == null }

    /** Vain testeihin: pakottaa salauksen epäonnistumaan (oikean laitteen virhepolku). */
    @Volatile internal var failForTest = false

    /**
     * Salattu teksti etuliitteellä. null = salaus epäonnistui laitteessa, jossa Keystore on:
     * kutsuja ei saa tallentaa arvoa (salaisuutta ei koskaan tallenneta salaamatta oikeaan laitteeseen).
     * Vain testiympäristössä ilman Keystorea palautetaan alkuperäinen.
     */
    fun seal(plain: String): String? {
        if (failForTest) return null
        if (keystoreMissing) return plain
        val k = key() ?: return null.also { warn("Keystoren avain ei käytettävissä: arvoa ei tallenneta") }
        return runCatching {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, k)
            val iv = cipher.iv
            val sealed = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
            PREFIX + Base64.getEncoder().encodeToString(iv + sealed)
        }.onFailure { warn("Salaus epäonnistui: arvoa ei tallenneta") }.getOrNull()
    }

    private fun warn(text: String) {
        runCatching { android.util.Log.w("AikaPolku", text) }
    }

    /** Avaa [seal]in tuloksen; vanha salaamaton arvo palautuu sellaisenaan. null = ei aukea. */
    fun open(text: String): String? {
        if (!text.startsWith(PREFIX)) return text
        val k = key() ?: return null
        return runCatching {
            val bytes = Base64.getDecoder().decode(text.removePrefix(PREFIX))
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, k, GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
            String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8)
        }.getOrNull()
    }
}
