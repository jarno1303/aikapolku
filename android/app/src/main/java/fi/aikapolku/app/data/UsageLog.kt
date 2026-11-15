package fi.aikapolku.app.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import fi.aikapolku.app.R
import java.time.LocalDate
import org.json.JSONObject

/** Käytön seurannan tapahtumat. [key] on tallennusmuodon arvo – älä muuta. */
internal enum class UsageKind(val key: String, val label: Int) {
    PROGRAM_START("program_start", R.string.usage_program_start),
    PROGRAM_DONE("program_done", R.string.usage_program_done),
    STEP_DONE("step_done", R.string.usage_step_done),
    HELP("help", R.string.usage_help),
    NOT_UNDERSTAND("not_understand", R.string.usage_not_understand),
    BREAK("break", R.string.usage_break),
    MORE_TIME("more_time", R.string.usage_more_time),
    CHOICE("choice", R.string.usage_choice),
    CHOICE_NONE("choice_none", R.string.usage_choice_none),
    CHANGE_SEEN("change_seen", R.string.usage_change_seen),
    WAIT("wait", R.string.usage_wait),
    DONE_EARLY("done_early", R.string.usage_done_early);

    companion object {
        fun from(key: String?): UsageKind? = entries.firstOrNull { it.key == key }

        /**
         * Asiakkaan omat pyynnöt: tuen tarpeen seuranta (tuen taso). En ymmärrä on avun
         * tarkennus samassa näkymässä, joten sitä ei lasketa erikseen (ei kahta kertaa).
         */
        val REQUESTS = listOf(HELP, BREAK, MORE_TIME)

        fun of(kind: SupportKind): UsageKind = when (kind) {
            SupportKind.HELP -> HELP
            SupportKind.NOT_UNDERSTAND -> NOT_UNDERSTAND
            SupportKind.BREAK -> BREAK
            SupportKind.MORE_TIME -> MORE_TIME
            SupportKind.CHOICE_NONE -> CHOICE_NONE
        }
    }
}

/**
 * Käytön seuranta pilottia varten (palaute 15.11.2026): vain päiväkohtaiset lukumäärät
 * asiakkaittain, ei kellonaikoja, ei sisältöä eikä omaa tilaa. Ohjaaja ottaa seurannan
 * käyttöön laitteessa; oletuksena pois. Tiedot ovat vain laitteella, säilyvät
 * [KEEP_DAYS] päivää eivätkä tule varmuuskopioon. Kooste viedään CSV:nä tunnuksin (A1, A2 …).
 */
internal object UsageLog {
    private const val DEVICE_PREFS = "aikapolku_usage_v1"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_SINCE = "since"
    private const val KEY_CODES = "codes"
    private const val KEY_DAYS = "days"
    const val KEEP_DAYS = 180L

    /** Näkymät lukevat koosteen uudelleen, kun tämä muuttuu. */
    var revision by mutableIntStateOf(0)
        private set

    private fun device(context: Context) = context.applicationContext.getSharedPreferences(DEVICE_PREFS, Context.MODE_PRIVATE)
    private fun counts(context: Context, profileId: String) = ProfileScope.prefs(context, ProfileScope.USAGE, profileId)

    fun enabled(context: Context): Boolean = device(context).getBoolean(KEY_ENABLED, false)

    /** Seurannan alkupäivä (lähtötilanteen vertailuun). */
    fun since(context: Context): LocalDate? =
        device(context).getString(KEY_SINCE, null)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    fun setEnabled(context: Context, on: Boolean, today: LocalDate = LocalDate.now()) {
        val e = device(context).edit().putBoolean(KEY_ENABLED, on)
        if (on && since(context) == null) e.putString(KEY_SINCE, today.toString())
        e.apply()
        revision++
    }

    /** Kirjaa tapahtuman nykyiselle asiakkaalle (vain, jos seuranta on käytössä). */
    fun record(context: Context, kind: UsageKind, today: LocalDate = LocalDate.now()) {
        if (!enabled(context)) return
        record(context, ProfileStore.currentId(context), kind, today)
    }

    @Synchronized
    fun record(context: Context, profileId: String, kind: UsageKind, today: LocalDate = LocalDate.now()) {
        if (!enabled(context)) return
        val prefs = counts(context, profileId)
        val days = prune(increment(decode(prefs.getString(KEY_DAYS, null)), today, kind), today)
        prefs.edit().putString(KEY_DAYS, encode(days)).apply()
        code(context, profileId)
        revision++
    }

