package fi.aikapolku.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
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

    // --- Ajan näyttötapa (P020TimeDisplay) ---

    @Test
    fun `ajan naytto on oletuksena numerot`() {
        assertEquals(P020TimeDisplay.NUMBERS, P020TimeDisplay.load(context))
    }

    @Test
    fun `ajan nayttotapa sailyy`() {
        P020TimeDisplay.save(context, P020TimeDisplay.CALM)
        assertEquals(P020TimeDisplay.CALM, P020TimeDisplay.load(context))
    }

    @Test
    fun `virheellista nayttotapaa ei tallenneta`() {
        P020TimeDisplay.save(context, P020TimeDisplay.RING)
        P020TimeDisplay.save(context, "tuntematon")
        assertEquals(P020TimeDisplay.RING, P020TimeDisplay.load(context))
    }

    @Test
    fun `rikkinainen tallennettu nayttotapa palautuu oletukseen`() {
        context.getSharedPreferences("aikapolku_timer_preferences", Context.MODE_PRIVATE)
            .edit().putString("time_display_mode", "rikki").commit()
        assertEquals(P020TimeDisplay.NUMBERS, P020TimeDisplay.load(context))
    }

    // --- Päättymisääni ---

    @Test
    fun `paattymisaani on oletuksena pois`() {
        assertEquals("off", P018Timer.selectedSound(context))
    }

    // --- Seuraavan vaiheen näkyvyys ja viestiäänet (P021c) ---

    @Test
    fun `seuraava vaihe naytetaan oletuksena himmennettyna korttina`() {
        assertEquals(P021cPreferences.NEXT_CARD, P021cPreferences.nextMode(context))
    }

    @Test
    fun `seuraavan vaiheen nakyvyys sailyy ja virheellinen arvo ohitetaan`() {
        P021cPreferences.setNextMode(context, P021cPreferences.NEXT_HIDDEN)
        P021cPreferences.setNextMode(context, "jotain")
        assertEquals(P021cPreferences.NEXT_HIDDEN, P021cPreferences.nextMode(context))
    }

    @Test
    fun `viestiaanet ovat oletuksena hiljaiset`() {
        assertEquals(P021cPreferences.AUDIO_SILENT, P021cPreferences.voiceMode(context, P021cPreferences.HELP))
        assertEquals(P021cPreferences.AUDIO_SILENT, P021cPreferences.voiceMode(context, P021cPreferences.BREAK))
    }

    @Test
    fun `apu- ja taukoviestin aanet asetetaan toisistaan riippumatta`() {
        P021cPreferences.setVoiceMode(context, P021cPreferences.HELP, P021cPreferences.AUDIO_TTS)
        P021cPreferences.setVoiceMode(context, P021cPreferences.BREAK, P021cPreferences.AUDIO_RECORDED)

        assertEquals(P021cPreferences.AUDIO_TTS, P021cPreferences.voiceMode(context, P021cPreferences.HELP))
        assertEquals(P021cPreferences.AUDIO_RECORDED, P021cPreferences.voiceMode(context, P021cPreferences.BREAK))
    }

    @Test
    fun `tuntematonta viestityyppia ei tallenneta`() {
        P021cPreferences.setVoiceMode(context, "tuntematon", P021cPreferences.AUDIO_TTS)
        P021cPreferences.setRecording(context, "tuntematon", "/x.m4a")

        assertEquals(P021cPreferences.AUDIO_SILENT, P021cPreferences.voiceMode(context, "tuntematon"))
        assertEquals("", P021cPreferences.recording(context, "tuntematon"))
    }

    @Test
    fun `oma aanitys tallentuu viestikohtaisesti`() {
        P021cPreferences.setRecording(context, P021cPreferences.HELP, "/apu.m4a")

        assertEquals("/apu.m4a", P021cPreferences.recording(context, P021cPreferences.HELP))
        assertEquals("", P021cPreferences.recording(context, P021cPreferences.BREAK))
    }

    // --- Puheääni (P021d) ---

    @Test
    fun `puheaanen oletukset`() {
        assertEquals("", P021dVoicePrefs.voiceId(context))
        assertEquals(1.0f, P021dVoicePrefs.speed(context), 0.0001f)
    }

    @Test
    fun `puheaani ja nopeus sailyvat`() {
        P021dVoicePrefs.setVoiceId(context, "fi-fi-x-abc-local")
        P021dVoicePrefs.setSpeed(context, 0.9f)

        assertEquals("fi-fi-x-abc-local", P021dVoicePrefs.voiceId(context))
        assertEquals(0.9f, P021dVoicePrefs.speed(context), 0.0001f)
    }

    @Test
    fun `puhenopeus rajataan valille 0_8-1_2`() {
        P021dVoicePrefs.setSpeed(context, 3.0f)
        assertEquals(1.2f, P021dVoicePrefs.speed(context), 0.0001f)

        P021dVoicePrefs.setSpeed(context, 0.1f)
        assertEquals(0.8f, P021dVoicePrefs.speed(context), 0.0001f)
    }
}
