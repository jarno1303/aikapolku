package fi.aikapolku.app.week

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fi.aikapolku.app.R
import fi.aikapolku.app.cards.BuiltinCardPicker
import fi.aikapolku.app.cards.CardLibraryDialog
import fi.aikapolku.app.cards.CardPhoto
import fi.aikapolku.app.data.DayProgram
import fi.aikapolku.app.data.EventKind
import fi.aikapolku.app.data.WeekEvent
import fi.aikapolku.app.data.WeekSchedule
import fi.aikapolku.app.ui.AppColors
import java.util.UUID

/**
 * Tapahtuman lisäys tai muokkaus: kellonaika, sisältö (päiväohjelma tai kortti) ja
 * uudelle tapahtumalle päivät, joille se lisätään kerralla (esim. ma–pe).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WeekEventDialog(
    initial: WeekEvent?,
    day: Int,
    week: Int,
    programs: List<DayProgram>,
    onSave: (List<WeekEvent>) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val startMinutes = initial?.let { WeekSchedule.minutesOf(it.time) } ?: (8 * 60)
    var hour by remember { mutableIntStateOf(startMinutes / 60) }
    var minute by remember { mutableIntStateOf(startMinutes % 60) }
    var kind by remember { mutableStateOf(initial?.kind ?: if (programs.isNotEmpty()) EventKind.PROGRAM else EventKind.CARD) }
    var programId by remember { mutableStateOf(initial?.programId ?: "") }
    var cardName by remember { mutableStateOf(initial?.cardName ?: "") }
    var cardPhoto by remember { mutableStateOf(initial?.cardPhoto ?: "") }
    var days by remember { mutableStateOf(setOf(day)) }
    var showLibrary by remember { mutableStateOf(false) }
    var showBuiltin by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    val needProgram = stringResource(R.string.week_need_program)
    val needCard = stringResource(R.string.week_need_card)
    val needDay = stringResource(R.string.week_need_day)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (initial == null) R.string.week_add_title else R.string.week_edit_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Kellonaika: tunnit ja minuutit erikseen (minuutit 5 min välein), isot painikkeet.
                Text(stringResource(R.string.week_time), fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Stepper(stringResource(R.string.week_hour_down), "−") { hour = (hour + 23) % 24 }
                    Text("%02d".format(hour), fontSize = 30.sp, fontWeight = FontWeight.Bold)
                    Stepper(stringResource(R.string.week_hour_up), "+") { hour = (hour + 1) % 24 }
                    Text(":", fontSize = 30.sp, fontWeight = FontWeight.Bold)
                    Stepper(stringResource(R.string.week_minute_down), "−") { minute = ((minute / 5) * 5 + 55) % 60 }
                    Text("%02d".format(minute), fontSize = 30.sp, fontWeight = FontWeight.Bold)
                    Stepper(stringResource(R.string.week_minute_up), "+") { minute = ((minute / 5) * 5 + 5) % 60 }
                }

                Text(stringResource(R.string.week_content), fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = kind == EventKind.PROGRAM,
                        onClick = { kind = EventKind.PROGRAM; error = "" },
                        label = { Text(stringResource(R.string.week_kind_program)) }
                    )
                    FilterChip(
                        selected = kind == EventKind.CARD,
                        onClick = { kind = EventKind.CARD; error = "" },
                        label = { Text(stringResource(R.string.week_kind_card)) }
                    )
                }

                if (kind == EventKind.PROGRAM) {
                    if (programs.isEmpty()) {
                        Text(stringResource(R.string.week_no_programs), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    programs.forEach { program ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .selectable(selected = programId == program.id, role = Role.RadioButton) {
                                    programId = program.id
                                    error = ""
                                },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = programId == program.id, onClick = null)
                            Text(program.name, modifier = Modifier.padding(start = 8.dp), fontSize = 16.sp)
                        }
                    }
                } else {
                    if (cardName.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            CardPhoto(cardName, cardPhoto, Modifier.size(64.dp).clip(RoundedCornerShape(14.dp)))
                            Text(cardName, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { showLibrary = true }) { Text(stringResource(R.string.week_own_cards)) }
                        OutlinedButton(onClick = { showBuiltin = true }) { Text(stringResource(R.string.week_builtin_cards)) }
                    }
                }

                if (initial == null) {
                    Text(stringResource(R.string.week_days), fontWeight = FontWeight.SemiBold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        (1..7).forEach { d ->
                            val on = d in days
                            val full = dayName(d)
                            FilterChip(
                                selected = on,
                                onClick = { days = if (on) days - d else days + d; error = "" },
                                label = { Text(dayShort(d)) },
                                modifier = Modifier.widthIn(min = 48.dp).semantics { contentDescription = full }
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { days = (1..5).toSet() }) { Text(stringResource(R.string.week_weekdays)) }
                        TextButton(onClick = { days = (1..7).toSet() }) { Text(stringResource(R.string.week_every_day)) }
                    }
                }

                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)

                if (initial != null) {
                    TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.week_delete_event), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val time = WeekSchedule.formatTime(hour, minute)
                when {
                    kind == EventKind.PROGRAM && programs.none { it.id == programId } -> error = needProgram
                    kind == EventKind.CARD && cardName.isBlank() -> error = needCard
                    initial == null && days.isEmpty() -> error = needDay
                    else -> {
                        fun build(id: String, d: Int) = WeekEvent(
                            id = id,
                            day = d,
                            week = week,
                            time = time,
                            kind = kind,
                            programId = if (kind == EventKind.PROGRAM) programId else "",
                            cardName = if (kind == EventKind.CARD) cardName else "",
                            cardPhoto = if (kind == EventKind.CARD) cardPhoto else ""
                        )
                        onSave(
                            if (initial != null) listOf(build(initial.id, initial.day))
                            else days.sorted().map { build(UUID.randomUUID().toString(), it) }
                        )
                    }
                }
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )

    if (showLibrary) {
        CardLibraryDialog(
            onDismiss = { showLibrary = false },
            onSelect = { card ->
                cardName = card.name
                cardPhoto = card.photoPath
                error = ""
                showLibrary = false
            }
        )
    }
    if (showBuiltin) {
        BuiltinCardPicker(
            onDismiss = { showBuiltin = false },
            onSelect = { id, name ->
                cardName = name
                cardPhoto = "builtin:$id"
                error = ""
                showBuiltin = false
            }
        )
    }
}

@Composable
private fun Stepper(description: String, symbol: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.size(48.dp).semantics { contentDescription = description },
        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
    ) { Text(symbol, fontSize = 22.sp, color = AppColors.Teal) }
}
