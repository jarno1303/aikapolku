package fi.aikapolku.app.data

import android.content.Context

/** Kuinka paljon päivästä asiakas näkee etusivullaan. */
internal enum class TimelineMode(val key: String) {
    /** Koko päivä aikajanana (oletus). */
    FULL("full"),
    /** Nyt ja kaksi seuraavaa. */
    NEXT("next"),
    /** Ei aikajanaa, vain NYT-kortti. */
    NONE("none");

    companion object {
        fun from(key: String?): TimelineMode = entries.firstOrNull { it.key == key } ?: FULL
    }
}

/**
 * Asiakkaan etusivun asetukset (profiilikohtaiset, ohjaaja säätää). Työkalut ovat
 * oletuksena piilossa: ohjaaja valmistelee Ensin–Sitten- tai ajastintoiminnon, ja se näkyy
 * asiakkaalle NYT-kortissa.
 */
internal data class ClientViewPrefs(
    val timeline: TimelineMode = TimelineMode.FULL,
    val showTimes: Boolean = true,
    val showTimer: Boolean = false,
    val showFirstThen: Boolean = false,
    val showPrograms: Boolean = false,
    /** Väliajan kortti (valmis kortti): näkyy NYT-kortissa, kun seuraava ei ole vielä alkanut. */
    val gapCard: String = GAP_FREE
) {
    val anyTool: Boolean get() = showTimer || showFirstThen || showPrograms

    companion object {
        private const val PREFIX = "client_"
        /** Oletus: myönteinen "tämä aika on sinun". Odota sopii vain todelliseen odottamiseen. */
        const val GAP_FREE = "free_time"
        const val GAP_WAITING = "waiting"
        val GAP_CHOICES = listOf(GAP_FREE, GAP_WAITING, "rest", "break")

        // Uusi avain: aiemmin tallentunut oletus "Odota" ei jää voimaan, vaan kaikki alkavat Vapaa-ajasta.
        private const val GAP_KEY = "gap_card"

        fun load(context: Context): ClientViewPrefs {
            val p = ProfileScope.prefs(context, ProfileScope.RUN_PREFS)
            return ClientViewPrefs(
                timeline = TimelineMode.from(p.getString(PREFIX + "timeline", null)),
                showTimes = p.getBoolean(PREFIX + "times", true),
                showTimer = p.getBoolean(PREFIX + "timer", false),
                showFirstThen = p.getBoolean(PREFIX + "first_then", false),
                showPrograms = p.getBoolean(PREFIX + "programs", false),
                gapCard = p.getString(PREFIX + GAP_KEY, GAP_FREE)?.takeIf { it in GAP_CHOICES } ?: GAP_FREE
            )
        }

        fun save(context: Context, prefs: ClientViewPrefs) {
            ProfileScope.prefs(context, ProfileScope.RUN_PREFS).edit()
                .putString(PREFIX + "timeline", prefs.timeline.key)
                .putBoolean(PREFIX + "times", prefs.showTimes)
                .putBoolean(PREFIX + "timer", prefs.showTimer)
                .putBoolean(PREFIX + "first_then", prefs.showFirstThen)
                .putBoolean(PREFIX + "programs", prefs.showPrograms)
                .putString(PREFIX + GAP_KEY, prefs.gapCard)
                .apply()
        }
    }
}

/** Mitä NYT-kortti näyttää päivän nykyisestä kohdasta. */
internal enum class NowKind {
    /** Alkanut tehtävä: iso kuva ja Aloita. */
    EVENT,
    /** Kestävä tapahtuma (koulu, päivätoiminta): ei painiketta, päättyy seuraavaan. */
    ONGOING,
    /** Väliaika: edellinen tehty, seuraava ei vielä alkanut. Väliajan kortti ja "Seuraavaksi". */
    GAP
}

/** Päivän osat aikajanalle: aamu ennen klo 11, päivä ennen klo 17, sitten ilta. */
internal object DayParts {
    fun of(time: String): DayTime {
        val m = WeekSchedule.minutesOf(time)
        return when {
            m < 11 * 60 -> DayTime.MORNING
            m < 17 * 60 -> DayTime.DAY
            else -> DayTime.EVENING
        }
    }

    /** NYT-kortin sisältö: [currentTime] = nykyisen tapahtuman aika, [nowMinutes] = kello. */
    fun nowKind(currentTime: String, nowMinutes: Int, ongoing: Boolean): NowKind = when {
        WeekSchedule.minutesOf(currentTime) > nowMinutes -> NowKind.GAP
        ongoing -> NowKind.ONGOING
        else -> NowKind.EVENT
    }

    /**
     * Väliajan kuittausrivi: lähin ei-peruttu tapahtuma ennen [current], jos se on kuitattu
     * tehdyksi. Null, jos edellistä ei ole tai se jäi tekemättä (silloin riviä ei näytetä).
     */
    fun previousDone(current: Int, cancelled: List<Boolean>, done: (Int) -> Boolean): Int? =
        (current - 1 downTo 0).firstOrNull { !cancelled[it] }?.takeIf(done)

    /** Kestävän tapahtuman loppu: seuraavan (ei peruttu) tapahtuman alku, tai null. */
    fun endTime(times: List<String>, cancelled: List<Boolean>, index: Int): String? =
        (index + 1 until times.size).firstOrNull { !cancelled[it] }?.let { times[it] }

    /** Aikajanan rivit [mode]n mukaan: kaikki, tai nykyinen ja kaksi seuraavaa tekemätöntä. */
    fun visible(count: Int, current: Int, skipped: (Int) -> Boolean, mode: TimelineMode): List<Int> = when (mode) {
        TimelineMode.FULL -> (0 until count).toList()
        TimelineMode.NONE -> emptyList()
        TimelineMode.NEXT -> if (current < 0) emptyList() else
            listOf(current) + (current + 1 until count).filterNot(skipped).take(2)
    }
}
