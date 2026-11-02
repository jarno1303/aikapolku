package fi.aikapolku.app.programs.editor

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import fi.aikapolku.app.ui.InfoButton
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fi.aikapolku.app.R
import fi.aikapolku.app.cards.CardPhoto
import fi.aikapolku.app.data.CardItem
import fi.aikapolku.app.data.DayProgram
import fi.aikapolku.app.data.ProgramStep
import fi.aikapolku.app.ui.AppColors

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ChoosePage(
    draft: DayProgram,
    cards: List<Pair<String, List<CardItem>>>,
    onDraftChange: (DayProgram) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Yksi lyhyt ohjerivi; tarkennus (poisto, sama toiminto useasti) ⓘ-kuvakkeen takana.
        var showHelp by remember { mutableStateOf(false) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.napauta_toiminnot_siina_jarjestyksessa_kuin),
                modifier = Modifier.weight(1f),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            InfoButton(stringResource(R.string.korttien_kayttoohje)) { showHelp = true }
        }
        if (showHelp) {
            AlertDialog(
                onDismissRequest = { showHelp = false },
                text = { Text(stringResource(R.string.uusi_napautus_poistaa_pida_painettuna)) },
                confirmButton = { TextButton(onClick = { showHelp = false }) { Text(stringResource(R.string.selva)) } }
            )
        }

        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val columns = maxOf(3, (maxWidth / 112.dp).toInt())
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
              cards.forEach { (category, group) ->
                Text(
                    category,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.Muted,
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
                                        onDraftChange(draft.copy(steps = draft.steps + stepFromCard(card) { context.getString(it) }))
                                    } else {
                                        val removeAt = positions.last() - 1
                                        onDraftChange(draft.copy(steps = draft.steps.filterIndexed { i, _ -> i != removeAt }))
                                    }
                                },
                                onLongPress = {
                                    onDraftChange(draft.copy(steps = draft.steps + stepFromCard(card) { context.getString(it) }))
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
    card: CardItem,
    positions: List<Int>,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    val selected = positions.isNotEmpty()
    val selectedText = stringResource(R.string.a11y_selected_at, positions.joinToString(", "))
    val notSelectedText = stringResource(R.string.a11y_not_selected)
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (selected) AppColors.TealSoft else MaterialTheme.colorScheme.surface,
        border = BorderStroke(if (selected) 3.dp else 1.dp, if (selected) AppColors.Teal else Color(0xFFCBD5E1)),
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = onTap,
                onLongClick = onLongPress,
                onClickLabel = if (selected) stringResource(R.string.poista_ohjelmasta) else stringResource(R.string.lisaa_ohjelmaan),
                onLongClickLabel = stringResource(R.string.lisaa_uudelleen)
            )
            .semantics {
                contentDescription = card.name
                stateDescription = if (selected) selectedText else notSelectedText
            }
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(6.dp)
        ) {
            Box {
                CardPhoto(
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
                            .background(AppColors.Teal)
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
internal fun SelectedStrip(steps: List<ProgramStep>) {
    val scroll = rememberScrollState()
    LaunchedEffect(steps.size) { scroll.animateScrollTo(scroll.maxValue) }
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(scroll),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        steps.forEachIndexed { index, step ->
            if (index > 0) Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = AppColors.Muted, modifier = Modifier.size(18.dp))
            Box {
                CardPhoto(step.name, step.photo, Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)))
                Text(
                    "${index + 1}",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .clip(RoundedCornerShape(topEnd = 8.dp))
                        .background(AppColors.Teal)
                        .padding(horizontal = 5.dp)
                )
            }
        }
    }
}
