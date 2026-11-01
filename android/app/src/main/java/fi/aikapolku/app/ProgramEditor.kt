package fi.aikapolku.app

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import java.util.UUID

/*
 * P0-23: päiväohjelman luonti neljässä vaiheessa.
 *   1 Valitse   – kuvaruudukko; napautusjärjestys = ohjelman järjestys
 *   2 Järjestä  – vedä kahvasta, poista (Kumoa), osavaiheet
 *   3 Ajat      – omaan tahtiin / kestot / kellonajat (+ kiinteät ajat)
 *   4 Tallenna  – esikatselu ja nimi (ehdotetaan valmiiksi)
 */

private val edTeal = Color(0xFF0F766E)
private val edTealSoft = Color(0xFFE6F4F1)
private val edMuted = Color(0xFF475569)

private val pageTitles = listOf("Valitse", "Järjestä", "Ajat", "Tallenna")

/**
 * Valittavat kortit ryhmittäin: omat kortit ensin, sitten valmiit luokittain.
 * Sama nimi näytetään kerran; oma kortti korvaa samannimisen valmiin.
 */
internal fun programEditorCardGroups(context: Context): List<Pair<String, List<P015Card>>> {
    val custom = runCatching { P015CardStore(context).loadCards() }.getOrDefault(emptyList())
        .distinctBy { it.name.trim().lowercase() }
    val customNames = custom.map { it.name.trim().lowercase() }.toSet()
    val builtIn = AikaCardArt.templates
        .filter { it.name.trim().lowercase() !in customNames }
        .groupBy { it.category }
        .map { (category, items) ->
            category to items.map { t ->
                P015Card(id = "basic_${t.id}", name = t.name, description = "", photoPath = "builtin:${t.id}")
            }
        }
    return (if (custom.isEmpty()) emptyList() else listOf("Omat kortit" to custom)) + builtIn
}

internal fun stepFromCard(card: P015Card): AP16Step = AP16Step(
    id = UUID.randomUUID().toString(),
    name = card.name,
    photo = card.photoPath,
    description = card.description,
    voicePath = card.voicePath,
    autoPlayVoice = card.autoPlayVoice,
    subSteps = P021SubstepDefaults.forCard(card.name)
)

private class Ref<T>(var value: T)

@Composable
internal fun ProgramEditorScreen(
    draft: AP16Program,
    onDraftChange: (AP16Program) -> Unit,
    otherProgramNames: List<String>,
    onSave: (AP16Program) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val cards = remember(context) { programEditorCardGroups(context) }
    var page by rememberSaveable(draft.id) { mutableIntStateOf(if (draft.steps.isEmpty()) 0 else 1) }
    val original = rememberSaveable(draft.id) { p017SerializeDraft(draft) }
    var confirmDiscard by remember { mutableStateOf(false) }
    val isNew = remember(draft.id) { draft.name.isBlank() && draft.steps.isEmpty() }

    fun requestCancel() {
        if (p017SerializeDraft(draft) != original) confirmDiscard = true else onCancel()
    }

    fun goTo(target: Int) {
        if (target > 0 && draft.steps.isEmpty()) return
        if (target == 3 && draft.name.isBlank()) {
            onDraftChange(draft.copy(name = ProgramTiming.suggestName(draft, otherProgramNames)))
        }
        page = target.coerceIn(0, 3)
    }

    BackHandler { if (page > 0) page-- else requestCancel() }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (isNew) "Uusi päiväohjelma" else "Muokkaa päiväohjelmaa",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = { requestCancel() }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Peruuta")
            }
        }

        EditorProgress(
            page = page,
            canJump = draft.steps.isNotEmpty(),
            onSelect = { goTo(it) }
        )
        Spacer(Modifier.height(8.dp))

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (page) {
                0 -> ChoosePage(draft, cards, onDraftChange)
                1 -> ArrangePage(draft, onDraftChange, onAddMore = { page = 0 })
                2 -> TimesPage(draft, onDraftChange)
                else -> SavePage(draft, onDraftChange)
            }
        }

        // Alapalkki: valitut kortit (vaihe 1) ja eteneminen.
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (page == 0 && draft.steps.isNotEmpty()) SelectedStrip(draft.steps)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (page > 0) {
                    OutlinedButton(
                        onClick = { page-- },
                        modifier = Modifier.weight(1f).heightIn(min = 56.dp)
                    ) { Text("‹ Takaisin", fontSize = 16.sp) }
                }
                if (page < 3) {
                    Button(
                        onClick = { goTo(page + 1) },
                        enabled = draft.steps.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(containerColor = edTeal),
                        modifier = Modifier.weight(1f).heightIn(min = 56.dp)
                    ) {
                        Text(
                            if (page == 0 && draft.steps.isNotEmpty()) "Seuraava (${draft.steps.size}) ›" else "Seuraava ›",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Button(
                        onClick = { onSave(draft) },
                        enabled = draft.name.isNotBlank() && draft.steps.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(containerColor = edTeal),
                        modifier = Modifier.weight(1f).heightIn(min = 56.dp)
                    ) { Text("Tallenna", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Hylätäänkö muutokset?") },
            text = { Text("Tallentamattomat muutokset katoavat.") },
            confirmButton = {
                TextButton(onClick = { confirmDiscard = false; onCancel() }) { Text("Hylkää") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false }) { Text("Jatka muokkausta") }
            }
        )
    }
}

@Composable
private fun EditorProgress(page: Int, canJump: Boolean, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        pageTitles.forEachIndexed { index, title ->
            val active = index == page
            val done = index < page
            val enabled = index == 0 || canJump
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(enabled = enabled && !active, role = Role.Tab) { onSelect(index) }
                    .padding(vertical = 6.dp)
                    .semantics {
                        stateDescription = if (active) "Nykyinen vaihe" else ""
                    }
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(if (active || done) edTeal else Color(0xFFE2E8F0))
                ) {
                    Text(
                        if (done) "✓" else "${index + 1}",
                        color = if (active || done) Color.White else edMuted,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
                Text(
                    title,
                    fontSize = 13.sp,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                    color = if (active) edTeal else edMuted,
                    maxLines = 1
                )
            }
        }
    }
}

