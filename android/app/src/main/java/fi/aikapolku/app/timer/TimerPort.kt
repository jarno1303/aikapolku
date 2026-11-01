package fi.aikapolku.app.timer

import android.content.Context
import android.os.SystemClock

/** Ajastimen rajapinta tilaluokille. Androidissa [BackgroundTimerPort]; testeissä väärennös. */
internal interface TimerPort {
    fun now(): Long
    fun allowed(): Boolean
    fun requestPermissions()
    /** Käynnistää ajastimen ja palauttaa päättymishetken ([now]-aikaa). */
    fun start(durationMs: Long, title: String): Long
    fun stop(remainingMs: Long)
    fun remaining(fallbackMs: Long): Long
    fun isRunning(): Boolean
    /** Merkitsee ajan päättyneeksi; true, jos tämä oli ensimmäinen merkintä (äänimerkki soitetaan). */
    fun complete(): Boolean
}

/** Ajan kulku yhdellä tick()-kutsulla. */
internal enum class Tick {
    /** Aikaa on vielä jäljellä. */
    RUNNING,
    /** Aika päättyi nyt; äänimerkki soitetaan. */
    ENDED,
    /** Aika päättyi, mutta päättyminen oli jo käsitelty (esim. taustalla). */
    ENDED_SILENT
}

/** [TimerPort] Androidin taustalla toimivalla ajastimella ([owner] = "timer" tai "program"). */
internal class BackgroundTimerPort(private val context: Context, private val owner: String) : TimerPort {
    override fun now() = SystemClock.elapsedRealtime()
    override fun allowed() = BackgroundTimer.allowed(context)
    override fun requestPermissions() = BackgroundTimer.requestPermissions(context)
    override fun start(durationMs: Long, title: String) = BackgroundTimer.start(context, owner, durationMs, title)
    override fun stop(remainingMs: Long) = BackgroundTimer.stop(context, owner, remainingMs)
    override fun remaining(fallbackMs: Long) = BackgroundTimer.remaining(context, owner, fallbackMs)
    override fun isRunning() = BackgroundTimer.running(context, owner)
    override fun complete() = BackgroundTimer.complete(context, owner)

}

