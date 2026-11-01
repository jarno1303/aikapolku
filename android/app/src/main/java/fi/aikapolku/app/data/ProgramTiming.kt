package fi.aikapolku.app.data

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
    fun schedule(startClock: String, steps: List<ProgramStep>): List<Slot> {
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
    fun endOf(startClock: String, steps: List<ProgramStep>): Int? {
        val slots = schedule(startClock, steps)
        val last = steps.lastOrNull() ?: return null
        return slots.last().start + last.minutes
    }

    /** Kirjoittaa aikatavan toimintojen omiin kenttiin ennen tallennusta. */
    fun apply(program: DayProgram): DayProgram {
        val steps = when (program.timeMode) {
            TimeMode.CLOCK -> {
                val slots = schedule(program.startClock, program.steps)
                program.steps.mapIndexed { i, step ->
                    step.copy(
                        mode = StepMode.CLOCK,
                        clock = format(slots[i].start),
                        fixedClock = i > 0 && step.fixedClock
                    )
                }
            }
            TimeMode.DURATION -> program.steps.map {
                it.copy(mode = if (it.mode == StepMode.MANUAL) StepMode.MANUAL else StepMode.DURATION, fixedClock = false)
            }
            TimeMode.FREE, null -> program.steps.map { it.copy(mode = StepMode.MANUAL, fixedClock = false) }
        }
        return program.copy(timeMode = program.timeMode ?: TimeMode.FREE, steps = steps)
    }

    /**
     * Vanha (ennen P0-23) tai valmiista pohjasta luotu ohjelma: päätellään aikatapa
     * toimintojen kentistä. Vanhan ohjelman kellonajat säilyvät lukittuina.
     */
    fun infer(program: DayProgram): DayProgram {
        if (program.timeMode != null) return program
        val steps = program.steps
        return when {
            steps.any { it.mode == StepMode.CLOCK } -> {
                val first = steps.first()
                val start = if (first.mode == StepMode.CLOCK && parse(first.clock) != null) first.clock else DEFAULT_START
                program.copy(
                    timeMode = TimeMode.CLOCK,
                    startClock = start,
                    steps = steps.mapIndexed { i, s -> s.copy(fixedClock = i > 0 && s.mode == StepMode.CLOCK) }
                )
            }
            steps.any { it.mode == StepMode.DURATION } -> program.copy(timeMode = TimeMode.DURATION)
            else -> program.copy(timeMode = TimeMode.FREE)
        }
    }

    /** Aikatavan vaihto muokkaimessa. */
    fun switchMode(program: DayProgram, mode: TimeMode): DayProgram = when (mode) {
        // Kestoihin siirryttäessä jokainen toiminto saa ajastimen; "Ei aikaa" valitaan erikseen.
        TimeMode.DURATION -> program.copy(timeMode = TimeMode.DURATION, steps = program.steps.map { it.copy(mode = StepMode.DURATION) })
        TimeMode.CLOCK -> program.copy(timeMode = TimeMode.CLOCK)
        TimeMode.FREE -> program.copy(timeMode = TimeMode.FREE)
    }

    /** Lyhyt aikateksti esikatseluun ja listaan. */
    fun label(program: DayProgram, index: Int, minutesText: (Int) -> String): String {
        val step = program.steps.getOrNull(index) ?: return ""
        return when (program.timeMode) {
            TimeMode.CLOCK -> format(schedule(program.startClock, program.steps)[index].start)
            TimeMode.DURATION -> if (step.mode == StepMode.MANUAL) "" else minutesText(step.minutes)
            TimeMode.FREE, null -> ""
        }
    }

    /** Nimiehdotuksen vuorokaudenaika; teksti tulee käännettävistä resursseista. */
    enum class DayPart { MORNING, DAY, EVENING }

    fun dayPart(program: DayProgram): DayPart {
        if (program.timeMode != TimeMode.CLOCK) return DayPart.DAY
        val start = parse(program.startClock) ?: parse(DEFAULT_START)!!
        return when {
            start < 10 * 60 -> DayPart.MORNING
            start < 17 * 60 -> DayPart.DAY
            else -> DayPart.EVENING
        }
    }

    /** Nimiehdotus tyhjälle nimelle. Ei toista olemassa olevaa nimeä. */
    fun suggestName(program: DayProgram, existingNames: Collection<String>, partName: (DayPart) -> String): String {
        val base = partName(dayPart(program))
        val taken = existingNames.map { it.trim().lowercase() }.toSet()
        if (base.lowercase() !in taken) return base
        var n = 2
        while ("$base $n".lowercase() in taken) n++
        return "$base $n"
    }
}
