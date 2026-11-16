package fi.aikapolku.app.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import fi.aikapolku.app.R
import fi.aikapolku.app.cards.CardArt
import fi.aikapolku.app.cards.Texts
import java.util.Locale
import org.json.JSONObject

/**
 * Toiminnan luokka (värikoodaus). Väri ei koskaan yksin kerro luokkaa: jokaisella on oma merkki
 * (muoto), joten värisokea erottaa luokat. Sävyt ovat rauhallisia; valkoinen merkki erottuu niistä.
 */
internal enum class Category(val id: String, val color: Long, val nameRes: Int) {
    MEAL("meal", 0xFFB45F06, R.string.category_meal),
    HYGIENE("hygiene", 0xFF2F6FB0, R.string.category_hygiene),
    OUTDOOR("outdoor", 0xFF2E7D32, R.string.category_outdoor),
    REST("rest", 0xFF6A4FA3, R.string.category_rest),
    ACTIVITY("activity", 0xFF8A6A00, R.string.category_activity),
    HEALTH("health", 0xFFB0304F, R.string.category_health);

    companion object {
        fun from(id: String?): Category? = entries.firstOrNull { it.id == id }
    }
}

/**
 * Korttien ja päiväohjelmien luokat koko laitteella (sama "Lounas" on ruokailu kaikille asukkaille).
 * Valmiilla korteilla on oletusluokka; ohjaaja voi vaihtaa minkä tahansa kortin tai päiväohjelman
 * luokan ja nimetä luokat uudelleen. Tunnus on kortin nimi (tai valmiin kortin tunnus), ei kuvan
 * polku, jotta luokka säilyy asukkaan puhelimeen siirrettäessä.
 */
internal object CategoryStore {
    const val PREFS = "aikapolku_categories_v1"
    private const val KEY_MAP = "map"
    private const val KEY_NAMES = "names"
    private const val NONE = "none"

    /** Asukaskohtainen: värikoodaus näkyy (oletus päällä). */
    const val ENABLED_KEY = "client_colors"

    var revision by mutableIntStateOf(0)
        private set

    /** Valmiiden korttien oletusluokat. Puuttuva = ei luokkaa (esim. Lähtö, Odota). */
    private val DEFAULTS = mapOf(
        "breakfast" to Category.MEAL, "lunch_meal" to Category.MEAL, "snack" to Category.MEAL,
        "dinner" to Category.MEAL, "evening_snack" to Category.MEAL, "lunch" to Category.MEAL,
        "brush_teeth" to Category.HYGIENE, "dress" to Category.HYGIENE, "toilet" to Category.HYGIENE,
        "wash_hands" to Category.HYGIENE,
        "outdoors" to Category.OUTDOOR, "exercise" to Category.OUTDOOR,
        "sleep" to Category.REST, "rest" to Category.REST, "break" to Category.REST,
        "free_time" to Category.REST, "reading" to Category.REST, "gaming" to Category.REST,
        "morning_meeting" to Category.ACTIVITY, "schoolwork" to Category.ACTIVITY, "crafts" to Category.ACTIVITY,
        "music" to Category.ACTIVITY, "homework" to Category.ACTIVITY
    )

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun norm(text: String) = text.trim().lowercase(Locale.ROOT)

    /**
     * Kortin tunnus nimen mukaan: valmiin kortin nimi tunnuksellaan (kielestä riippumatta), muuten
     * nimi. Kuva ei vaikuta: oma "Ainon aamiainen" valmiilla kuvalla on oma korttinsa.
     * [imagePath] riittää vain, kun nimi puuttuu.
     */
    fun key(name: String, imagePath: String = "", texts: Texts? = null): String {
        val byName = if (name.isNotBlank()) CardArt.find(name, "", texts) else CardArt.find(name, imagePath, texts)
        byName?.let { return "b:${it.id}" }
        return "n:${norm(name)}"
    }

    /** Päiväohjelman tunnus (nimi); oma etuliite, jottei samanniminen kortti ja ohjelma sekoitu. */
    fun programKey(name: String): String = "p:${norm(name)}"

    /** Viimeksi luettu valintojen JSON (kortteja piirretään listoissa paljon; ei jäsennetä joka kerta). */
    @Volatile private var cached: Pair<String, JSONObject>? = null

    /** Valinnat; palauttaa aina kopion, jota kutsuja voi muokata. */
    private fun map(context: Context): JSONObject = JSONObject(readMap(context).toString())

    private fun readMap(context: Context): JSONObject {
        val raw = prefs(context).getString(KEY_MAP, null) ?: "{}"
        cached?.let { (text, obj) -> if (text == raw) return obj }
        val parsed = runCatching { JSONObject(raw) }.getOrDefault(JSONObject())
        cached = raw to parsed
        return parsed
    }

    /** Ohjaajan valinta tunnukselle: null = ei valintaa (oletus), [Result.None] = valittu "ei luokkaa". */
    private fun explicit(map: JSONObject, key: String): Result? = when {
        key == "n:" || key == "p:" || !map.has(key) -> null
        map.optString(key) == NONE -> Result.None
        else -> Category.from(map.optString(key))?.let { Result.Some(it) }
    }

    private sealed interface Result {
        object None : Result
        data class Some(val category: Category) : Result
    }

