package fi.aikapolku.app.data

import android.content.Context

/**
 * "Tarvitsen vielä hetken" (tutkielma A2): asiakas pyytää lisäaikaa ohjaajan asettamissa rajoissa.
 *
 * Säännöt:
 *  - Painike näkyy ajastetussa toiminnossa, kun aika on loppumassa (ennakkovaroituksen aikana)
 *    tai jo loppunut. Pysäytettynä sitä ei näytetä.
 *  - Lisäaika annetaan heti ilman PIN-koodia, enintään [count] kertaa toimintoa kohden.
 *  - Jos lisäaikaa ei sallita (0 min) tai se on käytetty, painike näyttää viestin
 *    "Tarvitsen vielä hetken", jonka asiakas voi näyttää aikuiselle. Aika jatkuu.
 *  - Lisäaika ei ole palkinto eikä rangaistus: ei laskureita, ei moitteita.
 *
 * Asetukset ovat asiakaskohtaisia (ProfileScope.TIMER_PREFS), kuten ennakkovaroitus.
 */
internal object MoreTime {
    /** Lisäajan pituus minuutteina; 0 = ei lisäaikaa, painike pyytää aikuiselta. */
    val MINUTE_OPTIONS = listOf(0, 2, 3, 5)
    const val MINUTES_DEFAULT = 3
    /** Kuinka monta kertaa samassa toiminnossa. */
    val COUNT_OPTIONS = listOf(1, 2)
    const val COUNT_DEFAULT = 1
    /** Painikkeen näkymisaika, kun ennakkovaroitus on pois päältä. */
    const val FALLBACK_WINDOW_MIN = 2

    private const val MINUTES_KEY = "more_time_min"
    private const val COUNT_KEY = "more_time_count"

    fun minutes(context: Context): Int =
        ProfileScope.prefs(context, ProfileScope.TIMER_PREFS).getInt(MINUTES_KEY, MINUTES_DEFAULT)
            .takeIf { it in MINUTE_OPTIONS } ?: MINUTES_DEFAULT

    fun setMinutes(context: Context, minutes: Int) {
        if (minutes in MINUTE_OPTIONS) ProfileScope.prefs(context, ProfileScope.TIMER_PREFS).edit().putInt(MINUTES_KEY, minutes).apply()
    }

    fun count(context: Context): Int =
        ProfileScope.prefs(context, ProfileScope.TIMER_PREFS).getInt(COUNT_KEY, COUNT_DEFAULT)
            .takeIf { it in COUNT_OPTIONS } ?: COUNT_DEFAULT

    fun setCount(context: Context, count: Int) {
        if (count in COUNT_OPTIONS) ProfileScope.prefs(context, ProfileScope.TIMER_PREFS).edit().putInt(COUNT_KEY, count).apply()
    }

    /**
     * Näkyykö "Tarvitsen vielä hetken". [ended] = toiminnon aika on loppunut (aina näkyvissä).
     * Muuten aika käy ja jäljellä on enintään ennakkovaroituksen verran (tai [FALLBACK_WINDOW_MIN],
     * jos varoitus on pois), kuitenkin enintään puolet toiminnon kestosta: lyhyessä toiminnossa
     * painike ei näy heti alusta.
     */
    fun visible(remainingMs: Long, durationMs: Long, running: Boolean, ended: Boolean, warnMinutes: Int): Boolean {
        if (ended) return true
        if (!running || remainingMs <= 0L) return false
        val windowMs = (if (warnMinutes > 0) warnMinutes else FALLBACK_WINDOW_MIN) * 60_000L
        return remainingMs <= minOf(windowMs, durationMs / 2)
    }

    /** Annetaanko lisäaika nyt? false = näytetään viesti aikuiselle. */
    fun canGrant(used: Int, minutes: Int, count: Int): Boolean = minutes > 0 && used < count
}
