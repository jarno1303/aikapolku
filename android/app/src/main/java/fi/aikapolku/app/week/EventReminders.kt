package fi.aikapolku.app.week

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import androidx.compose.runtime.mutableStateOf
import fi.aikapolku.app.MainActivity
import fi.aikapolku.app.R
import fi.aikapolku.app.cards.CardArt
import fi.aikapolku.app.data.ClientWeek
import fi.aikapolku.app.data.DaySource
import fi.aikapolku.app.data.AckStore
import fi.aikapolku.app.data.ChangeStore
import fi.aikapolku.app.data.ChangeType
import fi.aikapolku.app.data.EventChange
import fi.aikapolku.app.data.EventKind
import fi.aikapolku.app.data.ProfileStore
import fi.aikapolku.app.data.ProgramStore
import fi.aikapolku.app.data.Reminder
import fi.aikapolku.app.data.WeekPlan
import fi.aikapolku.app.data.WeekSchedule
import fi.aikapolku.app.data.WeekStore
import fi.aikapolku.app.timer.BackgroundTimer
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Viikko-ohjelman ilmoitukset kaikista profiileista. Ajastettuna on aina vain seuraava
 * ilmoitushetki; kun se laukeaa, näytetään sen hetken ilmoitukset ja ajastetaan seuraava.
 * Ajastus tehdään uudelleen sovelluksen käynnistyessä, viikon muuttuessa, puhelimen
 * käynnistyessä ja kellonajan tai aikavyöhykkeen muuttuessa.
 */
internal object EventReminders {
    private const val CHANNEL = "aikapolku_events_v1"
    private const val ALARM_REQUEST = 1810
    private const val TEST_REQUEST = 1811
    private const val TEST_NOTIFICATION = 1812
    const val EXTRA_TEST = "reminder_test"
    const val EXTRA_AT = "reminder_at"
    const val EXTRA_PROFILE = "event_profile"
    const val EXTRA_EVENT = "event_id"
    const val EXTRA_DATE = "event_date"
    const val EXTRA_NOTIFICATION = "notification_id"

    /** Asiakkaiden koko päivät (omat kohdat ja ryhmien rungot). */
    private fun plans(c: Context): Map<String, DaySource> =
        ProfileStore.list(c).associate { it.id to ClientWeek.load(c, it.id) }

    private fun changes(c: Context): Map<String, List<EventChange>> =
        ProfileStore.list(c).associate { it.id to ChangeStore(c, it.id).load() }

