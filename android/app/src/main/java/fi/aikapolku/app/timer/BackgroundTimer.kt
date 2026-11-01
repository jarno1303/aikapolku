package fi.aikapolku.app.timer

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.widget.Toast
import fi.aikapolku.app.MainActivity
import fi.aikapolku.app.P018AlarmReceiver
import fi.aikapolku.app.R

/** Two independent user-started timers: the dial and a day-programme step. */
internal object BackgroundTimer {
    private const val STORE = "aikapolku_background_timers_v1"
    private const val CHANNEL = "aikapolku_timer_finished_v1"
    private const val ACTION = "fi.aikapolku.app.TIMER_EXPIRED"

    private fun prefs(c: Context) = c.getSharedPreferences(STORE, Context.MODE_PRIVATE)
    private fun key(owner: String, suffix: String) = "${owner}_$suffix"
    private fun requestCode(owner: String) = if (owner == "program") 1802 else 1801
    private fun service(c: Context) = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private fun alarmIntent(c: Context, owner: String): PendingIntent =
        PendingIntent.getBroadcast(
            c, requestCode(owner),
            Intent(c, P018AlarmReceiver::class.java).apply {
                action = ACTION
                putExtra("owner", owner)
            }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    fun remaining(c: Context, owner: String, default: Long): Long {
        val p = prefs(c)
        return if (p.getBoolean(key(owner, "running"), false)) {
            (p.getLong(key(owner, c.getString(R.string.endelapsed)), 0L) -
                SystemClock.elapsedRealtime()).coerceAtLeast(0L)
        } else p.getLong(key(owner, "remaining"), default).coerceAtLeast(0L)
    }

    fun running(c: Context, owner: String): Boolean =
        prefs(c).getBoolean(key(owner, "running"), false)

    /** Tarkat hälytykset: ilman niitä Android voi viivästyttää taustahälytystä. */
    fun exactAlarmsAllowed(c: Context): Boolean =
        Build.VERSION.SDK_INT < 31 || service(c).canScheduleExactAlarms()

    /** Ilmoitukset: ilman niitä taustalla päättynyt ajastin ei näy ilmoituksena. */
    fun notificationsAllowed(c: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    // Kumpaakin lupaa pyydetään enintään kerran sovelluksen käynnistyskertaa kohden.
    // Luvan epääminen ei saa estää ajastimen käyttöä.
    @Volatile private var exactPrompted = false
    @Volatile private var notificationPrompted = false

    /**
     * Voiko ajastimen käynnistää pyytämättä ensin lupaa? Palauttaa false vain, jos
     * jokin lupa puuttuu eikä sitä ole vielä tällä käynnistyskerralla pyydetty.
     * Ajastin toimii myös ilman lupia (ks. start).
     */
    fun allowed(c: Context): Boolean =
        (exactAlarmsAllowed(c) || exactPrompted) &&
            (notificationsAllowed(c) || notificationPrompted)

    fun requestPermissions(c: Context) {
        if (!exactAlarmsAllowed(c) && !exactPrompted) {
            exactPrompted = true
            Toast.makeText(c, c.getString(R.string.salli_aikapolkun_tarkat_halytykset_ja), Toast.LENGTH_LONG).show()
            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                Uri.parse("package:${c.packageName}"))
            if (c !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { c.startActivity(intent) }
            return
        }
        if (!notificationsAllowed(c) && !notificationPrompted) {
            notificationPrompted = true
            Toast.makeText(c, c.getString(R.string.salli_aikapolkun_ilmoitukset), Toast.LENGTH_LONG).show()
            (c as? Activity)?.requestPermissions(
                arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1803)
        }
    }

    /** Vain testejä varten: nollaa käynnistyskertakohtaiset lupakyselymerkinnät. */
    internal fun resetPermissionPrompts() {
        exactPrompted = false
        notificationPrompted = false
    }

    /** Returns a monotonic clock deadline for display in Compose. */
    @Synchronized
    fun start(c: Context, owner: String, remainingMs: Long, title: String): Long {
        val delayMs = remainingMs.coerceAtLeast(1L)
        val endElapsed = SystemClock.elapsedRealtime() + delayMs
        val endWall = System.currentTimeMillis() + delayMs
        val p = prefs(c)
        p.edit().putBoolean(key(owner, "running"), true)
            .putLong(key(owner, c.getString(R.string.endelapsed)), endElapsed)
            .putLong(key(owner, c.getString(R.string.endwall)), endWall)
            .putLong(key(owner, "remaining"), delayMs)
            .putString(key(owner, "title"), title)
            .commit()
        scheduleAlarm(c, owner, endElapsed)
        return endElapsed
    }

    /**
     * Tarkka hälytys, jos lupa on. Muuten (tai jos lupa perutaan juuri nyt) tavallinen
     * hälytys, jota Android voi viivästyttää taustalla. Näkymässä aika lasketaan
     * joka tapauksessa tarkasti, eikä sovellus kaadu.
     */
    private fun scheduleAlarm(c: Context, owner: String, endElapsed: Long) {
        val am = service(c)
        val intent = alarmIntent(c, owner)
        if (exactAlarmsAllowed(c)) {
            try {
                am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, endElapsed, intent)
                return
            } catch (e: SecurityException) {
                // Lupa peruttiin tarkistuksen jälkeen: jatketaan tavallisella hälytyksellä.
            }
        }
        am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, endElapsed, intent)
    }

