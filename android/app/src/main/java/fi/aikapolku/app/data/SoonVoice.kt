package fi.aikapolku.app.data

import android.content.Context

/** Mitä "Pian vaihtuu" -ennakkovaroituksen tullessa soitetaan. */
internal sealed interface SoonVoicePlan {
    data object None : SoonVoicePlan
    data class Speak(val text: String) : SoonVoicePlan
    data class Play(val paths: List<String>) : SoonVoicePlan
}

/**
 * "Pian vaihtuu" -ennakkovaroituksen ääni (A1/A2 jatko, 15.11.2026).
 *
 * - Äänen tapa (ei ääntä / puheääni / oma ääni) valitaan asiakaskohtaisesti; oletus ei ääntä,
 *   jotta ääniherkät eivät kuormitu.
 * - Oma ääni: laitteen yleinen äänite kaikille asiakkaille, ja asiakkaalle voi tarvittaessa
 *   äänittää oman, joka korvaa yleisen.
 * - Oman äänitteen perään soi seuraavan tehtävän oma ääni, jos sellainen on ja seuraava
 *   näytetään asiakkaalle.
 * - Ei soi, kun puhelin on äänettömällä tai Älä häiritse -tilassa.
 */
internal object SoonVoice {
    private const val SHARED = "aikapolku_shared_audio_v1"
    private const val KEY_GENERAL = "recording_soon_general"

    fun mode(context: Context): String = RunPreferences.voiceMode(context, RunPreferences.SOON)

    fun generalRecording(context: Context): String =
        context.getSharedPreferences(SHARED, Context.MODE_PRIVATE).getString(KEY_GENERAL, "") ?: ""

    fun setGeneralRecording(context: Context, path: String) {
        context.getSharedPreferences(SHARED, Context.MODE_PRIVATE).edit().putString(KEY_GENERAL, path).apply()
    }

    fun clientRecording(context: Context): String = RunPreferences.recording(context, RunPreferences.SOON)

    fun setClientRecording(context: Context, path: String) =
        RunPreferences.setRecording(context, RunPreferences.SOON, path)

    /** Asiakkaan oma äänite korvaa yleisen. */
    fun effectiveRecording(client: String, general: String): String = client.ifBlank { general }

    /**
     * @param nextName seuraavan nimi tai null, jos ohjaaja on piilottanut seuraavan.
     * @param nextVoicePath seuraavan tehtävän oma ääni (tyhjä = ei ole).
     * @param soonText "Pian vaihtuu"; [nextText] muotoilee "Seuraavaksi: X".
     */
    fun plan(
        mode: String,
        clientRecording: String,
        generalRecording: String,
        nextName: String?,
        nextVoicePath: String,
        muted: Boolean,
        soonText: String,
        nextText: (String) -> String
    ): SoonVoicePlan {
        if (muted) return SoonVoicePlan.None
        val hasNext = !nextName.isNullOrBlank()
        return when (mode) {
            RunPreferences.AUDIO_TTS ->
                SoonVoicePlan.Speak(if (hasNext) "$soonText. ${nextText(nextName!!)}." else "$soonText.")
            RunPreferences.AUDIO_RECORDED -> {
                val phrase = effectiveRecording(clientRecording, generalRecording)
                if (phrase.isBlank()) SoonVoicePlan.None
                else SoonVoicePlan.Play(listOf(phrase) + listOfNotNull(nextVoicePath.takeIf { hasNext && it.isNotBlank() }))
            }
            else -> SoonVoicePlan.None
        }
    }
}
