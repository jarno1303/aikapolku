package fi.aikapolku.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import fi.aikapolku.app.timer.BackgroundTimer
import fi.aikapolku.app.timer.playCompletionSound
import kotlinx.coroutines.runBlocking

/*
 * Ajastimen hälytys- ja käynnistysvastaanottimet.
 * Luokkien nimet (P018…) ja paikka juuripaketissa säilytetään tarkoituksella:
 * jo ajastetut hälytykset ja AndroidManifest viittaavat niihin nimellä, joten
 * uudelleennimeäminen voisi hukata päivityksen aikana käynnissä olevan ajastimen.
 */
class P018AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val owner = intent.getStringExtra("owner") ?: return
        if (owner != "timer" && owner != "program") return
        if (BackgroundTimer.complete(context, owner)) {
            val pending = goAsync()
            Thread {
                try { runBlocking { playCompletionSound(BackgroundTimer.selectedSound(context)) } }
                finally { pending.finish() }
            }.start()
        }
    }
}

class P018BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            BackgroundTimer.restoreAfterBoot(context)
        }
    }
}
