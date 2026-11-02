package fi.aikapolku.app.programs.editor

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import fi.aikapolku.app.ui.SecondaryButton
import fi.aikapolku.app.ui.SegmentedChoice
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fi.aikapolku.app.R
import fi.aikapolku.app.cards.CardPhoto
import fi.aikapolku.app.data.DayProgram
import fi.aikapolku.app.data.ProgramStep
import fi.aikapolku.app.data.ProgramTiming
import fi.aikapolku.app.data.StepMode
import fi.aikapolku.app.data.TimeMode
import fi.aikapolku.app.ui.AppColors

@Composable
internal fun TimesPage(draft: DayProgram, onDraftChange: (DayProgram) -> Unit) {
    val context = LocalContext.current
    fun updateStep(updated: ProgramStep) {
        onDraftChange(draft.copy(steps = draft.steps.map { if (it.id == updated.id) updated else it }))
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Ajan kulku: sama valitsin kuin muualla ja yksi selitysrivi valitusta.
        val modes = listOf(TimeMode.FREE, TimeMode.DURATION, TimeMode.CLOCK)
        SegmentedChoice(
            options = listOf(
                stringResource(R.string.omaan_tahtiin),
                stringResource(R.string.kestot),
                stringResource(R.string.time_mode_clock)
            ),
            selected = modes.indexOf(draft.timeMode).coerceAtLeast(0),
            onSelect = { onDraftChange(ProgramTiming.switchMode(draft, modes[it])) }
        )
        Text(
            stringResource(
                when (draft.timeMode) {
                    TimeMode.DURATION -> R.string.jokaisella_toiminnolla_on_oma_ajastin
                    TimeMode.CLOCK -> R.string.anna_aloitusaika_ja_kestot_kellonajat
                    else -> R.string.ei_aikoja_valmis_painike_siirtaa
                }
            ),
            fontSize = 14.sp,
            color = AppColors.Muted
        )

        when (draft.timeMode) {
            TimeMode.DURATION -> {
                draft.steps.forEach { step ->
                    TimeRowCard {
                        StepHeader(step = step, timeLabel = null)
                        DurationChips(
                            step = step,
                            allowNone = true,
                            onPick = { minutes ->
                                updateStep(
                                    if (minutes == null) step.copy(mode = StepMode.MANUAL)
                                    else step.copy(mode = StepMode.DURATION, minutes = minutes)
                                )
                            }
                        )
                    }
                }
            }

            TimeMode.CLOCK -> {
                SecondaryButton(
                    text = stringResource(R.string.aloitus_klo_muuta, draft.startClock),
                    icon = Icons.Rounded.Edit,
                    onClick = {
                        showClockPicker(context, draft.startClock) { onDraftChange(draft.copy(startClock = it)) }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                val slots = ProgramTiming.schedule(draft.startClock, draft.steps)
                draft.steps.forEachIndexed { index, step ->
                    val slot = slots[index]
                    TimeRowCard {
                        StepHeader(
                            step = step,
                            timeLabel = ProgramTiming.format(slot.start),
                            fixed = index > 0 && step.fixedClock
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
                                    label = { Text(stringResource(R.string.kiintea_aika)) }
                                )
                                if (step.fixedClock) {
                                    SecondaryButton(
                                        text = stringResource(R.string.klo_muuta, step.clock),
                                        icon = Icons.Rounded.Edit,
                                        onClick = {
                                            showClockPicker(context, step.clock) { updateStep(step.copy(clock = it)) }
                                        }
                                    )
                                }
                            }
                        }
                        if (slot.overlap) {
                            Text(
                                stringResource(R.string.alkaa_ennen_kuin_edellinen_paattyy, ProgramTiming.format(slot.previousEnd)),
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
                ProgramTiming.endOf(draft.startClock, draft.steps)?.let { end ->
                    Text(
                        stringResource(R.string.program_summary_ends, ProgramTiming.format(end)),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AppColors.Muted
                    )
                }
            }

            else -> Unit
        }
        Spacer(Modifier.height(4.dp))
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
private fun StepHeader(step: ProgramStep, timeLabel: String?, fixed: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (fixed) Icon(Icons.Rounded.Lock, contentDescription = stringResource(R.string.kiintea_aika), tint = AppColors.Teal, modifier = Modifier.size(18.dp))
        if (timeLabel != null) {
            Text(
                timeLabel,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = AppColors.Teal,
                modifier = Modifier.widthIn(min = 64.dp)
            )
        }
        CardPhoto(step.name, step.photo, Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)))
        Text(step.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun DurationChips(step: ProgramStep, allowNone: Boolean, onPick: (Int?) -> Unit) {
    val options = (ProgramTiming.durationChoices + step.minutes).filter { it > 0 }.distinct().sorted()
    val none = allowNone && step.mode == StepMode.MANUAL
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (allowNone) {
            FilterChip(selected = none, onClick = { onPick(null) }, label = { Text(stringResource(R.string.ei_aikaa)) })
        }
        options.forEach { minutes ->
            FilterChip(
                selected = !none && step.minutes == minutes,
                onClick = { onPick(minutes) },
                label = { Text(stringResource(R.string.minutes_value, minutes)) }
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