    /**
     * Luokka näytettävälle kortille. [title] on päiväohjelman nimi (ohjelman oma valinta voittaa),
     * [name] ja [imagePath] kuvan kortti. Järjestys: ohjelman valinta, kortin valinta, oletus.
     */
    fun resolve(context: Context, name: String, imagePath: String = "", title: String? = null, texts: Texts? = null): Category? {
        val map = readMap(context)
        if (!title.isNullOrBlank()) {
            when (val r = explicit(map, programKey(title))) {
                is Result.Some -> return r.category
                Result.None -> return null
                null -> Unit
            }
        }
        val key = key(name, imagePath, texts)
        when (val r = explicit(map, key)) {
            is Result.Some -> return r.category
            Result.None -> return null
            null -> Unit
        }
        // Oletus valmiin kortin (nimi tai kuva) mukaan.
        return CardArt.find(name, imagePath, texts)?.let { DEFAULTS[it.id] }
    }

    /** Ohjaajan valinta: [category] null = ei luokkaa. */
    @Synchronized
    fun set(context: Context, key: String, category: Category?) {
        if (key == "n:" || key == "p:") return
        val map = map(context).put(key, category?.id ?: NONE)
        prefs(context).edit().putString(KEY_MAP, map.toString()).apply()
        revision++
    }

    /** Luokan nimi: ohjaajan antama tai oletus. */
    fun name(context: Context, category: Category): String =
        names(context).optString(category.id).takeIf { it.isNotBlank() } ?: context.getString(category.nameRes)

    private fun names(context: Context): JSONObject =
        runCatching { JSONObject(prefs(context).getString(KEY_NAMES, null) ?: "{}") }.getOrDefault(JSONObject())

    /**
     * Kortti tai ohjelma nimettiin uudelleen: ohjaajan valinta siirtyy uudelle nimelle, ellei
     * uudella nimellä ole jo omaa valintaa.
     */
    @Synchronized
    fun rekey(context: Context, oldKey: String, newKey: String) {
        if (oldKey == newKey || newKey == "n:" || newKey == "p:") return
        val map = map(context)
        if (!map.has(oldKey) || map.has(newKey)) return
        map.put(newKey, map.optString(oldKey))
        prefs(context).edit().putString(KEY_MAP, map.toString()).apply()
        revision++
    }

    /** Uusi nimi luokalle; tyhjä palauttaa oletusnimen. */
    @Synchronized
    fun rename(context: Context, category: Category, name: String) {
        val names = names(context)
        val clean = name.trim().take(30)
        if (clean.isEmpty()) names.remove(category.id) else names.put(category.id, clean)
        prefs(context).edit().putString(KEY_NAMES, names.toString()).apply()
        revision++
    }

    /** Näkyykö värikoodaus nykyiselle (tai annetulle) asukkaalle. */
    fun enabled(context: Context, profileId: String = ProfileStore.currentId(context)): Boolean =
        ProfileScope.prefs(context, ProfileScope.RUN_PREFS, profileId).getBoolean(ENABLED_KEY, true)

    @Synchronized
    fun setEnabled(context: Context, on: Boolean) {
        ProfileScope.prefs(context, ProfileScope.RUN_PREFS).edit().putBoolean(ENABLED_KEY, on).apply()
        revision++
    }

    /**
     * Siirto asukkaan puhelimeen (paikan laitteen paketti): valinnat ja nimet. [residentText] =
     * asukkaan paketin tiedot tekstinä: muiden asukkaiden omien korttien ja ohjelmien nimiä
     * ei viedä (valmiiden korttien valinnat menevät aina). null = kaikki (laitteen varmuuskopio).
     */
    fun export(context: Context, residentText: String? = null): JSONObject {
        val all = map(context)
        val map = if (residentText == null) all else {
            val text = residentText.lowercase(Locale.ROOT)
            JSONObject().also { out ->
                all.keys().forEach { key ->
                    val keep = key.startsWith("b:") || key.substring(2).let { it.isNotBlank() && text.contains(it) }
                    if (keep) out.put(key, all.optString(key))
                }
            }
        }
        return JSONObject().put(KEY_MAP, map).put(KEY_NAMES, names(context))
    }

    /**
     * Paikan laitteen luokat: paikan valinnat voittavat, laitteen omat muut valinnat säilyvät.
     * Luokkien nimet tulevat kokonaan paikalta (yksikön yhteiset sanat). Näkymät päivittyvät
     * PlaceSyncin kautta pääsäikeessä (SettingsRevision), joten tämä ei kirjoita Composen tilaa.
     */
    @Synchronized
    fun importFrom(context: Context, json: JSONObject?) {
        json ?: return
        val map = map(context)
        json.optJSONObject(KEY_MAP)?.let { incoming ->
            incoming.keys().forEach { key ->
                val value = incoming.optString(key)
                if (value == NONE || Category.from(value) != null) map.put(key, value)
            }
        }
        val names = json.optJSONObject(KEY_NAMES)?.let { incoming ->
            JSONObject().also { out ->
                Category.entries.forEach { c -> incoming.optString(c.id).takeIf { it.isNotBlank() }?.let { out.put(c.id, it.take(30)) } }
            }
        } ?: names(context)
        prefs(context).edit().putString(KEY_MAP, map.toString()).putString(KEY_NAMES, names.toString()).apply()
    }
}
