package fi.aikapolku.app.week

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import fi.aikapolku.app.R
import fi.aikapolku.app.caregiver.CaregiverSession
import fi.aikapolku.app.timer.BackgroundTimer
import fi.aikapolku.app.ui.AppColors

/**
 * Ohjaajan asetusten "Muistutusten tila": näkyykö muistutus ajallaan tässä puhelimessa.
 * Jokaiselle puuttuvalle asetukselle painike, joka avaa oikean kohdan puhelimen asetuksista,
 * sekä testimuistutus. Tila päivittyy, kun ohjaaja palaa asetuksista sovellukseen.
 */
@Composable
internal fun NotificationStatusCard() {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        refresh++
        // Lupa voi juuri muuttua asetuksissa: ajastus uudelleen, jotta tarkka hälytys otetaan käyttöön.
        EventReminders.reschedule(context.applicationContext)
    }
    val notifications = remember(refresh) { BackgroundTimer.notificationsAllowed(context) }
    val exact = remember(refresh) { BackgroundTimer.exactAlarmsAllowed(context) }
    val battery = remember(refresh) { ignoresBatteryOptimizations(context) }
    var testSent by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.reminder_status_title), fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(
                stringResource(R.string.reminder_status_info),
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            StatusRow(stringResource(R.string.reminder_status_notifications), notifications) {
                openSettings(context, notificationSettings(context))
            }
            StatusRow(stringResource(R.string.reminder_status_exact), exact) {
                if (Build.VERSION.SDK_INT >= 31) {
                    openSettings(
                        context,
                        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
                    )
                }
            }
            StatusRow(stringResource(R.string.reminder_status_battery), battery) {
                openSettings(context, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
            Text(
                stringResource(R.string.reminder_status_oem),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = {
                openSettings(
                    context,
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                )
            }) { Text(stringResource(R.string.reminder_status_app_settings)) }

            OutlinedButton(
                onClick = {
                    EventReminders.scheduleTest(context.applicationContext)
                    testSent = true
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
            ) { Text(stringResource(R.string.reminder_test_button)) }
            if (testSent) {
                Text(stringResource(R.string.reminder_test_sent), fontSize = 14.sp, color = AppColors.Teal)
            }
        }
    }
}

@Composable
private fun StatusRow(label: String, ok: Boolean, onFix: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            if (ok) "✓" else "!",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = if (ok) AppColors.Teal else Color(0xFFB45309)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(
                stringResource(if (ok) R.string.reminder_status_ok else R.string.reminder_status_missing),
                fontSize = 13.sp,
                color = if (ok) AppColors.Teal else Color(0xFFB45309)
            )
        }
        if (!ok) {
            OutlinedButton(onClick = onFix) { Text(stringResource(R.string.reminder_status_fix)) }
        }
    }
}

private fun ignoresBatteryOptimizations(context: Context): Boolean {
    val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return true
    return pm.isIgnoringBatteryOptimizations(context.packageName)
}

private fun notificationSettings(context: Context): Intent =
    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

/** Avaa puhelimen asetukset. Ohjaajan avaus säilyy, kun palataan (ks. CaregiverSession). */
private fun openSettings(context: Context, intent: Intent) {
    CaregiverSession.keepOpenForOwnPicker()
    if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }.onFailure {
        // Joissain puhelimissa erillistä asetussivua ei ole: avataan sovelluksen tiedot.
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                    .apply { if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            )
        }
    }
}