    @Synchronized
    fun stop(c: Context, owner: String, remainingMs: Long) {
        service(c).cancel(alarmIntent(c, owner))
        prefs(c).edit().putBoolean(key(owner, "running"), false)
            .putLong(key(owner, "remaining"), remainingMs.coerceAtLeast(0L))
            .commit()
    }

    /** Exactly one of the foreground UI or the broadcast receiver handles completion. */
    @Synchronized
    fun complete(c: Context, owner: String): Boolean {
        val p = prefs(c)
        if (!p.getBoolean(key(owner, "running"), false)) return false
        val end = p.getLong(key(owner, c.getString(R.string.endelapsed)), 0L)
        if (SystemClock.elapsedRealtime() < end) return false
        p.edit().putBoolean(key(owner, "running"), false)
            .putLong(key(owner, "remaining"), 0L).commit()
        service(c).cancel(alarmIntent(c, owner))
        showNotification(c, owner, p.getString(key(owner, "title"), c.getString(R.string.aika_on_paattynyt)) ?: c.getString(R.string.aika_on_paattynyt))
        return true
    }

    fun selectedSound(c: Context): String = c.getSharedPreferences(
        "aikapolku_timer_preferences", Context.MODE_PRIVATE
    ).getString("end_sound", "off") ?: "off"

    private fun showNotification(c: Context, owner: String, title: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(CHANNEL, c.getString(R.string.ajastimen_valmistuminen), NotificationManager.IMPORTANCE_HIGH)
        channel.setSound(null, null) // Custom sound plays once through playCompletionSound.
        nm.createNotificationChannel(channel)
        val open = PendingIntent.getActivity(c, requestCode(owner),
            Intent(c, MainActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val note = Notification.Builder(c, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(c.getString(R.string.aikapolku_aika_paattyi))
            .setContentText(title)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        nm.notify(requestCode(owner), note)
    }

    /** Called on boot; elapsedRealtime resets on device restart. */
    fun restoreAfterBoot(c: Context) {
        for (owner in listOf("timer", "program")) {
            val p = prefs(c)
            if (!p.getBoolean(key(owner, "running"), false)) continue
            val left = (p.getLong(key(owner, c.getString(R.string.endwall)), 0L) -
                System.currentTimeMillis()).coerceAtLeast(1L)
            // Palautetaan aina: start toimii myös ilman lupia.
            start(c, owner, left,
                p.getString(key(owner, "title"), c.getString(R.string.ajastin)) ?: c.getString(R.string.ajastin))
        }
    }
}
