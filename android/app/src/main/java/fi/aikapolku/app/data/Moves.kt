package fi.aikapolku.app.data

import java.time.LocalDate

/*
 * Kohdan siirto raahaamalla uuteen aikaan: tälle päivälle (päivän muutos) tai asiakkaan
 * viikko-ohjelmaan joka viikolle. Puhdas logiikka, testattavissa ilman laitetta.
 */
internal object Moves {

    /**
     * Vain tänään: päivän muutos uuteen aikaan. Jos kohta on tälle päivälle jo korvattu,
     * korvaus säilyy ja vain aika vaihtuu; muuten siirto (MOVE). Peruttua ei siirretä.
     */
    fun dayChange(
        changes: List<EventChange>,
        eventId: String,
        date: LocalDate,
        time: String,
        reveal: ChangeReveal,
        showOriginal: Boolean,
        newId: () -> String
    ): EventChange? {
        val existing = DayChanges.wholeChange(changes, date, eventId)
        return when (existing?.type) {
            ChangeType.CANCEL -> null
            ChangeType.REPLACE -> existing.copy(time = time)
            else -> EventChange(
                id = existing?.id ?: newId(),
                date = date,
                eventId = eventId,
                type = ChangeType.MOVE,
                time = time,
                reveal = reveal,
                showOriginal = showOriginal
            )
        }
    }

    /** Joka viikko: muuttuneet tallennettavat (asiakkaan oma viikko ja mahdollinen ryhmä). */
    data class Weekly(val own: WeekPlan, val group: Group?)

    /**
     * Joka viikko tälle asiakkaalle. Oma kohta saa uuden ajan. Ryhmän kohdasta tulee
     * asiakkaalle poikkeus (ei osallistu ryhmän aikaan) ja oma kohta uuteen aikaan samalla
     * kortilla ja muistutuksella; ryhmän muiden asiakkaiden päivä ei muutu. [date] = päivä,
     * jolta siirto tehtiin (viikonpäivä ja oma A/B-viikko).
     */
    fun weekly(
        own: WeekPlan,
        groups: List<Group>,
        memberId: String,
        eventId: String,
        date: LocalDate,
        time: String,
        newId: () -> String
    ): Weekly? {
        if (own.event(eventId) != null) {
            return Weekly(own.copy(events = own.events.map { if (it.id == eventId) it.copy(time = time) else it }), null)
        }
        val (groupId, rawId) = GroupSchedule.parse(eventId) ?: return null
        val group = groups.firstOrNull { it.id == groupId && memberId in it.members } ?: return null
        val raw = group.plan.event(rawId) ?: return null
        val shown = GroupSchedule.forMember(group, memberId, raw) ?: return null
        val weeks = when {
            !own.alternating -> listOf(WeekSchedule.WEEK_A)
            group.plan.alternating -> listOf(WeekSchedule.weekOf(own, date))
            else -> listOf(WeekSchedule.WEEK_A, WeekSchedule.WEEK_B)
        }
        val copies = weeks.map { w ->
            WeekEvent(
                id = newId(),
                day = date.dayOfWeek.value,
                week = w,
                time = time,
                kind = EventKind.CARD,
                cardName = shown.cardName,
                cardPhoto = shown.cardPhoto,
                notify = raw.notify,
                notifyBefore = raw.notifyBefore,
                ongoing = raw.ongoing
            )
        }
        val skipped = Runko.withException(group, rawId, memberId, MemberException(rawId, memberId, ExceptionKind.SKIP))
        return Weekly(own.copy(events = own.events + copies), skipped)
    }
}
