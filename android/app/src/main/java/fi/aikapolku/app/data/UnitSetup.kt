package fi.aikapolku.app.data

/*
 * Ohjattu aloitus (V4, Y8): yksikkö, alayksiköt, asukkaat, yhteiset ruokailut ja päivän toiminnat
 * yhdellä kertaa. Puhdas rakentaja: tuottaa ryhmät, jotka tallennetaan GroupStoreen.
 */

/**
 * Yhteinen kohta (yleensä ruokailu). [weekday] / [weekend] = kellonaika, null = ei niinä päivinä.
 * [notifyBefore] = muistutus minuuttia ennen, null = ei muistutusta.
 */
internal data class MealSpec(
    val cardId: String,
    val name: String,
    val weekday: String?,
    val weekend: String?,
    val notifyBefore: Int? = null
)

/** Päivän toiminta: nimi, kuva, kokoontumiset (päivä ja aika) ja asukkaat. */
internal data class ActivitySpec(
    val name: String,
    val photo: String,
    val sessions: List<ActivitySession>,
    val members: Set<String>
)

/** Alayksikkö ja sen asukkaat. */
internal data class SubUnitSpec(val name: String, val members: List<String>)

internal data class UnitSpec(
    val name: String,
    val subUnits: List<SubUnitSpec>,
    /** Asukkaat suoraan ylimmässä yksikössä (ei alayksikköä). */
    val members: List<String>,
    val meals: List<MealSpec>,
    val activities: List<ActivitySpec>
)

internal object UnitSetup {
    val WEEKDAYS = 1..5
    val WEEKEND = 6..7

    /** Tavalliset ruokailut ja ajat (arki / viikonloppu); ohjaaja muuttaa tarvittaessa. */
    fun defaultMeals(name: (String) -> String): List<MealSpec> = listOf(
        MealSpec("breakfast", name("breakfast"), "08:00", "09:00"),
        MealSpec("lunch_meal", name("lunch_meal"), "11:00", "12:00"),
        MealSpec("snack", name("snack"), "14:30", "14:30"),
        MealSpec("dinner", name("dinner"), "17:00", "17:00"),
        MealSpec("evening_snack", name("evening_snack"), "20:00", "20:00")
    )

    /** Rakentaa ryhmät: ylin yksikkö (ruokailut), alayksiköt (asukkaat) ja toiminnat. */
    fun build(spec: UnitSpec, newId: () -> String): List<Group> {
        val topId = newId()
        var n = 0
        fun meal(m: MealSpec, d: Int, t: String) = WeekEvent(
            "m${n++}", d, WeekSchedule.WEEK_A, t, EventKind.CARD, cardName = m.name, cardPhoto = "builtin:${m.cardId}",
            notify = m.notifyBefore != null, notifyBefore = m.notifyBefore ?: 0
        )
        val events = spec.meals.flatMap { m ->
            WEEKDAYS.mapNotNull { d -> m.weekday?.let { t -> meal(m, d, t) } } +
                WEEKEND.mapNotNull { d -> m.weekend?.let { t -> meal(m, d, t) } }
        }
        val subUnits = spec.subUnits.filter { it.name.isNotBlank() }
        val top = Group(
            id = topId,
            name = spec.name.trim(),
            members = spec.members.distinct(),
            plan = WeekPlan(events),
            kind = GroupKind.UNIT
        )
        val subs = subUnits.map { Group(newId(), it.name.trim(), it.members.distinct(), kind = GroupKind.UNIT, parentId = topId) }
        val activities = spec.activities.filter { a ->
            a.name.isNotBlank() && a.sessions.isNotEmpty() &&
                a.sessions.all { WeekSchedule.minutesOf(it.end) > WeekSchedule.minutesOf(it.start) }
        }.map { a ->
            Group(
                id = newId(),
                name = a.name.trim(),
                members = a.members.toList(),
                kind = GroupKind.ACTIVITY,
                sessions = a.sessions.sortedBy { it.day },
                cardPhoto = a.photo
            )
        }
        return listOf(top) + subs + activities
    }
}