// ------------------------------------------------------------------ 1 Valitse

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChoosePage(
    draft: AP16Program,
    cards: List<Pair<String, List<P015Card>>>,
    onDraftChange: (AP16Program) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            "Napauta toiminnot siinä järjestyksessä kuin ne tehdään.",
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            "Uusi napautus poistaa. Pidä painettuna, jos sama toiminto tulee päivään useamman kerran.",
            fontSize = 13.sp,
            color = edMuted
        )

        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val columns = maxOf(3, (maxWidth / 112.dp).toInt())
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
              cards.forEach { (category, group) ->
                Text(
                    category,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = edMuted,
                    modifier = Modifier.padding(top = 6.dp)
                )
                group.chunked(columns).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { card ->
                            val positions = draft.steps.indices
                                .filter { draft.steps[it].name.equals(card.name, ignoreCase = true) }
                                .map { it + 1 }
                            CardTile(
                                card = card,
                                positions = positions,
                                onTap = {
                                    if (positions.isEmpty()) {
                                        onDraftChange(draft.copy(steps = draft.steps + stepFromCard(card)))
                                    } else {
                                        val removeAt = positions.last() - 1
                                        onDraftChange(draft.copy(steps = draft.steps.filterIndexed { i, _ -> i != removeAt }))
                                    }
                                },
                                onLongPress = {
                                    onDraftChange(draft.copy(steps = draft.steps + stepFromCard(card)))
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
              }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CardTile(
    card: P015Card,
    positions: List<Int>,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    val selected = positions.isNotEmpty()
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (selected) edTealSoft else MaterialTheme.colorScheme.surface,
        border = BorderStroke(if (selected) 3.dp else 1.dp, if (selected) edTeal else Color(0xFFCBD5E1)),
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = onTap,
                onLongClick = onLongPress,
                onClickLabel = if (selected) "Poista ohjelmasta" else "Lisää ohjelmaan",
                onLongClickLabel = "Lisää uudelleen"
            )
            .semantics {
                contentDescription = card.name
                stateDescription = if (selected) "Valittu, kohta ${positions.joinToString(", ")}" else "Ei valittu"
            }
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(6.dp)
        ) {
            Box {
                P011Photo(
                    card.name,
                    card.photoPath,
                    Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp))
                )
                if (selected) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .defaultMinSize(minWidth = 28.dp, minHeight = 28.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(edTeal)
                            .padding(horizontal = 6.dp)
                    ) {
                        Text(
                            positions.joinToString(","),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                }
            }
            Text(
                card.name,
                fontSize = 14.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 2,
                minLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun SelectedStrip(steps: List<AP16Step>) {
    val scroll = rememberScrollState()
    LaunchedEffect(steps.size) { scroll.animateScrollTo(scroll.maxValue) }
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(scroll),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        steps.forEachIndexed { index, step ->
            if (index > 0) Text("›", color = edMuted, fontSize = 16.sp)
            Box {
                P011Photo(step.name, step.photo, Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)))
                Text(
                    "${index + 1}",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .clip(RoundedCornerShape(topEnd = 8.dp))
                        .background(edTeal)
                        .padding(horizontal = 5.dp)
                )
            }
        }
    }
}

