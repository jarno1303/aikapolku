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

    fun save(context: Context, mode: String) {
        if (mode in values) {
            ProfileScope.prefs(context, ProfileScope.TIMER_PREFS)
                .edit().putString(KEY, mode).apply()
        }
    }
}
