package fi.aikapolku.app.data

import android.content.Context
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import org.json.JSONArray
import org.json.JSONObject

/** Viikko-ohjelman tapahtuman sisältö: kokonainen päiväohjelma tai yksittäinen kortti. */
internal enum class EventKind(val key: String) {
    PROGRAM("program"),
    CARD("card");

    companion object {
        fun from(key: String?): EventKind = entries.firstOrNull { it.key == key } ?: PROGRAM
    }
}

/**
 * Yksi viikko-ohjelman tapahtuma.
 * [day] 1 = maanantai … 7 = sunnuntai. [week] 0 = viikko A, 1 = viikko B (vuoroviikot).
 * [time] muodossa HH:mm. Kortilla [cardName] ja [cardPhoto] (oma kuva tai builtin:tunnus).
 */
internal data class WeekEvent(
    val id: String,
    val day: Int,
    val week: Int,
    val time: String,
    val kind: EventKind,
    val programId: String = "",
    val cardName: String = "",
    val cardPhoto: String = "",
    /** Ilmoitus tapahtumasta: alkaessa ([notifyBefore] = 0) tai 5/10/15 min ennen. */
    val notify: Boolean = false,
    val notifyBefore: Int = 0
)

/** Yksi tuleva ilmoitus: kenen profiilin tapahtuma, minä päivänä ja milloin ilmoitetaan. */
internal data class Reminder(
    val profileId: String,
    val event: WeekEvent,
    val date: LocalDate,
    val at: LocalDateTime
)

/**
 * Profiilin viikko-ohjelma. Vuoroviikoissa [anchorMonday] on jonkin A-viikon maanantai
 * (epochDay); siitä lähtien viikot vuorottelevat A, B, A, B …
 */
internal data class WeekPlan(
    val events: List<WeekEvent> = emptyList(),
    val alternating: Boolean = false,
    val anchorMonday: Long = 0L
)

/** Viikko-ohjelman päättely: puhdas logiikka, testattavissa ilman laitetta. */
internal object WeekSchedule {
    const val WEEK_A = 0
    const val WEEK_B = 1

    fun monday(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    /** Kumpi viikko (A/B) on käynnissä annettuna päivänä. Ilman vuoroviikkoja aina A. */
    fun weekOf(plan: WeekPlan, date: LocalDate): Int {
        if (!plan.alternating) return WEEK_A
        val weeks = ChronoUnit.WEEKS.between(LocalDate.ofEpochDay(plan.anchorMonday), monday(date))
        return Math.floorMod(weeks, 2L).toInt()
    }

    /** Asettaa vuorottelun niin, että annettu päivä osuu viikolle [week]. */
    fun withCurrentWeek(plan: WeekPlan, today: LocalDate, week: Int): WeekPlan {
        val thisMonday = monday(today)
        val anchor = if (week == WEEK_A) thisMonday else thisMonday.minusWeeks(1)
        return plan.copy(anchorMonday = anchor.toEpochDay())
    }

    /** Päivän tapahtumat kellonajan mukaan. */
    fun eventsFor(plan: WeekPlan, date: LocalDate): List<WeekEvent> {
        val week = weekOf(plan, date)
        return eventsOn(plan, date.dayOfWeek.value, week)
    }

    /** Viikonpäivän tapahtumat muokkausta varten. Ilman vuoroviikkoja näytetään viikko A. */
    fun eventsOn(plan: WeekPlan, day: Int, week: Int): List<WeekEvent> {
        val shownWeek = if (plan.alternating) week else WEEK_A
        return plan.events
            .filter { it.day == day && it.week == shownWeek }
            .sortedWith(compareBy({ minutesOf(it.time) }, { it.id }))
    }

    /**
     * "Nyt"-tapahtuma: viimeisin jo alkanut. Ennen ensimmäistä tapahtumaa korostetaan
     * ensimmäinen (tulossa). Tyhjällä listalla -1.
     */
    fun currentIndex(events: List<WeekEvent>, now: LocalTime): Int {
        if (events.isEmpty()) return -1
        val nowMinutes = now.hour * 60 + now.minute
        val started = events.indexOfLast { minutesOf(it.time) <= nowMinutes }
        return if (started >= 0) started else 0
    }

    fun minutesOf(time: String): Int {
        val parts = time.split(":")
        val h = parts.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: 0
        val m = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: 0
        return h * 60 + m
    }

    fun formatTime(hour: Int, minute: Int): String =
        "%02d:%02d".format(hour.coerceIn(0, 23), minute.coerceIn(0, 59))

    val NOTIFY_BEFORE_CHOICES = listOf(0, 5, 10, 15)

    /** Ilmoitukset päiviltä [from] … [from] + [days] - 1 kaikista profiileista. */
    fun reminders(plans: Map<String, WeekPlan>, from: LocalDate, days: Int): List<Reminder> =
        (0 until days).flatMap { offset ->
            val date = from.plusDays(offset.toLong())
            plans.flatMap { (profileId, plan) ->
                eventsFor(plan, date).filter { it.notify }.map { event ->
                    val start = date.atStartOfDay().plusMinutes(minutesOf(event.time).toLong())
                    Reminder(profileId, event, date, start.minusMinutes(event.notifyBefore.coerceIn(0, 60).toLong()))
                }
            }
        }

    /** Seuraava ilmoitushetki [now]:n jälkeen (minuutin tarkkuudella), tai null. */
    fun nextReminderAt(plans: Map<String, WeekPlan>, now: LocalDateTime): LocalDateTime? {
        val minute = now.withSecond(0).withNano(0)
        return reminders(plans, minute.toLocalDate(), 16).map { it.at }.filter { it.isAfter(minute) }.minOrNull()
    }

    /** Ilmoitukset, jotka kuuluvat hetkelle [at]. Ennakkoilmoitus voi osua edelliselle päivälle. */
    fun dueReminders(plans: Map<String, WeekPlan>, at: LocalDateTime): List<Reminder> {
        val minute = at.withSecond(0).withNano(0)
        return reminders(plans, minute.toLocalDate(), 2).filter { it.at == minute }
    }

    /** Poistaa tapahtumat, joiden päiväohjelmaa ei enää ole. */
    fun withoutMissingPrograms(plan: WeekPlan, programIds: Set<String>): WeekPlan =
        plan.copy(events = plan.events.filterNot { it.kind == EventKind.PROGRAM && it.programId !in programIds })
}

/** Viikko-ohjelman tallennus nykyiseen profiiliin. */
internal class WeekStore(context: Context, profileId: String = ProfileStore.currentId(context)) {
    private val prefs = ProfileScope.prefs(context, ProfileScope.WEEK, profileId)

