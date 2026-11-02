package fi.aikapolku.app.week

import android.widget.Toast
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fi.aikapolku.app.R
import fi.aikapolku.app.cards.CardPhoto
import fi.aikapolku.app.cards.cardsByDayTime
import fi.aikapolku.app.cards.labelRes
import fi.aikapolku.app.data.CardItem
import fi.aikapolku.app.data.CardStore
import fi.aikapolku.app.data.ChangeReveal
import fi.aikapolku.app.data.ChangeStore
import fi.aikapolku.app.data.ChangeType
import fi.aikapolku.app.data.DayChanges
import fi.aikapolku.app.data.DayProgram
import fi.aikapolku.app.data.EventChange
import fi.aikapolku.app.data.EventKind
import fi.aikapolku.app.data.ProgramStep
import fi.aikapolku.app.data.ProgramStore
import fi.aikapolku.app.data.WeekEvent
import fi.aikapolku.app.data.WeekSchedule
import fi.aikapolku.app.data.WeekStore
import fi.aikapolku.app.ui.AppColors
import fi.aikapolku.app.ui.BackOverride
import fi.aikapolku.app.ui.ChangeSymbol
import fi.aikapolku.app.ui.PrimaryButton
import fi.aikapolku.app.ui.SegmentedChoice
import fi.aikapolku.app.voice.VoiceControls
import java.time.LocalDate
import java.util.UUID

/** Iso valintapainike pikamuutoksessa (Peruttu, Siirrä aikaa …). */
@Composable
private fun BigChoice(text: String, modifier: Modifier = Modifier, leading: @Composable () -> Unit = {}, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = AppColors.ChangeSoft,
        modifier = modifier
            .heightIn(min = 64.dp)
            .border(2.dp, AppColors.ChangeFill, RoundedCornerShape(16.dp))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            leading()
            Text(text, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = AppColors.Ink)
        }
    }
}

