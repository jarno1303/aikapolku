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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
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
import fi.aikapolku.app.data.ChangeReveal
import fi.aikapolku.app.data.ChangeStore
import fi.aikapolku.app.data.ChangeType
import fi.aikapolku.app.data.DayProgram
import fi.aikapolku.app.data.EventChange
import fi.aikapolku.app.data.EventKind
import fi.aikapolku.app.data.WeekEvent
import fi.aikapolku.app.data.WeekSchedule
import fi.aikapolku.app.ui.AppColors
import fi.aikapolku.app.ui.ChangeSymbol
import fi.aikapolku.app.voice.VoiceControls
import java.time.LocalDate
import java.util.UUID

/**
 * Ohjaajan muutos yhden päivän tapahtumaan: peruttu, tilalle muuta tai uusi aika; syy
 * tekstinä ja äänenä; milloin käyttäjä näkee muutoksen. Viikko-ohjelma ei muutu.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ChangeDialog(
    date: LocalDate,
    original: WeekEvent,
    existing: EventChange?,
    programs: List<DayProgram>,
    onSaved: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val store = remember(context) { ChangeStore(context.applicationContext) }
    var type by remember { mutableStateOf(existing?.type ?: ChangeType.REPLACE) }
    var kind by remember { mutableStateOf(existing?.kind ?: EventKind.CARD) }
    var programId by remember { mutableStateOf(existing?.programId ?: "") }
    var cardName by remember { mutableStateOf(existing?.cardName ?: "") }
    var cardPhoto by remember { mutableStateOf(existing?.cardPhoto ?: "") }
    val startMinutes = WeekSchedule.minutesOf(existing?.time?.ifBlank { null } ?: original.time)
    var hour by remember { mutableIntStateOf(startMinutes / 60) }
    var minute by remember { mutableIntStateOf(startMinutes % 60) }
    var reason by remember { mutableStateOf(existing?.reason ?: "") }
    var voicePath by remember { mutableStateOf(existing?.voicePath ?: "") }
    var autoPlay by remember { mutableStateOf(existing?.autoPlayVoice ?: false) }
    var reveal by remember { mutableStateOf(existing?.reveal ?: store.lastReveal) }
    var showOriginal by remember { mutableStateOf(existing?.showOriginal ?: store.lastShowOriginal) }
    var showLibrary by remember { mutableStateOf(false) }
    var showBuiltin by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val resolved = remember(original, programs) { resolveEvent(original, programs) }

    val needReplacement = stringResource(R.string.change_need_replacement)
    val needTime = stringResource(R.string.change_need_time)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ChangeSymbol(Modifier.size(30.dp))
                Text(stringResource(if (existing == null) R.string.change_make else R.string.change_edit))
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Mitä muutetaan: päivä, aika, kuva ja nimi.
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    EventImage(resolved, Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)))
                    Column {
                        Text(resolved.title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            stringResource(if (date == LocalDate.now()) R.string.change_day_today else R.string.change_day_tomorrow) +
                                " · " + original.time.replace(':', '.'),
                            fontSize = 14.sp,
                            color = AppColors.Muted
                        )
                    }
                }

                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        ChangeType.CANCEL to R.string.change_type_cancel,
                        ChangeType.REPLACE to R.string.change_type_replace,
                        ChangeType.MOVE to R.string.change_type_move
                    ).forEach { (t, label) ->
                        FilterChip(
                            selected = type == t,
                            onClick = { type = t; error = "" },
                            label = { Text(stringResource(label), fontSize = 15.sp) }
                        )
                    }
                }

                when (type) {
                    ChangeType.CANCEL -> Unit
                    ChangeType.REPLACE -> {
                        Text(stringResource(R.string.change_replacement), fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = kind == EventKind.CARD,
                                onClick = { kind = EventKind.CARD; error = "" },
                                label = { Text(stringResource(R.string.week_kind_card)) }
                            )
                            FilterChip(
                                selected = kind == EventKind.PROGRAM,
                                onClick = { kind = EventKind.PROGRAM; error = "" },
                                label = { Text(stringResource(R.string.week_kind_program)) }
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
                        val switchLabel = stringResource(R.string.change_show_original)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(switchLabel, fontWeight = FontWeight.SemiBold)
                                Text(stringResource(R.string.change_show_original_info), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = showOriginal,
                                onCheckedChange = { showOriginal = it },
                                modifier = Modifier.semantics { contentDescription = switchLabel }
                            )
                        }
                    }
                    ChangeType.MOVE -> {
                        Text(stringResource(R.string.change_type_move), fontWeight = FontWeight.SemiBold)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Stepper(stringResource(R.string.week_hour_down), "−") { hour = (hour + 23) % 24; error = "" }
                            Text("%02d".format(hour), fontSize = 30.sp, fontWeight = FontWeight.Bold)
                            Stepper(stringResource(R.string.week_hour_up), "+") { hour = (hour + 1) % 24; error = "" }
                            Text(":", fontSize = 30.sp, fontWeight = FontWeight.Bold)
                            Stepper(stringResource(R.string.week_minute_down), "−") { minute = ((minute / 5) * 5 + 55) % 60; error = "" }
                            Text("%02d".format(minute), fontSize = 30.sp, fontWeight = FontWeight.Bold)
                            Stepper(stringResource(R.string.week_minute_up), "+") { minute = ((minute / 5) * 5 + 5) % 60; error = "" }
                        }
                    }
                }

                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text(stringResource(R.string.change_reason)) },
                    placeholder = { Text(stringResource(R.string.change_reason_hint)) },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
                VoiceControls(
                    voicePath = voicePath,
                    autoPlay = autoPlay,
                    onVoicePathChange = { voicePath = it },
                    onAutoPlayChange = { autoPlay = it }
                )

                // Milloin käyttäjä näkee muutoksen: valitaan muutoskohtaisesti.
                Text(stringResource(R.string.change_reveal_title), fontWeight = FontWeight.SemiBold)
                listOf(
                    Triple(ChangeReveal.NOW, R.string.change_reveal_now, R.string.change_reveal_now_info),
                    Triple(ChangeReveal.MORNING, R.string.change_reveal_morning, R.string.change_reveal_morning_info),
                    Triple(ChangeReveal.AT_EVENT, R.string.change_reveal_event, R.string.change_reveal_event_info)
                ).forEach { (option, title, info) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .selectable(selected = reveal == option, role = Role.RadioButton) { reveal = option },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = reveal == option, onClick = null)
                        Column(modifier = Modifier.padding(start = 8.dp)) {
                            Text(stringResource(title), fontSize = 16.sp)
                            Text(stringResource(info), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Text(stringResource(R.string.change_only_this_device), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)

                if (existing != null) {
                    TextButton(
                        onClick = {
                            store.delete(existing.id)
                            EventReminders.reschedule(context.applicationContext)
                            onSaved()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.change_remove), color = MaterialTheme.colorScheme.error) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val time = WeekSchedule.formatTime(hour, minute)
                when {
                    type == ChangeType.REPLACE && kind == EventKind.PROGRAM && programs.none { it.id == programId } -> error = needReplacement
                    type == ChangeType.REPLACE && kind == EventKind.CARD && cardName.isBlank() -> error = needReplacement
                    type == ChangeType.MOVE && time == original.time -> error = needTime
                    else -> {
                        store.upsert(
                            EventChange(
                                id = existing?.id ?: UUID.randomUUID().toString(),
                                date = date,
                                eventId = original.id,
                                type = type,
                                kind = kind,
                                programId = if (type == ChangeType.REPLACE && kind == EventKind.PROGRAM) programId else "",
                                cardName = if (type == ChangeType.REPLACE && kind == EventKind.CARD) cardName else "",
                                cardPhoto = if (type == ChangeType.REPLACE && kind == EventKind.CARD) cardPhoto else "",
                                time = if (type == ChangeType.MOVE) time else "",
                                reason = reason.trim(),
                                voicePath = voicePath,
                                autoPlayVoice = autoPlay && voicePath.isNotBlank(),
                                reveal = reveal,
                                showOriginal = showOriginal
                            )
                        )
                        EventReminders.reschedule(context.applicationContext)
                        onSaved()
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
