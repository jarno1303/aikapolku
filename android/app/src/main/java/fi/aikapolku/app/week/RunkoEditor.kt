package fi.aikapolku.app.week

import androidx.compose.foundation.background
import fi.aikapolku.app.ui.LocalPageScroll
import androidx.compose.ui.zIndex
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fi.aikapolku.app.R
import fi.aikapolku.app.data.DayProgram
import fi.aikapolku.app.data.EventKind
import fi.aikapolku.app.data.ExceptionKind
import fi.aikapolku.app.data.Group
import fi.aikapolku.app.data.MemberException
import fi.aikapolku.app.data.Runko
import fi.aikapolku.app.data.RunkoTemplate
import fi.aikapolku.app.data.WeekEvent
import fi.aikapolku.app.data.WeekPlan
import fi.aikapolku.app.data.WeekSchedule
import fi.aikapolku.app.ui.AppColors
import fi.aikapolku.app.ui.ChoiceRow
import fi.aikapolku.app.ui.OverflowMenu
import fi.aikapolku.app.ui.RowIcon
import java.time.LocalDate
import java.util.UUID

/** Ryhmän kohta asiakkaan viikossa: ryhmä, rungon kohta ja asiakkaan toistuva poikkeus. */
internal data class GroupRow(val group: Group, val event: WeekEvent, val exception: MemberException?)

/**
 * Viikkorungon muokkaus aikajanalla. Sama muokkain ryhmän rungolle ja asiakkaan omille
 * kohdille: päivävälilehdet, kohdat kellonajan mukaan ja "+" jokaisen välin kohdalla.
 * "+" avaa korttivalikon, ja kortti lisätään ehdotettuun aikaan; kohdan napautus avaa
 * tarkemmat valinnat (aika, ilmoitus, kesto, päiväohjelma, poisto).
 *
 * Asiakkaan viikossa näkyvät myös ryhmien kohdat ([groupRows]); niiden napautus
 * ([onGroupRow]) avaa asiakkaan toistuvan poikkeuksen.
 */
