package fi.aikapolku.app

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAlarmManager
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

/**
 * Taustalla toimiva ajastin (P018Timer): hälytys, jäljellä oleva aika,
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
    }

    private fun scheduledAlarmCount() = shadowOf(alarms).scheduledAlarms.size
    private fun timerPrefs() =
        app.getSharedPreferences("aikapolku_background_timers_v1", Context.MODE_PRIVATE)
    private fun notificationCount() =
        shadowOf(app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .allNotifications.size

    @Test
    fun `luvat myonnetty - ajastin sallitaan`() {
        assertTrue(P018Timer.allowed(app))
    }

    @Test
    fun `ilman tarkkojen halytysten lupaa ajastinta ei sallita`() {
        ShadowAlarmManager.setCanScheduleExactAlarms(false)

        assertFalse(P018Timer.allowed(app))
    }

    @Test
    fun `kaynnistys asettaa halytyksen ja merkitsee ajastimen kayntiin`() {
        P018Timer.start(app, "timer", 60_000L, "Askartelu")

        assertTrue(P018Timer.running(app, "timer"))
        assertEquals(1, scheduledAlarmCount())
        assertEquals(60_000L, P018Timer.remaining(app, "timer", 0L))
    }

    @Test
    fun `jaljella oleva aika vahenee kellon mukana`() {
        P018Timer.start(app, "timer", 60_000L, "Askartelu")

        ShadowSystemClock.advanceBy(Duration.ofSeconds(20))

        assertEquals(40_000L, P018Timer.remaining(app, "timer", 0L))
    }

    @Test
    fun `jaljella oleva aika ei mene koskaan negatiiviseksi`() {
        P018Timer.start(app, "timer", 5_000L, "Askartelu")

        ShadowSystemClock.advanceBy(Duration.ofMinutes(10))

        assertEquals(0L, P018Timer.remaining(app, "timer", 0L))
    }

    @Test
    fun `pysaytys tallentaa jaljella olevan ajan ja peruu halytyksen`() {
        P018Timer.start(app, "timer", 60_000L, "Askartelu")
        ShadowSystemClock.advanceBy(Duration.ofSeconds(15))

        P018Timer.stop(app, "timer", P018Timer.remaining(app, "timer", 0L))

        assertFalse(P018Timer.running(app, "timer"))
        assertEquals(45_000L, P018Timer.remaining(app, "timer", 0L))
        assertEquals(0, scheduledAlarmCount())
    }

    @Test
    fun `pysaytetty aika pysyy samana vaikka kello etenee`() {
        P018Timer.start(app, "timer", 60_000L, "Askartelu")
        P018Timer.stop(app, "timer", 30_000L)

        ShadowSystemClock.advanceBy(Duration.ofMinutes(5))

        assertEquals(30_000L, P018Timer.remaining(app, "timer", 0L))
    }

    @Test
    fun `ajastin ei valmistu ennen aikaa`() {
        P018Timer.start(app, "timer", 60_000L, "Askartelu")
        ShadowSystemClock.advanceBy(Duration.ofSeconds(59))

        assertFalse(P018Timer.complete(app, "timer"))
        assertTrue(P018Timer.running(app, "timer"))
        assertEquals(0, notificationCount())
    }

    @Test
    fun `valmistuminen kasitellaan tasmalleen kerran`() {
        P018Timer.start(app, "timer", 60_000L, "Askartelu")
        ShadowSystemClock.advanceBy(Duration.ofSeconds(61))

        // Näkymä ja hälytysvastaanotin voivat kumpikin yrittää – vain ensimmäinen onnistuu.
        assertTrue(P018Timer.complete(app, "timer"))
        assertFalse(P018Timer.complete(app, "timer"))

        assertFalse(P018Timer.running(app, "timer"))
        assertEquals(0L, P018Timer.remaining(app, "timer", 99L))
        assertEquals(1, notificationCount())
        assertEquals(0, scheduledAlarmCount())
    }

    @Test
    fun `pysaytettya ajastinta ei merkita valmiiksi`() {
        P018Timer.start(app, "timer", 1_000L, "Askartelu")
        P018Timer.stop(app, "timer", 1_000L)
        ShadowSystemClock.advanceBy(Duration.ofMinutes(1))

        assertFalse(P018Timer.complete(app, "timer"))
        assertEquals(0, notificationCount())
    }

    @Test
    fun `kellotaulun ajastin ja paivaohjelman ajastin ovat toisistaan riippumattomat`() {
        P018Timer.start(app, "timer", 60_000L, "Ajastin")
        P018Timer.start(app, "program", 120_000L, "Ruokailu")
        assertEquals(2, scheduledAlarmCount())

        P018Timer.stop(app, "timer", 10_000L)

        assertFalse(P018Timer.running(app, "timer"))
        assertTrue(P018Timer.running(app, "program"))
        assertEquals(120_000L, P018Timer.remaining(app, "program", 0L))
        assertEquals(1, scheduledAlarmCount())
    }

    @Test
    fun `uudelleenkaynnistyksessa vanhentunut ajastin hälyttää heti`() {
        timerPrefs().edit()
            .putBoolean("timer_running", true)
            .putLong("timer_endWall", 0L) // päättynyt puhelimen ollessa pois päältä
            .putString("timer_title", "Lepo")
            .commit()

        P018Timer.restoreAfterBoot(app)

        assertTrue(P018Timer.running(app, "timer"))
        assertTrue(P018Timer.remaining(app, "timer", 0L) <= 1L)
        assertEquals(1, scheduledAlarmCount())
    }

    @Test
    fun `uudelleenkaynnistyksessa kaynnissa ollut ajastin jatkuu`() {
        timerPrefs().edit()
            .putBoolean("program_running", true)
            .putLong("program_endWall", Long.MAX_VALUE / 2)
            .putString("program_title", "Ulkoilu")
            .commit()

        P018Timer.restoreAfterBoot(app)

        assertTrue(P018Timer.running(app, "program"))
        assertTrue(P018Timer.remaining(app, "program", 0L) > Duration.ofDays(1).toMillis())
        assertEquals(1, scheduledAlarmCount())
    }

    @Test
    fun `uudelleenkaynnistys ei kaynnista pysaytettya ajastinta`() {
        P018Timer.start(app, "timer", 60_000L, "Askartelu")
        P018Timer.stop(app, "timer", 30_000L)

        P018Timer.restoreAfterBoot(app)

        assertFalse(P018Timer.running(app, "timer"))
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
        P018Timer.start(app, "timer", 1_000L, "Askartelu")
        ShadowSystemClock.advanceBy(Duration.ofSeconds(5))

        P018AlarmReceiver().onReceive(app, Intent().putExtra("owner", "tuntematon"))
        P018AlarmReceiver().onReceive(app, Intent())

        assertTrue("Oikeaa ajastinta ei saa merkitä valmiiksi", P018Timer.running(app, "timer"))
        assertEquals(0, notificationCount())
    }

    /**
     * TUNNETTU RISKI (koodiarvio 1.11.2026): start() heittää poikkeuksen, jos tarkkojen
     * hälytysten lupa on peruttu. Kutsukohdat (+1 min, ajan vähennys) eivät tarkista
     * lupaa ennen kutsua, joten sovellus voi kaatua. Tämä testi dokumentoi nykyisen
     * käytöksen; kun riski korjataan, testi päivitetään odottamaan hallittua käsittelyä.
     */
    @Test
    fun `TUNNETTU RISKI - kaynnistys ilman lupaa heittaa poikkeuksen`() {
        ShadowAlarmManager.setCanScheduleExactAlarms(false)

        assertThrows(IllegalStateException::class.java) {
            P018Timer.start(app, "timer", 60_000L, "Askartelu")
        }
    }
}
