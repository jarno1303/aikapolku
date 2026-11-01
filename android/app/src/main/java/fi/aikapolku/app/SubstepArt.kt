package fi.aikapolku.app

import java.util.Locale

/**
 * Osavaiheiden valmiit kuvat (P0-22, työkuvat 1.11.2026).
 *
 * Kuva haetaan osavaiheen nimen perusteella, joten tallennusmuotoon ei tarvita muutoksia.
 * Jos osavaiheelle ei ole kuvaa, suoritusnäkymä käyttää tehtävän omaa kuvaa.
 * Lopulliset kuvat vaihdetaan samoilla tiedostonimillä.
 */
internal object SubstepArt {
    private val byName: Map<String, Int> = mapOf(
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

    fun imageRes(subStepName: String): Int? =
        byName[subStepName.trim().lowercase(Locale.ROOT)]

    /** Viestinäkymän kuva, jos sellainen on. Apupyynnön kuva puuttuu vielä tarkoituksella. */
    fun messageImageRes(message: String): Int? = when (message) {
        "Tarvitsen tauon" -> R.drawable.ap_msg_break
        "Tarvitsen apua" -> R.drawable.ap_msg_help
        else -> null
    }
}
