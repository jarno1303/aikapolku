package fi.aikapolku.app.link

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Ohjaajan puhelimessa odottava pariliitospyyntö: vastaanottopalvelu odottaa (taustasäikeessä),
 * kunnes ohjaaja hyväksyy tai hylkää näkymässä (PairingRequestDialog), tai aika loppuu.
 */
internal object LinkPairing {
    /** [place] = tämä on paikan laite: ohjaaja valitsee, kenen asukkaan puhelin liitetään. */
    class Request(val name: String, val code: String, val place: Boolean = false) {
        internal val latch = CountDownLatch(1)
        @Volatile internal var accepted = false
        @Volatile internal var resident = ""
    }

    var pending by mutableStateOf<Request?>(null)
        private set

    /**
     * Estävä: palauttaa null, jos ohjaaja hylkäsi tai aika loppui; muuten valitun asukkaan
     * tunnuksen (tyhjä, kun laite ei ole paikan laite).
     */
    fun request(name: String, code: String, timeoutMs: Long, place: Boolean = false): String? {
        val request = Request(name, code, place)
        pending = request
        val answered = request.latch.await(timeoutMs, TimeUnit.MILLISECONDS)
        if (pending === request) pending = null
        return if (answered && request.accepted) request.resident else null
    }

    fun answer(accept: Boolean, resident: String = "") {
        val request = pending ?: return
        request.accepted = accept
        request.resident = resident
        pending = null
        request.latch.countDown()
    }
}
