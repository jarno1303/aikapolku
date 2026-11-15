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
    class Request(val name: String, val code: String) {
        internal val latch = CountDownLatch(1)
        @Volatile internal var accepted = false
    }

    var pending by mutableStateOf<Request?>(null)
        private set

    /** Estävä: palauttaa true, jos ohjaaja hyväksyi ajoissa. */
    fun request(name: String, code: String, timeoutMs: Long): Boolean {
        val request = Request(name, code)
        pending = request
        val answered = request.latch.await(timeoutMs, TimeUnit.MILLISECONDS)
        if (pending === request) pending = null
        return answered && request.accepted
    }

    fun answer(accept: Boolean) {
        val request = pending ?: return
        request.accepted = accept
        pending = null
        request.latch.countDown()
    }
}
