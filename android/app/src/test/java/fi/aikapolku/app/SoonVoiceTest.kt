package fi.aikapolku.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** "Pian vaihtuu" -ennakkovaroituksen ääni: mitä soitetaan. Puhdas JVM-testi. */
class SoonVoiceTest {
    private fun plan(
        mode: String,
        client: String = "",
        general: String = "",
        nextName: String? = "Lounas",
        nextVoice: String = "",
        muted: Boolean = false
    ) = SoonVoice.plan(mode, client, general, nextName, nextVoice, muted, "Pian vaihtuu") { "Seuraavaksi: $it" }

    @Test
    fun `oletus ei aanta`() {
        assertEquals(SoonVoicePlan.None, plan(RunPreferences.AUDIO_SILENT, general = "/g.m4a"))
    }

    @Test
    fun `puheaani kertoo seuraavan`() {
        assertEquals(SoonVoicePlan.Speak("Pian vaihtuu. Seuraavaksi: Lounas."), plan(RunPreferences.AUDIO_TTS))
    }

    @Test
    fun `piilotettu seuraava ei paljastu puheessa eika aanitteessa`() {
        assertEquals(SoonVoicePlan.Speak("Pian vaihtuu."), plan(RunPreferences.AUDIO_TTS, nextName = null))
        assertEquals(
            SoonVoicePlan.Play(listOf("/g.m4a")),
            plan(RunPreferences.AUDIO_RECORDED, general = "/g.m4a", nextName = null, nextVoice = "/lounas.m4a")
        )
    }

    @Test
    fun `yleinen aanite soi kun asiakkaalla ei ole omaa`() {
        assertEquals(SoonVoicePlan.Play(listOf("/g.m4a")), plan(RunPreferences.AUDIO_RECORDED, general = "/g.m4a"))
    }

    @Test
    fun `asiakkaan oma aanite korvaa yleisen`() {
        assertEquals(
            SoonVoicePlan.Play(listOf("/oma.m4a")),
            plan(RunPreferences.AUDIO_RECORDED, client = "/oma.m4a", general = "/g.m4a")
        )
    }

    @Test
    fun `seuraavan tehtavan oma aani soi peraan`() {
        assertEquals(
            SoonVoicePlan.Play(listOf("/g.m4a", "/lounas.m4a")),
            plan(RunPreferences.AUDIO_RECORDED, general = "/g.m4a", nextVoice = "/lounas.m4a")
        )
    }

    @Test
    fun `ilman aanitetta oma aani on hiljaa`() {
        assertEquals(SoonVoicePlan.None, plan(RunPreferences.AUDIO_RECORDED))
    }

    @Test
    fun `aaneton puhelin ei soita mitaan`() {
        assertEquals(SoonVoicePlan.None, plan(RunPreferences.AUDIO_TTS, muted = true))
        assertEquals(SoonVoicePlan.None, plan(RunPreferences.AUDIO_RECORDED, general = "/g.m4a", muted = true))
    }
}
