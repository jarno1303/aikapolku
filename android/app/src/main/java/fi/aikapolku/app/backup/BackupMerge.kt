package fi.aikapolku.app.backup

import fi.aikapolku.app.data.CardItem
import fi.aikapolku.app.data.DayProgram
import fi.aikapolku.app.data.EventKind
import fi.aikapolku.app.data.WeekPlan

/**
 * Tuonti nykyiseen profiiliin: mitään olemassa olevaa ei korvata. Täysin samanlainen ohitetaan,
 * samanniminen mutta erilainen saa nimen "(2)". Puhdas logiikka, testattavissa ilman laitetta.
 */
internal object BackupMerge {
    internal data class Result<T>(
        val added: List<T>,
        /** Tuodun tunnus -> tunnus tässä profiilissa (uusi tai ohitetun samanlaisen). */
        val idMap: Map<String, String>,
        val skipped: Int
    )

    /** Oma tiedosto ja polku vertailussa samaksi: tuodulla kuvalla on aina uusi polku. */
    private fun media(path: String) = when {
        path.isBlank() -> ""
        path.startsWith("builtin:") -> path
        else -> "file"
    }

    private fun normalized(p: DayProgram) = p.copy(
        id = "",
        name = p.name.trim().lowercase(),
        steps = p.steps.map { it.copy(id = "", photo = media(it.photo), voicePath = media(it.voicePath)) }
    )

    private fun normalized(c: CardItem) = c.copy(
        id = "",
        name = c.name.trim().lowercase(),
        photoPath = media(c.photoPath),
        voicePath = media(c.voicePath)
    )

    fun programs(existing: List<DayProgram>, incoming: List<DayProgram>, newId: () -> String): Result<DayProgram> {
        val added = mutableListOf<DayProgram>()
        val idMap = mutableMapOf<String, String>()
        var skipped = 0
        incoming.forEach { p ->
            val same = (existing + added).firstOrNull { normalized(it) == normalized(p) }
            if (same != null) {
                idMap[p.id] = same.id
                skipped++
            } else {
                val copy = p.copy(id = newId(), name = UniqueNames.unique(p.name, (existing + added).map { it.name }))
                idMap[p.id] = copy.id
                added += copy
            }
        }
        return Result(added, idMap, skipped)
    }

    fun cards(existing: List<CardItem>, incoming: List<CardItem>, newId: () -> String): Result<CardItem> {
        val added = mutableListOf<CardItem>()
        val idMap = mutableMapOf<String, String>()
        var skipped = 0
        incoming.forEach { c ->
            val same = (existing + added).firstOrNull { normalized(it) == normalized(c) }
            if (same != null) {
                idMap[c.id] = same.id
                skipped++
            } else {
                val copy = c.copy(id = newId(), name = UniqueNames.unique(c.name, (existing + added).map { it.name }))
                idMap[c.id] = copy.id
                added += copy
            }
        }
        return Result(added, idMap, skipped)
    }

    /**
     * Tuotu viikko-ohjelma tähän profiiliin: päiväohjelmatapahtumat osoittavat tuotuihin
     * (tai samanlaisiin olemassa oleviin) ohjelmiin; puuttuvat jätetään pois.
     */
    fun week(incoming: WeekPlan, programIds: Map<String, String>, newId: () -> String): WeekPlan =
        incoming.copy(
            events = incoming.events.mapNotNull { e ->
                if (e.kind == EventKind.PROGRAM) {
                    val mapped = programIds[e.programId] ?: return@mapNotNull null
                    e.copy(id = newId(), programId = mapped)
                } else {
                    e.copy(id = newId())
                }
            }
        )

    /**
     * Ohjelmapohja ilman henkilötietoja: omat valokuvat korvataan valmiilla kuvalla, jos
     * sellainen löytyy vaiheen nimellä ([builtinFor]), muuten kuva jätetään pois.
     * Äänitteet jätetään pois (niissä voi olla ohjaajan tai asiakkaan ääni).
     */
    fun anonymized(program: DayProgram, builtinFor: (String) -> String?): DayProgram =
        program.copy(
            steps = program.steps.map { step ->
                val photo = when {
                    step.photo.isBlank() || step.photo.startsWith("builtin:") -> step.photo
                    else -> builtinFor(step.name) ?: ""
                }
                step.copy(photo = photo, voicePath = "", autoPlayVoice = false)
            }
        )
}
