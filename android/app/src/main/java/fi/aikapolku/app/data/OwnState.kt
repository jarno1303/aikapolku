package fi.aikapolku.app.data

import android.content.Context
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.json.JSONArray
import org.json.JSONObject

/** Oman tilan asteikko: kasvot (hyvä / ihan ok / huono) tai akku (jaksaminen). */
internal enum class StateScale(val key: String) {
    FACES("faces"), BATTERY("battery");

    companion object {
        fun fromKey(key: String?) = entries.firstOrNull { it.key == key } ?: FACES
    }
}

internal enum class StateLevel(val key: String) {
    GOOD("good"), OK("ok"), LOW("low");

    companion object {
        fun fromKey(key: String?) = entries.firstOrNull { it.key == key }
    }
}

internal data class StateEntry(val level: StateLevel, val at: Long)

/**
 * Oma tila (15.11.2026, JarnoL): asiakkaan oma tieto. Säilyy vain tällä laitteella (eilinen ja
 * tämä päivä), ei näy ohjaajan näkymissä, ei siirry tietojen viennissä. Asiakas voi itse näyttää
 * sen aikuiselle tai lähettää ohjaajan puhelimeen (Kerro ohjaajalle).
 * Asetukset ovat asiakaskohtaisia; oletuksena pois päältä.
 */
internal object OwnState {
    private const val ENTRIES_BASE = ProfileScope.OWN_STATE
    private const val KEY_ENTRIES = "entries"
    private const val KEY_ENABLED = "own_state_enabled"
    private const val KEY_SCALE = "own_state_scale"
    private const val KEY_AFTER = "own_state_after_tasks"

    private fun settings(context: Context) = ProfileScope.prefs(context, ProfileScope.RUN_PREFS)
    private fun entries(context: Context) = ProfileScope.prefs(context, ENTRIES_BASE)

    fun enabled(context: Context) = settings(context).getBoolean(KEY_ENABLED, false)
    fun setEnabled(context: Context, on: Boolean) = settings(context).edit().putBoolean(KEY_ENABLED, on).apply()
    fun scale(context: Context) = StateScale.fromKey(settings(context).getString(KEY_SCALE, null))
    fun setScale(context: Context, scale: StateScale) = settings(context).edit().putString(KEY_SCALE, scale.key).apply()
    /** Kysytäänkö päiväohjelmassa tehtävän jälkeen (aina ohitettavissa). */
    fun askAfterTasks(context: Context) = enabled(context) && settings(context).getBoolean(KEY_AFTER, false)
    fun setAskAfterTasks(context: Context, on: Boolean) = settings(context).edit().putBoolean(KEY_AFTER, on).apply()

    fun record(context: Context, level: StateLevel, now: Long = System.currentTimeMillis()) {
        val kept = prune(load(context), now) + StateEntry(level, now)
        entries(context).edit().putString(KEY_ENTRIES, encode(kept)).apply()
    }

    fun today(context: Context, now: Long = System.currentTimeMillis()): List<StateEntry> {
        val day = dateOf(now)
        return load(context).filter { dateOf(it.at) == day }
    }

    private fun load(context: Context) = decode(entries(context).getString(KEY_ENTRIES, null))

    /** Vain eilinen ja tämä päivä säilyvät. */
    fun prune(list: List<StateEntry>, now: Long): List<StateEntry> {
        val yesterday = dateOf(now).minusDays(1)
        return list.filter { !dateOf(it.at).isBefore(yesterday) }
    }

    private fun dateOf(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()

    fun encode(list: List<StateEntry>): String = JSONArray().also { arr ->
        list.forEach { arr.put(JSONObject().put("l", it.level.key).put("t", it.at)) }
    }.toString()

    fun decode(text: String?): List<StateEntry> = runCatching {
        val arr = JSONArray(text ?: return emptyList())
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.getJSONObject(i)
            StateLevel.fromKey(o.optString("l"))?.let { StateEntry(it, o.getLong("t")) }
        }
    }.getOrDefault(emptyList())
}
