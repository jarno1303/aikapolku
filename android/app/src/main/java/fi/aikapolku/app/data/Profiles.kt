package fi.aikapolku.app.data

import android.content.Context
import android.content.SharedPreferences
import java.io.File
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

/** Käyttäjäprofiili: omat päiväohjelmat, kortit ja asetukset. [photo] on oma valokuva tai tyhjä. */
internal data class Profile(val id: String, val name: String, val photo: String = "")

/**
 * Profiilikohtaiset asetustiedostot.
 *
 * Oletusprofiili käyttää täsmälleen vanhoja tiedostonimiä, joten aiempi data kuuluu
 * suoraan ensimmäiseen profiiliin ilman siirtoa. Muut profiilit saavat oman päätteen.
 * Laitekohtaisia (ei profiilikohtaisia) ovat ohjaajan lukitus ja ajastimen hälytystila.
 */
internal object ProfileScope {
    const val DEFAULT_ID = "default"

    const val PROGRAMS = "aikapolku_programs_v1"
    const val CARDS = "aikapolku_cards_v1"
    const val RUN_PREFS = "aikapolku_p021c_preferences"
    const val PROGRESS = "aikapolku_program_progress_v1"
    const val TIMER_PREFS = "aikapolku_timer_preferences"
    const val WEEK = "aikapolku_week_v1"
    const val ACKS = "aikapolku_acks_v1"
    const val CHANGES = "aikapolku_changes_v1"

    /** Kaikki profiilikohtaiset tiedostot. Uusi profiilikohtainen tieto lisätään tähän. */
    val SCOPED = listOf(PROGRAMS, CARDS, RUN_PREFS, PROGRESS, TIMER_PREFS, WEEK, ACKS, CHANGES)

    fun prefsName(base: String, profileId: String): String =
        if (profileId == DEFAULT_ID) base else "${base}__$profileId"

    /** Nykyisen profiilin asetustiedosto. */
    fun prefs(context: Context, base: String): SharedPreferences =
        prefs(context, base, ProfileStore.currentId(context))

    fun prefs(context: Context, base: String, profileId: String): SharedPreferences =
        context.applicationContext.getSharedPreferences(prefsName(base, profileId), Context.MODE_PRIVATE)
}

/** Profiilien luettelo, nykyinen profiili ja valinta-asetus. */
internal object ProfileStore {
    private const val REGISTRY = "aikapolku_profiles_v1"
    private const val KEY_LIST = "profiles"
    private const val KEY_CURRENT = "current"
    private const val KEY_SELF_SELECT = "self_select"

    private fun registry(context: Context) =
        context.applicationContext.getSharedPreferences(REGISTRY, Context.MODE_PRIVATE)

    /** Profiilit. Jos mitään ei ole tallennettu, laitteessa on yksi nimetön oletusprofiili. */
    fun list(context: Context): List<Profile> {
        val parsed = decode(registry(context).getString(KEY_LIST, null))
        return parsed.ifEmpty { listOf(Profile(ProfileScope.DEFAULT_ID, "")) }
    }

    fun currentId(context: Context): String {
        val all = list(context)
        val saved = registry(context).getString(KEY_CURRENT, null)
        return all.firstOrNull { it.id == saved }?.id ?: all.first().id
    }

    fun current(context: Context): Profile = list(context).first { it.id == currentId(context) }

    /** Saako käyttäjä itse vaihtaa profiilia ilman ohjaajan PIN-koodia. Oletus: ei. */
    fun selfSelect(context: Context): Boolean = registry(context).getBoolean(KEY_SELF_SELECT, false)

    fun setSelfSelect(context: Context, allowed: Boolean) {
        registry(context).edit().putBoolean(KEY_SELF_SELECT, allowed).apply()
    }

    fun add(context: Context, name: String, photo: String = ""): Profile {
        val profile = Profile(UUID.randomUUID().toString().replace("-", "").take(12), name.trim(), photo)
        write(context, list(context) + profile)
        return profile
    }

    fun update(context: Context, profile: Profile) {
        write(context, list(context).map { if (it.id == profile.id) profile.copy(name = profile.name.trim()) else it })
    }

