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
    val showPrograms: Boolean = false
) {
    val anyTool: Boolean get() = showTimer || showFirstThen || showPrograms

    companion object {
        private const val PREFIX = "client_"

        fun load(context: Context): ClientViewPrefs {
            val p = ProfileScope.prefs(context, ProfileScope.RUN_PREFS)
            return ClientViewPrefs(
                timeline = TimelineMode.from(p.getString(PREFIX + "timeline", null)),
                showTimes = p.getBoolean(PREFIX + "times", true),
                showTimer = p.getBoolean(PREFIX + "timer", false),
                showFirstThen = p.getBoolean(PREFIX + "first_then", false),
                showPrograms = p.getBoolean(PREFIX + "programs", false)
            )
        }

        fun save(context: Context, prefs: ClientViewPrefs) {
            ProfileScope.prefs(context, ProfileScope.RUN_PREFS).edit()
                .putString(PREFIX + "timeline", prefs.timeline.key)
                .putBoolean(PREFIX + "times", prefs.showTimes)
                .putBoolean(PREFIX + "timer", prefs.showTimer)
                .putBoolean(PREFIX + "first_then", prefs.showFirstThen)
                .putBoolean(PREFIX + "programs", prefs.showPrograms)
                .apply()
        }
    }
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

    /** Aikajanan rivit [mode]n mukaan: kaikki, tai nykyinen ja kaksi seuraavaa tekemätöntä. */
    fun visible(count: Int, current: Int, skipped: (Int) -> Boolean, mode: TimelineMode): List<Int> = when (mode) {
        TimelineMode.FULL -> (0 until count).toList()
        TimelineMode.NONE -> emptyList()
        TimelineMode.NEXT -> if (current < 0) emptyList() else
            listOf(current) + (current + 1 until count).filterNot(skipped).take(2)
    }
}
