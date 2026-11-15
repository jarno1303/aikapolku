package fi.aikapolku.app.data

import android.content.Context
import org.json.JSONObject

/**
 * Valinta kahdesta toiminnasta (15.11.2026): vaiheella tai Sitten-kortilla voi olla toinen
 * vaihtoehto. Asiakas valitsee ennen aloitusta; ohjaaja voi sallia vastauksen "Ei kumpikaan".
 */
internal data class ChoiceAlt(val name: String, val photo: String, val voicePath: String = "") {
    fun toJson(): JSONObject = JSONObject().put("name", name).put("photo", photo).put("voicePath", voicePath)

    companion object {
        fun fromJson(o: JSONObject?): ChoiceAlt? =
            o?.optString("name")?.takeIf { it.isNotBlank() }?.let { ChoiceAlt(it, o.optString("photo"), o.optString("voicePath")) }
    }
}

/** Valintanäkymän vaihtoehto. */
internal data class ChoiceOption(val name: String, val photo: String, val voicePath: String = "")

internal object ChoicePrefs {
    private const val KEY_NONE = "choice_allow_none"

    /** Saako asiakas vastata "En halua kumpaakaan" (asiakaskohtainen, oletus ei). */
    fun allowNone(context: Context): Boolean =
        ProfileScope.prefs(context, ProfileScope.RUN_PREFS).getBoolean(KEY_NONE, false)

    fun setAllowNone(context: Context, allowed: Boolean) {
        ProfileScope.prefs(context, ProfileScope.RUN_PREFS).edit().putBoolean(KEY_NONE, allowed).apply()
    }
}

/** Vaihe valinnan jälkeen: valittu vaihtoehto korvaa vaiheen kuvan, nimen ja äänen. */
internal fun ProgramStep.chosen(useAlternative: Boolean): ProgramStep {
    val alt = alternative ?: return this
    return if (!useAlternative) copy(alternative = null)
    else copy(
        name = alt.name, photo = alt.photo, voicePath = alt.voicePath, description = "",
        autoPlayVoice = false, subSteps = emptyList(), alternative = null
    )
}
