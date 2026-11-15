package fi.aikapolku.app.settings

import android.app.Activity
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fi.aikapolku.app.R
import fi.aikapolku.app.data.LevelHint
import fi.aikapolku.app.data.ProfileStore
import fi.aikapolku.app.data.SupportLevel
import fi.aikapolku.app.data.SupportLevels
import fi.aikapolku.app.data.UsageKind
import fi.aikapolku.app.data.UsageLog
import fi.aikapolku.app.profiles.profileName
import fi.aikapolku.app.ui.AppColors
import fi.aikapolku.app.ui.PrimaryButton
import fi.aikapolku.app.ui.SecondaryButton
import java.io.File
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Tuen taso (asiakaskohtainen): ohjaaja vähentää kehotuksia askel kerrallaan ja voi palata
 * edelliseen. Seuranta näyttää asukkaan pyynnöt ennen ja jälkeen; muutos on aina ohjaajan
 * ja asukkaan yhteinen päätös, sovellus ei vaihda tasoa itse.
 */
@Composable
internal fun SupportLevelSettings() {
    val context = LocalContext.current
    var revision by remember { mutableIntStateOf(0) }
    val level = remember(revision) { SupportLevels.current(context) }
    val canRevert = remember(revision) { SupportLevels.canRevert(context) }
    val history = remember(revision) { SupportLevels.history(context) }
    fun changed() {
        revision++
        SettingsRevision.bump()
    }

    Text(stringResource(R.string.support_level_info), fontSize = 14.sp, color = AppColors.Muted)
    SupportLevel.entries.forEach { l ->
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                .selectable(selected = l == level, role = Role.RadioButton) {
                    if (l != level) {
                        SupportLevels.apply(context, l)
                        changed()
                    }
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = l == level, onClick = null)
            Column(Modifier.padding(start = 8.dp)) {
                Text(stringResource(l.title), fontSize = 16.sp, fontWeight = if (l == level) FontWeight.Bold else FontWeight.Normal)
                Text(stringResource(l.info), fontSize = 13.sp, color = AppColors.Muted)
            }
        }
    }
    if (level == null) Text(stringResource(R.string.support_level_none), fontSize = 13.sp, color = AppColors.Muted)
    if (canRevert) {
        SecondaryButton(stringResource(R.string.support_level_revert), {
            SupportLevels.revert(context)
            changed()
        }, modifier = Modifier.fillMaxWidth())
    }
    history.takeLast(3).reversed().forEach { h ->
        Text(stringResource(R.string.support_level_changed, "${h.date.dayOfMonth}.${h.date.monthValue}.${h.date.year}", stringResource(h.level.title)),
            fontSize = 13.sp, color = AppColors.Muted)
    }

    // Seuranta: asukkaan pyynnöt kahdella peräkkäisellä jaksolla (vain, jos käytön seuranta on päällä).
    val usageRevision = UsageLog.revision
    if (!UsageLog.enabled(context)) {
        Text(stringResource(R.string.support_level_no_usage), fontSize = 13.sp, color = AppColors.Muted)
        return
    }
    val today = LocalDate.now()
    val days = remember(usageRevision, revision) { UsageLog.days(context, ProfileStore.currentId(context)) }
    val w = SupportLevels.WINDOW_DAYS
    fun requests(from: LocalDate, to: LocalDate) = UsageLog.total(days, from, to).filterKeys { it in UsageKind.REQUESTS }.values.sum()
    val recent = requests(today.minusDays(w - 1), today)
    val previous = requests(today.minusDays(2 * w - 1), today.minusDays(w))
    val sinceChange = history.lastOrNull()?.let { ChronoUnit.DAYS.between(it.date, today) }
    Text(stringResource(R.string.support_level_requests, recent, previous), fontSize = 14.sp, color = AppColors.Ink)
    when (SupportLevels.hint(recent, previous, sinceChange, hasNext = level?.next != null)) {
        LevelHint.CONSIDER_NEXT -> Text(stringResource(R.string.support_level_hint_next), fontSize = 14.sp, color = AppColors.Teal)
        LevelHint.CONSIDER_BACK -> Text(stringResource(R.string.support_level_hint_back), fontSize = 14.sp, color = AppColors.Change)
        LevelHint.NONE -> Unit
    }
}

