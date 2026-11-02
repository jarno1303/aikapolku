package fi.aikapolku.app

import fi.aikapolku.app.backup.BackupArchive
import fi.aikapolku.app.backup.BackupException
import fi.aikapolku.app.backup.MediaRefs
import fi.aikapolku.app.backup.UniqueNames
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Vientitiedoston paketointi, salaus ja mediaviittaukset. Puhdas JVM-testi. */
class BackupCoreTest {
    private val entries = mapOf(
        "data.json" to """{"format":"aikapolku"}""".toByteArray(),
        "media/m1.jpg" to ByteArray(5000) { (it % 251).toByte() }
    )

    private fun reason(block: () -> Unit): BackupException.Reason {
        try {
            block()
        } catch (e: BackupException) {
            return e.reason
        }
        fail("BackupException puuttui")
        error("")
    }

    @Test
    fun `paketti aukeaa samana ilman salasanaa`() {
        val packed = BackupArchive.pack(entries, null)
        assertFalse(BackupArchive.isEncrypted(packed))
        val opened = BackupArchive.open(packed, null)
        assertEquals(entries.keys, opened.keys)
        assertArrayEquals(entries["media/m1.jpg"], opened["media/m1.jpg"])
    }

    @Test
    fun `salattu paketti aukeaa vain oikealla salasanalla`() {
        val packed = BackupArchive.pack(entries, "kissa123")
        assertTrue(BackupArchive.isEncrypted(packed))
        // Salattu sisältö ei näy selväkielisenä.
        assertFalse(String(packed, Charsets.ISO_8859_1).contains("aikapolku\""))
        assertArrayEquals(entries["data.json"], BackupArchive.open(packed, "kissa123")["data.json"])
        assertEquals(BackupException.Reason.WRONG_PASSWORD, reason { BackupArchive.open(packed, "koira123") })
        assertEquals(BackupException.Reason.PASSWORD_NEEDED, reason { BackupArchive.open(packed, null) })
    }

    @Test
    fun `muokattu salattu tiedosto torjutaan`() {
        val packed = BackupArchive.pack(entries, "kissa123")
        packed[packed.size - 5] = (packed[packed.size - 5] + 1).toByte()
        assertEquals(BackupException.Reason.WRONG_PASSWORD, reason { BackupArchive.open(packed, "kissa123") })
    }

    @Test
    fun `muu tiedosto ei ole aikapolun tiedosto`() {
        assertEquals(BackupException.Reason.NOT_AIKAPOLKU, reason { BackupArchive.open("hei".toByteArray(), null) })
    }

    @Test
    fun `polut korvataan tunnisteilla myos json-muodossa ja palautetaan`() {
        val photo = "/data/user/0/fi.aikapolku.app/files/activity_images/card_1.image"
        val longer = "$photo.bak"
        val json = """{"photo":"${MediaRefs.jsonEscaped(photo)}","raw":"$photo","other":"${MediaRefs.jsonEscaped(longer)}"}"""
        val names = mapOf(photo to "m1.image", longer to "m2.bak")
        val tokenized = MediaRefs.toTokens(json, names)
        assertFalse(tokenized.contains("activity_images"))
        assertEquals(setOf("m1.image", "m2.bak"), MediaRefs.tokens(tokenized))
        val restored = MediaRefs.fromTokens(tokenized, mapOf("m1.image" to "/uusi/m1.image"))
        assertEquals("""{"photo":"/uusi/m1.image","raw":"/uusi/m1.image","other":""}""", restored)
    }

    @Test
    fun `windows-polku tunnistetaan json-tekstista ja palautetaan kelvollisena jsonina`() {
        val photo = "C:\\Temp\\files\\activity_images\\card_1.image"
        // org.json kirjoittaa kenoviivan kahdennettuna.
        val json = "{\"photo\":\"" + photo.replace("\\", "\\\\") + "\"}"
        assertTrue(MediaRefs.isReferenced(photo, json))
        val tokenized = MediaRefs.toTokens(json, mapOf(photo to "m1.image"))
        assertEquals("{\"photo\":\"aikapolku-media:m1.image\"}", tokenized)
        val newPath = "C:\\Uusi\\m1.image"
        assertEquals(
            "{\"photo\":\"C:\\\\Uusi\\\\m1.image\"}",
            MediaRefs.fromTokens(tokenized, mapOf("m1.image" to newPath), json = true)
        )
        assertEquals(newPath, MediaRefs.fromTokens("aikapolku-media:m1.image", mapOf("m1.image" to newPath)))
        assertTrue(MediaRefs.looksLikeJson(" [1]"))
    }

    @Test
    fun `mediatiedoston nimi on turvallinen`() {
        assertEquals("m3.jpg", MediaRefs.safeName(3, "/x/../kuva.JPG"))
        assertEquals("m4", MediaRefs.safeName(4, "/x/aani"))
    }

    @Test
    fun `samanniminen saa numeron`() {
        assertEquals("Aamu", UniqueNames.unique("Aamu", listOf("Ilta")))
        assertEquals("Aamu (2)", UniqueNames.unique("Aamu", listOf("aamu")))
        assertEquals("Aamu (3)", UniqueNames.unique("Aamu", listOf("Aamu", "Aamu (2)")))
    }
}
