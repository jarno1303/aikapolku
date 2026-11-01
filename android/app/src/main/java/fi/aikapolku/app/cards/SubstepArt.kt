package fi.aikapolku.app.cards

import fi.aikapolku.app.R
import fi.aikapolku.app.data.RunPreferences
import java.util.Locale

/**
 * Osavaiheiden valmiit kuvat (P0-22).
 *
 * Kuva haetaan osavaiheen nimen perusteella, joten tallennusmuotoon ei tarvita muutoksia.
 * Suomenkieliset nimet tunnistetaan aina (vanhat ohjelmat); kun [Texts] annetaan, myös
 * nimet sovelluksen nykyisellä kielellä. Ilman kuvaa suoritusnäkymä käyttää tehtävän kuvaa.
 */
internal object SubstepArt {
    /** Osavaiheen nimiresurssi -> kuva. */
    val entries: List<Pair<Int, Int>> = listOf(
        R.string.substep_socks to R.drawable.ap_step_socks,
        R.string.substep_trousers to R.drawable.ap_step_trousers,
        R.string.substep_shirt to R.drawable.ap_step_shirt,
        R.string.substep_jacket to R.drawable.ap_step_jacket,
        R.string.substep_shoes to R.drawable.ap_step_shoes,
        R.string.substep_take_toothbrush to R.drawable.ap_step_take_toothbrush,
        R.string.substep_brush_teeth to R.drawable.ap_step_brush_teeth,
        R.string.substep_put_back to R.drawable.ap_step_put_back
    )

    private val finnish: Map<String, Int> = mapOf(
        "sukat" to R.drawable.ap_step_socks,
        "housut" to R.drawable.ap_step_trousers,
        "paita" to R.drawable.ap_step_shirt,
        "takki" to R.drawable.ap_step_jacket,
        "kengät" to R.drawable.ap_step_shoes,
        "ota hammasharja" to R.drawable.ap_step_take_toothbrush,
        "pese hampaat" to R.drawable.ap_step_brush_teeth,
        "laita hammasharja takaisin" to R.drawable.ap_step_put_back,
        // Vanhoihin ohjelmiin tallentunut nimi (ennen 1.11.2026).
        "laita välineet takaisin" to R.drawable.ap_step_put_back
    )

    private fun norm(text: String) = text.trim().lowercase(Locale.ROOT)

    fun imageRes(subStepName: String, texts: Texts? = null): Int? {
        val key = norm(subStepName)
        if (key.isEmpty()) return null
        return finnish[key] ?: texts?.let { t -> entries.firstOrNull { norm(t(it.first)) == key }?.second }
    }

    /** Viestinäkymän kuva: [kind] on RunPreferences.HELP tai RunPreferences.BREAK. */
    fun messageImageRes(kind: String): Int? = when (kind) {
        RunPreferences.BREAK -> R.drawable.ap_msg_break
        RunPreferences.HELP -> R.drawable.ap_msg_help
        else -> null
    }
}
