package fi.aikapolku.app

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import fi.aikapolku.app.timer.BackgroundTimer
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAlarmManager
import org.robolectric.shadows.ShadowSystemClock

/**
 * Taustalla toimiva ajastin (BackgroundTimer): hälytys, jäljellä oleva aika,
 * valmistuminen täsmälleen kerran ja palautus uudelleenkäynnistyksen jälkeen.
 */
@RunWith(RobolectricTestRunner::class)
class BackgroundTimerTest {

    private lateinit var app: Application
    private lateinit var alarms: AlarmManager

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        alarms = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        BackgroundTimer.resetPermissionPrompts()
    }

    private fun scheduledAlarmCount() = shadowOf(alarms).scheduledAlarms.size
    private fun timerPrefs() =
        app.getSharedPreferences("aikapolku_background_timers_v1", Context.MODE_PRIVATE)
    private fun notificationCount() =
        shadowOf(app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .allNotifications.size

    @Test
    fun `luvat myonnetty - ajastin sallitaan`() {
        assertTrue(BackgroundTimer.allowed(app))
    }

    @Test
    fun `puuttuvaa tarkkojen halytysten lupaa kysytaan ennen kaynnistysta`() {
        ShadowAlarmManager.setCanScheduleExactAlarms(false)

        assertFalse(BackgroundTimer.allowed(app))
    }

    @Test
    fun `kaynnistys asettaa halytyksen ja merkitsee ajastimen kayntiin`() {
        BackgroundTimer.start(app, "timer", 60_000L, "Askartelu")

        assertTrue(BackgroundTimer.running(app, "timer"))
        assertEquals(1, scheduledAlarmCount())
        assertEquals(60_000L, BackgroundTimer.remaining(app, "timer", 0L))
    }

    @Test
    fun `jaljella oleva aika vahenee kellon mukana`() {
        BackgroundTimer.start(app, "timer", 60_000L, "Askartelu")

        ShadowSystemClock.advanceBy(Duration.ofSeconds(20))

        assertEquals(40_000L, BackgroundTimer.remaining(app, "timer", 0L))
    }

    @Test
    fun `jaljella oleva aika ei mene koskaan negatiiviseksi`() {
        BackgroundTimer.start(app, "timer", 5_000L, "Askartelu")

        ShadowSystemClock.advanceBy(Duration.ofMinutes(10))

        assertEquals(0L, BackgroundTimer.remaining(app, "timer", 0L))
    }

    @Test
    fun `pysaytys tallentaa jaljella olevan ajan ja peruu halytyksen`() {
        BackgroundTimer.start(app, "timer", 60_000L, "Askartelu")
        ShadowSystemClock.advanceBy(Duration.ofSeconds(15))

        BackgroundTimer.stop(app, "timer", BackgroundTimer.remaining(app, "timer", 0L))

        assertFalse(BackgroundTimer.running(app, "timer"))
        assertEquals(45_000L, BackgroundTimer.remaining(app, "timer", 0L))
        assertEquals(0, scheduledAlarmCount())
    }

    @Test
    fun `pysaytetty aika pysyy samana vaikka kello etenee`() {
        BackgroundTimer.start(app, "timer", 60_000L, "Askartelu")
        BackgroundTimer.stop(app, "timer", 30_000L)

        ShadowSystemClock.advanceBy(Duration.ofMinutes(5))

        assertEquals(30_000L, BackgroundTimer.remaining(app, "timer", 0L))
    }

    @Test
    fun `ajastin ei valmistu ennen aikaa`() {
        BackgroundTimer.start(app, "timer", 60_000L, "Askartelu")
        ShadowSystemClock.advanceBy(Duration.ofSeconds(59))

        assertFalse(BackgroundTimer.complete(app, "timer"))
        assertTrue(BackgroundTimer.running(app, "timer"))
        assertEquals(0, notificationCount())
    }

    @Test
    fun `valmistuminen kasitellaan tasmalleen kerran`() {
        BackgroundTimer.start(app, "timer", 60_000L, "Askartelu")
        ShadowSystemClock.advanceBy(Duration.ofSeconds(61))

        // Näkymä ja hälytysvastaanotin voivat kumpikin yrittää – vain ensimmäinen onnistuu.
        assertTrue(BackgroundTimer.complete(app, "timer"))
        assertFalse(BackgroundTimer.complete(app, "timer"))

        assertFalse(BackgroundTimer.running(app, "timer"))
        assertEquals(0L, BackgroundTimer.remaining(app, "timer", 99L))
        assertEquals(1, notificationCount())
        assertEquals(0, scheduledAlarmCount())
    }

    @Test
    fun `pysaytettya ajastinta ei merkita valmiiksi`() {
        BackgroundTimer.start(app, "timer", 1_000L, "Askartelu")
        BackgroundTimer.stop(app, "timer", 1_000L)
        ShadowSystemClock.advanceBy(Duration.ofMinutes(1))

        assertFalse(BackgroundTimer.complete(app, "timer"))
        assertEquals(0, notificationCount())
    }

    @Test
    fun `kellotaulun ajastin ja paivaohjelman ajastin ovat toisistaan riippumattomat`() {
        BackgroundTimer.start(app, "timer", 60_000L, "Ajastin")
        BackgroundTimer.start(app, "program", 120_000L, "Ruokailu")
        assertEquals(2, scheduledAlarmCount())

        BackgroundTimer.stop(app, "timer", 10_000L)

        assertFalse(BackgroundTimer.running(app, "timer"))
        assertTrue(BackgroundTimer.running(app, "program"))
        assertEquals(120_000L, BackgroundTimer.remaining(app, "program", 0L))
        assertEquals(1, scheduledAlarmCount())
    }

    @Test
    fun `uudelleenkaynnistyksessa vanhentunut ajastin hälyttää heti`() {
        timerPrefs().edit()
            .putBoolean("timer_running", true)
            .putLong("timer_endWall", 0L) // päättynyt puhelimen ollessa pois päältä
            .putString("timer_title", "Lepo")
            .commit()

        BackgroundTimer.restoreAfterBoot(app)

        assertTrue(BackgroundTimer.running(app, "timer"))
        assertTrue(BackgroundTimer.remaining(app, "timer", 0L) <= 1L)
        assertEquals(1, scheduledAlarmCount())
    }

    @Test
    fun `uudelleenkaynnistyksessa kaynnissa ollut ajastin jatkuu`() {
        timerPrefs().edit()
            .putBoolean("program_running", true)
            .putLong("program_endWall", Long.MAX_VALUE / 2)
            .putString("program_title", "Ulkoilu")
            .commit()

        BackgroundTimer.restoreAfterBoot(app)

        assertTrue(BackgroundTimer.running(app, "program"))
        assertTrue(BackgroundTimer.remaining(app, "program", 0L) > Duration.ofDays(1).toMillis())
        assertEquals(1, scheduledAlarmCount())
    }

    @Test
    fun `uudelleenkaynnistys ei kaynnista pysaytettya ajastinta`() {
        BackgroundTimer.start(app, "timer", 60_000L, "Askartelu")
        BackgroundTimer.stop(app, "timer", 30_000L)

        BackgroundTimer.restoreAfterBoot(app)

        assertFalse(BackgroundTimer.running(app, "timer"))
        assertEquals(0, scheduledAlarmCount())
    }

    @Test
    fun `kaynnistysvastaanotin reagoi vain kaynnistystapahtumaan`() {
        timerPrefs().edit()
            .putBoolean("timer_running", true)
            .putLong("timer_endWall", Long.MAX_VALUE / 2)
            .commit()

        P018BootReceiver().onReceive(app, Intent("jokin.muu.tapahtuma"))
        assertEquals(0, scheduledAlarmCount())

        P018BootReceiver().onReceive(app, Intent(Intent.ACTION_BOOT_COMPLETED))
        assertEquals(1, scheduledAlarmCount())
    }

    @Test
    fun `halytysvastaanotin ohittaa tuntemattoman ajastimen`() {
        BackgroundTimer.start(app, "timer", 1_000L, "Askartelu")
        ShadowSystemClock.advanceBy(Duration.ofSeconds(5))

        P018AlarmReceiver().onReceive(app, Intent().putExtra("owner", "tuntematon"))
        P018AlarmReceiver().onReceive(app, Intent())

        assertTrue("Oikeaa ajastinta ei saa merkitä valmiiksi", BackgroundTimer.running(app, "timer"))
        assertEquals(0, notificationCount())
    }

    // --- Luvat puuttuvat: ajastimen pitää silti toimia (korjattu 1.11.2026) ---

    @Test
    fun `kaynnistys ilman tarkkojen halytysten lupaa ei kaada ja asettaa halytyksen`() {
        ShadowAlarmManager.setCanScheduleExactAlarms(false)

        BackgroundTimer.start(app, "timer", 60_000L, "Askartelu")

        assertTrue(BackgroundTimer.running(app, "timer"))
        assertEquals(60_000L, BackgroundTimer.remaining(app, "timer", 0L))
        assertEquals(1, scheduledAlarmCount())
    }

    @Test
    fun `lisaaika kesken ajon ei kaada vaikka lupa on peruttu`() {
        BackgroundTimer.start(app, "program", 60_000L, "Ulkoilu")
        ShadowAlarmManager.setCanScheduleExactAlarms(false) // käyttäjä perui luvan

        // +5 min / +1 min / -1 min käynnistävät ajastimen uudelleen
        BackgroundTimer.start(app, "program", 360_000L, "Ulkoilu")

        assertTrue(BackgroundTimer.running(app, "program"))
        assertEquals(360_000L, BackgroundTimer.remaining(app, "program", 0L))
        assertEquals(1, scheduledAlarmCount())
    }

    @Test
    fun `ilman ilmoituslupaa ajastin toimii mutta ilmoitusta ei nayteta`() {
        shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)

        BackgroundTimer.start(app, "timer", 1_000L, "Askartelu")
        ShadowSystemClock.advanceBy(Duration.ofSeconds(2))

        assertTrue(BackgroundTimer.complete(app, "timer"))
        assertFalse(BackgroundTimer.running(app, "timer"))
        assertEquals(0, notificationCount())
    }

    @Test
    fun `ilmoituslupaa kysytaan kerran ja sen jalkeen ajastin sallitaan`() {
        shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertFalse(BackgroundTimer.allowed(app))

        BackgroundTimer.requestPermissions(app) // käyttäjä kieltää

        assertTrue("Epääminen ei saa estää ajastinta", BackgroundTimer.allowed(app))
    }

    @Test
    fun `tarkkojen halytysten lupaa kysytaan kerran ja sen jalkeen ajastin sallitaan`() {
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        assertFalse(BackgroundTimer.allowed(app))

        BackgroundTimer.requestPermissions(app)

        assertEquals(
            Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
            shadowOf(app).nextStartedActivity?.action
        )
        assertTrue("Epääminen ei saa estää ajastinta", BackgroundTimer.allowed(app))
    }

    @Test
    fun `uudelleenkaynnistys palauttaa ajastimen myos ilman lupia`() {
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        timerPrefs().edit()
            .putBoolean("timer_running", true)
            .putLong("timer_endWall", Long.MAX_VALUE / 2)
            .commit()

        BackgroundTimer.restoreAfterBoot(app)

        assertTrue(BackgroundTimer.running(app, "timer"))
        assertEquals(1, scheduledAlarmCount())
    }
}