    fun days(context: Context, profileId: String): Map<LocalDate, Map<UsageKind, Int>> =
        decode(counts(context, profileId).getString(KEY_DAYS, null))

    /** Pysyvä tunnus koostetta varten (A1, A2 …); nimi ei lähde viennissä. */
    @Synchronized
    fun code(context: Context, profileId: String): String {
        val prefs = device(context)
        val codes = runCatching { JSONObject(prefs.getString(KEY_CODES, null) ?: "{}") }.getOrDefault(JSONObject())
        codes.optString(profileId).takeIf { it.isNotBlank() }?.let { return it }
        val code = "A" + (codes.length() + 1)
        codes.put(profileId, code)
        prefs.edit().putString(KEY_CODES, codes.toString()).apply()
        return code
    }

    /** Tyhjentää kaikki laskurit ja tunnukset (seuranta-asetus säilyy). */
    fun clear(context: Context, profileIds: List<String>) {
        profileIds.forEach { counts(context, it).edit().clear().apply() }
        device(context).edit().remove(KEY_CODES).remove(KEY_SINCE).apply()
        if (enabled(context)) device(context).edit().putString(KEY_SINCE, LocalDate.now().toString()).apply()
        revision++
    }

    // --- Puhdas logiikka (testattavissa ilman laitetta) ---

    fun increment(days: Map<LocalDate, Map<UsageKind, Int>>, date: LocalDate, kind: UsageKind): Map<LocalDate, Map<UsageKind, Int>> {
        val day = days[date].orEmpty()
        return days + (date to (day + (kind to (day[kind] ?: 0) + 1)))
    }

    fun prune(days: Map<LocalDate, Map<UsageKind, Int>>, today: LocalDate, keep: Long = KEEP_DAYS): Map<LocalDate, Map<UsageKind, Int>> =
        days.filterKeys { !it.isBefore(today.minusDays(keep - 1)) && !it.isAfter(today) }

    /** Lukumäärät ajalta [from]..[to] (molemmat mukaan). */
    fun total(days: Map<LocalDate, Map<UsageKind, Int>>, from: LocalDate, to: LocalDate): Map<UsageKind, Int> {
        val result = mutableMapOf<UsageKind, Int>()
        days.filterKeys { !it.isBefore(from) && !it.isAfter(to) }.values.forEach { day ->
            day.forEach { (k, n) -> result[k] = (result[k] ?: 0) + n }
        }
        return result
    }

    /** Montako päivää jaksolla oli jotain kirjattua (käyttöaste). */
    fun activeDays(days: Map<LocalDate, Map<UsageKind, Int>>, from: LocalDate, to: LocalDate): Int =
        days.count { (d, m) -> !d.isBefore(from) && !d.isAfter(to) && m.values.any { it > 0 } }

    /** CSV: tunnus;päivä;tapahtumat sarakkeina. Ei nimiä. */
    fun csv(rows: List<Pair<String, Map<LocalDate, Map<UsageKind, Int>>>>): String {
        val sb = StringBuilder()
        sb.append("tunnus;paiva;").append(UsageKind.entries.joinToString(";") { it.key }).append('\n')
        rows.forEach { (code, days) ->
            days.keys.sorted().forEach { d ->
                val m = days.getValue(d)
                sb.append(code).append(';').append(d).append(';')
                    .append(UsageKind.entries.joinToString(";") { (m[it] ?: 0).toString() }).append('\n')
            }
        }
        return sb.toString()
    }

    fun encode(days: Map<LocalDate, Map<UsageKind, Int>>): String = JSONObject().also { o ->
        days.forEach { (d, m) -> o.put(d.toString(), JSONObject().also { j -> m.forEach { (k, n) -> j.put(k.key, n) } }) }
    }.toString()

    fun decode(text: String?): Map<LocalDate, Map<UsageKind, Int>> = runCatching {
        val o = JSONObject(text ?: return emptyMap())
        o.keys().asSequence().mapNotNull { key ->
            val date = runCatching { LocalDate.parse(key) }.getOrNull() ?: return@mapNotNull null
            val j = o.optJSONObject(key) ?: return@mapNotNull null
            date to j.keys().asSequence().mapNotNull { k -> UsageKind.from(k)?.let { it to j.optInt(k) } }.toMap()
        }.toMap()
    }.getOrDefault(emptyMap())
}