    fun switchTo(context: Context, id: String) {
        if (list(context).any { it.id == id }) registry(context).edit().putString(KEY_CURRENT, id).commit()
    }

    /**
     * Poistaa profiilin ja kaiken sen tiedon: päiväohjelmat, kortit, asetukset ja omat
     * kuvat ja äänitteet, joita mikään muu profiili ei käytä. Viimeistä profiilia ei poisteta.
     */
    fun delete(context: Context, id: String): Boolean {
        val all = list(context)
        val target = all.firstOrNull { it.id == id } ?: return false
        if (all.size <= 1) return false
        val remaining = all.filterNot { it.id == id }

        val removedTexts = profileTexts(context, target)
        val keptTexts = remaining.flatMap { profileTexts(context, it) }

        ProfileScope.SCOPED.forEach { base ->
            val name = ProfileScope.prefsName(base, id)
            context.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
            context.applicationContext.deleteSharedPreferences(name)
        }
        write(context, remaining)
        // Poistettu asiakas pois ryhmistä, poikkeuksista ja poissaoloista.
        GroupStore(context).removeMember(id)
        if (registry(context).getString(KEY_CURRENT, null) == id) {
            registry(context).edit().putString(KEY_CURRENT, remaining.first().id).commit()
        }

        // Profiilin omat tiedostot: sovelluksen tiedostokansiossa olevat, joihin vain poistettu profiili viittaa.
        // Polut samassa muodossa kuin tallennettaessa (filesDir.absolutePath); poisto vain kansion sisältä.
        val root = context.filesDir.canonicalFile
        val candidates = context.filesDir.walkTopDown().maxDepth(4).filter { it.isFile }.map { it.absolutePath }.toList()
        ProfileFiles.orphans(candidates, removedTexts, keptTexts).forEach { path ->
            val file = File(path).canonicalFile
            if (file.path.startsWith(root.path + File.separator) && file.isFile) file.delete()
        }
        return true
    }

    /** Kaikki profiilin tallennetut tekstit (myös JSON), joista etsitään viittaukset omiin tiedostoihin. */
    private fun profileTexts(context: Context, profile: Profile): List<String> =
        ProfileScope.SCOPED.flatMap { base ->
            ProfileScope.prefs(context, base, profile.id).all.values.filterIsInstance<String>()
        } + profile.photo

    private fun write(context: Context, profiles: List<Profile>) {
        registry(context).edit().putString(KEY_LIST, encode(profiles)).commit()
    }

    fun encode(profiles: List<Profile>): String {
        val array = JSONArray()
        profiles.forEach {
            array.put(JSONObject().put("id", it.id).put("name", it.name).put("photo", it.photo))
        }
        return array.toString()
    }

    fun decode(text: String?): List<Profile> {
        if (text.isNullOrBlank()) return emptyList()
        val array = runCatching { JSONArray(text) }.getOrElse { return emptyList() }
        return (0 until array.length()).mapNotNull { i ->
            val o = array.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optString("id")
            if (id.isBlank()) null else Profile(id, o.optString("name"), o.optString("photo"))
        }.distinctBy { it.id }
    }
}

/**
 * Profiilin poiston tiedostosiivous: puhdas logiikka, testattavissa ilman laitetta.
 * Polku tunnistetaan sekä sellaisenaan että JSON-muodossa (\/ ja \\), joten tunnistus
 * toimii kaikilla käyttöjärjestelmillä.
 */
internal object ProfileFiles {
    fun jsonEscaped(path: String): String = path.replace("\\", "\\\\").replace("/", "\\/")

    fun isReferenced(path: String, texts: Collection<String>): Boolean {
        if (path.isBlank()) return false
        val escaped = jsonEscaped(path)
        return texts.any { it.contains(path) || it.contains(escaped) }
    }

    /** Tiedostot [candidates], joihin poistettava profiili viittaa mutta jäljelle jäävät eivät. */
    fun orphans(candidates: Collection<String>, removed: Collection<String>, kept: Collection<String>): List<String> =
        candidates.filter { isReferenced(it, removed) && !isReferenced(it, kept) }
}
