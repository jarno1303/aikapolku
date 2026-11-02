package fi.aikapolku.app.cards

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import fi.aikapolku.app.R
import fi.aikapolku.app.data.CardItem
import fi.aikapolku.app.data.DayTime
import java.util.Locale

/** Resurssitekstin haku (Androidissa `{ context.getString(it) }`, testeissä mikä tahansa funktio). Pitää tietokerroksen erillään Contextista. */
internal typealias Texts = (Int) -> String

/**
 * Valmiit korttikuvat. Nimi on käännettävä resurssi ([nameRes]); [dayTime] ryhmittelee kortit valinnassa.
 * [legacyName] on suomenkielinen nimi, jolla vanhat tallennetut ohjelmat ja kortit tunnistetaan
 * kielestä riippumatta – sitä ei näytetä käyttäjälle eikä käännetä.
 */
internal object CardArt {
    internal data class Template(
        val id: String,
        val legacyName: String,
        val nameRes: Int,
        val dayTime: DayTime,
        val drawable: Int
    )

    /** Järjestys: aamu, päivä, ilta, milloin tahansa – ryhmän sisällä tavallinen kulku. */
    val templates: List<Template> = listOf(
        Template("breakfast", "Aamupala", R.string.card_breakfast, DayTime.MORNING, R.drawable.ap_card_breakfast),
        Template("brush_teeth", "Hampaiden pesu", R.string.card_brush_teeth, DayTime.MORNING, R.drawable.ap_card_brush_teeth),
        Template("dress", "Pukeutuminen", R.string.card_dress, DayTime.MORNING, R.drawable.ap_card_dress),
        Template("morning_meeting", "Aamukokous", R.string.card_morning_meeting, DayTime.MORNING, R.drawable.ap_card_morning_meeting),
        Template("leaving", "Lähtö", R.string.card_leaving, DayTime.MORNING, R.drawable.ap_card_leaving),
        Template("schoolwork", "Koulutyö", R.string.card_schoolwork, DayTime.DAY, R.drawable.ap_card_schoolwork),
        Template("lunch_meal", "Lounas", R.string.card_lunch_meal, DayTime.DAY, R.drawable.ap_card_lunch_meal),
        Template("snack", "Välipala", R.string.card_snack, DayTime.DAY, R.drawable.ap_card_snack),
        Template("outdoors", "Ulkoilu", R.string.card_outdoors, DayTime.DAY, R.drawable.ap_card_outdoors),
        Template("exercise", "Liikunta", R.string.card_exercise, DayTime.DAY, R.drawable.ap_card_exercise),
        Template("crafts", "Askartelu", R.string.card_crafts, DayTime.DAY, R.drawable.ap_card_crafts),
        Template("music", "Musiikki", R.string.card_music, DayTime.DAY, R.drawable.ap_card_music),
        Template("dinner", "Päivällinen", R.string.card_dinner, DayTime.EVENING, R.drawable.ap_card_dinner),
        Template("homework", "Kotitehtävät", R.string.card_homework, DayTime.EVENING, R.drawable.ap_card_homework),
        Template("reading", "Lukeminen", R.string.card_reading, DayTime.EVENING, R.drawable.ap_card_reading),
        Template("gaming", "Pelaaminen", R.string.card_gaming, DayTime.EVENING, R.drawable.ap_card_gaming),
        Template("evening_snack", "Iltapala", R.string.card_evening_snack, DayTime.EVENING, R.drawable.ap_card_evening_snack),
        Template("sleep", "Nukkumaan", R.string.card_sleep, DayTime.EVENING, R.drawable.ap_card_sleep),
        Template("lunch", "Ruokailu", R.string.card_lunch, DayTime.ANYTIME, R.drawable.ap_card_lunch),
        Template("toilet", "WC", R.string.card_toilet, DayTime.ANYTIME, R.drawable.ap_card_toilet),
        Template("wash_hands", "Käsienpesu", R.string.card_wash_hands, DayTime.ANYTIME, R.drawable.ap_card_wash_hands),
        Template("break", "Tauko", R.string.card_break, DayTime.ANYTIME, R.drawable.ap_card_break),
        Template("waiting", "Odota", R.string.card_waiting, DayTime.ANYTIME, R.drawable.ap_card_waiting),
        Template("rest", "Lepo", R.string.card_rest, DayTime.ANYTIME, R.drawable.ap_card_rest)
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

/**
 * Valittavat kortit vuorokauden mukaan ryhmiteltyinä (aamu, päivä, ilta, milloin tahansa).
 * Omat kortit ovat ryhmänsä alussa. Sama nimi näytetään kerran: oma kortti korvaa
 * samannimisen valmiin. Tyhjät ryhmät jätetään pois.
 */
internal fun cardsByDayTime(custom: List<CardItem>, texts: Texts): List<Pair<DayTime, List<CardItem>>> {
    fun key(name: String) = name.trim().lowercase(Locale.ROOT)
    val own = custom.distinctBy { key(it.name) }
    val ownNames = own.map { key(it.name) }.toSet()
    val builtIn = CardArt.templates
        .filter { key(texts(it.nameRes)) !in ownNames }
        .map { t ->
            CardItem(
                id = "basic_${t.id}",
                name = texts(t.nameRes),
                description = "",
                photoPath = "builtin:${t.id}",
                dayTime = t.dayTime
            )
        }
    return DayTime.entries.mapNotNull { time ->
        val group = own.filter { it.dayTime == time } + builtIn.filter { it.dayTime == time }
        if (group.isEmpty()) null else time to group
    }
}

/** Ajankohdan nimi käyttäjälle. */
internal fun DayTime.labelRes(): Int = when (this) {
    DayTime.MORNING -> R.string.day_time_morning
    DayTime.DAY -> R.string.day_time_day
    DayTime.EVENING -> R.string.day_time_evening
    DayTime.ANYTIME -> R.string.day_time_anytime
}
