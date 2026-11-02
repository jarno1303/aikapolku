package fi.aikapolku.app.data

import android.content.Context

/** P0-21c: Local presentation and support-message settings; no account or network. */
/** Apu- ja taukopyyntö. [key] on asetusten tallennusavain (RunPreferences.HELP / BREAK). */
internal enum class SupportKind(val key: String) {
    HELP(RunPreferences.HELP),
    BREAK(RunPreferences.BREAK)
}

internal object RunPreferences {
    const val NEXT_HIDDEN = "hidden"
    const val NEXT_TEXT = "text"
    const val NEXT_CARD = "card"
    const val AUDIO_SILENT = "silent"
    const val AUDIO_TTS = "tts"
    const val AUDIO_RECORDED = "recorded"
    const val HELP = "help"
    /** Valmis, kun kestotehtävän aikaa on jäljellä: aina, kysy ensin (oletus) tai vasta ajan päätyttyä. */
    const val DONE_ALWAYS = "always"
    const val DONE_CONFIRM = "confirm"
    const val DONE_AFTER_TIME = "after_time"
    private val doneModes = setOf(DONE_ALWAYS, DONE_CONFIRM, DONE_AFTER_TIME)
    const val BREAK = "break"
    /** Kestotehtävän aika: alkaa itsestään (oletus) tai kun asiakas painaa Aloita aika. */
    const val TIMER_AUTO = "auto"
    const val TIMER_BUTTON = "button"
    private val timerModes = setOf(TIMER_AUTO, TIMER_BUTTON)

    private fun prefs(context: Context) = ProfileScope.prefs(context, ProfileScope.RUN_PREFS)

    fun nextMode(context: Context): String =
        (prefs(context).getString("next_mode", NEXT_CARD) ?: NEXT_CARD)
            .takeIf { it in setOf(NEXT_HIDDEN, NEXT_TEXT, NEXT_CARD) } ?: NEXT_CARD

    fun setNextMode(context: Context, mode: String) {
        if (mode in setOf(NEXT_HIDDEN, NEXT_TEXT, NEXT_CARD))
            prefs(context).edit().putString("next_mode", mode).apply()
    }

    fun doneMode(context: Context): String =
        (prefs(context).getString("done_mode", DONE_CONFIRM) ?: DONE_CONFIRM).takeIf { it in doneModes } ?: DONE_CONFIRM

    fun setDoneMode(context: Context, mode: String) {
        if (mode in doneModes) prefs(context).edit().putString("done_mode", mode).apply()
    }

    fun timerStart(context: Context): String =
        (prefs(context).getString("timer_start", TIMER_AUTO) ?: TIMER_AUTO).takeIf { it in timerModes } ?: TIMER_AUTO

    fun setTimerStart(context: Context, mode: String) {
        if (mode in timerModes) prefs(context).edit().putString("timer_start", mode).apply()
    }

    fun voiceMode(context: Context, kind: String): String =
        (prefs(context).getString("audio_$kind", AUDIO_SILENT) ?: AUDIO_SILENT)
            .takeIf { it in setOf(AUDIO_SILENT, AUDIO_TTS, AUDIO_RECORDED) } ?: AUDIO_SILENT

    fun setVoiceMode(context: Context, kind: String, mode: String) {
        if (kind in setOf(HELP, BREAK) && mode in setOf(AUDIO_SILENT, AUDIO_TTS, AUDIO_RECORDED)) {
            prefs(context).edit().putString("audio_$kind", mode).apply()
        }
    }

    fun recording(context: Context, kind: String): String =
        if (kind in setOf(HELP, BREAK)) prefs(context).getString("recording_$kind", "") ?: "" else ""

    fun setRecording(context: Context, kind: String, path: String) {
        if (kind in setOf(HELP, BREAK))
            prefs(context).edit().putString("recording_$kind", path).apply()
    }
}
