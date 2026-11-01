package fi.aikapolku.app.data

import java.util.Locale

/** P0-21: Simple editable substeps, kept separate from the parent step timer. */
internal object SubstepDefaults {
    fun forCard(cardName: String): List<String> = when (cardName.trim().lowercase(Locale.ROOT)) {
        "pukeutuminen" -> listOf("Sukat", "Housut", "Paita", "Takki", "Kengät")
        "hampaiden pesu" -> listOf("Ota hammasharja", "Pese hampaat", "Laita hammasharja takaisin")
        else -> emptyList()
    }
}
