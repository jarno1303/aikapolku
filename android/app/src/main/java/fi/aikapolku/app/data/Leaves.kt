package fi.aikapolku.app.data

import android.content.Context
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import org.json.JSONArray
import org.json.JSONObject

/*
 * Poissa / lomalla (yksikkömalli): asukkaan pidempi poissaolo ajanjaksona, esim. kotiloma
 * pe klo 15 – su klo 18. Koskee kaikkia asukkaan yksiköitä, toimintoja ja ryhmiä kerralla:
 * niiden kohdat ja muutokset eivät näy hänelle eivätkä koske häntä. Omat kohdat (esim. lääkkeet)
 * jäävät. Asukas näkee kortin "Lomalla – takaisin su klo 18" (sovittu 15.11.2026, vaihtoehto A).
 */

internal enum class LeaveReason(val key: String, val photo: String) {
    HOLIDAY("holiday", "builtin:free_time"),
    HOME("home", "builtin:leaving"),
    HOSPITAL("hospital", "builtin:rest"),
    OTHER("other", "builtin:waiting");

    companion object {
        fun from(key: String?): LeaveReason = entries.firstOrNull { it.key == key } ?: OTHER
    }
}

internal data class Leave(
    val id: String,
    val memberId: String,
    val from: LocalDateTime,
    val until: LocalDateTime,
    val reason: LeaveReason = LeaveReason.HOLIDAY
) {
    /** Onko asukas lomalla hetkellä [at]. */
    fun covers(at: LocalDateTime): Boolean = !at.isBefore(from) && at.isBefore(until)

    /** Koskeeko loma päivää [date] lainkaan. */
    fun touches(date: LocalDate): Boolean =
        from.isBefore(date.plusDays(1).atStartOfDay()) && until.isAfter(date.atStartOfDay())

    /** Kortin tunnus asukkaan päivässä. */
    val eventId: String get() = PREFIX + id

    companion object {
        const val PREFIX = "leave:"
    }
}

internal object Leaves {
    /** Lomakortti päivälle: alkaa loman alussa (tai päivän alussa) ja kestää paluuseen asti. */
    fun cardFor(leave: Leave, date: LocalDate, title: String): WeekEvent? {
        if (!leave.touches(date)) return null
        val start = if (leave.from.toLocalDate() == date) leave.from.toLocalTime() else LocalTime.MIDNIGHT
        val end = if (leave.until.toLocalDate() == date) WeekSchedule.formatTime(leave.until.hour, leave.until.minute) else ""
        return WeekEvent(
            leave.eventId, date.dayOfWeek.value, WeekSchedule.WEEK_A, WeekSchedule.formatTime(start.hour, start.minute),
            EventKind.CARD, cardName = title, cardPhoto = leave.reason.photo, ongoing = true, until = end
        )
    }

    /** Onko kellonaika [time] päivänä [date] jonkin loman sisällä. */
    fun onLeave(leaves: List<Leave>, date: LocalDate, time: String): Boolean {
        val m = WeekSchedule.minutesOf(time)
        val at = date.atTime(m / 60, m % 60)
        return leaves.any { it.covers(at) }
    }

    fun encode(leaves: List<Leave>): String = JSONArray().apply {
        leaves.forEach {
            put(JSONObject().put("id", it.id).put("memberId", it.memberId).put("from", it.from.toString())
                .put("until", it.until.toString()).put("reason", it.reason.key))
        }
    }.toString()

    fun decode(text: String?): List<Leave> {
        if (text.isNullOrBlank()) return emptyList()
        val array = runCatching { JSONArray(text) }.getOrElse { return emptyList() }
        return (0 until array.length()).mapNotNull { i ->
            val o = array.optJSONObject(i) ?: return@mapNotNull null
            val from = runCatching { LocalDateTime.parse(o.optString("from")) }.getOrNull() ?: return@mapNotNull null
            val until = runCatching { LocalDateTime.parse(o.optString("until")) }.getOrNull() ?: return@mapNotNull null
            val id = o.optString("id"); val member = o.optString("memberId")
            if (id.isBlank() || member.isBlank() || !until.isAfter(from)) null
            else Leave(id, member, from, until, LeaveReason.from(o.optString("reason")))
        }
    }
}

/**
 * Laitteen rajaus alayksikköön (ohjaajan käytettävyys isossa yksikössä): esim. Omenan
 * laitteessa ohjaaja näkee vain Omenan asukkaat. Ei tietosuojaraja: asukas näkee aina vain
 * omat tietonsa joka tapauksessa.
 */
internal object UnitScope {
    private const val PREFS = "aikapolku_unit_scope_v1"
    private const val KEY = "unit_id"

    fun get(context: Context): String =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "") ?: ""

    fun set(context: Context, unitId: String) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, unitId).apply()
    }

    /** Rajauksen asukkaat; null = ei rajausta (kaikki). */
    fun residents(scopeId: String, groups: List<Group>): Set<String>? {
        if (scopeId.isBlank()) return null
        val unit = groups.firstOrNull { it.id == scopeId && it.kind == GroupKind.UNIT } ?: return null
        return Units.residents(unit, groups).toSet()
    }

    /** Profiilit rajattuina; nykyinen asiakas näkyy aina (ettei valittu katoa listasta). */
    fun filter(profiles: List<Profile>, scopeId: String, groups: List<Group>, currentId: String): List<Profile> {
        val allowed = residents(scopeId, groups) ?: return profiles
        return profiles.filter { it.id in allowed || it.id == currentId }
    }

    fun filter(context: Context, profiles: List<Profile>): List<Profile> =
        filter(profiles, get(context), GroupStore(context).load(), ProfileStore.currentId(context))
}
