package fi.aikapolku.app.programs.editor

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import fi.aikapolku.app.cards.CardPhoto
import fi.aikapolku.app.data.DayProgram
import fi.aikapolku.app.data.ProgramStep
import fi.aikapolku.app.programs.SubstepEditor
import kotlinx.coroutines.delay

@Composable
internal fun ArrangePage(
    draft: DayProgram,
    onDraftChange: (DayProgram) -> Unit,
    onAddMore: () -> Unit
) {
    // Vedon aikana useampi siirto voi tapahtua ennen uudelleenpiirtoa: pidetään tuorein tila tässä.
    val latest = remember { Ref(draft) }
    latest.value = draft
    fun commit(next: DayProgram) {
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
    var undo by remember { mutableStateOf<Pair<Int, ProgramStep>?>(null) }
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
                            CardPhoto(step.name, step.photo, Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)))
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
                            SubstepEditor(
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
