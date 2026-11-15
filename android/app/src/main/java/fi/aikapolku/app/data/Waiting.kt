package fi.aikapolku.app.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject

/**
 * Odotetaan (15.11.2026): odotus, jonka kestoa ei aina tiedä (taksi myöhässä, ruoka ei valmis).
 * Ohjaaja aloittaa ja lopettaa ("Nyt mennään"); asiakas näkee mitä odotetaan, rauhallisen kuvion
 * tai ajan kulun (kesto tiedossa) ja kortit "Odottaessa voit".
 * [minutes] 0 = kesto ei tiedossa. Ajat seinäkelloa (ms), koska odotus säilyy sovelluksen sulkemisen yli.
 */
internal data class WaitState(val name: String, val photo: String, val startedAt: Long, val minutes: Int) {
    fun elapsedMs(now: Long): Long = (now - startedAt).coerceAtLeast(0L)

    /** 0..1 kuinka suuri osa arvioidusta odotuksesta on jäljellä; null = kesto ei tiedossa. */
    fun fractionLeft(now: Long): Float? =
        if (minutes <= 0) null else (1f - elapsedMs(now).toFloat() / (minutes * 60_000f)).coerceIn(0f, 1f)
}

internal object Waiting {
    private const val BASE = ProfileScope.WAIT
    private const val KEY_STATE = "state"
    private const val KEY_OPTIONS = "options"
    /** Unohtunut odotus ei jää asiakkaan ruudulle seuraavaan päivään. */
    const val MAX_AGE_MS = 12L * 60 * 60 * 1000
    val DURATIONS = listOf(0, 5, 10, 15, 30)
    val DEFAULT_OPTIONS = listOf("music", "reading", "rest")

    /** Näkymät lukevat odotuksen uudelleen, kun tämä muuttuu. */
    var revision by mutableIntStateOf(0)
        private set

    private fun prefs(context: Context, profileId: String = ProfileStore.currentId(context)) =
        ProfileScope.prefs(context, BASE, profileId)

    fun current(context: Context, now: Long = System.currentTimeMillis()): WaitState? {
        val text = prefs(context).getString(KEY_STATE, null) ?: return null
        val state = decode(text) ?: return null
        return state.takeIf { now - it.startedAt in 0..MAX_AGE_MS }
    }

    fun start(context: Context, name: String, photo: String, minutes: Int, now: Long = System.currentTimeMillis()) {
        prefs(context).edit().putString(KEY_STATE, encode(WaitState(name, photo, now, minutes.coerceAtLeast(0)))).apply()
        UsageLog.record(context, UsageKind.WAIT)
        revision++
    }

    fun stop(context: Context) {
        prefs(context).edit().remove(KEY_STATE).apply()
        revision++
    }

    /** "Odottaessa voit" -kortit asiakkaalle (enintään 3). */
    fun options(context: Context): List<ChoiceOption> {
        val text = prefs(context).getString(KEY_OPTIONS, null)
        val list = text?.let { decodeOptions(it) }
        return list ?: DEFAULT_OPTIONS.mapNotNull { id ->
            fi.aikapolku.app.cards.CardArt.templates.firstOrNull { it.id == id }
                ?.let { ChoiceOption(context.getString(it.nameRes), "builtin:$id") }
        }
    }

    fun setOptions(context: Context, options: List<ChoiceOption>) {
        prefs(context).edit().putString(KEY_OPTIONS, encodeOptions(options.take(3))).apply()
        revision++
    }

    fun encode(s: WaitState): String = JSONObject()
        .put("name", s.name).put("photo", s.photo).put("startedAt", s.startedAt).put("minutes", s.minutes).toString()

    fun decode(text: String): WaitState? = runCatching {
        val o = JSONObject(text)
        WaitState(o.getString("name"), o.optString("photo"), o.getLong("startedAt"), o.optInt("minutes", 0))
    }.getOrNull()

    fun encodeOptions(list: List<ChoiceOption>): String = JSONArray().also { arr ->
        list.forEach { arr.put(JSONObject().put("name", it.name).put("photo", it.photo).put("voicePath", it.voicePath)) }
    }.toString()

    fun decodeOptions(text: String): List<ChoiceOption>? = runCatching {
        val arr = JSONArray(text)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            ChoiceOption(o.getString("name"), o.optString("photo"), o.optString("voicePath"))
        }
    }.getOrNull()
}
