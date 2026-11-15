package fi.aikapolku.app.link

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject

/** Lähetyksen tulos asiakkaalle. */
internal enum class SendResult {
    /** Ainakin yksi ohjaajan puhelin kuittasi (muille yritetään uudelleen). */
    DELIVERED,
    /** Mikään ohjaajan puhelin ei vastannut: viesti jonossa, lähtee kun yhteys palaa. */
    QUEUED,
    /** Ei lähetettävissä (ei liitettyä puhelinta tai ei käytössä). */
    FAILED
}

/** Jonossa odottava viesti: [peers] = ohjaajan puhelimet, joille se ei vielä mennyt. */
internal data class QueuedMessage(
    val id: String,
    val kind: String,
    val text: String,
    val who: String,
    val at: Long,
    val peers: List<String>
)

/**
 * Lähtevien viestien jono asiakkaan laitteessa (palaute 15.11.2026: yhteyden luotettavuus).
 * Viesti, joka ei mennyt perille, yritetään uudelleen, kun sovellus avataan ja puolen minuutin
 * välein niin kauan kuin sovellus on käynnissä. Vanha pyyntö ei ole enää ajankohtainen:
 * jonosta poistetaan [MAX_AGE_MS] jälkeen (vastaanottaja hylkää yli 30 min vanhat).
 */
internal object LinkOutbox {
    private const val PREFS = "aikapolku_link_outbox_v1"
    private const val KEY = "queue"
    const val MAX_AGE_MS = 20L * 60 * 1000

    var revision by mutableIntStateOf(0)
        private set

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun pending(context: Context, now: Long = System.currentTimeMillis()): List<QueuedMessage> =
        prune(decode(prefs(context).getString(KEY, null)), now)

    @Synchronized
    fun add(context: Context, message: QueuedMessage, now: Long = System.currentTimeMillis()) {
        save(context, prune(pending(context, now), now) + message)
    }

    @Synchronized
    fun save(context: Context, list: List<QueuedMessage>) {
        prefs(context).edit().putString(KEY, encode(list.filter { it.peers.isNotEmpty() }.takeLast(20))).commit()
        revision++
    }

    fun prune(list: List<QueuedMessage>, now: Long): List<QueuedMessage> =
        list.filter { now - it.at in 0..MAX_AGE_MS && it.peers.isNotEmpty() }

    fun encode(list: List<QueuedMessage>): String = JSONArray().also { arr ->
        list.forEach { q ->
            arr.put(
                JSONObject().put("i", q.id).put("k", q.kind).put("x", q.text).put("w", q.who).put("a", q.at)
                    .put("p", JSONArray(q.peers))
            )
        }
    }.toString()

    fun decode(text: String?): List<QueuedMessage> = runCatching {
        val arr = JSONArray(text ?: return emptyList())
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val p = o.optJSONArray("p") ?: JSONArray()
            QueuedMessage(o.optString("i"), o.optString("k"), o.optString("x"), o.optString("w"), o.optLong("a"),
                (0 until p.length()).map { p.optString(it) })
        }
    }.getOrDefault(emptyList())
}
