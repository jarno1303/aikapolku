package fi.aikapolku.app.programs.editor

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
        Text(stringResource(R.string.miten_aika_kulkee), fontSize = 18.sp, fontWeight = FontWeight.Bold)
        ModeOption(
            selected = draft.timeMode == ProgramTiming.FREE,
            title = stringResource(R.string.omaan_tahtiin),
            description = stringResource(R.string.ei_aikoja_valmis_painike_siirtaa),
            onSelect = { onDraftChange(ProgramTiming.switchMode(draft, ProgramTiming.FREE)) }
        )
        ModeOption(
            selected = draft.timeMode == ProgramTiming.DURATION,
            title = stringResource(R.string.kestot),
            description = stringResource(R.string.jokaisella_toiminnolla_on_oma_ajastin),
            onSelect = { onDraftChange(ProgramTiming.switchMode(draft, ProgramTiming.DURATION)) }
        )
        ModeOption(
            selected = draft.timeMode == ProgramTiming.CLOCK,
            title = stringResource(R.string.kellonajat),
            description = stringResource(R.string.anna_aloitusaika_ja_kestot_kellonajat),
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
                    Text(stringResource(R.string.aloitus_klo_muuta, draft.startClock), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
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
                                    label = { Text(stringResource(R.string.kiintea_aika)) }
                                )
                                if (step.fixedClock) {
                                    OutlinedButton(
                                        onClick = {
                                            showClockPicker(context, step.clock) { updateStep(step.copy(clock = it)) }
                                        },
                                        modifier = Modifier.heightIn(min = 48.dp)
                                    ) { Text(stringResource(R.string.klo_muuta, step.clock)) }
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
                        stringResource(R.string.paattyy_noin_klo_2, ProgramTiming.format(end)),
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
private fun ModeOption(selected: Boolean, title: String, description: String, onSelect: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (selected) AppColors.TealSoft else MaterialTheme.colorScheme.surface,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) AppColors.Teal else Color(0xFFCBD5E1)),
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
                Text(description, fontSize = 13.sp, color = AppColors.Muted)
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
private fun StepHeader(step: ProgramStep, timeLabel: String?) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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
    val none = allowNone && step.mode == "manual"
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
                label = { Text(stringResource(R.string.min, minutes)) }
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