    private fun alarmIntent(c: Context, at: LocalDateTime?): PendingIntent =
        PendingIntent.getBroadcast(
            c, ALARM_REQUEST,
            Intent(c, EventReminderReceiver::class.java).apply { if (at != null) putExtra(EXTRA_AT, at.toString()) },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    /** Sovellus käynnistyy: vanhat kuittaukset pois ja ilmoitukset ajan tasalle. */
    fun onAppStart(c: Context) {
        val today = LocalDate.now()
        ProfileStore.list(c).forEach {
            AckStore(c, it.id).prune(today)
            ChangeStore(c, it.id).clean(today, ClientWeek.load(c, it.id).eventIds())
        }
        reschedule(c)
    }

    /** Ajastaa seuraavan ilmoitushetken (tai perii ajastuksen, jos ilmoituksia ei ole). */
    fun reschedule(c: Context) {
        val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val next = WeekSchedule.nextReminderAt(plans(c), LocalDateTime.now(), changes(c))
        am.cancel(alarmIntent(c, null))
        // Kanava luodaan heti, jotta se näkyy puhelimen asetuksissa jo ennen ensimmäistä muistutusta.
        createChannel(c, c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
        if (next == null) return
        val whenMs = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        setAlarm(c, whenMs, alarmIntent(c, next))
    }

    /**
     * Muistutus herätyskellon tavoin (setAlarmClock): puhelimet, myös tiukan akunhallinnan
     * mallit (esim. OnePlus), toimittavat nämä luotettavasti ja ajallaan. Ilman tarkkojen
     * hälytysten lupaa tavallinen hälytys, joka voi myöhästyä.
     */
    private fun setAlarm(c: Context, whenMs: Long, intent: PendingIntent) {
        val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (BackgroundTimer.exactAlarmsAllowed(c)) {
            try {
                val show = PendingIntent.getActivity(
                    c, ALARM_REQUEST,
                    Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                am.setAlarmClock(AlarmManager.AlarmClockInfo(whenMs, show), intent)
                return
            } catch (_: SecurityException) {
                // Lupa peruttiin juuri: tavallinen hälytys (voi myöhästyä hieman).
            }
        }
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, whenMs, intent)
    }

    /** Testimuistutus noin minuutin päästä: ohjaaja näkee, toimivatko muistutukset tässä puhelimessa. */
    fun scheduleTest(c: Context) {
        val intent = PendingIntent.getBroadcast(
            c, TEST_REQUEST,
            Intent(c, EventReminderReceiver::class.java).putExtra(EXTRA_TEST, true),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        setAlarm(c, System.currentTimeMillis() + 60_000L, intent)
    }

    fun showTest(c: Context) {
        if (Build.VERSION.SDK_INT >= 33 &&
            c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createChannel(c, nm)
        val open = PendingIntent.getActivity(
            c, TEST_NOTIFICATION,
            Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        nm.notify(
            TEST_NOTIFICATION,
            Notification.Builder(c, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle(c.getString(R.string.reminder_test_title))
                .setContentText(c.getString(R.string.reminder_test_text))
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_REMINDER)
                .setContentIntent(open)
                .build()
        )
    }

    private fun createChannel(c: Context, nm: NotificationManager) {
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, c.getString(R.string.reminder_channel), NotificationManager.IMPORTANCE_HIGH)
        )
    }

    /** Hälytys laukesi: näytetään hetken ilmoitukset ja ajastetaan seuraava. */
    fun onAlarm(c: Context, atText: String?) {
        val at = atText?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() }
        if (at != null) {
            val plans = plans(c)
            WeekSchedule.dueReminders(plans, at, changes(c)).forEach { show(c, it, plans.size > 1) }
        }
        reschedule(c)
    }

    fun notificationId(r: Reminder): Int = (r.profileId + "|" + r.event.id + "|" + r.date).hashCode()

    private fun show(c: Context, r: Reminder, manyProfiles: Boolean) {
        if (Build.VERSION.SDK_INT >= 33 &&
            c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createChannel(c, nm)

        val (title, imageName, imagePath) = when (r.event.kind) {
            EventKind.CARD -> Triple(r.event.cardName, r.event.cardName, r.event.cardPhoto)
            EventKind.PROGRAM -> {
                val program = ProgramStore(c, r.profileId).load().firstOrNull { it.id == r.event.programId } ?: return
                val first = program.steps.firstOrNull()
                Triple(program.name, first?.name ?: "", first?.photo ?: "")
            }
        }
        val profileName = ProfileStore.list(c).firstOrNull { it.id == r.profileId }?.name.orEmpty()
        // Muutos kerrotaan jo otsikossa ("Muutos: Ulkoilu", "Muutos: Uinti on peruttu").
        val changedTitle = when (r.change?.type) {
            null -> title
            ChangeType.CANCEL -> c.getString(R.string.change_notification_cancelled, title)
            else -> c.getString(R.string.change_notification, title)
        }
        val shownTitle = if (manyProfiles && profileName.isNotBlank()) "$profileName: $changedTitle" else changedTitle
        val time = r.event.time.replace(':', '.')
        val text = when {
            r.change?.type == ChangeType.CANCEL -> r.change.reason.ifBlank { c.getString(R.string.change_open_hint) }
            r.event.notifyBefore > 0 -> c.getString(R.string.reminder_in_minutes, r.event.notifyBefore, time)
            else -> c.getString(R.string.reminder_now, time)
        }
        val id = notificationId(r)

        val open = PendingIntent.getActivity(
            c, id,
            Intent(c, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(EXTRA_PROFILE, r.profileId)
                .putExtra(EXTRA_EVENT, r.event.id)
                .putExtra(EXTRA_DATE, r.date.toString()),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = Notification.Builder(c, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(shownTitle)
            .setContentText(text)
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setContentIntent(open)
        image(c, imageName, imagePath)?.let { bitmap ->
            builder.setLargeIcon(bitmap)
            builder.setStyle(Notification.BigPictureStyle().bigPicture(bitmap).setSummaryText(text))
        }
        if (r.event.kind == EventKind.CARD && r.change?.type != ChangeType.CANCEL && !r.event.ongoing) {
            val done = PendingIntent.getBroadcast(
                c, id,
                Intent(c, EventAckReceiver::class.java)
                    .putExtra(EXTRA_PROFILE, r.profileId)
                    .putExtra(EXTRA_EVENT, r.event.id)
                    .putExtra(EXTRA_DATE, r.date.toString())
                    .putExtra(EXTRA_NOTIFICATION, id),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(
                Notification.Action.Builder(null, c.getString(R.string.run_done_button), done).build()
            )
        }
        nm.notify(id, builder.build())
    }

    /** Ilmoituksen kuva: oma valokuva pienennettynä tai valmis kuva. */
    private fun image(c: Context, name: String, path: String): Bitmap? = runCatching {
        val file = path.takeIf { it.isNotBlank() && !it.startsWith("builtin:") }?.let(::File)
        if (file != null && file.isFile) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            var sample = 1
            while (bounds.outWidth / sample > 512 || bounds.outHeight / sample > 512) sample *= 2
            BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
        } else {
            CardArt.bitmap(c, name, path)
        }
    }.getOrNull()

    fun acknowledge(c: Context, profileId: String, eventId: String, date: LocalDate) {
        AckStore(c, profileId).ack(date, eventId)
    }
}

/**
 * Ilmoituksesta avattu tapahtuma. MainActivity asettaa, sovellus avaa tapahtuman,
 * jos se kuuluu nykyiseen profiiliin (profiilia ei vaihdeta ohi ohjaajan PIN-koodin).
 */
internal data class EventLaunchRequest(val profileId: String, val eventId: String, val date: LocalDate)

internal object EventLaunch {
    val pending = mutableStateOf<EventLaunchRequest?>(null)

    fun from(intent: Intent?) {
        val profile = intent?.getStringExtra(EventReminders.EXTRA_PROFILE) ?: return
        val event = intent.getStringExtra(EventReminders.EXTRA_EVENT) ?: return
        val date = intent.getStringExtra(EventReminders.EXTRA_DATE)
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: LocalDate.now()
        pending.value = EventLaunchRequest(profile, event, date)
        // Sama ilmoitus ei avaa tapahtumaa uudelleen esim. näytön kääntyessä.
        intent.removeExtra(EventReminders.EXTRA_PROFILE)
    }
}

class EventReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.getBooleanExtra(EventReminders.EXTRA_TEST, false)) {
            EventReminders.showTest(context)
        } else {
            EventReminders.onAlarm(context, intent.getStringExtra(EventReminders.EXTRA_AT))
        }
    }
}

/** Ilmoituksen Valmis-painike: kuittaa tapahtuman ilman sovelluksen avaamista. */
class EventAckReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val profile = intent.getStringExtra(EventReminders.EXTRA_PROFILE) ?: return
        val event = intent.getStringExtra(EventReminders.EXTRA_EVENT) ?: return
        val date = intent.getStringExtra(EventReminders.EXTRA_DATE)
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: LocalDate.now()
        EventReminders.acknowledge(context, profile, event, date)
        val id = intent.getIntExtra(EventReminders.EXTRA_NOTIFICATION, 0)
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(id)
    }
}

/** Kellonaika, aikavyöhyke tai sovelluksen päivitys muuttui: ilmoitukset ajastetaan uudelleen. */
class EventTimeChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        EventReminders.reschedule(context)
    }
}