@Composable
internal fun RunkoEditor(
    plan: WeekPlan,
    onChange: (WeekPlan) -> Unit,
    programs: List<DayProgram>,
    canEdit: Boolean,
    allowPrograms: Boolean,
    groupRows: (day: Int, week: Int) -> List<GroupRow> = { _, _ -> emptyList() },
    onGroupRow: (GroupRow) -> Unit = {},
    /** Ryhmän kohdan raahaus asiakkaan viikossa: uusi aika (null = ei raahattavissa). */
    onGroupMove: ((GroupRow, String) -> Unit)? = null,
    /** Kopioitavat rungot (toiset asiakkaat ja ryhmät): nimi ja runko. */
    copySources: List<Pair<String, WeekPlan>> = emptyList()
) {
    val today = remember { LocalDate.now() }
    val currentWeek = WeekSchedule.weekOf(plan, today)
    var day by rememberSaveable { mutableStateOf(today.dayOfWeek.value) }
    var week by rememberSaveable { mutableStateOf(currentWeek) }
    val shownWeek = if (plan.alternating) week else WeekSchedule.WEEK_A
    var addAt by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<WeekEvent?>(null) }
    var programAt by remember { mutableStateOf<String?>(null) }
    var copyDayOpen by remember { mutableStateOf(false) }
    var replaceWith by remember { mutableStateOf<WeekPlan?>(null) }
    var chooseStart by remember { mutableStateOf(false) }
    var moveToDayOf by remember { mutableStateOf<WeekEvent?>(null) }
    val newId = { UUID.randomUUID().toString() }

    // Raahaus (pitkä painallus). Omat kohdat: sisällöt vaihtavat järjestystä, ajat pysyvät.
    // Ryhmän kohta (asiakkaan viikossa): saa uuden ajan pudotuskohdasta, varmistus ennen tallennusta.
    val haptic = LocalHapticFeedback.current
    val currentPlan by rememberUpdatedState(plan)
    val currentOnChange by rememberUpdatedState(onChange)
    val drag = rememberRowDrag()
    AutoScrollWhileDragging(drag)

    fun ownNow() = WeekSchedule.eventsOn(currentPlan, day, if (currentPlan.alternating) week else WeekSchedule.WEEK_A)

    fun move(from: Int, to: Int) {
        val wk = if (currentPlan.alternating) week else WeekSchedule.WEEK_A
        currentOnChange(Runko.moveContent(currentPlan, day, wk, from, to))
    }

    if (plan.alternating) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            listOf(WeekSchedule.WEEK_A, WeekSchedule.WEEK_B).forEach { w ->
                val label = weekLabel(w) + if (w == currentWeek) " · " + stringResource(R.string.week_this_week) else ""
                ChoiceButton(label, selected = week == w, modifier = Modifier.weight(1f)) { week = w }
            }
        }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
        (1..7).forEach { d ->
            val has = WeekSchedule.eventsOn(plan, d, shownWeek).isNotEmpty() || groupRows(d, shownWeek).isNotEmpty()
            DayTab(d, selected = d == day, hasEvents = has, modifier = Modifier.weight(1f)) { day = d }
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            dayName(day),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = AppColors.Ink,
            modifier = Modifier.weight(1f).semantics { heading() }
        )
        if (canEdit) {
            val copyLabel = stringResource(R.string.runko_copy_day)
            val clearLabel = stringResource(R.string.runko_clear_day)
            val startLabel = stringResource(R.string.runko_use_template)
            OverflowMenu(
                contentDescription = stringResource(R.string.runko_day_actions, dayName(day)),
                items = listOf(
                    copyLabel to { copyDayOpen = true },
                    clearLabel to { onChange(Runko.clearDay(plan, day, shownWeek)) },
                    startLabel to { chooseStart = true }
                )
            )
        }
    }

    // Aikajana: omat ja ryhmien kohdat kellonajan mukaan, "+" jokaisen välin kohdalla.
    val own = WeekSchedule.eventsOn(plan, day, shownWeek)
    val groups = groupRows(day, shownWeek)
    val rows: List<Pair<String, Any>> = (own.map { it.time to (it as Any) } + groups.map { it.event.time to (it as Any) })
        .sortedBy { WeekSchedule.minutesOf(it.first) }

    if (plan.events.isEmpty() && groups.isEmpty() && canEdit) {
        StartChoices(copySources = copySources, onTemplate = { onChange(it) }, onManual = { addAt = Runko.DEFAULT_TIME })
    } else if (rows.isEmpty()) {
        Text(stringResource(R.string.week_no_events), color = AppColors.Muted)
    }

    // Pudotuskohta raahauksen aikana ja aika, jonka kohta saa.
    fun rowKey(row: Any): String = when (row) {
        is WeekEvent -> row.id
        is GroupRow -> "grp-row:" + row.group.id + ":" + row.event.id
        else -> ""
    }
    val dragging = drag.dragId
    val draggingGroup = dragging?.startsWith("grp-row:") == true
    val ownOthers = if (dragging != null && !draggingGroup) own.filter { it.id != dragging } else emptyList()
    val allOthers = if (dragging != null && draggingGroup) rows.filter { rowKey(it.second) != dragging } else emptyList()
    val target = when {
        dragging == null -> null
        draggingGroup -> drag.target(allOthers.map { rowKey(it.second) })
        else -> drag.target(ownOthers.map { it.id })
    }
    val targetKey = when {
        target == null -> null
        draggingGroup -> allOthers.getOrNull(target)?.let { rowKey(it.second) }
        else -> ownOthers.getOrNull(target)?.id
    }
    val lastKey = if (draggingGroup) allOthers.lastOrNull()?.let { rowKey(it.second) } else ownOthers.lastOrNull()?.id
    val atEnd = target != null && target == (if (draggingGroup) allOthers.size else ownOthers.size)
    val targetTime = when {
        target == null -> null
        draggingGroup -> Runko.suggestTime(allOthers.getOrNull(target - 1)?.first, allOthers.getOrNull(target)?.first)
        else -> own.map { it.time }.getOrNull(target)
    }
    val moveUpLabel = stringResource(R.string.runko_move_up)
    val moveDownLabel = stringResource(R.string.runko_move_down)
    val context = LocalContext.current
    val onlyOneText = stringResource(R.string.runko_drag_only_one)
    val groupBlockedText = stringResource(R.string.runko_drag_group_blocked)

    rows.forEachIndexed { index, (time, row) ->
        if (canEdit) AddSlot(Runko.suggestTime(rows.getOrNull(index - 1)?.first, time)) { addAt = it }
        val key = rowKey(row)
        val lineAbove = targetKey != null && targetKey == key
        val lineBelow = atEnd && lastKey == key
        when (row) {
            is WeekEvent -> {
                val ownIndex = own.indexOfFirst { it.id == row.id }
                val draggable = canEdit && own.size > 1
                Box(
                    modifier = Modifier
                        .dragRow(
                            drag = drag,
                            id = key,
                            enabled = draggable,
                            haptic = haptic,
                            onDrop = {
                                val now = ownNow()
                                val from = now.indexOfFirst { it.id == row.id }
                                val to = drag.target(now.filter { it.id != row.id }.map { it.id })
                                drag.dragId = null
                                if (from >= 0 && to != from) {
                                    move(from, to)
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            },
                            onBlocked = if (canEdit) {
                                { android.widget.Toast.makeText(context, onlyOneText, android.widget.Toast.LENGTH_LONG).show() }
                            } else null
                        )
                        .semantics {
                            if (draggable) customActions = listOfNotNull(
                                if (ownIndex > 0) CustomAccessibilityAction(moveUpLabel) { move(ownIndex, ownIndex - 1); true } else null,
                                if (ownIndex < own.size - 1) CustomAccessibilityAction(moveDownLabel) { move(ownIndex, ownIndex + 1); true } else null
                            )
                        }
                ) {
                    OwnRow(row, programs, canEdit) { editing = row }
                    if (lineAbove && targetTime != null) DropLine(targetTime, Modifier.align(Alignment.TopCenter))
                    if (lineBelow && targetTime != null) DropLine(targetTime, Modifier.align(Alignment.BottomCenter))
                }
            }
            is GroupRow -> Box(
                modifier = Modifier.dragRow(
                    drag = drag,
                    id = key,
                    enabled = canEdit && onGroupMove != null && rows.size > 1,
                    haptic = haptic,
                    onDrop = {
                        val rest = rows.filter { rowKey(it.second) != key }
                        val to = drag.target(rest.map { rowKey(it.second) })
                        val newTime = Runko.suggestTime(rest.getOrNull(to - 1)?.first, rest.getOrNull(to)?.first)
                        drag.dragId = null
                        onGroupMove?.invoke(row, newTime)
                    },
                    onBlocked = if (canEdit && onGroupMove == null) {
                        { android.widget.Toast.makeText(context, groupBlockedText, android.widget.Toast.LENGTH_LONG).show() }
                    } else null
                )
            ) {
                GroupEventRow(row, canEdit) { onGroupRow(row) }
                if (lineAbove && targetTime != null) DropLine(targetTime, Modifier.align(Alignment.TopCenter))
                if (lineBelow && targetTime != null) DropLine(targetTime, Modifier.align(Alignment.BottomCenter))
            }
        }
    }
    if (canEdit && rows.size > 1 && dragging == null) {
        Text(stringResource(R.string.runko_drag_hint), fontSize = 13.sp, color = AppColors.Muted)
    }
    if (canEdit && rows.isNotEmpty()) AddSlot(Runko.suggestTime(rows.last().first, null)) { addAt = it }
    if (canEdit && rows.isEmpty() && !(plan.events.isEmpty() && groups.isEmpty())) {
        AddSlot(Runko.suggestTime(null, null)) { addAt = it }
    }

    if (canEdit) AlternatingCard(plan, today, onChange = { next ->
        onChange(next)
        week = WeekSchedule.weekOf(next, today)
    })

    addAt?.let { time ->
        QuickCardDialog(
            initialTime = time,
            allowPrograms = allowPrograms && programs.isNotEmpty(),
            onCard = { at, name, photo ->
                onChange(Runko.addCard(plan, day, shownWeek, at, name, photo, newId))
                addAt = null
            },
            onProgram = { at ->
                addAt = null
                programAt = at
            },
            onDismiss = { addAt = null }
        )
    }
    programAt?.let { time ->
        WeekEventDialog(
            initial = null,
            day = day,
            week = shownWeek,
            programs = programs,
            initialTime = time,
            initialKind = EventKind.PROGRAM,
            onSave = { created ->
                onChange(plan.copy(events = plan.events + created))
                programAt = null
            },
            onDelete = {},
            onDismiss = { programAt = null }
        )
    }
    editing?.let { event ->
        WeekEventDialog(
            initial = event,
            day = event.day,
            week = event.week,
            programs = if (allowPrograms) programs else emptyList(),
            allowPrograms = allowPrograms,
            onSave = { changed ->
                onChange(plan.copy(events = plan.events.filterNot { it.id == event.id } + changed))
                editing = null
            },
            onDelete = {
                onChange(plan.copy(events = plan.events.filterNot { it.id == event.id }))
                editing = null
            },
            onDismiss = { editing = null },
            extraActions = run {
                val list = WeekSchedule.eventsOn(plan, event.day, event.week)
                val i = list.indexOfFirst { it.id == event.id }
                listOfNotNull(
                    if (i > 0) stringResource(R.string.runko_move_up) to {
                        onChange(Runko.moveContent(plan, event.day, event.week, i, i - 1)); editing = null
                    } else null,
                    if (i in 0 until list.size - 1) stringResource(R.string.runko_move_down) to {
                        onChange(Runko.moveContent(plan, event.day, event.week, i, i + 1)); editing = null
                    } else null,
                    stringResource(R.string.runko_move_day) to {
                        moveToDayOf = event; editing = null
                    }
                )
            }
        )
    }
    moveToDayOf?.let { event ->
        AlertDialog(
            onDismissRequest = { moveToDayOf = null },
            title = { Text(stringResource(R.string.runko_move_day_title, event.time.replace(':', '.'))) },
            text = {
                Column {
                    (1..7).filter { it != event.day }.forEach { d ->
                        ChoiceRow(title = dayName(d), onClick = {
                            onChange(Runko.moveToDay(plan, event.id, d))
                            moveToDayOf = null
                            day = d
                        })
                    }
                }
            },
            confirmButton = { TextButton(onClick = { moveToDayOf = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }
    if (copyDayOpen) {
        CopyDayDialog(day, onCopy = { targets ->
            onChange(Runko.copyDay(plan, day, shownWeek, targets, newId))
            copyDayOpen = false
        }, onDismiss = { copyDayOpen = false })
    }
    if (chooseStart) {
        AlertDialog(
            onDismissRequest = { chooseStart = false },
            title = { Text(stringResource(R.string.runko_use_template)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    StartChoices(copySources = copySources, onTemplate = {
                        chooseStart = false
                        replaceWith = it
                    }, framed = false)
                }
            },
            confirmButton = { TextButton(onClick = { chooseStart = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }
    replaceWith?.let { next ->
        AlertDialog(
            onDismissRequest = { replaceWith = null },
            title = { Text(stringResource(R.string.runko_replace_title)) },
            text = { Text(stringResource(R.string.runko_replace_text)) },
            confirmButton = {
                TextButton(onClick = {
                    onChange(next)
                    replaceWith = null
                }) { Text(stringResource(R.string.runko_replace_confirm)) }
            },
            dismissButton = { TextButton(onClick = { replaceWith = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }
    // Lukko (ks. canEdit): avoimet ikkunat suljetaan.
    LaunchedEffect(canEdit) {
        if (!canEdit) {
            addAt = null; editing = null; copyDayOpen = false; chooseStart = false; programAt = null; replaceWith = null
            moveToDayOf = null; drag.dragId = null
        }
    }
}

/** Tyhjä runko: valmis pohja tai kopio toisesta. */
@Composable
private fun StartChoices(
    copySources: List<Pair<String, WeekPlan>>,
    onTemplate: (WeekPlan) -> Unit,
    framed: Boolean = true,
    onManual: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val newId = { UUID.randomUUID().toString() }
    val content: @Composable () -> Unit = {
        if (framed) {
            Text(stringResource(R.string.runko_start_title), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = AppColors.Ink)
            Text(stringResource(R.string.runko_start_info), fontSize = 14.sp, color = AppColors.Muted)
        }
        RunkoTemplate.all.forEach { template ->
            ChoiceRow(
                title = stringResource(template.nameRes),
                subtitle = stringResource(template.infoRes),
                onClick = {
                    val names = fi.aikapolku.app.cards.CardArt.templates.associate { it.id to context.getString(it.nameRes) }
                    onTemplate(template.build({ names[it] ?: it }, newId))
                },
                leading = { RowIcon(Icons.Rounded.Add) }
            )
        }
        copySources.forEach { (name, source) ->
            ChoiceRow(
                title = stringResource(R.string.runko_copy_from, name),
                subtitle = stringResource(R.string.runko_copy_from_info),
                onClick = { onTemplate(Runko.copyFrom(source, newId)) },
                leading = { RowIcon(Icons.Rounded.Add) }
            )
        }
        if (onManual != null) {
            ChoiceRow(
                title = stringResource(R.string.runko_manual),
                subtitle = stringResource(R.string.runko_manual_info),
                onClick = onManual,
                leading = { RowIcon(Icons.Rounded.Add) }
            )
        }
    }
    if (framed) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = AppColors.TealSoft)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { content() }
        }
    } else content()
}

/** "+" kahden kohdan välissä: lisää kohdan ehdotettuun aikaan. */
@Composable
private fun AddSlot(time: String, onAdd: (String) -> Unit) {
    val label = stringResource(R.string.runko_add_at, time.replace(':', '.'))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button) { onAdd(time) }
            .semantics(mergeDescendants = true) { contentDescription = label },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(Modifier.width(56.dp))
        Box(
            Modifier.size(32.dp).clip(RoundedCornerShape(50)).background(AppColors.TealSoft),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Rounded.Add, contentDescription = null, tint = AppColors.Teal, modifier = Modifier.size(22.dp)) }
        Text(time.replace(':', '.'), fontSize = 14.sp, color = AppColors.Teal, fontWeight = FontWeight.SemiBold)
        Box(Modifier.weight(1f).heightIn(min = 1.dp).background(AppColors.Border))
    }
}

@Composable
private fun OwnRow(event: WeekEvent, programs: List<DayProgram>, canEdit: Boolean, onClick: () -> Unit) {
    val resolved = resolveEvent(event, programs)
    val kind = stringResource(if (resolved.isProgram) R.string.week_kind_program else R.string.week_kind_card)
    val details = listOfNotNull(
        kind,
        if (event.ongoing) stringResource(R.string.runko_ongoing) else null
    ).joinToString(" · ")
    RowCard(
        time = event.time,
        canEdit = canEdit,
        onClick = onClick,
        image = { EventImage(resolved, it) },
        title = resolved.title.ifBlank { stringResource(R.string.week_missing_program) },
        subtitle = details,
        notify = event.notify
    )
}

@Composable
private fun GroupEventRow(row: GroupRow, canEdit: Boolean, onClick: () -> Unit) {
    val skipped = row.exception?.kind == ExceptionKind.SKIP
    val replaced = row.exception?.kind == ExceptionKind.REPLACE
    val name = if (replaced) row.exception!!.cardName else row.event.cardName
    val photo = if (replaced) row.exception!!.cardPhoto else row.event.cardPhoto
    val subtitle = when {
        skipped -> stringResource(R.string.runko_group_skipped, row.group.name)
        replaced -> stringResource(R.string.runko_group_replaced, row.group.name, row.event.cardName)
        else -> stringResource(R.string.runko_group_tag, row.group.name)
    }
    RowCard(
        time = row.event.time,
        canEdit = canEdit,
        onClick = onClick,
        image = { fi.aikapolku.app.cards.CardPhoto(name, photo, it) },
        title = name,
        subtitle = subtitle,
        notify = row.event.notify,
        group = true,
        faded = skipped
    )
}

@Composable
private fun RowCard(
    time: String,
    canEdit: Boolean,
    onClick: () -> Unit,
    image: @Composable (Modifier) -> Unit,
    title: String,
    subtitle: String,
    notify: Boolean,
    group: Boolean = false,
    faded: Boolean = false
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (canEdit) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .then(if (group) Modifier.border(2.dp, AppColors.TealSoft, RoundedCornerShape(16.dp)) else Modifier)
            .alpha(if (faded) 0.55f else 1f),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = if (group) Color(0xFFF3FAF8) else Color.White)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(time.replace(':', '.'), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = AppColors.Ink, modifier = Modifier.width(56.dp))
            image(Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AppColors.Ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textDecoration = if (faded) TextDecoration.LineThrough else null
                )
                Text(subtitle, fontSize = 13.sp, color = if (group) AppColors.Teal else AppColors.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (notify) {
                Icon(
                    Icons.Rounded.Notifications,
                    contentDescription = stringResource(R.string.runko_notify_on),
                    tint = AppColors.Muted,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun DayTab(day: Int, selected: Boolean, hasEvents: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val full = dayName(day)
    Column(
        modifier = modifier
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) AppColors.Teal else Color.White)
            .border(1.dp, if (selected) AppColors.Teal else AppColors.Border, RoundedCornerShape(12.dp))
            .clickable(role = Role.Tab, onClick = onClick)
            .semantics {
                contentDescription = full
                this.selected = selected
            }
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(dayShort(day), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = if (selected) Color.White else AppColors.Ink)
        // Pieni merkki: päivälle on kohtia.
        Box(
            Modifier
                .padding(top = 4.dp)
                .size(6.dp)
                .clip(RoundedCornerShape(50))
                .background(if (!hasEvents) Color.Transparent else if (selected) Color.White else AppColors.Teal)
        )
    }
}

/** Kopioi päivä muille päiville: valmiina ma–pe. */
@Composable
private fun CopyDayDialog(from: Int, onCopy: (Set<Int>) -> Unit, onDismiss: () -> Unit) {
    var targets by remember { mutableStateOf(((1..5).toSet() - from)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.runko_copy_day_title, dayName(from))) },
        text = {
            Column {
                Text(stringResource(R.string.runko_copy_day_info), fontSize = 14.sp, color = AppColors.Muted)
                (1..7).filter { it != from }.forEach { d ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable(role = Role.Checkbox) { targets = if (d in targets) targets - d else targets + d },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = d in targets, onCheckedChange = null)
                        Text(dayName(d), fontSize = 16.sp, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = targets.isNotEmpty(), onClick = { onCopy(targets) }) { Text(stringResource(R.string.runko_copy_confirm)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

/** Vuoroviikot (A/B) ja kumpi viikko on nyt. */
@Composable
private fun AlternatingCard(plan: WeekPlan, today: LocalDate, onChange: (WeekPlan) -> Unit) {
    val currentWeek = WeekSchedule.weekOf(plan, today)
    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = AppColors.TealSoft)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val switchLabel = stringResource(R.string.week_alternating)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(switchLabel, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = AppColors.Ink)
                    Text(stringResource(R.string.week_alternating_info), fontSize = 13.sp, color = AppColors.Muted)
                }
                Switch(
                    checked = plan.alternating,
                    onCheckedChange = { on ->
                        var next = plan.copy(alternating = on)
                        if (on && plan.anchorMonday == 0L) next = WeekSchedule.withCurrentWeek(next, today, WeekSchedule.WEEK_A)
                        onChange(next)
                    },
                    modifier = Modifier.semantics { contentDescription = switchLabel }
                )
            }
            if (plan.alternating) {
                Text(stringResource(R.string.week_current_is), fontSize = 15.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf(WeekSchedule.WEEK_A, WeekSchedule.WEEK_B).forEach { w ->
                        ChoiceButton(weekLabel(w), selected = currentWeek == w, modifier = Modifier.weight(1f)) {
                            onChange(WeekSchedule.withCurrentWeek(plan, today, w))
                        }
                    }
                }
            }
        }
    }
}

/**
 * Asiakkaan toistuva poikkeus ryhmän kohtaan: osallistuu (ryhmän mukaan), ei osallistu, tai
 * tilalla toinen kortti. Koskee joka viikkoa; yksittäinen päivä tehdään Tee muutos -toiminnolla.
 */
@Composable
internal fun GroupExceptionDialog(
    row: GroupRow,
    clientName: String,
    onSet: (MemberException?) -> Unit,
    onDismiss: () -> Unit
) {
    var pickCard by remember { mutableStateOf(false) }
    if (pickCard) {
        QuickCardDialog(
            initialTime = row.event.time,
            allowPrograms = false,
            showTime = false,
            onCard = { _, name, photo ->
                pickCard = false
                onSet(MemberException(row.event.id, row.exception?.memberId ?: "", ExceptionKind.REPLACE, name, photo))
            },
            onProgram = {},
            onDismiss = { pickCard = false }
        )
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${row.event.time.replace(':', '.')} ${row.event.cardName}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.runko_exception_info, row.group.name, clientName), fontSize = 14.sp, color = AppColors.Muted)
                ChoiceRow(
                    title = stringResource(R.string.runko_exception_follow),
                    onClick = { onSet(null) },
                    trailing = { if (row.exception == null) SelectedMark() }
                )
                ChoiceRow(
                    title = stringResource(R.string.runko_exception_skip, clientName),
                    onClick = { onSet(MemberException(row.event.id, "", ExceptionKind.SKIP)) },
                    trailing = { if (row.exception?.kind == ExceptionKind.SKIP) SelectedMark() }
                )
                ChoiceRow(
                    title = stringResource(R.string.runko_exception_replace, clientName),
                    subtitle = row.exception?.takeIf { it.kind == ExceptionKind.REPLACE }?.cardName,
                    onClick = { pickCard = true },
                    trailing = { if (row.exception?.kind == ExceptionKind.REPLACE) SelectedMark() }
                )
                Text(stringResource(R.string.runko_exception_one_day), fontSize = 13.sp, color = AppColors.Muted, modifier = Modifier.padding(top = 6.dp))
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } }
    )
}

@Composable
private fun SelectedMark() {
    Icon(
        Icons.Rounded.CheckCircle,
        contentDescription = stringResource(R.string.runko_selected),
        tint = AppColors.Teal,
        modifier = Modifier.size(26.dp)
    )
}

