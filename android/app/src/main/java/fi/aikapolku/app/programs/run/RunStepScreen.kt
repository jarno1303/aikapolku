package fi.aikapolku.app.programs.run

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material3.Icon
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fi.aikapolku.app.R
import fi.aikapolku.app.cards.CardPhoto
import fi.aikapolku.app.cards.ResourceImage
import fi.aikapolku.app.cards.SubstepArt
import fi.aikapolku.app.data.RunPreferences
import fi.aikapolku.app.ui.AppColors

internal object RunColors {
    val background = AppColors.Background
    val card = Color(0xFFFFFFFF)
    val ink = AppColors.Ink
    val secondary = AppColors.Muted
    val border = AppColors.Border
    val dotOutline = Color(0xFF94A3B8)
    val teal = AppColors.Teal
    val tealSoft = Color(0xFF5FA8A0)
    val timer = AppColors.Coral
    val timerTrack = Color(0xFFF1D9D6)
    val nextSurface = Color(0xFFEEF2F6)
}

/** Seuraavan vaiheen esikatselu: osavaihe tai seuraava tehtävä. */
internal data class RunNext(
    val name: String,
    val taskName: String,
    val taskPhoto: String,
    val isSubStep: Boolean
)

/** Ajan tila yläkulman aikamerkkiin. null = vaiheella ei ole aikaa. */
internal sealed class RunTime {
    data class Clock(val time: String) : RunTime()
    data class Countdown(
        val remainingMs: Long,
        val durationMs: Long,
        val running: Boolean,
        val displayMode: String,
        val pauseLocked: Boolean
    ) : RunTime()
}

@Composable
internal fun RunStepScreen(
    taskName: String,
    taskPhoto: String,
    description: String,
    subSteps: List<String>,
    subStepIndex: Int,
    taskIndex: Int,
    taskCount: Int,
    time: RunTime?,
    next: RunNext?,
    nextMode: String,
    onToggleTimer: () -> Unit,
    onHelp: () -> Unit,
    onBreak: () -> Unit,
    onDone: () -> Unit,
    onMore: () -> Unit,
    /** Asiakkaalla Muut toiminnot -painikkeen tilalla "Koko ohjelma". */
    moreIsProgram: Boolean = false,
    /** Valmis-painikkeen teksti, kun se poikkeaa tavallisesta (esim. "Aloita aika" tai jäljellä oleva aika). */
    doneLabel: String? = null,
    /** false = odotetaan ajan päättymistä (painike himmeä). */
    doneEnabled: Boolean = true,
    /** Päivän muutos: null = ei muutosta, tyhjä = muutos ilman alkuperäistä, muuten korvattu vaihe. */
    changedFrom: String? = null
) {
    val hasSubSteps = subSteps.isNotEmpty()
    val safeIndex = subStepIndex.coerceIn(0, maxOf(0, subSteps.lastIndex))
    val currentSubStep = if (hasSubSteps) subSteps[safeIndex] else null
    val title = currentSubStep ?: taskName

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(RunColors.background)) {
        // Pystytilassa kaikki mahtuu aina ruudulle: kuva joustaa, jotta apu, tauko ja
        // Valmis näkyvät myös suurella fonttikoolla (puhelintesti 1.11.2026).
        // Vieritys vain vaakatilassa tai hyvin matalalla näytöllä.
        val compact = maxWidth > maxHeight || maxHeight < 440.dp
        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(if (compact) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .padding(bottom = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            RunHeader(
                hasSubSteps = hasSubSteps,
                taskName = taskName,
                time = time,
                onToggleTimer = onToggleTimer,
                dots = {
                    if (hasSubSteps) {
                        RunProgressDots(index = safeIndex, count = subSteps.size, labelRes = R.string.vaihe)
                    } else {
                        RunProgressDots(index = taskIndex, count = taskCount, labelRes = R.string.tehtava)
                    }
                }
            )

            Card(
                modifier = if (compact) Modifier.fillMaxWidth() else Modifier.fillMaxWidth().weight(1f),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = RunColors.card),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .then(if (compact) Modifier.fillMaxWidth() else Modifier.fillMaxSize())
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically)
                ) {
                    // Muutosmerkki: sama kuin muutosnäkymässä ja päivän listassa.
                    if (changedFrom != null) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            fi.aikapolku.app.ui.ChangeSymbol(Modifier.size(28.dp))
                            Text(
                                if (changedFrom.isBlank()) stringResource(R.string.run_changed)
                                else stringResource(R.string.run_changed_from, changedFrom),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = fi.aikapolku.app.ui.AppColors.Change
                            )
                        }
                    }
                    val imageModifier = if (compact) {
                        Modifier.size(160.dp)
                    } else {
                        // Kuva täyttää vapaan tilan (enintään 340 dp, aina neliönä) ja pienenee
                        // tarvittaessa, jotta painikkeet mahtuvat näkyviin.
                        Modifier
                            .weight(1f, fill = false)
                            .sizeIn(maxWidth = 340.dp, maxHeight = 340.dp)
                            .aspectRatio(1f)
                    }
                    RunImage(
                        taskName = taskName,
                        taskPhoto = taskPhoto,
                        subStepName = currentSubStep,
                        modifier = imageModifier.clip(RoundedCornerShape(24.dp))
                    )
                    // Pitkä otsikko pienenee, jotta se mahtuu kahdelle riville eikä vie kuvan tilaa.
                    val titleSize = when {
                        title.length > 24 -> 22
                        title.length > 14 -> 26
                        else -> 30
                    }
                    Text(
                        title,
                        modifier = Modifier.semantics { heading() },
                        fontSize = titleSize.sp,
                        lineHeight = (titleSize + 5).sp,
                        fontWeight = FontWeight.Bold,
                        color = RunColors.ink,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!hasSubSteps && description.isNotBlank()) {
                        Text(
                            description,
                            fontSize = 18.sp,
                            lineHeight = 24.sp,
                            color = RunColors.secondary,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Apu ja tauko koskevat aina nykyistä tehtävää: heti kuvan alla.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                RunSupportButton(
                    label = stringResource(R.string.support_help),
                    icon = { RunHelpIcon() },
                    onClick = onHelp,
                    modifier = Modifier.weight(1f)
                )
                RunSupportButton(
                    label = stringResource(R.string.support_break),
                    icon = { RunPauseIcon(color = RunColors.ink) },
                    onClick = onBreak,
                    modifier = Modifier.weight(1f)
                )
            }

            Button(
                onClick = onDone,
                enabled = doneEnabled,
                modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = RunColors.teal,
                    contentColor = Color.White
                )
            ) {
                Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(28.dp))
                Text(doneLabel ?: stringResource(R.string.run_done_button), fontSize = 21.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 6.dp))
            }

            if (next != null) {
                RunNextRow(next = next, mode = nextMode)
            }

            // Ohjaajalla Ohita ja muut harvinaiset toiminnot yhden painikkeen takana;
            // asiakkaalla vain koko ohjelman katselu.
            TextButton(
                onClick = onMore,
                modifier = Modifier.align(Alignment.CenterHorizontally).heightIn(min = 48.dp)
            ) {
                Icon(
                    if (moreIsProgram) Icons.AutoMirrored.Rounded.List else Icons.Rounded.MoreVert,
                    contentDescription = null, tint = RunColors.teal, modifier = Modifier.size(20.dp)
                )
                Text(
                    stringResource(if (moreIsProgram) R.string.nayta_koko_ohjelma else R.string.muut_toiminnot),
                    color = RunColors.teal, fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun RunHeader(
    hasSubSteps: Boolean,
    taskName: String,
    time: RunTime?,
    onToggleTimer: () -> Unit,
    dots: @Composable () -> Unit
) {
    // Tiivis otsake: "NYT · tehtävä" samalla rivillä ja eteneminen palloina sen alla.
    // Ilman osavaiheita tehtävän nimi näkyy jo kortissa, joten sitä ei toisteta tässä.
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.now_label),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    color = RunColors.secondary
                )
                if (hasSubSteps) {
                    Text(
                        "  ·  $taskName",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = RunColors.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            dots()
        }
        when (time) {
            is RunTime.Clock -> RunClockPill(time.time)
            is RunTime.Countdown -> RunCountdownPill(time, onToggleTimer)
            null -> Unit
        }
    }
}

