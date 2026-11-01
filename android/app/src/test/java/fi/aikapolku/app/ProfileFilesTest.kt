package fi.aikapolku.app

import fi.aikapolku.app.data.ProfileFiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Profiilin poiston tiedostosiivous: vain poistettavan profiilin omat tiedostot poistetaan. */
class ProfileFilesTest {
    private val androidPath = "/data/user/0/fi.aikapolku.app/files/activity_images/card_1.image"
    private val windowsPath = "C:\\Users\\X\\AppData\\files\\activity_images\\card_2.image"

    @Test
    fun `polku tunnistetaan JSONin kenoviivamuodosta`() {
        val json = """[{"photoPath":"\/data\/user\/0\/fi.aikapolku.app\/files\/activity_images\/card_1.image"}]"""
        assertTrue(ProfileFiles.isReferenced(androidPath, listOf(json)))
    }

    @Test
    fun `windows-polku tunnistetaan sellaisenaan ja JSON-muodossa`() {
        val json = """{"photoPath":"C:\\Users\\X\\AppData\\files\\activity_images\\card_2.image"}"""
        assertTrue(ProfileFiles.isReferenced(windowsPath, listOf(windowsPath)))
        assertTrue(ProfileFiles.isReferenced(windowsPath, listOf(json)))
    }

    @Test
    fun `muiden profiilien kayttamaa tiedostoa ei poisteta`() {
        val voice = "/data/user/0/fi.aikapolku.app/files/voice_messages/x.m4a"
        val removed = listOf(androidPath, """{"v":"$voice"}""")
        val kept = listOf("""[{"photo":"$androidPath"}]""")
        assertEquals(listOf(voice), ProfileFiles.orphans(listOf(androidPath, voice), removed, kept))
    }

    @Test
    fun `tiedostoa johon kukaan ei viittaa ei poisteta`() {
        assertEquals(emptyList<String>(), ProfileFiles.orphans(listOf(androidPath), listOf("builtin:dress", ""), emptyList()))
    }
}
