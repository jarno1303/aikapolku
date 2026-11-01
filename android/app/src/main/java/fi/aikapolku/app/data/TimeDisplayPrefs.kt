package fi.aikapolku.app.data

import android.content.Context
import fi.aikapolku.app.timer.BackgroundTimer

/** Only changes the presentation. The BackgroundTimer alarm and deadline are unchanged. */
internal object TimeDisplayPrefs {
    const val NUMBERS = "numbers"
    const val RING = "ring"
    const val CALM = "calm"
    private const val PREFS = "aikapolku_timer_preferences"
    private const val KEY = "time_display_mode"
    private val values = setOf(NUMBERS, RING, CALM)

    fun load(context: Context): String {
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, NUMBERS) ?: NUMBERS
        return if (saved in values) saved else NUMBERS
    }

    fun save(context: Context, mode: String) {
        if (mode in values) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY, mode).apply()
        }
    }
}
