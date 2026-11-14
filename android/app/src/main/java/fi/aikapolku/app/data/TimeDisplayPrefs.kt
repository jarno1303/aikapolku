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

    /**
     * Ennakkovaroitus ennen vaihtoa (tutkielma A1/A2): minuutteina, 0 = pois. Oletus 5 min.
     * Koskee ajastinta (Ensin–Sitten) ja päiväohjelman ajastettuja vaiheita.
     */
    fun warnMinutes(context: Context): Int =
        ProfileScope.prefs(context, ProfileScope.TIMER_PREFS).getInt(WARN_KEY, WARN_DEFAULT)
            .takeIf { it in WARN_OPTIONS } ?: WARN_DEFAULT

    fun setWarnMinutes(context: Context, minutes: Int) {
        if (minutes in WARN_OPTIONS) ProfileScope.prefs(context, ProfileScope.TIMER_PREFS).edit().putInt(WARN_KEY, minutes).apply()
    }

    /**
     * Näytetäänkö ennakkovaroitus: aika käy, jäljellä on enintään [warnMinutes] ja vaihe on
     * varoitusaikaa pidempi (lyhyessä vaiheessa varoitus näkyisi heti alusta, eikä se ole ennakko).
     */
    fun showSoon(remainingMs: Long, durationMs: Long, running: Boolean, warnMinutes: Int): Boolean {
        if (!running || warnMinutes <= 0) return false
        val warnMs = warnMinutes * 60_000L
        return durationMs > warnMs && remainingMs in 1..warnMs
    }

    val WARN_OPTIONS = listOf(0, 2, 5, 10)
    const val WARN_DEFAULT = 5
    private const val WARN_KEY = "transition_warn_min"

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
