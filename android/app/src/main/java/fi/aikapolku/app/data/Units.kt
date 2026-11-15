package fi.aikapolku.app.data

import java.time.LocalDate

/*
 * Yksikkömalli (docs/YKSIKKOMALLI_SUUNNITELMA.md, tutkielma P10, Y1–Y8).
 *
 *  - Yksikkö (GroupKind.UNIT) voi olla alayksikkö: Villa Metsäketo → Omena, Päärynä. Yläyksikön
 *    runko (esim. ruokailut) kuuluu kaikille alayksiköiden asukkaille; alayksiköllä voi olla omia kohtia.
 *  - Päivän toiminta (GroupKind.ACTIVITY), esim. päivätoiminta ma–pe 9–14 tai paja. Toiminnalla on
 *    kokoontumiset (päivä, alku, loppu); asukkaalla voi olla oma aika (MemberTime).
 *  - Sijainti (P10): asukas on toiminnassa sen ajan, muuten kotona. Toiminnan aikana yksikön kohdat
 *    eivät näy hänelle. Peruttu toiminta tai poissaolo = kotona.
 *  - Vanhat ryhmät (GroupKind.GROUP) toimivat ennallaan: rungon kohdat näkyvät jäsenille.
 *
 * Kaikki tämä on puhdasta logiikkaa: testattavissa ilman laitetta.
 */

internal enum class GroupKind(val key: String) {
    /** Ryhmä kuten ennen yksikkömallia: rungon kohdat näkyvät jäsenille aina. */
    GROUP("group"),
    /** Asumisyksikkö tai sen alayksikkö: yhteinen runko (ruokailut ym.). */
    UNIT("unit"),
    /** Päivän toiminta (päivätoiminta, paja): asukas on poissa yksiköstä kokoontumisen ajan. */
    ACTIVITY("activity");

    companion object {
        fun from(key: String?): GroupKind = entries.firstOrNull { it.key == key } ?: GROUP
    }
}

/** Toiminnan kokoontuminen viikonpäivänä [day] (1 = ma … 7 = su) klo [start]–[end]. */
internal data class ActivitySession(val day: Int, val start: String, val end: String)

/**
 * Asukkaan oma aika toiminnassa, esim. Liisa klo 10–13. [days] tyhjä = kaikki kokoontumispäivät,
 * muuten vain nämä viikonpäivät.
 */
internal data class MemberTime(val memberId: String, val days: Set<Int> = emptySet(), val start: String, val end: String)

/** Mitä asukas näkee toiminnan aikana (asukaskohtainen asetus, Y5). */
internal enum class ActivityView(val key: String) {
    /** Yksi kortti "Päivätoiminta klo 9–14" (oletus). */
    CARD("card"),
    /** Toiminnan oma runko (aamukokous, ulkoilu, lounas …), jos se on tehty. */
    PLAN("plan");

    companion object {
        fun from(key: String?): ActivityView = entries.firstOrNull { it.key == key } ?: CARD
    }
}

/** Asukkaan toiminta-aika päivänä: missä hän on klo [start]–[end]. */
internal data class ActivitySpan(
    val group: Group,
    val start: String,
    val end: String,
    /** Kokoontumisen tunnus asukkaan päivässä (muutokset ja kuittaukset kohdistuvat tähän). */
    val eventId: String,
    /** Toiminta on peruttu tältä päivältä: asukas on kotona. */
    val cancelled: Boolean
) {
    fun covers(time: String): Boolean {
        val m = WeekSchedule.minutesOf(time)
        return m >= WeekSchedule.minutesOf(start) && m < WeekSchedule.minutesOf(end)
    }
}

/** Saman asukkaan kaksi toimintaa päällekkäin samana päivänä (ohjaajalle varoitus). */
internal data class ActivityConflict(val memberId: String, val day: Int, val first: Group, val second: Group)

internal object Units {
    const val SESSION_PREFIX = "session-"

    /** Kokoontumisen tunnus ryhmän rungossa (päiväkohtainen, pysyvä viikosta toiseen). */
    fun sessionEventId(day: Int): String = "$SESSION_PREFIX$day"

    fun isSessionEvent(eventId: String): Boolean = eventId.startsWith(SESSION_PREFIX)

    /** Yksikön yläyksiköt lähimmästä alkaen (silmukat katkaistaan). */
    fun ancestors(unit: Group, all: List<Group>): List<Group> {
        val out = mutableListOf<Group>()
        var parentId = unit.parentId
        while (parentId.isNotBlank() && out.none { it.id == parentId } && parentId != unit.id) {
            val parent = all.firstOrNull { it.id == parentId && it.kind == GroupKind.UNIT } ?: break
            out += parent
            parentId = parent.parentId
        }
        return out
    }

