package fi.aikapolku.app.data

import fi.aikapolku.app.R
import fi.aikapolku.app.cards.CardArt
import fi.aikapolku.app.cards.Texts

/** P0-21: Valmiiden korttien oletusosavaiheet sovelluksen nykyisellä kielellä. */
internal object SubstepDefaults {
    private val byCard: Map<String, List<Int>> = mapOf(
        "dress" to listOf(R.string.substep_socks, R.string.substep_trousers, R.string.substep_shirt,
            R.string.substep_jacket, R.string.substep_shoes),
        "brush_teeth" to listOf(R.string.substep_take_toothbrush, R.string.substep_brush_teeth,
            R.string.substep_put_back)
    )

    /** Kortin nimen (millä tahansa tunnistetulla kielellä) tai "builtin:id"-polun perusteella. */
    fun forCard(cardName: String, texts: Texts, imagePath: String = ""): List<String> =
        forCardId(CardArt.find(cardName, imagePath, texts)?.id, texts)

    fun forCardId(cardId: String?, texts: Texts): List<String> =
        byCard[cardId].orEmpty().map(texts)
}
