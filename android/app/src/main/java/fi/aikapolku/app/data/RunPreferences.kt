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
    const val BREAK = "break"

    private fun prefs(context: Context) = ProfileScope.prefs(context, ProfileScope.RUN_PREFS)

    fun nextMode(context: Context): String =
        (prefs(context).getString("next_mode", NEXT_CARD) ?: NEXT_CARD)
            .takeIf { it in setOf(NEXT_HIDDEN, NEXT_TEXT, NEXT_CARD) } ?: NEXT_CARD

    fun setNextMode(context: Context, mode: String) {
        if (mode in setOf(NEXT_HIDDEN, NEXT_TEXT, NEXT_CARD))
            prefs(context).edit().putString("next_mode", mode).apply()
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
