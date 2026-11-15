package fi.aikapolku.app.link

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.json.JSONArray
import org.json.JSONObject

/**
 * Ohjaajan puhelimeen tulleet asiakkaiden viestit (laitteen yhteiset, eilinen ja tämä päivä).
 * Asiakkaan oma tila tulee tänne vain, jos asiakas itse lähetti sen.
 */
internal object LinkInbox {
    private const val PREFS = "aikapolku_link_inbox_v1"
    private const val KEY = "messages"

    var revision by mutableIntStateOf(0)
        private set

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * Tallentaa saapuneen viestin; false, jos sama viesti on jo saatu (jonosta uudelleen
     * lähetetty, kun kuittaus ei aiemmin ehtinyt perille).
     */
    @Synchronized
    fun add(context: Context, message: LinkMessage, receivedAt: Long = System.currentTimeMillis()): Boolean {
        val existing = all(context)
        if (isDuplicate(existing, message)) return false
        val kept = prune(existing, receivedAt) + message.copy(at = receivedAt, sentAt = message.at)
        prefs(context).edit().putString(KEY, encode(kept.takeLast(100))).commit()
        revision++
        return true
    }

    fun isDuplicate(existing: List<LinkMessage>, message: LinkMessage): Boolean =
        message.at > 0 && existing.any { it.sentAt == message.at && it.who == message.who && it.kind == message.kind && it.text == message.text }

    fun today(context: Context, now: Long = System.currentTimeMillis()): List<LinkMessage> {
        val day = dateOf(now)
        return all(context).filter { dateOf(it.at) == day }
    }

    fun clear(context: Context) {
        prefs(context).edit().remove(KEY).apply()
        revision++
    }

    private fun all(context: Context) = decode(prefs(context).getString(KEY, null))

    fun prune(list: List<LinkMessage>, now: Long): List<LinkMessage> {
        val yesterday = dateOf(now).minusDays(1)
        return list.filter { !dateOf(it.at).isBefore(yesterday) }
    }

    private fun dateOf(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()

    fun encode(list: List<LinkMessage>): String = JSONArray().also { arr ->
        list.forEach { arr.put(JSONObject().put("k", it.kind).put("x", it.text).put("w", it.who).put("a", it.at).put("f", it.fromName).put("s", it.sentAt)) }
    }.toString()

    fun decode(text: String?): List<LinkMessage> = runCatching {
        val arr = JSONArray(text ?: return emptyList())
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            LinkMessage(o.optString("k"), o.optString("x"), o.optString("w"), o.optLong("a"), o.optString("f"), o.optLong("s"))
        }
    }.getOrDefault(emptyList())
}
