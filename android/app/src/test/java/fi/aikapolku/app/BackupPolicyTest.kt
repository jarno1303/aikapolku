package fi.aikapolku.app

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Asiakkaiden tiedot eivät lähde laitteelta Androidin automaattisessa varmuuskopiossa
 * (Googlen pilvi) eivätkä laitteesta laitteeseen -siirrossa. Tavallinen JVM-testi:
 * lukee lähdetiedostot (Gradlen yksikkötestit ajetaan app-kansiosta).
 */
class BackupPolicyTest {
    private fun read(path: String) = File(path).readText()

    @Test
    fun `automaattinen varmuuskopio on pois`() {
        val manifest = read("src/main/AndroidManifest.xml")
        assertTrue(manifest.contains("android:allowBackup=\"false\""))
        assertFalse(manifest.contains("android:allowBackup=\"true\""))
    }

    @Test
    fun `pilvivarmuuskopio ja laitesiirto eivat sisalla mitaan`() {
        val rules = read("src/main/res/xml/data_extraction_rules.xml")
        listOf("<cloud-backup>", "<device-transfer>").forEach { section ->
            val body = rules.substringAfter(section).substringBefore("</" + section.drop(1))
            assertFalse("$section ei saa sisältää include-sääntöjä", body.contains("<include"))
            listOf("root", "file", "database", "sharedpref", "external").forEach { domain ->
                assertTrue("$section: $domain pois", body.contains("<exclude domain=\"$domain\" path=\".\""))
            }
        }
        val legacy = read("src/main/res/xml/backup_rules.xml")
        assertFalse(legacy.contains("<include"))
        assertTrue(legacy.contains("<exclude domain=\"sharedpref\" path=\".\""))
    }
}
