package fi.aikapolku.app.data

import android.content.Context
import fi.aikapolku.app.timer.BackgroundTimer

/** Only changes the presentation. The BackgroundTimer alarm and deadline are unchanged. */
internal object TimeDisplayPrefs {
    const val NUMBERS = "numbers"
    const val RING = "ring"
    const val CALM = "calm"
    private const val KEY = "time_display_mode"
    private val values = setOf(NUMBERS, RING, CALM)

    fun load(context: Context): String {
        val saved = ProfileScope.prefs(context, ProfileScope.TIMER_PREFS)
            .getString(KEY, NUMBERS) ?: NUMBERS
        return if (saved in values) saved else NUMBERS
    }

    /** Viimeisten 10 sekunnin laskenta ajastimessa (vain numeronäytössä). */
    fun lastTen(context: Context): Boolean =
        ProfileScope.prefs(context, ProfileScope.TIMER_PREFS).getBoolean(LAST_TEN_KEY, true)

    fun setLastTen(context: Context, on: Boolean) {
        ProfileScope.prefs(context, ProfileScope.TIMER_PREFS).edit().putBoolean(LAST_TEN_KEY, on).apply()
    }

    /** Äänimerkki ajan päättyessä: [SOUNDS]. Sama asetus koskee ajastinta ja päiväohjelmaa. */
    fun endSound(context: Context): String =
        (ProfileScope.prefs(context, ProfileScope.TIMER_PREFS).getString(END_SOUND_KEY, SOUND_OFF) ?: SOUND_OFF)
            .takeIf { it in SOUNDS } ?: SOUND_OFF

    fun setEndSound(context: Context, sound: String) {
        if (sound in SOUNDS) ProfileScope.prefs(context, ProfileScope.TIMER_PREFS).edit().putString(END_SOUND_KEY, sound).apply()
    }

    const val SOUND_OFF = "off"
    const val SOUND_SHORT = "short"
    const val SOUND_CLEAR = "clear"
    val SOUNDS = listOf(SOUND_OFF, SOUND_SHORT, SOUND_CLEAR)
    private const val LAST_TEN_KEY = "last_ten"
    // Avain säilyy ennallaan (BackgroundTimer.selectedSound lukee samaa).
    private const val END_SOUND_KEY = "end_sound"

    fun save(context: Context, mode: String) {
        if (mode in values) {
            ProfileScope.prefs(context, ProfileScope.TIMER_PREFS)
                .edit().putString(KEY, mode).apply()
        }
    }
}
