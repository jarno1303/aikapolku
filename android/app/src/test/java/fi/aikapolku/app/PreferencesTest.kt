package fi.aikapolku.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import fi.aikapolku.app.data.RunPreferences
import fi.aikapolku.app.data.TimeDisplayPrefs
import fi.aikapolku.app.timer.BackgroundTimer
import fi.aikapolku.app.voice.VoicePrefs
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Käyttäjän näkymä- ja ääniasetukset: oletukset, tallennus ja virheellisten arvojen torjunta. */
@RunWith(RobolectricTestRunner::class)
class PreferencesTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    // --- Ajan näyttötapa (TimeDisplayPrefs) ---

    @Test
    fun `ajan naytto on oletuksena numerot`() {
        assertEquals(TimeDisplayPrefs.NUMBERS, TimeDisplayPrefs.load(context))
    }

    @Test
    fun `ajan nayttotapa sailyy`() {
        TimeDisplayPrefs.save(context, TimeDisplayPrefs.CALM)
        assertEquals(TimeDisplayPrefs.CALM, TimeDisplayPrefs.load(context))
    }

    @Test
    fun `virheellista nayttotapaa ei tallenneta`() {
        TimeDisplayPrefs.save(context, TimeDisplayPrefs.RING)
        TimeDisplayPrefs.save(context, "tuntematon")
        assertEquals(TimeDisplayPrefs.RING, TimeDisplayPrefs.load(context))
    }

    @Test
    fun `rikkinainen tallennettu nayttotapa palautuu oletukseen`() {
        context.getSharedPreferences("aikapolku_timer_preferences", Context.MODE_PRIVATE)
            .edit().putString("time_display_mode", "rikki").commit()
        assertEquals(TimeDisplayPrefs.NUMBERS, TimeDisplayPrefs.load(context))
    }

    // --- Päättymisääni ---

    @Test
    fun `paattymisaani on oletuksena pois`() {
        assertEquals("off", BackgroundTimer.selectedSound(context))
    }

    // --- Seuraavan vaiheen näkyvyys ja viestiäänet (P021c) ---

    @Test
    fun `seuraava vaihe naytetaan oletuksena himmennettyna korttina`() {
        assertEquals(RunPreferences.NEXT_CARD, RunPreferences.nextMode(context))
    }

    @Test
    fun `seuraavan vaiheen nakyvyys sailyy ja virheellinen arvo ohitetaan`() {
        RunPreferences.setNextMode(context, RunPreferences.NEXT_HIDDEN)
        RunPreferences.setNextMode(context, "jotain")
        assertEquals(RunPreferences.NEXT_HIDDEN, RunPreferences.nextMode(context))
    }

    @Test
    fun `viestiaanet ovat oletuksena hiljaiset`() {
        assertEquals(RunPreferences.AUDIO_SILENT, RunPreferences.voiceMode(context, RunPreferences.HELP))
        assertEquals(RunPreferences.AUDIO_SILENT, RunPreferences.voiceMode(context, RunPreferences.BREAK))
    }

    @Test
    fun `apu- ja taukoviestin aanet asetetaan toisistaan riippumatta`() {
        RunPreferences.setVoiceMode(context, RunPreferences.HELP, RunPreferences.AUDIO_TTS)
        RunPreferences.setVoiceMode(context, RunPreferences.BREAK, RunPreferences.AUDIO_RECORDED)

        assertEquals(RunPreferences.AUDIO_TTS, RunPreferences.voiceMode(context, RunPreferences.HELP))
        assertEquals(RunPreferences.AUDIO_RECORDED, RunPreferences.voiceMode(context, RunPreferences.BREAK))
    }

    @Test
    fun `tuntematonta viestityyppia ei tallenneta`() {
        RunPreferences.setVoiceMode(context, "tuntematon", RunPreferences.AUDIO_TTS)
        RunPreferences.setRecording(context, "tuntematon", "/x.m4a")

        assertEquals(RunPreferences.AUDIO_SILENT, RunPreferences.voiceMode(context, "tuntematon"))
        assertEquals("", RunPreferences.recording(context, "tuntematon"))
    }

    @Test
    fun `oma aanitys tallentuu viestikohtaisesti`() {
        RunPreferences.setRecording(context, RunPreferences.HELP, "/apu.m4a")

        assertEquals("/apu.m4a", RunPreferences.recording(context, RunPreferences.HELP))
        assertEquals("", RunPreferences.recording(context, RunPreferences.BREAK))
    }

    // --- Puheääni (P021d) ---

    @Test
    fun `puheaanen oletukset`() {
        assertEquals("", VoicePrefs.voiceId(context))
        assertEquals(1.0f, VoicePrefs.speed(context), 0.0001f)
    }

    @Test
    fun `puheaani ja nopeus sailyvat`() {
        VoicePrefs.setVoiceId(context, "fi-fi-x-abc-local")
        VoicePrefs.setSpeed(context, 0.9f)

        assertEquals("fi-fi-x-abc-local", VoicePrefs.voiceId(context))
        assertEquals(0.9f, VoicePrefs.speed(context), 0.0001f)
    }

    @Test
    fun `puhenopeus rajataan valille 0_8-1_2`() {
        VoicePrefs.setSpeed(context, 3.0f)
        assertEquals(1.2f, VoicePrefs.speed(context), 0.0001f)

        VoicePrefs.setSpeed(context, 0.1f)
        assertEquals(0.8f, VoicePrefs.speed(context), 0.0001f)
    }
}