/** Kuvallinen rivi (tapahtuma, vaihe, kortti, ohjelma). */
@Composable
private fun PictureRow(
    title: String,
    subtitle: String? = null,
    name: String,
    photo: String,
    changed: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .then(if (changed) Modifier.border(2.dp, AppColors.ChangeFill, RoundedCornerShape(16.dp)) else Modifier)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box {
                CardPhoto(name, photo, Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)))
                if (changed) ChangeSymbol(Modifier.size(20.dp).align(Alignment.TopEnd).offset(x = 6.dp, y = (-6).dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = AppColors.Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (!subtitle.isNullOrBlank()) Text(subtitle, fontSize = 14.sp, color = AppColors.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun StageTitle(text: String) {
    Text(text, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = AppColors.Ink, modifier = Modifier.semantics { heading() })
}

/**
 * Mitä tilalle: isot painikkeet (Peruttu, Siirrä aikaa) ja kuvallinen korttivalinta
 * vuorokauden mukaan, kuten Ensin–Sitten-valinnassa. [programs] = toinen päiväohjelma tilalle.
 */
@Composable
internal fun ReplacementChoices(
    cancelLabel: String,
    onCancel: () -> Unit,
    onMove: (() -> Unit)?,
    programs: List<DayProgram>,
    onProgram: (DayProgram) -> Unit,
    onCard: (CardItem) -> Unit
) {
    val context = LocalContext.current
    val groups = remember(context) {
        cardsByDayTime(runCatching { CardStore(context).loadCards() }.getOrDefault(emptyList())) { context.getString(it) }
    }
    var showPrograms by remember { mutableStateOf(false) }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        BigChoice(cancelLabel, Modifier.weight(1f), leading = {
            androidx.compose.material3.Icon(Icons.Rounded.Close, contentDescription = null, tint = AppColors.Change)
        }, onClick = onCancel)
        if (onMove != null) {
            BigChoice(stringResource(R.string.change_type_move), Modifier.weight(1f), leading = {
                androidx.compose.material3.Icon(Icons.Rounded.Refresh, contentDescription = null, tint = AppColors.Change)
            }, onClick = onMove)
        }
    }
    if (programs.isNotEmpty()) {
        TextButton(onClick = { showPrograms = !showPrograms }) {
            Text((if (showPrograms) "▾ " else "▸ ") + stringResource(R.string.quick_other_program), fontSize = 16.sp, color = AppColors.Teal)
        }
        if (showPrograms) {
            programs.forEach { p ->
                val first = p.steps.firstOrNull()
                PictureRow(p.name, null, first?.name ?: "", first?.photo ?: "") { onProgram(p) }
            }
        }
    }
    Text(stringResource(R.string.quick_card_header), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = AppColors.Muted)
    groups.forEach { (time, cards) ->
        Text(
            stringResource(time.labelRes()),
            modifier = Modifier.padding(top = 6.dp).semantics { heading() },
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = AppColors.Teal
        )
        cards.forEach { card ->
            PictureRow(card.name, card.description.takeIf { it.isNotBlank() }, card.name, card.photoPath) { onCard(card) }
        }
    }
}

/** Valmiin kortin kuva tallennetaan tunnuksella ("builtin:id"), oma kortti omalla kuvallaan. */
private fun cardPhoto(card: CardItem) = card.photoPath

private enum class Stage { EVENT, STEP, REPLACEMENT, TIME, CONFIRM }

/**
 * Pikamuutos: Mikä muuttuu? → Mitä tilalle? → Valmis. Syy, äänite ja näkymisen ajankohta
 * ovat Lisävalinnoissa (edellisen muutoksen arvoilla). [initialEventId] = valmiiksi valittu.
 */
@Composable
internal fun QuickChangeScreen(initialDayOffset: Int, initialEventId: String?, onDone: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext
    val store = remember { ChangeStore(app) }
    val plan = remember { WeekStore(app).load() }
    val programs = remember { ProgramStore(app).load() }
    var refresh by remember { mutableIntStateOf(0) }
    val changes = remember(refresh) { store.load() }

    var dayOffset by rememberSaveable { mutableIntStateOf(initialDayOffset.coerceIn(0, 1)) }
    var eventId by rememberSaveable { mutableStateOf(initialEventId) }
    var stepId by rememberSaveable { mutableStateOf("") }
    var stage by rememberSaveable {
        mutableStateOf(
            when {
                initialEventId == null -> Stage.EVENT
                plan.events.firstOrNull { it.id == initialEventId }?.kind == EventKind.PROGRAM -> Stage.STEP
                else -> Stage.REPLACEMENT
            }
        )
    }
    var type by rememberSaveable { mutableStateOf(ChangeType.REPLACE) }
    var kind by rememberSaveable { mutableStateOf(EventKind.CARD) }
    var programId by rememberSaveable { mutableStateOf("") }
    var cardName by rememberSaveable { mutableStateOf("") }
    var cardPhoto by rememberSaveable { mutableStateOf("") }
    var hour by rememberSaveable { mutableIntStateOf(8) }
    var minute by rememberSaveable { mutableIntStateOf(0) }
    var reason by rememberSaveable { mutableStateOf("") }
    var voicePath by rememberSaveable { mutableStateOf("") }
    var autoPlay by rememberSaveable { mutableStateOf(false) }
    var reveal by rememberSaveable { mutableStateOf(store.lastReveal) }
    var showOriginal by rememberSaveable { mutableStateOf(store.lastShowOriginal) }
    var showMore by rememberSaveable { mutableStateOf(false) }

    val date = LocalDate.now().plusDays(dayOffset.toLong())
    val events = WeekSchedule.eventsFor(plan, date)
    val event = events.firstOrNull { it.id == eventId }
    val program = event?.takeIf { it.kind == EventKind.PROGRAM }?.let { e -> programs.firstOrNull { it.id == e.programId } }
    val step = program?.steps?.firstOrNull { it.id == stepId }
    val existing = event?.let { e -> changes.firstOrNull { it.date == date && it.eventId == e.id && it.stepId == stepId } }

    // Olemassa olevan muutoksen arvot pohjaksi, kun kohde valitaan.
    fun loadExisting(target: EventChange?) {
        if (target == null) return
        type = target.type
        kind = target.kind
        programId = target.programId
        cardName = target.cardName
        cardPhoto = target.cardPhoto
        target.time.takeIf { it.isNotBlank() }?.let { WeekSchedule.minutesOf(it).let { m -> hour = m / 60; minute = m % 60 } }
        reason = target.reason
        voicePath = target.voicePath
        autoPlay = target.autoPlayVoice
        reveal = target.reveal
        showOriginal = target.showOriginal
    }

    // Valmiiksi valittu tapahtuma (rivin muutosmerkistä): nykyinen muutos pohjaksi.
    var preloaded by rememberSaveable { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (!preloaded && initialEventId != null && stage == Stage.REPLACEMENT && stepId.isEmpty()) {
            preloaded = true
            loadExisting(DayChanges.wholeChange(changes, date, initialEventId))
        }
    }

    fun back() {
        stage = when (stage) {
            Stage.EVENT -> { onDone(); Stage.EVENT }
            Stage.STEP -> Stage.EVENT
            Stage.REPLACEMENT -> if (program != null) Stage.STEP else Stage.EVENT
            Stage.TIME -> Stage.REPLACEMENT
            Stage.CONFIRM -> if (type == ChangeType.MOVE) Stage.TIME else Stage.REPLACEMENT
        }
    }
    BackOverride { back() }

    when (stage) {
        Stage.EVENT -> {
            StageTitle(stringResource(R.string.quick_what))
            SegmentedChoice(
                options = listOf(stringResource(R.string.change_day_today), stringResource(R.string.change_day_tomorrow)),
                selected = dayOffset,
                onSelect = { dayOffset = it }
            )
            if (events.isEmpty()) Text(stringResource(R.string.quick_no_events), color = AppColors.Muted)
            events.forEach { e ->
                val r = resolveEvent(e, programs)
                if (r.title.isBlank()) return@forEach
                val has = changes.any { it.date == date && it.eventId == e.id }
                PictureRow(e.time.replace(':', '.') + "  " + r.title, null, r.imageName, r.imagePath, changed = has) {
                    eventId = e.id
                    stepId = ""
                    if (e.kind == EventKind.PROGRAM) {
                        stage = Stage.STEP
                    } else {
                        loadExisting(DayChanges.wholeChange(changes, date, e.id))
                        stage = Stage.REPLACEMENT
                    }
                }
            }
        }

        Stage.STEP -> {
            val e = event ?: run { stage = Stage.EVENT; return }
            StageTitle(stringResource(R.string.quick_what))
            Text(resolveEvent(e, programs).title, fontSize = 17.sp, color = AppColors.Muted)
            val r = resolveEvent(e, programs)
            PictureRow(
                stringResource(R.string.quick_whole_program),
                stringResource(R.string.quick_whole_program_info),
                r.imageName, r.imagePath,
                changed = DayChanges.wholeChange(changes, date, e.id) != null
            ) {
                stepId = ""
                loadExisting(DayChanges.wholeChange(changes, date, e.id))
                stage = Stage.REPLACEMENT
            }
            program?.steps?.forEach { s ->
                val has = changes.any { it.date == date && it.eventId == e.id && it.stepId == s.id }
                PictureRow(s.name, null, s.name, s.photo, changed = has) {
                    stepId = s.id
                    loadExisting(changes.firstOrNull { it.date == date && it.eventId == e.id && it.stepId == s.id })
                    stage = Stage.REPLACEMENT
                }
            }
        }

        Stage.REPLACEMENT -> {
            val e = event ?: run { stage = Stage.EVENT; return }
            StageTitle(stringResource(R.string.quick_replacement))
            val r = resolveEvent(e, programs)
            Text(
                (if (step != null) "${r.title}: ${step.name}" else r.title) + " · " + e.time.replace(':', '.'),
                fontSize = 17.sp,
                color = AppColors.Muted
            )
            if (existing != null) {
                Text(
                    stringResource(R.string.quick_current, changeSummary(existing, r, resolveEvent(DayChanges.effective(e, existing), programs))),
                    fontSize = 15.sp,
                    color = AppColors.Change
                )
                TextButton(onClick = {
                    store.delete(existing.id)
                    EventReminders.reschedule(app)
                    refresh++
                }) { Text(stringResource(R.string.change_remove), color = MaterialTheme.colorScheme.error) }
            }
            ReplacementChoices(
                cancelLabel = stringResource(R.string.change_type_cancel),
                onCancel = { type = ChangeType.CANCEL; stage = Stage.CONFIRM },
                onMove = if (step == null) {
                    {
                        type = ChangeType.MOVE
                        if (existing?.type != ChangeType.MOVE) WeekSchedule.minutesOf(e.time).let { hour = it / 60; minute = it % 60 }
                        stage = Stage.TIME
                    }
                } else null,
                programs = if (step == null) programs.filter { it.id != e.programId } else emptyList(),
                onProgram = { p ->
                    type = ChangeType.REPLACE
                    kind = EventKind.PROGRAM
                    programId = p.id
                    stage = Stage.CONFIRM
                },
                onCard = { card ->
                    type = ChangeType.REPLACE
                    kind = EventKind.CARD
                    cardName = card.name
                    cardPhoto = cardPhoto(card)
                    stage = Stage.CONFIRM
                }
            )
        }

        Stage.TIME -> {
            val e = event ?: run { stage = Stage.EVENT; return }
            StageTitle(stringResource(R.string.change_type_move))
            Text(resolveEvent(e, programs).title + " · " + e.time.replace(':', '.'), fontSize = 17.sp, color = AppColors.Muted)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Stepper(stringResource(R.string.week_hour_down), "−") { hour = (hour + 23) % 24 }
                Text("%02d".format(hour), fontSize = 34.sp, fontWeight = FontWeight.Bold)
                Stepper(stringResource(R.string.week_hour_up), "+") { hour = (hour + 1) % 24 }
                Text(":", fontSize = 34.sp, fontWeight = FontWeight.Bold)
                Stepper(stringResource(R.string.week_minute_down), "−") { minute = ((minute / 5) * 5 + 55) % 60 }
                Text("%02d".format(minute), fontSize = 34.sp, fontWeight = FontWeight.Bold)
                Stepper(stringResource(R.string.week_minute_up), "+") { minute = ((minute / 5) * 5 + 5) % 60 }
            }
            PrimaryButton(
                text = stringResource(R.string.quick_next),
                enabled = WeekSchedule.formatTime(hour, minute) != e.time,
                onClick = { stage = Stage.CONFIRM },
                modifier = Modifier.fillMaxWidth()
            )
        }

        Stage.CONFIRM -> {
            val e = event ?: run { stage = Stage.EVENT; return }
            val draft = EventChange(
                id = existing?.id ?: UUID.randomUUID().toString(),
                date = date,
                eventId = e.id,
                type = type,
                kind = kind,
                programId = if (type == ChangeType.REPLACE && kind == EventKind.PROGRAM && step == null) programId else "",
                cardName = if (type == ChangeType.REPLACE && (kind == EventKind.CARD || step != null)) cardName else "",
                cardPhoto = if (type == ChangeType.REPLACE && (kind == EventKind.CARD || step != null)) cardPhoto else "",
                time = if (type == ChangeType.MOVE) WeekSchedule.formatTime(hour, minute) else "",
                reason = reason.trim(),
                voicePath = voicePath,
                autoPlayVoice = autoPlay && voicePath.isNotBlank(),
                reveal = reveal,
                showOriginal = showOriginal,
                stepId = step?.id ?: "",
                stepName = step?.name ?: "",
                stepPhoto = step?.photo ?: ""
            )
            StageTitle(stringResource(R.string.quick_confirm))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ChangeSymbol(Modifier.size(44.dp))
                Text(
                    changeSummary(draft, resolveEvent(e, programs), resolveEvent(DayChanges.effective(e, draft), programs)),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.Ink,
                    modifier = Modifier.weight(1f)
                )
            }
            Text(
                stringResource(if (dayOffset == 0) R.string.change_day_today else R.string.change_day_tomorrow),
                fontSize = 15.sp,
                color = AppColors.Muted
            )

            Surface(
                onClick = { showMore = !showMore },
                shape = RoundedCornerShape(14.dp),
                color = AppColors.TealSoft,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                    Text((if (showMore) "▾ " else "▸ ") + stringResource(R.string.quick_more), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = AppColors.Teal)
                    // Kuvaus vain suljettuna; avattuna kentät kertovat saman.
                    if (!showMore) Text(stringResource(R.string.quick_more_info), fontSize = 13.sp, color = AppColors.Muted)
                }
            }
            if (showMore) {
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
                            .selectable(selected = reveal == option, role = Role.RadioButton) { reveal = option }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        // Valintaympyrä otsikon kohdalla, kuvaus sen alla.
                        RadioButton(selected = reveal == option, onClick = null, modifier = Modifier.padding(top = 2.dp))
                        Column(modifier = Modifier.padding(start = 8.dp)) {
                            Text(stringResource(title), fontSize = 16.sp)
                            Text(stringResource(info), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (type == ChangeType.REPLACE) {
                    val switchLabel = stringResource(R.string.change_show_original)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(switchLabel, fontWeight = FontWeight.SemiBold)
                            Text(stringResource(R.string.change_show_original_info), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = showOriginal, onCheckedChange = { showOriginal = it }, modifier = Modifier.semantics { contentDescription = switchLabel })
                    }
                }
                Text(stringResource(R.string.change_only_this_device), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            PrimaryButton(
                text = stringResource(R.string.quick_save),
                onClick = {
                    store.upsert(draft)
                    EventReminders.reschedule(app)
                    Toast.makeText(context, context.getString(R.string.quick_saved), Toast.LENGTH_SHORT).show()
                    onDone()
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * Kesken suorituksen: muutos nykyiseen vaiheeseen (ohitetaan tänään tai kortti tilalle).
 * [onCancelStep] ohittaa vaiheen, [onReplace] korvaa sen.
 */
@Composable
internal fun StepChangeDialog(onCancelStep: () -> Unit, onReplace: (CardItem) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ChangeSymbol(Modifier.size(28.dp))
                Text(stringResource(R.string.quick_step_change))
            }
        },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ReplacementChoices(
                    cancelLabel = stringResource(R.string.quick_step_cancel),
                    onCancel = onCancelStep,
                    onMove = null,
                    programs = emptyList(),
                    onProgram = {},
                    onCard = onReplace
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

/** Korvaava vaihe suoritukseen: ajoitus säilyy, sisältö kortista. */
internal fun replacementStep(step: ProgramStep, card: CardItem, substeps: List<String>): ProgramStep = step.copy(
    name = card.name,
    photo = card.photoPath,
    description = card.description,
    voicePath = card.voicePath,
    autoPlayVoice = card.autoPlayVoice,
    subSteps = substeps,
    changedToday = true,
    changedFrom = step.name
)
