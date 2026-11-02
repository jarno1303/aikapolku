package fi.aikapolku.app.week

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import fi.aikapolku.app.R
import fi.aikapolku.app.cards.CardArt
import fi.aikapolku.app.cards.CardPhoto
import fi.aikapolku.app.data.CardStore
import fi.aikapolku.app.data.WeekSchedule
import fi.aikapolku.app.ui.AppColors
import fi.aikapolku.app.ui.ChoiceRow
import fi.aikapolku.app.ui.RoundIconButton
import fi.aikapolku.app.ui.RowIcon

/**
 * Aikajanan "+": kortti valitaan yhdellä napautuksella ja lisätään ehdotettuun aikaan, jota
 * voi säätää 5 minuutin askelin. Omat kortit ensin, sitten valmiit kortit. Päiväohjelma
 * (vaiheittainen tehtävälista) avaa tarkemman ikkunan.
 */
@Composable
internal fun QuickCardDialog(
    initialTime: String,
    allowPrograms: Boolean,
    onCard: (time: String, name: String, photo: String) -> Unit,
    onProgram: (time: String) -> Unit,
    onDismiss: () -> Unit,
    showTime: Boolean = true
) {
    val context = LocalContext.current
    val own = remember { CardStore(context.applicationContext).loadCards() }
    val builtin = remember { CardArt.templates.map { it.id to context.getString(it.nameRes) } }
    var minutes by remember { mutableIntStateOf(WeekSchedule.minutesOf(initialTime)) }
    val time = WeekSchedule.formatTime(minutes / 60, minutes % 60)

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = AppColors.Background) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(if (showTime) R.string.runko_add_title else R.string.runko_pick_card),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.Ink,
                        modifier = Modifier.weight(1f).semantics { heading() }
                    )
                    RoundIconButton(icon = Icons.Rounded.Close, contentDescription = stringResource(R.string.close), onClick = onDismiss)
                }
                if (showTime) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(stringResource(R.string.week_time), fontSize = 16.sp, color = AppColors.Muted)
                        RoundIconButton(
                            icon = fi.aikapolku.app.ui.AppIcons.Minus,
                            contentDescription = stringResource(R.string.runko_time_earlier),
                            onClick = { minutes = (minutes - 5).coerceAtLeast(0) },
                            container = AppColors.TealSoft,
                            tint = AppColors.Teal
                        )
                        Text(time.replace(':', '.'), fontSize = 28.sp, fontWeight = FontWeight.Bold, color = AppColors.Ink)
                        RoundIconButton(
                            icon = Icons.Rounded.Add,
                            contentDescription = stringResource(R.string.runko_time_later),
                            onClick = { minutes = (minutes + 5).coerceAtMost(23 * 60 + 55) },
                            container = AppColors.TealSoft,
                            tint = AppColors.Teal
                        )
                    }
                }
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(100.dp),
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (allowPrograms) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            ChoiceRow(
                                title = stringResource(R.string.runko_add_program),
                                subtitle = stringResource(R.string.runko_add_program_info),
                                onClick = { onProgram(time) },
                                leading = { RowIcon(Icons.AutoMirrored.Rounded.List) }
                            )
                        }
                    }
                    if (own.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) { SectionTitle(stringResource(R.string.omat_kortit)) }
                        items(own, key = { "own:" + it.id }) { card ->
                            CardTile(card.name, card.photoPath) { onCard(time, card.name, card.photoPath) }
                        }
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) { SectionTitle(stringResource(R.string.runko_builtin_cards)) }
                    items(builtin, key = { "builtin:" + it.first }) { (id, name) ->
                        CardTile(name, "builtin:$id") { onCard(time, name, "builtin:$id") }
                    }
                }
            }
        }
    }
}


@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        fontSize = 17.sp,
        fontWeight = FontWeight.Bold,
        color = AppColors.Ink,
        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp).semantics { heading() }
    )
}

@Composable
private fun CardTile(name: String, photo: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CardPhoto(name, photo, Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp)))
        Text(
            name,
            fontSize = 14.sp,
            color = AppColors.Ink,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
