package fi.aikapolku.app.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import fi.aikapolku.app.R
import fi.aikapolku.app.data.ProfileStore
import fi.aikapolku.app.profiles.profileName
import fi.aikapolku.app.ui.AppColors
import fi.aikapolku.app.ui.AppIcons
import fi.aikapolku.app.ui.PrimaryButton
import fi.aikapolku.app.ui.RoundIconButton

/** Asiakkaan asetusten osat. Ajastin ja päiväohjelma avaavat vain niitä koskevat osat. */
internal enum class SettingsPart { HOME, TASKS, TIME, MESSAGES }

/** Asiakkaan asetukset osioina (valitun asiakkaan, ks. ClientPickerCard). */
@Composable
internal fun ClientSettingsSections(parts: List<SettingsPart> = SettingsPart.entries, open: Boolean = false) {
    parts.forEach { part ->
        when (part) {
            SettingsPart.HOME -> SettingsSection(
                stringResource(R.string.settings_home), stringResource(R.string.settings_home_info), Icons.Rounded.Home, open
            ) { HomeSettings() }
            SettingsPart.TASKS -> SettingsSection(
                stringResource(R.string.settings_tasks), stringResource(R.string.settings_tasks_info), Icons.AutoMirrored.Rounded.List, open
            ) { TaskSettings() }
            SettingsPart.TIME -> SettingsSection(
                stringResource(R.string.settings_time), stringResource(R.string.settings_time_info), Icons.Rounded.Refresh, open
            ) { TimeSettings() }
            SettingsPart.MESSAGES -> SettingsSection(
                stringResource(R.string.settings_messages), stringResource(R.string.settings_messages_info), AppIcons.Speaker, open
            ) { MessageSettings() }
        }
    }
}

/** Osan otsikko Asetukset-sivulla (Asiakkaan asetukset / Puhelimen asetukset). */
@Composable
internal fun SettingsHeading(title: String, info: String? = null) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 2.dp)) {
        Text(
            title,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = AppColors.Ink,
            modifier = Modifier.semantics { heading() }
        )
        if (info != null) Text(info, fontSize = 14.sp, color = AppColors.Muted)
    }
}

/**
 * Asetukset kesken käytön (ajastimen tai päiväohjelman rataskuvake): samat osiot kuin
 * Asetukset-sivulla, koko ruudun näkymänä, jottei käynnissä oleva aika tai ohjelma katkea.
 */
@Composable
internal fun SettingsDialog(parts: List<SettingsPart>, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val profile = remember { ProfileStore.list(context).firstOrNull { it.id == ProfileStore.currentId(context) } }
    val name = profile?.let { profileName(it) } ?: ""
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = AppColors.Background) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.asetukset),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppColors.Ink,
                            modifier = Modifier.semantics { heading() }
                        )
                        if (name.isNotBlank()) {
                            Text(
                                stringResource(R.string.settings_for_client, name),
                                fontSize = 15.sp,
                                color = AppColors.Teal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    RoundIconButton(icon = Icons.Rounded.Close, contentDescription = stringResource(R.string.close), onClick = onDismiss)
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    ClientSettingsSections(parts, open = true)
                    Text(stringResource(R.string.settings_all_hint), fontSize = 13.sp, color = AppColors.Muted)
                }
                PrimaryButton(
                    text = stringResource(R.string.dialog_done),
                    onClick = onDismiss,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
    }
}
