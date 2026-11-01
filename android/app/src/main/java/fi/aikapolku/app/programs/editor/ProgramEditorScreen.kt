package fi.aikapolku.app.programs.editor

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fi.aikapolku.app.cards.CardArt
import fi.aikapolku.app.data.CardItem
import fi.aikapolku.app.data.CardStore
import fi.aikapolku.app.data.DayProgram
import fi.aikapolku.app.data.ProgramStep
import fi.aikapolku.app.data.ProgramTiming
import fi.aikapolku.app.data.SubstepDefaults
import fi.aikapolku.app.data.serializeDraft
import java.util.UUID

internal val edTeal = Color(0xFF0F766E)

internal val edTealSoft = Color(0xFFE6F4F1)

internal val edMuted = Color(0xFF475569)

private val pageTitles = listOf("Valitse", "Järjestä", "Ajat", "Tallenna")

/**
 * Valittavat kortit ryhmittäin: omat kortit ensin, sitten valmiit luokittain.
 * Sama nimi näytetään kerran; oma kortti korvaa samannimisen valmiin.
 */
internal fun programEditorCardGroups(context: Context): List<Pair<String, List<CardItem>>> {
    val custom = runCatching { CardStore(context).loadCards() }.getOrDefault(emptyList())
        .distinctBy { it.name.trim().lowercase() }
    val customNames = custom.map { it.name.trim().lowercase() }.toSet()
    val builtIn = CardArt.templates
        .filter { it.name.trim().lowercase() !in customNames }
        .groupBy { it.category }
        .map { (category, items) ->
            category to items.map { t ->
                CardItem(id = "basic_${t.id}", name = t.name, description = "", photoPath = "builtin:${t.id}")
            }
        }
    return (if (custom.isEmpty()) emptyList() else listOf("Omat kortit" to custom)) + builtIn
}

internal fun stepFromCard(card: CardItem): ProgramStep = ProgramStep(
    id = UUID.randomUUID().toString(),
    name = card.name,
    photo = card.photoPath,
    description = card.description,
    voicePath = card.voicePath,
    autoPlayVoice = card.autoPlayVoice,
    subSteps = SubstepDefaults.forCard(card.name)
)

internal class Ref<T>(var value: T)

@Composable
internal fun ProgramEditorScreen(
    draft: DayProgram,
    onDraftChange: (DayProgram) -> Unit,
    otherProgramNames: List<String>,
    onSave: (DayProgram) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val cards = remember(context) { programEditorCardGroups(context) }
    var page by rememberSaveable(draft.id) { mutableIntStateOf(if (draft.steps.isEmpty()) 0 else 1) }
    val original = rememberSaveable(draft.id) { serializeDraft(draft) }
    var confirmDiscard by remember { mutableStateOf(false) }
    val isNew = remember(draft.id) { draft.name.isBlank() && draft.steps.isEmpty() }

    fun requestCancel() {
        if (serializeDraft(draft) != original) confirmDiscard = true else onCancel()
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
