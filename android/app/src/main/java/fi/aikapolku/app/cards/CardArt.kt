package fi.aikapolku.app.cards

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import fi.aikapolku.app.R
import java.util.Locale

/** Resurssitekstin haku (Androidissa `{ context.getString(it) }`, testeissä mikä tahansa funktio). Pitää tietokerroksen erillään Contextista. */
internal typealias Texts = (Int) -> String

/**
 * Valmiit korttikuvat. Nimi ja luokka ovat käännettäviä resursseja ([nameRes], [categoryRes]).
 * [legacyName] on suomenkielinen nimi, jolla vanhat tallennetut ohjelmat ja kortit tunnistetaan
 * kielestä riippumatta – sitä ei näytetä käyttäjälle eikä käännetä.
 */
internal object CardArt {
    internal data class Template(
        val id: String,
        val legacyName: String,
        val nameRes: Int,
        val categoryRes: Int,
        val drawable: Int
    )

    val templates: List<Template> = listOf(
        Template("breakfast", "Aamupala", R.string.card_breakfast, R.string.card_category_meals, R.drawable.ap_card_breakfast),
        Template("lunch", "Ruokailu", R.string.card_lunch, R.string.card_category_meals, R.drawable.ap_card_lunch),
        Template("snack", "Välipala", R.string.card_snack, R.string.card_category_meals, R.drawable.ap_card_snack),
        Template("dress", "Pukeutuminen", R.string.card_dress, R.string.card_category_self_care, R.drawable.ap_card_dress),
        Template("wash_hands", "Käsienpesu", R.string.card_wash_hands, R.string.card_category_self_care, R.drawable.ap_card_wash_hands),
        Template("toilet", "WC", R.string.card_toilet, R.string.card_category_self_care, R.drawable.ap_card_toilet),
        Template("brush_teeth", "Hampaiden pesu", R.string.card_brush_teeth, R.string.card_category_self_care, R.drawable.ap_card_brush_teeth),
        Template("outdoors", "Ulkoilu", R.string.card_outdoors, R.string.card_category_movement, R.drawable.ap_card_outdoors),
        Template("exercise", "Liikunta", R.string.card_exercise, R.string.card_category_movement, R.drawable.ap_card_exercise),
        Template("crafts", "Askartelu", R.string.card_crafts, R.string.card_category_activities, R.drawable.ap_card_crafts),
        Template("gaming", "Pelaaminen", R.string.card_gaming, R.string.card_category_activities, R.drawable.ap_card_gaming),
        Template("reading", "Lukeminen", R.string.card_reading, R.string.card_category_activities, R.drawable.ap_card_reading),
        Template("music", "Musiikki", R.string.card_music, R.string.card_category_activities, R.drawable.ap_card_music),
        Template("schoolwork", "Koulutyö", R.string.card_schoolwork, R.string.card_category_school, R.drawable.ap_card_schoolwork),
        Template("homework", "Kotitehtävät", R.string.card_homework, R.string.card_category_school, R.drawable.ap_card_homework),
        Template("break", "Tauko", R.string.card_break, R.string.card_category_transitions, R.drawable.ap_card_break),
        Template("waiting", "Odota", R.string.card_waiting, R.string.card_category_transitions, R.drawable.ap_card_waiting),
        Template("leaving", "Lähtö", R.string.card_leaving, R.string.card_category_transitions, R.drawable.ap_card_leaving),
        Template("rest", "Lepo", R.string.card_rest, R.string.card_category_rest, R.drawable.ap_card_rest),
        Template("sleep", "Nukkumaan", R.string.card_sleep, R.string.card_category_rest, R.drawable.ap_card_sleep),
        Template("morning_meeting", "Aamukokous", R.string.card_morning_meeting, R.string.card_category_group, R.drawable.ap_card_morning_meeting)
    )

    private val legacyAliases = mapOf(
        "tehtävät" to "schoolwork", // Vanha korttinimi.
        "aamupiiri" to "morning_meeting",
        "nukkuminen" to "sleep"
    )

    private fun norm(text: String) = text.trim().lowercase(Locale.ROOT)

    /**
     * Valmis kortti nimen tai "builtin:id"-polun perusteella. Tunnistaa tunnuksen, suomenkielisen
     * (myös vanhan) nimen sekä – kun [texts] annetaan – nimen sovelluksen nykyisellä kielellä.
     */
    fun find(name: String, imagePath: String = "", texts: Texts? = null): Template? {
        val key = norm(if (imagePath.startsWith("builtin:")) imagePath.removePrefix("builtin:") else name)
        val alias = legacyAliases[key] ?: key
        return templates.firstOrNull { it.id == alias || norm(it.legacyName) == alias }
            ?: texts?.let { t -> templates.firstOrNull { norm(t(it.nameRes)) == key } }
    }

    fun imageRes(name: String, imagePath: String = "", texts: Texts? = null): Int? =
        find(name, imagePath, texts)?.drawable

    fun bitmap(context: Context, name: String, imagePath: String = ""): Bitmap? {
        val res = imageRes(name, imagePath) { context.getString(it) } ?: return null
        return BitmapFactory.decodeResource(context.resources, res)
    }
}
