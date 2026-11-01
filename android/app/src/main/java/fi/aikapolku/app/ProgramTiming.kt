package fi.aikapolku.app

import java.util.Locale

/**
 * P0-23: päiväohjelman ajat.
 *
 * Ohjelmalla on yksi aikatapa:
 *  - FREE      omaan tahtiin: ei aikoja, Valmis siirtää seuraavaan
 *  - DURATION  kestot: jokaisella toiminnolla oma ajastin (tai "Ei aikaa")
 *  - CLOCK     kellonajat: aloitusaika + kestot, kellonajat lasketaan.
 *              Yksittäisen toiminnon voi lukita kiinteään kellonaikaan (fixedClock);
 *              seuraavat ajat lasketaan sen jälkeen.
 *
 * Suoritusnäkymä lukee edelleen vain toiminnon omat kentät (mode, minutes, clock).
 * [apply] kirjoittaa ne aikatavan mukaan ennen tallennusta, joten vanha suorituslogiikka,
 * tulostus ja vanhat tallennetut ohjelmat toimivat ennallaan. Puhdasta Kotlinia, testattavissa JVM:llä.
 */
internal object ProgramTiming {
    const val FREE = "free"
    const val DURATION = "duration"
    const val CLOCK = "clock"

    const val DEFAULT_START = "08:00"
    val durationChoices = listOf(2, 5, 10, 15, 20, 30, 45, 60)

    /** "07:30" -> 450. Virheellinen -> null. */
    fun parse(clock: String): Int? {
        val match = Regex("""^\s*(\d{1,2})[:.](\d{2})\s*$""").find(clock) ?: return null
        val h = match.groupValues[1].toInt()
        val m = match.groupValues[2].toInt()
        if (h !in 0..23 || m !in 0..59) return null
        return h * 60 + m
    }

    /** 450 -> "07:30". Yli vuorokauden menevä aika kiertää ympäri. */
    fun format(minutesOfDay: Int): String {
        val v = ((minutesOfDay % 1440) + 1440) % 1440
        return String.format(Locale.ROOT, "%02d:%02d", v / 60, v % 60)
    }

    data class Slot(
        val start: Int,
        /** Kiinteä aika alkaa ennen kuin edellinen toiminto on laskennallisesti päättynyt. */
        val overlap: Boolean,
        /** Laskennallinen alku ilman lukitusta (edellisen loppu). */
        val previousEnd: Int
    )

    /** Kellonaikatilan aikataulu: jokaisen toiminnon alku. */
    fun schedule(startClock: String, steps: List<AP16Step>): List<Slot> {
        var cursor = parse(startClock) ?: parse(DEFAULT_START)!!
        return steps.mapIndexed { index, step ->
            val fixed = if (index > 0 && step.fixedClock) parse(step.clock) else null
            val start = fixed ?: cursor
            val slot = Slot(start = start, overlap = fixed != null && fixed < cursor, previousEnd = cursor)
            cursor = start + step.minutes.coerceAtLeast(0)
            slot
        }
    }

    /** Ohjelman laskennallinen loppuaika kellonaikatilassa. */
    fun endOf(startClock: String, steps: List<AP16Step>): Int? {
        val slots = schedule(startClock, steps)
        val last = steps.lastOrNull() ?: return null
        return slots.last().start + last.minutes
    }

    /** Kirjoittaa aikatavan toimintojen omiin kenttiin ennen tallennusta. */
    fun apply(program: AP16Program): AP16Program {
        val steps = when (program.timeMode) {
            CLOCK -> {
                val slots = schedule(program.startClock, program.steps)
                program.steps.mapIndexed { i, step ->
                    step.copy(
                        mode = "clock",
                        clock = format(slots[i].start),
                        fixedClock = i > 0 && step.fixedClock
                    )
                }
            }
            DURATION -> program.steps.map {
                it.copy(mode = if (it.mode == "manual") "manual" else "duration", fixedClock = false)
            }
            else -> program.steps.map { it.copy(mode = "manual", fixedClock = false) }
        }
        val mode = if (program.timeMode in listOf(FREE, DURATION, CLOCK)) program.timeMode else FREE
        return program.copy(timeMode = mode, steps = steps)
    }

    /**
     * Vanha (ennen P0-23) tai valmiista pohjasta luotu ohjelma: päätellään aikatapa
     * toimintojen kentistä. Vanhan ohjelman kellonajat säilyvät lukittuina.
     */
    fun infer(program: AP16Program): AP16Program {
        if (program.timeMode in listOf(FREE, DURATION, CLOCK)) return program
        val steps = program.steps
        return when {
            steps.any { it.mode == "clock" } -> {
                val first = steps.first()
                val start = if (first.mode == "clock" && parse(first.clock) != null) first.clock else DEFAULT_START
                program.copy(
                    timeMode = CLOCK,
                    startClock = start,
                    steps = steps.mapIndexed { i, s -> s.copy(fixedClock = i > 0 && s.mode == "clock") }
                )
            }
            steps.any { it.mode == "duration" } -> program.copy(timeMode = DURATION)
            else -> program.copy(timeMode = FREE)
        }
    }

    /** Aikatavan vaihto muokkaimessa. */
    fun switchMode(program: AP16Program, mode: String): AP16Program = when (mode) {
        // Kestoihin siirryttäessä jokainen toiminto saa ajastimen; "Ei aikaa" valitaan erikseen.
        DURATION -> program.copy(timeMode = DURATION, steps = program.steps.map { it.copy(mode = "duration") })
        CLOCK -> program.copy(timeMode = CLOCK)
        else -> program.copy(timeMode = FREE)
    }

    /** Lyhyt aikateksti esikatseluun ja listaan. */
    fun label(program: AP16Program, index: Int): String {
        val step = program.steps.getOrNull(index) ?: return ""
        return when (program.timeMode) {
            CLOCK -> format(schedule(program.startClock, program.steps)[index].start)
            DURATION -> if (step.mode == "manual") "" else "${step.minutes} min"
            else -> ""
        }
    }

    /** Nimiehdotus tyhjälle nimelle. Ei toista olemassa olevaa nimeä. */
    fun suggestName(program: AP16Program, existingNames: Collection<String>): String {
        val base = if (program.timeMode == CLOCK) {
            val start = parse(program.startClock) ?: parse(DEFAULT_START)!!
            when {
                start < 10 * 60 -> "Aamurutiini"
                start < 17 * 60 -> "Päiväohjelma"
                else -> "Iltarutiini"
            }
        } else {
            "Päiväohjelma"
        }
        val taken = existingNames.map { it.trim().lowercase() }.toSet()
        if (base.lowercase() !in taken) return base
        var n = 2
        while ("$base $n".lowercase() in taken) n++
        return "$base $n"
    }
}
