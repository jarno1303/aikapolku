package fi.aikapolku.app.data

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import fi.aikapolku.app.R
import fi.aikapolku.app.settings.SettingsRevision

/**
 * Kuka auttaa asiakasta (16.11.2026). Asiakkaan viesteissä puhutaan auttajasta sillä sanalla,
 * joka sopii ympäristöön: asumisyksikössä ohjaaja, kotona läheinen. "Aikuinen" sopii lapselle.
 * Asetus on asiakaskohtainen (RUN_PREFS); oletus on ohjaaja.
 *
 * [nominative] aloittaa lauseen ("Ohjaaja kertoo …"), [allative] on muodossa "ohjaajalle".
 */
internal enum class Helper(val key: String, val label: Int, val nominative: Int, val allative: Int) {
    CAREGIVER("ohjaaja", R.string.helper_caregiver, R.string.helper_caregiver_nom, R.string.helper_caregiver_to),
    RELATIVE("laheinen", R.string.helper_relative, R.string.helper_relative_nom, R.string.helper_relative_to),
    ADULT("aikuinen", R.string.helper_adult, R.string.helper_adult_nom, R.string.helper_adult_to);

    companion object {
        internal const val KEY = "helper_word"

        fun of(key: String?): Helper = entries.firstOrNull { it.key == key } ?: CAREGIVER

        fun current(context: Context): Helper =
            of(ProfileScope.prefs(context, ProfileScope.RUN_PREFS).getString(KEY, null))

        fun set(context: Context, helper: Helper) {
            ProfileScope.prefs(context, ProfileScope.RUN_PREFS).edit().putString(KEY, helper.key).apply()
        }

        /** "ohjaajalle" / "läheiselle" / "aikuiselle" – myös Composen ulkopuolella (viestit, ääni). */
        fun to(context: Context): String = context.getString(current(context).allative)

        /** "Ohjaaja" / "Läheinen" / "Aikuinen" lauseen alkuun. */
        fun nominative(context: Context): String = context.getString(current(context).nominative)
    }
}

/** Auttajan sana asiakkaan näkymään; päivittyy, kun asetus muuttuu. */
@Composable
internal fun helperTo(): String {
    val context = LocalContext.current
    val rev = SettingsRevision.value
    return remember(rev) { Helper.to(context) }
}

@Composable
internal fun helperNominative(): String {
    val context = LocalContext.current
    val rev = SettingsRevision.value
    return remember(rev) { Helper.nominative(context) }
}
