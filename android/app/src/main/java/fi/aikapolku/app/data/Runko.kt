package fi.aikapolku.app.data

import fi.aikapolku.app.R

/*
 * Viikkorungon muokkaus aikajanalla (ryhmän runko ja asiakkaan omat kohdat): puhdas
 * logiikka, testattavissa ilman laitetta. Muokkaus koskee aina yhtä viikkoa (A tai B).
 */
internal object Runko {
    const val DEFAULT_TIME = "08:00"
    private const val STEP = 5

    /**
     * Ehdotettu aika uudelle kohdalle kahden kohdan väliin: puoliväli 5 minuutin tarkkuudella.
     * Ennen ensimmäistä tunti aiemmin, viimeisen jälkeen tunti myöhemmin; tyhjään päivään 08:00.
     */
    fun suggestTime(previous: String?, next: String?): String {
        val prev = previous?.let { WeekSchedule.minutesOf(it) }
        val nxt = next?.let { WeekSchedule.minutesOf(it) }
        val minutes = when {
            prev != null && nxt != null -> {
                val middle = (prev + nxt) / 2 / STEP * STEP
                if (middle <= prev) (prev + STEP).coerceAtMost(maxOf(prev, nxt - 1)) else middle
            }
            prev != null -> (prev + 60).coerceAtMost(23 * 60 + 55)
            nxt != null -> (nxt - 60).coerceAtLeast(0)
            else -> WeekSchedule.minutesOf(DEFAULT_TIME)
        }
        return WeekSchedule.formatTime(minutes / 60, minutes % 60)
    }

    /** Uusi korttikohta päivälle. Rungon kortit eivät oletuksena ilmoita (ohjaaja valitsee). */
    fun addCard(plan: WeekPlan, day: Int, week: Int, time: String, name: String, photo: String, newId: () -> String): WeekPlan =
        plan.copy(events = plan.events + WeekEvent(newId(), day, shownWeek(plan, week), time, EventKind.CARD, cardName = name, cardPhoto = photo))

    /** Kopioi päivän kohdat muille päiville (korvaa niiden kohdat samalla viikolla). */
    fun copyDay(plan: WeekPlan, from: Int, week: Int, to: Set<Int>, newId: () -> String): WeekPlan {
        val w = shownWeek(plan, week)
        val targets = to - from
        if (targets.isEmpty()) return plan
        val source = plan.events.filter { it.day == from && it.week == w }
        val kept = plan.events.filterNot { it.week == w && it.day in targets }
        val copies = targets.sorted().flatMap { d -> source.map { it.copy(id = newId(), day = d) } }
        return plan.copy(events = kept + copies)
    }

    fun clearDay(plan: WeekPlan, day: Int, week: Int): WeekPlan {
        val w = shownWeek(plan, week)
        return plan.copy(events = plan.events.filterNot { it.day == day && it.week == w })
    }

    /**
     * Toisen rungon kohdat tähän (pohja tai toinen asiakas/ryhmä). Vain kortit, koska
     * päiväohjelmat ovat asiakaskohtaisia. Vuoroviikot ja ankkuri tulevat lähteestä.
     */
    fun copyFrom(source: WeekPlan, newId: () -> String): WeekPlan =
        source.copy(events = source.events.filter { it.kind == EventKind.CARD }.map { it.copy(id = newId()) })

    private fun shownWeek(plan: WeekPlan, week: Int) = if (plan.alternating) week else WeekSchedule.WEEK_A

    /**
     * Siirto päivän sisällä: kellonajat pysyvät paikallaan ja sisällöt vaihtavat järjestystä.
     * Kohta [from] siirtyy järjestyksessä kohtaan [to]; väliin jäävät liukuvat yhden pykälän.
     * Sisällön mukana kulkevat kortti/ohjelma, muistutus, "kestää seuraavaan" ja tunnus
     * (tunnuksen mukana myös päivän muutokset ja kuittaukset).
     */
    fun moveContent(plan: WeekPlan, day: Int, week: Int, from: Int, to: Int): WeekPlan {
        val events = WeekSchedule.eventsOn(plan, day, week)
        if (from !in events.indices || to !in events.indices || from == to) return plan
        val times = events.map { it.time }
        val reordered = events.toMutableList().apply { add(to, removeAt(from)) }
        val retimed = reordered.mapIndexed { i, e -> e.copy(time = times[i]) }.associateBy { it.id }
        return plan.copy(events = plan.events.map { retimed[it.id] ?: it })
    }

