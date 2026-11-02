package fi.aikapolku.app.backup

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Viennin tai tuonnin virhe, jonka viesti näytetään ohjaajalle. */
internal class BackupException(val reason: Reason) : Exception(reason.name) {
    enum class Reason { NOT_AIKAPOLKU, TOO_NEW, WRONG_PASSWORD, PASSWORD_NEEDED, TOO_LARGE, BROKEN }
}

/**
 * Vientitiedoston (.aikapolku) paketointi: zip-paketti, valinnaisesti salasanalla salattuna
 * (AES-256-GCM, avain PBKDF2-HMAC-SHA256). Salattu tiedosto alkaa tunnisteella [MAGIC].
 * Puhdas JVM-koodi, testattavissa ilman laitetta.
 */
internal object BackupArchive {
    private val MAGIC = "AIKAPOLKU-ENC1".toByteArray(Charsets.US_ASCII)
    private const val SALT = 16
    private const val IV = 12
    private const val ITERATIONS = 120_000
    const val MAX_TOTAL_BYTES = 300L * 1024 * 1024
    const val MAX_ENTRIES = 5_000
    const val MIN_PASSWORD = 6

    fun isEncrypted(bytes: ByteArray): Boolean =
        bytes.size > MAGIC.size && bytes.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)

    fun isZip(bytes: ByteArray): Boolean = bytes.size > 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()

    fun zip(entries: Map<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            entries.forEach { (name, data) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(data)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    /** Purkaa paketin muistiin. Nimiä käytetään vain avaimina, ei tiedostopolkuina. */
    fun unzip(bytes: ByteArray): Map<String, ByteArray> {
        if (!isZip(bytes)) throw BackupException(BackupException.Reason.NOT_AIKAPOLKU)
        val result = LinkedHashMap<String, ByteArray>()
        var total = 0L
        try {
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.isDirectory) continue
                    if (result.size >= MAX_ENTRIES) throw BackupException(BackupException.Reason.TOO_LARGE)
                    val buffer = ByteArrayOutputStream()
                    val chunk = ByteArray(16 * 1024)
                    while (true) {
                        val n = zip.read(chunk)
                        if (n < 0) break
                        total += n
                        if (total > MAX_TOTAL_BYTES) throw BackupException(BackupException.Reason.TOO_LARGE)
                        buffer.write(chunk, 0, n)
                    }
                    result[entry.name] = buffer.toByteArray()
                }
            }
        } catch (e: BackupException) {
            throw e
        } catch (e: Exception) {
            throw BackupException(BackupException.Reason.BROKEN)
        }
        return result
    }

    private fun key(password: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, 256)
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        spec.clearPassword()
        return SecretKeySpec(bytes, "AES")
    }

    fun encrypt(plain: ByteArray, password: String, random: SecureRandom = SecureRandom()): ByteArray {
        val salt = ByteArray(SALT).also(random::nextBytes)
        val iv = ByteArray(IV).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(password, salt), GCMParameterSpec(128, iv))
        cipher.updateAAD(MAGIC)
        return MAGIC + salt + iv + cipher.doFinal(plain)
    }

    fun decrypt(bytes: ByteArray, password: String): ByteArray {
        if (!isEncrypted(bytes) || bytes.size < MAGIC.size + SALT + IV + 16) throw BackupException(BackupException.Reason.BROKEN)
        var at = MAGIC.size
        val salt = bytes.copyOfRange(at, at + SALT).also { at += SALT }
        val iv = bytes.copyOfRange(at, at + IV).also { at += IV }
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(password, salt), GCMParameterSpec(128, iv))
            cipher.updateAAD(MAGIC)
            cipher.doFinal(bytes, at, bytes.size - at)
        } catch (e: AEADBadTagException) {
            throw BackupException(BackupException.Reason.WRONG_PASSWORD)
        } catch (e: Exception) {
            throw BackupException(BackupException.Reason.WRONG_PASSWORD)
        }
    }

    /** Pakkaa ja tarvittaessa salaa. */
    fun pack(entries: Map<String, ByteArray>, password: String?): ByteArray {
        val zipped = zip(entries)
        return if (password.isNullOrEmpty()) zipped else encrypt(zipped, password)
    }

    /** Avaa paketin. Salattu vaatii salasanan ([BackupException.Reason.PASSWORD_NEEDED]). */
    fun open(bytes: ByteArray, password: String?): Map<String, ByteArray> = when {
        isEncrypted(bytes) ->
            if (password.isNullOrEmpty()) throw BackupException(BackupException.Reason.PASSWORD_NEEDED)
            else unzip(decrypt(bytes, password))
        else -> unzip(bytes)
    }
}

/**
 * Omien kuvien ja äänitteiden polut tallennetussa tekstissä (myös JSON:ssa, jossa "/" voi olla
 * muodossa "\/"). Viennissä polku korvataan tunnisteella "aikapolku-media:nimi", tuonnissa
 * tunniste uudella polulla.
 */
internal object MediaRefs {
    const val PREFIX = "aikapolku-media:"
    private val TOKEN = Regex("aikapolku-media:([A-Za-z0-9._-]+)")

    /** Polku JSON-merkkijonon sisällä, kuten org.json sen kirjoittaa (kenoviiva ja vinoviiva). */
    fun jsonEscaped(path: String) = path.replace("\\", "\\\\").replace("/", "\\/")

    fun isReferenced(path: String, text: String): Boolean =
        path.isNotBlank() && (text.contains(path) || text.contains(jsonEscaped(path)))

    /** [names]: polku -> median nimi. Pidemmät polut ensin, ettei osa polusta korvaudu. */
    fun toTokens(text: String, names: Map<String, String>): String {
        var result = text
        names.entries.sortedByDescending { it.key.length }.forEach { (path, name) ->
            result = result.replace(jsonEscaped(path), PREFIX + name).replace(path, PREFIX + name)
        }
        return result
    }

    fun tokens(text: String): Set<String> = TOKEN.findAll(text).map { it.groupValues[1] }.toSet()

    /**
     * [paths]: median nimi -> uusi polku. Tuntematon tunniste (puuttuva media) tyhjenee.
     * [json] = teksti on JSON:ia, jolloin polku kirjoitetaan JSON-muodossa.
     */
    fun fromTokens(text: String, paths: Map<String, String>, json: Boolean = false): String =
        TOKEN.replace(text) { m ->
            val path = paths[m.groupValues[1]] ?: ""
            if (json) jsonEscaped(path) else path
        }

    /** Tallennettu arvo on JSON-luettelo tai -olio (esim. päiväohjelmat), ei pelkkä polku. */
    fun looksLikeJson(text: String): Boolean = text.trimStart().let { it.startsWith("[") || it.startsWith("{") }

    /** Turvallinen tiedostonimi medialle. */
    fun safeName(index: Int, original: String): String {
        val ext = original.substringAfterLast('.', "").lowercase().filter { it.isLetterOrDigit() }.take(5)
        return "m$index" + if (ext.isNotEmpty()) ".$ext" else ""
    }
}

/** Nimen yksilöinti tuonnissa: "Aamu" -> "Aamu (2)", "Aamu (3)" … */
internal object UniqueNames {
    fun unique(name: String, taken: Collection<String>): String {
        val used = taken.map { it.trim().lowercase() }.toSet()
        if (name.trim().lowercase() !in used) return name
        var n = 2
        while ("$name ($n)".trim().lowercase() in used) n++
        return "$name ($n)"
    }
}