/**
 * Eteneminen palloina: ei vaadi numeroiden ymmärtämistä. Teksti ("Vaihe 2 / 5") on
 * ruudunlukijalle. Hyvin pitkä lista näytetään tekstinä, jotta pallot mahtuvat riville.
 */
@Composable
private fun RunProgressDots(index: Int, count: Int, labelRes: Int) {
    val label = stringResource(labelRes, index + 1, count)
    if (count <= 1) return
    if (count > 12) {
        Text(label, fontSize = 15.sp, color = RunColors.secondary)
        return
    }
    Row(
        modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = label },
        horizontalArrangement = Arrangement.spacedBy(if (count > 8) 5.dp else 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(count) { i ->
            val shape = RoundedCornerShape(5.dp)
            val base = Modifier
                .height(10.dp)
                .width(if (i == index) 22.dp else 10.dp)
                .clip(shape)
            Box(
                modifier = if (i <= index) base.background(RunColors.teal)
                    else base.border(2.dp, RunColors.dotOutline, shape)
            )
        }
    }
}

/** Seuraava vaihe himmennettynä yhdellä rivillä. Ei painettavissa. Näkyvyys käyttäjän valinnan mukaan. */
@Composable
private fun RunNextRow(next: RunNext, mode: String) {
    if (mode == RunPreferences.NEXT_HIDDEN) return
    val text = stringResource(R.string.run_next_line, next.name)
    Surface(
        color = RunColors.nextSurface,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { contentDescription = text }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp).heightIn(min = 36.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (mode == RunPreferences.NEXT_CARD) {
                RunImage(
                    taskName = next.taskName,
                    taskPhoto = next.taskPhoto,
                    subStepName = if (next.isSubStep) next.name else null,
                    modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).alpha(0.55f)
                )
            }
            Text(
                text,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = RunColors.secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Osavaiheen oma kuva, jos sellainen on; muuten tehtävän kuva (oma valokuva tai valmis kortti). */
@Composable
internal fun RunImage(taskName: String, taskPhoto: String, subStepName: String?, modifier: Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val subStepImage = subStepName?.let { SubstepArt.imageRes(it) { context.getString(it) } }
    if (subStepImage != null) {
        ResourceImage(subStepImage, modifier)
    } else {
        CardPhoto(taskName, taskPhoto, modifier)
    }
}