/**
 * Käytön seuranta pilottia varten (laitekohtainen): vain lukumäärät päivittäin, ei kellonaikoja,
 * ei sisältöä eikä omaa tilaa. Kooste tunnuksin (A1, A2 …); nimet eivät lähde laitteelta.
 */
@Composable
internal fun UsageSettings() {
    val context = LocalContext.current
    val revision = UsageLog.revision
    val enabled = remember(revision) { UsageLog.enabled(context) }
    val since = remember(revision) { UsageLog.since(context) }
    var confirmClear by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }

    Text(stringResource(R.string.usage_info), fontSize = 14.sp, color = AppColors.Muted)
    SwitchLine(stringResource(R.string.usage_enabled), enabled) { UsageLog.setEnabled(context, it) }
    if (since != null) Text(stringResource(R.string.usage_since, "${since.dayOfMonth}.${since.monthValue}.${since.year}"), fontSize = 13.sp, color = AppColors.Muted)
    if (!enabled && since == null) return

    val profiles = remember(revision) { ProfileStore.list(context) }
    val today = LocalDate.now()
    profiles.forEach { p ->
        val days = remember(revision, p.id) { UsageLog.days(context, p.id) }
        if (days.isEmpty()) return@forEach
        val week = UsageLog.total(days, today.minusDays(6), today)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = AppColors.TealSoft)
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    stringResource(R.string.usage_client_title, profileName(p), UsageLog.code(context, p.id)),
                    fontSize = 16.sp, fontWeight = FontWeight.Bold
                )
                Text(stringResource(R.string.usage_active_days, UsageLog.activeDays(days, today.minusDays(6), today)), fontSize = 14.sp)
                UsageKind.entries.filter { (week[it] ?: 0) > 0 }.forEach { k ->
                    Text("${stringResource(k.label)}: ${week[k]}", fontSize = 14.sp)
                }
            }
        }
    }
    PrimaryButton(stringResource(R.string.usage_export), {
        val rows = profiles.map { UsageLog.code(context, it.id) to UsageLog.days(context, it.id) }.filter { it.second.isNotEmpty() }
        val file = runCatching {
            File(context.cacheDir, "share").apply { mkdirs() }.let { dir ->
                File(dir, "AikaPolku_kaytto_${today}.csv").apply { writeText(UsageLog.csv(rows)) }
            }
        }.getOrNull()
        if (file == null) {
            error = true
        } else {
            val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.files", file)
            val send = Intent(Intent.ACTION_SEND).setType("text/csv").putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val chooser = Intent.createChooser(send, context.getString(R.string.usage_export))
            if (context !is Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            fi.aikapolku.app.caregiver.CaregiverSession.keepOpenForOwnPicker()
            runCatching { context.startActivity(chooser) }.onFailure { error = true }
        }
    }, modifier = Modifier.fillMaxWidth())
    Text(stringResource(R.string.usage_export_info), fontSize = 13.sp, color = AppColors.Muted)
    if (error) Text(stringResource(R.string.usage_export_failed), fontSize = 13.sp, color = AppColors.Coral)
    TextButton(onClick = { confirmClear = true }) { Text(stringResource(R.string.usage_clear)) }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.usage_clear_title)) },
            text = { Text(stringResource(R.string.usage_clear_text)) },
            confirmButton = {
                TextButton(onClick = {
                    UsageLog.clear(context, profiles.map { it.id })
                    confirmClear = false
                }) { Text(stringResource(R.string.usage_clear)) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}