    fun load(): WeekPlan = decode(prefs.getString(KEY, null))

    fun save(plan: WeekPlan) {
        prefs.edit().putString(KEY, encode(plan)).apply()
    }

    companion object {
        private const val KEY = "week_plan"

        fun encode(plan: WeekPlan): String {
            val events = JSONArray()
            plan.events.forEach { e ->
                events.put(
                    JSONObject()
                        .put("id", e.id)
                        .put("day", e.day)
                        .put("week", e.week)
                        .put("time", e.time)
                        .put("kind", e.kind.key)
                        .put("programId", e.programId)
                        .put("cardName", e.cardName)
                        .put("cardPhoto", e.cardPhoto)
                        .put("notify", e.notify)
                        .put("notifyBefore", e.notifyBefore)
                )
            }
            return JSONObject()
                .put("events", events)
                .put("alternating", plan.alternating)
                .put("anchorMonday", plan.anchorMonday)
                .toString()
        }

        fun decode(text: String?): WeekPlan {
            if (text.isNullOrBlank()) return WeekPlan()
            val root = runCatching { JSONObject(text) }.getOrElse { return WeekPlan() }
            val array = root.optJSONArray("events") ?: JSONArray()
            val events = (0 until array.length()).mapNotNull { i ->
                val o = array.optJSONObject(i) ?: return@mapNotNull null
                val id = o.optString("id")
                val day = o.optInt("day", 0)
                if (id.isBlank() || day !in 1..7) return@mapNotNull null
                val time = WeekSchedule.minutesOf(o.optString("time")).let { WeekSchedule.formatTime(it / 60, it % 60) }
                WeekEvent(
                    id = id,
                    day = day,
                    week = o.optInt("week", 0).coerceIn(0, 1),
                    time = time,
                    kind = EventKind.from(o.optString("kind")),
                    programId = o.optString("programId"),
                    cardName = o.optString("cardName"),
                    cardPhoto = o.optString("cardPhoto"),
                    notify = o.optBoolean("notify", false),
                    notifyBefore = o.optInt("notifyBefore", 0).coerceIn(0, 60)
                )
            }
            return WeekPlan(
                events = events,
                alternating = root.optBoolean("alternating", false),
                anchorMonday = root.optLong("anchorMonday", 0L)
            )
        }
    }
}