    /** Yksikön alayksiköt (suorat). */
    fun children(unit: Group, all: List<Group>): List<Group> =
        all.filter { it.kind == GroupKind.UNIT && it.parentId == unit.id && it.id != unit.id }

    /**
     * Asukkaan ryhmät: ne, joissa hän on jäsen, ja hänen yksikköjensä yläyksiköt. Yläyksikkö
     * palautetaan niin, että asukas on sen jäsen (sen runko kuuluu hänelle), ja [homeUnitOf]
     * kertoo, minkä oman yksikön kautta (poissaolo merkitään omaan yksikköön).
     */
    fun groupsFor(all: List<Group>, memberId: String): List<Group> {
        val direct = all.filter { memberId in it.members }
        val inherited = direct.filter { it.kind == GroupKind.UNIT }
            .flatMap { ancestors(it, all) }
            .filter { a -> direct.none { it.id == a.id } }
            .distinctBy { it.id }
            .map { it.copy(members = it.members + memberId) }
        return direct + inherited
    }

    /** Asukkaan oma (alin) yksikkö, jonka kautta yläyksikön runko tulee; null, jos ei yksikköä. */
    fun homeUnitOf(all: List<Group>, memberId: String, ancestorId: String): Group? =
        all.firstOrNull { g -> g.kind == GroupKind.UNIT && memberId in g.members && ancestors(g, all).any { it.id == ancestorId } }

    /** Asukkaan kokoontumisajat toiminnassa [group] viikonpäivänä [day] (oma aika huomioituna). */
    fun sessionsFor(group: Group, memberId: String, day: Int): List<Pair<String, String>> {
        if (group.kind != GroupKind.ACTIVITY || memberId !in group.members) return emptyList()
        val own = group.memberTimes.firstOrNull { it.memberId == memberId && (it.days.isEmpty() || day in it.days) }
        return group.sessions.filter { it.day == day }.map { s ->
            if (own != null) own.start to own.end else s.start to s.end
        }
    }

    /**
     * Asukkaan toiminta-ajat päivänä. [absent] = poissa toiminnasta (ei kokoontumista),
     * [cancelled] = kokoontuminen peruttu (näkyy perutuksi, asukas on kotona).
     */
    fun spans(
        groups: List<Group>,
        memberId: String,
        date: LocalDate,
        absent: (groupId: String) -> Boolean = { false },
        cancelled: (eventId: String) -> Boolean = { false }
    ): List<ActivitySpan> {
        val day = date.dayOfWeek.value
        return groups.filter { it.kind == GroupKind.ACTIVITY && memberId in it.members && !absent(it.id) }
            .flatMap { g ->
                sessionsFor(g, memberId, day).map { (start, end) ->
                    val id = GroupSchedule.eventId(g.id, sessionEventId(day))
                    ActivitySpan(g, start, end, id, cancelled(id))
                }
            }
            .filter { WeekSchedule.minutesOf(it.end) > WeekSchedule.minutesOf(it.start) }
            .sortedBy { WeekSchedule.minutesOf(it.start) }
    }

    /** Missä toiminnassa asukas on kellonaikana [time]; null = kotona. Peruttu ei sido. */
    fun at(spans: List<ActivitySpan>, time: String): ActivitySpan? = spans.firstOrNull { !it.cancelled && it.covers(time) }

    /** Päällekkäiset toiminnat samalle asukkaalle (ohjaajalle varoitus asetuksissa). */
    fun conflicts(groups: List<Group>, memberId: String): List<ActivityConflict> {
        val activities = groups.filter { it.kind == GroupKind.ACTIVITY && memberId in it.members }
        val out = mutableListOf<ActivityConflict>()
        for (day in 1..7) {
            val times = activities.flatMap { g -> sessionsFor(g, memberId, day).map { g to it } }
            for (i in times.indices) for (j in i + 1 until times.size) {
                val (a, ta) = times[i]
                val (b, tb) = times[j]
                if (a.id == b.id) continue
                val overlap = WeekSchedule.minutesOf(ta.first) < WeekSchedule.minutesOf(tb.second) &&
                    WeekSchedule.minutesOf(tb.first) < WeekSchedule.minutesOf(ta.second)
                if (overlap && out.none { it.day == day && setOf(it.first.id, it.second.id) == setOf(a.id, b.id) }) {
                    out += ActivityConflict(memberId, day, a, b)
                }
            }
        }
        return out
    }

    /** Kokoontumiskortti asukkaan päivään: kestää toiminnan loppuun ([WeekEvent.until]). */
    fun sessionEvent(span: ActivitySpan, date: LocalDate): WeekEvent = WeekEvent(
        id = span.eventId,
        day = date.dayOfWeek.value,
        week = WeekSchedule.WEEK_A,
        time = span.start,
        kind = EventKind.CARD,
        cardName = span.group.name,
        cardPhoto = span.group.cardPhoto,
        ongoing = true,
        until = span.end
    )
}
