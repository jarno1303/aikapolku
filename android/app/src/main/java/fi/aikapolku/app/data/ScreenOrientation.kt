package fi.aikapolku.app.data

import android.content.Context
import android.content.pm.ActivityInfo

/**
 * Näytön asento asiakaskohtaisesti (havainto 15.11.2026). Vahingossa kääntyvä näkymä kuormittaa,
 * joten puhelimessa asiakkaan näkymä on oletuksena pystyssä; tabletti saa kääntyä (teline).
 * Ohjaajan roolissa asento on aina vapaa.
 */
internal object ScreenOrientation {
    const val AUTO = "auto"
    const val PORTRAIT = "portrait"
    const val LANDSCAPE = "landscape"
    const val FREE = "free"
    val MODES = listOf(AUTO, PORTRAIT, LANDSCAPE, FREE)
    private const val KEY = "screen_orientation"

    fun mode(context: Context): String =
        ProfileScope.prefs(context, ProfileScope.RUN_PREFS).getString(KEY, AUTO)?.takeIf { it in MODES } ?: AUTO

    fun setMode(context: Context, mode: String) {
        ProfileScope.prefs(context, ProfileScope.RUN_PREFS).edit().putString(KEY, mode.takeIf { it in MODES } ?: AUTO).apply()
    }

    /** Activityn requestedOrientation: asiakkaan asetus, laitteen tyyppi ja rooli. */
    fun requested(mode: String, tablet: Boolean, caregiver: Boolean): Int = when {
        caregiver -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        mode == PORTRAIT || (mode == AUTO && !tablet) -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        mode == LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }
}
