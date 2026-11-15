package fi.aikapolku.app.programs.run

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fi.aikapolku.app.R
import fi.aikapolku.app.cards.CardPhoto
import fi.aikapolku.app.data.ChoiceAlt
import fi.aikapolku.app.data.ChoiceOption
import fi.aikapolku.app.ui.AppColors
import fi.aikapolku.app.voice.VoicePlayer

/**
 * Valinta (15.11.2026): kaksi isoa korttia rinnakkain. Napautus valitsee (ja soittaa kortin oman
 * äänen), Aloita vahvistaa – vahinkonapautus ei vielä aloita mitään, ja valinnan voi vaihtaa.
 * Enintään neljä painiketta: kaksi korttia, Aloita ja tarvittaessa "En halua kumpaakaan".
 */
@Composable
internal fun ChoiceScreen(
    options: List<ChoiceOption>,
    allowNone: Boolean,
    onStart: (Int) -> Unit,
    onNone: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selected by rememberSaveable { mutableStateOf(-1) }
    // Matala ruutu (puhelin vaakatasossa): kuvat pienempinä, jotta Aloita näkyy vierittämättä.
    val low = fi.aikapolku.app.ui.windowSize() == fi.aikapolku.app.ui.WindowSize.LOW
    Column(
        modifier = modifier.fillMaxSize().background(RunColors.background).padding(bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            stringResource(R.string.choice_title),
            modifier = Modifier.fillMaxWidth().semantics { heading() },
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            color = RunColors.ink,
            textAlign = TextAlign.Center
        )
        Row(
            modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            options.take(3).forEachIndexed { index, option ->
                val isSelected = index == selected
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(24.dp))
                        .semantics {
                            contentDescription = option.name
                            this.selected = isSelected
                        }
                        .clickable(role = Role.RadioButton) {
                            selected = index
                            if (option.voicePath.isNotBlank()) VoicePlayer.playSequence(context, listOf(option.voicePath))
                        },
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(if (isSelected) 5.dp else 2.dp, if (isSelected) RunColors.teal else RunColors.border),
                    colors = CardDefaults.cardColors(containerColor = RunColors.card)
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box {
                            CardPhoto(
                                option.name, option.photo,
                                (if (low) Modifier.size(96.dp) else Modifier.fillMaxWidth().aspectRatio(1f)).clip(RoundedCornerShape(18.dp))
                            )
                            if (isSelected) {
                                Box(
                                    Modifier.align(Alignment.TopEnd).padding(6.dp).size(36.dp).clip(CircleShape).background(RunColors.teal),
                                    contentAlignment = Alignment.Center
                                ) { Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp)) }
                            }
                        }
                        Text(
                            option.name,
                            fontSize = 21.sp,
                            fontWeight = FontWeight.Bold,
                            color = RunColors.ink,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
        Text(
            if (selected >= 0) stringResource(R.string.choice_selected, options[selected].name) else stringResource(R.string.choice_hint),
            modifier = Modifier.fillMaxWidth(),
            fontSize = 18.sp,
            color = if (selected >= 0) RunColors.teal else RunColors.secondary,
            fontWeight = if (selected >= 0) FontWeight.Bold else FontWeight.Normal,
            textAlign = TextAlign.Center
        )
        Button(
            onClick = {
                if (selected >= 0) {
                    fi.aikapolku.app.data.UsageLog.record(context, fi.aikapolku.app.data.UsageKind.CHOICE)
                    onStart(selected)
                }
            },
            enabled = selected >= 0,
            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = RunColors.teal, contentColor = Color.White)
        ) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(26.dp))
            Text(stringResource(R.string.choice_start), fontSize = 21.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 6.dp))
        }
        if (allowNone) {
            TextButton(onClick = onNone, modifier = Modifier.align(Alignment.CenterHorizontally).heightIn(min = 48.dp)) {
                Text(stringResource(R.string.choice_none), fontSize = 16.sp, color = RunColors.teal)
            }
        }
    }
}

/** Ohjaajalle: toinen vaihtoehto (tai painike sen lisäämiseen) päiväohjelman vaiheessa ja Sitten-kortissa. */
@Composable
internal fun ChoiceAltRow(alt: ChoiceAlt?, onPick: () -> Unit, onRemove: () -> Unit) {
    if (alt == null) {
        TextButton(onClick = onPick, modifier = Modifier.heightIn(min = 48.dp)) {
            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp), tint = AppColors.Teal)
            Text(stringResource(R.string.choice_add_alternative), color = AppColors.Teal, fontSize = 14.sp, modifier = Modifier.padding(start = 4.dp))
        }
    } else {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CardPhoto(alt.name, alt.photo, Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)))
            Text(
                stringResource(R.string.choice_or, alt.name),
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                color = AppColors.Teal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            val description = stringResource(R.string.choice_remove_alternative, alt.name)
            IconButton(onClick = onRemove, modifier = Modifier.semantics { contentDescription = description }) {
                Icon(Icons.Rounded.Close, contentDescription = null, tint = AppColors.Muted)
            }
        }
    }
}
