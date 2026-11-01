package fi.aikapolku.app.week

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import fi.aikapolku.app.cards.CardPhoto
import fi.aikapolku.app.data.DayProgram
import fi.aikapolku.app.data.EventKind
import fi.aikapolku.app.data.WeekEvent
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/** Tapahtuma näyttämistä varten: otsikko ja kuva päiväohjelmasta tai kortista. Tyhjä otsikko = ohjelma puuttuu. */
internal data class ResolvedEvent(
    val event: WeekEvent,
    val title: String,
    val imageName: String,
    val imagePath: String,
    val isProgram: Boolean
)

internal fun resolveEvent(event: WeekEvent, programs: List<DayProgram>): ResolvedEvent =
    when (event.kind) {
        EventKind.PROGRAM -> {
            val program = programs.firstOrNull { it.id == event.programId }
            val first = program?.steps?.firstOrNull()
            ResolvedEvent(event, program?.name ?: "", first?.name ?: "", first?.photo ?: "", isProgram = true)
        }
        EventKind.CARD -> ResolvedEvent(event, event.cardName, event.cardName, event.cardPhoto, isProgram = false)
    }

@Composable
internal fun EventImage(resolved: ResolvedEvent, modifier: Modifier) {
    CardPhoto(resolved.imageName, resolved.imagePath, modifier)
}

/** Viikonpäivän nimet laitteen kielellä (java.time), joten käännöksiä ei tarvita. */
internal fun dayName(day: Int): String =
    DayOfWeek.of(day).getDisplayName(TextStyle.FULL_STANDALONE, Locale.getDefault())
        .replaceFirstChar { it.titlecase(Locale.getDefault()) }

internal fun dayShort(day: Int): String =
    DayOfWeek.of(day).getDisplayName(TextStyle.SHORT_STANDALONE, Locale.getDefault())
        .trimEnd('.')
        .replaceFirstChar { it.titlecase(Locale.getDefault()) }