// ------------------------------------------------------------------ 2 Järjestä

@Composable
private fun ArrangePage(
    draft: AP16Program,
    onDraftChange: (AP16Program) -> Unit,
    onAddMore: () -> Unit
) {
    // Vedon aikana useampi siirto voi tapahtua ennen uudelleenpiirtoa: pidetään tuorein tila tässä.
    val latest = remember { Ref(draft) }
    latest.value = draft
    fun commit(next: AP16Program) {
        latest.value = next
        onDraftChange(next)
    }
    fun move(from: Int, to: Int) {
        val list = latest.value.steps.toMutableList()
        if (from !in list.indices || to !in list.indices) return
        list.add(to, list.removeAt(from))
        commit(latest.value.copy(steps = list))
    }

    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val heights = remember { mutableStateMapOf<String, Int>() }
    val gapPx = with(LocalDensity.current) { 10.dp.toPx() }
    var undo by remember { mutableStateOf<Pair<Int, AP16Step>?>(null) }
    LaunchedEffect(undo) {
        if (undo != null) {
            delay(6_000)
            undo = null
        }
    }

    fun followFinger(id: String) {
        val list = latest.value.steps
        val index = list.indexOfFirst { it.id == id }
        if (index < 0) return
        if (dragOffset > 0 && index < list.lastIndex) {
            val h = (heights[list[index + 1].id] ?: 0) + gapPx
            if (dragOffset > h / 2) {
                move(index, index + 1)
                dragOffset -= h
            }
        } else if (dragOffset < 0 && index > 0) {
            val h = (heights[list[index - 1].id] ?: 0) + gapPx
            if (-dragOffset > h / 2) {
                move(index, index - 1)
                dragOffset += h
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            "Vedä ≡-kahvasta muuttaaksesi järjestystä.",
            fontSize = 14.sp,
            color = edMuted
        )

        undo?.let { (index, step) ->
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 14.dp)
                ) {
                    Text("Poistettu: ${step.name}", modifier = Modifier.weight(1f))
                    TextButton(
                        onClick = {
                            val list = latest.value.steps.toMutableList()
                            list.add(index.coerceIn(0, list.size), step)
                            commit(latest.value.copy(steps = list))
                            undo = null
                        },
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) { Text("Kumoa", fontWeight = FontWeight.Bold) }
                }
            }
        }

        draft.steps.forEachIndexed { index, step ->
            key(step.id) {
                val dragging = draggingId == step.id
                Card(
                    shape = RoundedCornerShape(18.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = if (dragging) 8.dp else 1.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (dragging) edTealSoft else MaterialTheme.colorScheme.surface
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .zIndex(if (dragging) 1f else 0f)
                        .graphicsLayer { translationY = if (dragging) dragOffset else 0f }
                        .onSizeChanged { heights[step.id] = it.height }
                        .semantics {
                            customActions = listOf(
                                CustomAccessibilityAction("Siirrä ylemmäs") {
                                    val i = latest.value.steps.indexOfFirst { it.id == step.id }
                                    if (i > 0) { move(i, i - 1); true } else false
                                },
                                CustomAccessibilityAction("Siirrä alemmas") {
                                    val i = latest.value.steps.indexOfFirst { it.id == step.id }
                                    if (i in 0 until latest.value.steps.lastIndex) { move(i, i + 1); true } else false
                                }
                            )
                        }
                ) {
                    Column(modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 6.dp, bottom = 4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(48.dp)
                                    .pointerInput(step.id) {
                                        detectDragGestures(
                                            onDragStart = {
                                                draggingId = step.id
                                                dragOffset = 0f
                                            },
                                            onDragEnd = { draggingId = null; dragOffset = 0f },
                                            onDragCancel = { draggingId = null; dragOffset = 0f },
                                            onDrag = { change, amount ->
                                                change.consume()
                                                dragOffset += amount.y
                                                followFinger(step.id)
                                            }
                                        )
                                    }
                                    .semantics { contentDescription = "Siirrä: ${step.name}" }
                            ) {
                                Text("≡", fontSize = 26.sp, color = edMuted)
                            }
                            Text(
                                "${index + 1}.",
                                fontWeight = FontWeight.Bold,
                                color = edTeal,
                                fontSize = 17.sp,
                                modifier = Modifier.width(30.dp)
                            )
                            P011Photo(step.name, step.photo, Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)))
                            Spacer(Modifier.width(10.dp))
                            Text(
                                step.name,
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = {
                                    val i = latest.value.steps.indexOfFirst { it.id == step.id }
                                    if (i >= 0) {
                                        undo = i to step
                                        commit(latest.value.copy(steps = latest.value.steps.filterNot { it.id == step.id }))
                                    }
                                },
                                modifier = Modifier.semantics { contentDescription = "Poista ${step.name}" }
                            ) { Text("✕", fontSize = 20.sp, color = edMuted) }
                        }
                        Box(modifier = Modifier.padding(start = 48.dp, end = 8.dp)) {
                            P021SubstepEditor(
                                step = step,
                                onUpdate = { updated ->
                                    commit(latest.value.copy(steps = latest.value.steps.map {
                                        if (it.id == updated.id) updated else it
                                    }))
                                }
                            )
                        }
                    }
                }
            }
        }

        OutlinedButton(
            onClick = onAddMore,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
        ) { Text("+ Lisää toimintoja") }
        Spacer(Modifier.height(4.dp))
    }
}

