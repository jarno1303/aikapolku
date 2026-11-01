package fi.aikapolku.app.data

import android.content.Context
import java.time.LocalDate
import java.time.LocalTime

/**
 * Tapahtumien "Tehty"-kuittaukset. Säilytetään vain eiliseltä ja tältä päivältä:
 * aamulla näkee, otettiinko iltalääkkeet, mutta vanhempaa tietoa ei kerry.
 * AikaPolku muistuttaa, mutta se ei ole lääkehoidon kirjaamisväline.
 */
internal class AckStore(context: Context, profileId: String = ProfileStore.currentId(context)) {
    private val prefs = ProfileScope.prefs(context, ProfileScope.ACKS, profileId)

    /** Kuittauksen kellonaika (HH:mm) tai null. */
    fun ackTime(date: LocalDate, eventId: String): String? = prefs.getString(AckLog.key(date, eventId), null)

    fun ack(date: LocalDate, eventId: String, time: LocalTime = LocalTime.now()) {
        prune(LocalDate.now())
        prefs.edit().putString(AckLog.key(date, eventId), WeekSchedule.formatTime(time.hour, time.minute)).commit()
    }

    /** Poistaa toissapäiväiset ja vanhemmat kuittaukset. */
    fun prune(today: LocalDate) {
        val old = AckLog.expired(prefs.all.keys, today)
        if (old.isNotEmpty()) prefs.edit().apply { old.forEach { remove(it) } }.commit()
    }
}

/** Kuittausavainten käsittely: puhdas logiikka, testattavissa ilman laitetta. */
internal object AckLog {
    fun key(date: LocalDate, eventId: String) = "${date}|$eventId"

    fun dateOf(key: String): LocalDate? = runCatching { LocalDate.parse(key.substringBefore("|")) }.getOrNull()

    /** Avaimet, jotka ovat eilistä vanhempia (tai rikkinäisiä). */
    fun expired(keys: Collection<String>, today: LocalDate): List<String> {
        val keepFrom = today.minusDays(1)
        return keys.filter { k -> dateOf(k)?.isBefore(keepFrom) ?: true }
    }
}