    /** Kohta toiselle päivälle samaan kellonaikaan. */
    fun moveToDay(plan: WeekPlan, eventId: String, day: Int): WeekPlan =
        plan.copy(events = plan.events.map { if (it.id == eventId && day in 1..7) it.copy(day = day) else it })

    /**
     * Raahauksen pudotuskohta: monesko järjestyksessä raahattu kohta olisi, kun sormi on
     * kohdassa [y]. [centers] = muiden (ei raahatun) kohtien keskikohdat ylhäältä alas.
     */
    fun dropIndex(centers: List<Float>, y: Float): Int = centers.count { it < y }

    /** Asiakkaan toistuva poikkeus ryhmän kohtaan: asetus tai poisto (null = ryhmän mukaan). */
    fun withException(group: Group, eventId: String, memberId: String, exception: MemberException?): Group =
        group.copy(exceptions = group.exceptions.filterNot { it.eventId == eventId && it.memberId == memberId } + listOfNotNull(exception))

    /** Ryhmän kohta poistettiin: myös sen poikkeukset pois. */
    fun withoutEvent(group: Group, eventId: String): Group =
        group.copy(
            plan = group.plan.copy(events = group.plan.events.filterNot { it.id == eventId }),
            exceptions = group.exceptions.filterNot { it.eventId == eventId }
        )
}

/** Valmis runko: tavalliset kohdat kellonaikoineen valituille päiville. */
internal data class RunkoTemplate(
    val id: String,
    val nameRes: Int,
    val infoRes: Int,
    val days: Set<Int>,
    /** (aika, kortin tunnus, kestää seuraavaan) */
    val items: List<Triple<String, String, Boolean>>
) {
    /** Runko viikolle A. [cardName] = kortin nimi tunnuksesta (sovelluksen kielellä). */
    fun build(cardName: (String) -> String, newId: () -> String): WeekPlan = WeekPlan(
        events = days.sorted().flatMap { day ->
            items.map { (time, card, ongoing) ->
                WeekEvent(newId(), day, WeekSchedule.WEEK_A, time, EventKind.CARD, cardName = cardName(card), cardPhoto = "builtin:$card", ongoing = ongoing)
            }
        }
    )

    companion object {
        private val weekdays = (1..5).toSet()
        private val everyDay = (1..7).toSet()

        val all = listOf(
            RunkoTemplate(
                "day_activity", R.string.runko_day_activity, R.string.runko_day_activity_info, weekdays,
                listOf(
                    Triple("09:00", "morning_meeting", false),
                    Triple("10:00", "outdoors", false),
                    Triple("11:00", "lunch_meal", false),
                    Triple("12:00", "rest", false),
                    Triple("12:30", "crafts", false),
                    Triple("14:00", "snack", false),
                    Triple("15:00", "leaving", false)
                )
            ),
            RunkoTemplate(
                "home", R.string.runko_home, R.string.runko_home_info, everyDay,
                listOf(
                    Triple("08:00", "breakfast", false),
                    Triple("10:00", "outdoors", false),
                    Triple("12:00", "lunch_meal", false),
                    Triple("13:00", "rest", false),
                    Triple("15:00", "snack", false),
                    Triple("17:00", "dinner", false),
                    Triple("18:00", "free_time", true),
                    Triple("20:00", "evening_snack", false),
                    Triple("21:30", "sleep", false)
                )
            ),
            RunkoTemplate(
                "school", R.string.runko_school, R.string.runko_school_info, weekdays,
                listOf(
                    Triple("07:00", "breakfast", false),
                    Triple("07:45", "leaving", false),
                    Triple("08:15", "schoolwork", true),
                    Triple("11:30", "lunch", false),
                    Triple("12:00", "schoolwork", true),
                    Triple("14:30", "snack", false),
                    Triple("15:30", "homework", false),
                    Triple("17:00", "dinner", false),
                    Triple("19:30", "evening_snack", false),
                    Triple("21:00", "sleep", false)
                )
            )
        )
    }
}