// ------------------------------------------------------------------ 3 Ajat

@Composable
private fun TimesPage(draft: AP16Program, onDraftChange: (AP16Program) -> Unit) {
    val context = LocalContext.current
    fun updateStep(updated: AP16Step) {
        onDraftChange(draft.copy(steps = draft.steps.map { if (it.id == updated.id) updated else it }))
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Miten aika kulkee?", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        ModeOption(
            selected = draft.timeMode == ProgramTiming.FREE,
            title = "Omaan tahtiin",
            description = "Ei aikoja. Valmis-painike siirtää seuraavaan.",
            onSelect = { onDraftChange(ProgramTiming.switchMode(draft, ProgramTiming.FREE)) }
        )
        ModeOption(
            selected = draft.timeMode == ProgramTiming.DURATION,
            title = "Kestot",
            description = "Jokaisella toiminnolla on oma ajastin.",
            onSelect = { onDraftChange(ProgramTiming.switchMode(draft, ProgramTiming.DURATION)) }
        )
        ModeOption(
            selected = draft.timeMode == ProgramTiming.CLOCK,
            title = "Kellonajat",
            description = "Anna aloitusaika ja kestot – kellonajat lasketaan. Toiminnon voi myös lukita kiinteään aikaan.",
            onSelect = { onDraftChange(ProgramTiming.switchMode(draft, ProgramTiming.CLOCK)) }
        )

        when (draft.timeMode) {
            ProgramTiming.DURATION -> {
                HorizontalDivider()
                draft.steps.forEach { step ->
                    TimeRowCard {
                        StepHeader(step = step, timeLabel = null)
                        DurationChips(
                            step = step,
                            allowNone = true,
                            onPick = { minutes ->
                                updateStep(
                                    if (minutes == null) step.copy(mode = "manual")
                                    else step.copy(mode = "duration", minutes = minutes)
                                )
                            }
                        )
                    }
                }
            }

            ProgramTiming.CLOCK -> {
                HorizontalDivider()
                OutlinedButton(
                    onClick = {
                        showClockPicker(context, draft.startClock) { onDraftChange(draft.copy(startClock = it)) }
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                ) {
                    Text("Aloitus klo ${draft.startClock} – muuta", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                }
                val slots = ProgramTiming.schedule(draft.startClock, draft.steps)
                draft.steps.forEachIndexed { index, step ->
                    val slot = slots[index]
                    TimeRowCard {
                        StepHeader(
                            step = step,
                            timeLabel = (if (index > 0 && step.fixedClock) "🔒 " else "") +
                                ProgramTiming.format(slot.start)
                        )
                        DurationChips(
                            step = step,
                            allowNone = false,
                            onPick = { minutes -> if (minutes != null) updateStep(step.copy(minutes = minutes)) }
                        )
                        if (index > 0) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = step.fixedClock,
                                    onClick = {
                                        updateStep(
                                            if (step.fixedClock) step.copy(fixedClock = false)
                                            else step.copy(fixedClock = true, clock = ProgramTiming.format(slot.start))
                                        )
                                    },
                                    label = { Text("Kiinteä aika") }
                                )
                                if (step.fixedClock) {
                                    OutlinedButton(
                                        onClick = {
                                            showClockPicker(context, step.clock) { updateStep(step.copy(clock = it)) }
                                        },
                                        modifier = Modifier.heightIn(min = 48.dp)
                                    ) { Text("Klo ${step.clock} – muuta") }
                                }
                            }
                        }
                        if (slot.overlap) {
                            Text(
                                "Alkaa ennen kuin edellinen päättyy (klo ${ProgramTiming.format(slot.previousEnd)}).",
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
                ProgramTiming.endOf(draft.startClock, draft.steps)?.let { end ->
                    Text(
                        "Päättyy noin klo ${ProgramTiming.format(end)}",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = edMuted
                    )
                }
            }

            else -> Unit
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun ModeOption(selected: Boolean, title: String, description: String, onSelect: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (selected) edTealSoft else MaterialTheme.colorScheme.surface,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) edTeal else Color(0xFFCBD5E1)),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp)
        ) {
            RadioButton(selected = selected, onClick = null)
            Spacer(Modifier.width(8.dp))
            Column {
                Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text(description, fontSize = 13.sp, color = edMuted)
            }
        }
    }
}

@Composable
private fun TimeRowCard(content: @Composable ColumnScope.() -> Unit) {
    Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content
        )
    }
}

@Composable
private fun StepHeader(step: AP16Step, timeLabel: String?) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (timeLabel != null) {
            Text(
                timeLabel,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = edTeal,
                modifier = Modifier.widthIn(min = 64.dp)
            )
        }
        P011Photo(step.name, step.photo, Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)))
        Text(step.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun DurationChips(step: AP16Step, allowNone: Boolean, onPick: (Int?) -> Unit) {
    val options = (ProgramTiming.durationChoices + step.minutes).filter { it > 0 }.distinct().sorted()
    val none = allowNone && step.mode == "manual"
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (allowNone) {
            FilterChip(selected = none, onClick = { onPick(null) }, label = { Text("Ei aikaa") })
        }
        options.forEach { minutes ->
            FilterChip(
                selected = !none && step.minutes == minutes,
                onClick = { onPick(minutes) },
                label = { Text("$minutes min") }
            )
        }
    }
}

private fun showClockPicker(context: Context, clock: String, onPicked: (String) -> Unit) {
    val current = ProgramTiming.parse(clock) ?: ProgramTiming.parse(ProgramTiming.DEFAULT_START)!!
    android.app.TimePickerDialog(
        context,
        { _, hour, minute -> onPicked(ProgramTiming.format(hour * 60 + minute)) },
        current / 60,
        current % 60,
        true
    ).show()
}

// ------------------------------------------------------------------ 4 Tallenna

@Composable
private fun SavePage(draft: AP16Program, onDraftChange: (AP16Program) -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        OutlinedTextField(
            value = draft.name,
            onValueChange = { onDraftChange(draft.copy(name = it.take(80))) },
            label = { Text("Ohjelman nimi") },
            singleLine = true,
            isError = draft.name.isBlank(),
            supportingText = { if (draft.name.isBlank()) Text("Anna ohjelmalle nimi.") },
            modifier = Modifier.fillMaxWidth()
        )

        Text("Näin ohjelma näkyy", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        val summary = when (draft.timeMode) {
            ProgramTiming.CLOCK -> {
                val end = ProgramTiming.endOf(draft.startClock, draft.steps)
                "Kellonajat · alkaa klo ${draft.startClock}" +
                    (end?.let { " · päättyy noin klo ${ProgramTiming.format(it)}" } ?: "")
            }
            ProgramTiming.DURATION -> "Kestot · jokaisella toiminnolla oma ajastin"
            else -> "Omaan tahtiin · ei aikoja"
        }
        Text(summary, fontSize = 14.sp, color = edMuted)

        val slots = if (draft.timeMode == ProgramTiming.CLOCK)
            ProgramTiming.schedule(draft.startClock, draft.steps) else emptyList()
        if (slots.any { it.overlap }) {
            Text(
                "Huom: jokin kiinteä aika alkaa ennen kuin edellinen toiminto päättyy. Voit tallentaa silti.",
                color = MaterialTheme.colorScheme.error,
                fontSize = 13.sp
            )
        }

        draft.steps.forEachIndexed { index, step ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(8.dp)
            ) {
                val label = ProgramTiming.label(draft, index)
                if (draft.timeMode != ProgramTiming.FREE) {
                    Text(
                        label,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = edTeal,
                        modifier = Modifier.width(62.dp)
                    )
                }
                P011Photo(step.name, step.photo, Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)))
                Column(modifier = Modifier.weight(1f)) {
                    Text("${index + 1}. ${step.name}", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    if (step.subSteps.isNotEmpty()) {
                        Text("${step.subSteps.size} osavaihetta", fontSize = 12.sp, color = edMuted)
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}
