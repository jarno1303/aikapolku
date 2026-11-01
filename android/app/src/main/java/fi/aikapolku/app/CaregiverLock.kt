package fi.aikapolku.app

import android.content.Context
import androidx.compose.runtime.mutableLongStateOf
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * P0-21e – Ohjaajan lukitus: logiikka ilman käyttöliittymää.
 *
 * Periaate: käyttäjän omat toiminnot (apu, tauko, valmis, ohitus, aloita/tauko)
 * eivät ole koskaan lukituksen takana. Lukitus koskee vain ohjaajan toimintoja.
 */

/** Sovelluksen toiminnot ja se, kuuluvatko ne ohjaajalle. Yksi paikka koko linjaukselle. */
internal enum class AppAction(val caregiverOnly: Boolean) {
    // Käyttäjän omat toiminnot – EI KOSKAAN lukittuja (suunnitelman eettinen linjaus).
    HELP(false),
    BREAK(false),
    DONE(false),
    SKIP_STEP(false),
    START_PAUSE(false),
    START_PROGRAM(false),
    VIEW_PROGRAM(false),
    PRINT(false),

    // Ohjaajan toiminnot – lukittuja, kun lukitus on päällä.
    ADJUST_TIME(true),
    RESET_TIMER(true),
    EXTEND_TIME(true),
    END_PROGRAM(true),
    EDIT_PROGRAMS(true),
    EDIT_CARDS(true),
    SETTINGS(true)
}

/** Lukituksen tila yhdellä hetkellä: puhdas, testattava päätös siitä, mikä on sallittua. */
internal class CaregiverAccessState(
    val lockEnabled: Boolean,
    val sessionOpen: Boolean
) {
    /** true, kun ohjaajan toiminnot ovat piilossa ja vaativat PIN-koodin. */
    val isLocked: Boolean get() = lockEnabled && !sessionOpen

    fun allows(action: AppAction): Boolean = !action.caregiverOnly || !isLocked
}

/** PIN-koodin tarkistuksen tulos. */
internal sealed class PinResult {
    object Ok : PinResult()
    data class Wrong(val attemptsLeft: Int) : PinResult()
    data class LockedOut(val remainingMs: Long) : PinResult()
}

/**
 * PIN-koodi ja väärien yritysten esto. Tallennetaan sovelluksen yksityisiin asetuksiin:
 * vain suolattu PBKDF2-tiiviste, ei koskaan itse PIN-koodia.
 */
internal class CaregiverLockStore(
    context: Context,
    private val wallClock: () -> Long = System::currentTimeMillis
) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    val isEnabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false) && prefs.contains(KEY_HASH)

    /** Ottaa lukituksen käyttöön tai vaihtaa PIN-koodin. */
    fun enable(pin: String) {
        require(pinProblem(pin) == null) { "PIN ei kelpaa" }
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        prefs.edit()
            .putString(KEY_SALT, encode(salt))
            .putString(KEY_HASH, encode(hash(pin, salt)))
            .putBoolean(KEY_ENABLED, true)
            .putInt(KEY_FAILURES, 0)
            .putInt(KEY_LOCKOUTS, 0)
            .putLong(KEY_LOCKOUT_UNTIL, 0L)
            .commit()
    }

    /** Poistaa lukituksen ja PIN-koodin. Kertakysymyksen tila säilyy. */
    fun disable() {
        prefs.edit()
            .remove(KEY_SALT).remove(KEY_HASH)
            .putBoolean(KEY_ENABLED, false)
            .remove(KEY_FAILURES).remove(KEY_LOCKOUTS).remove(KEY_LOCKOUT_UNTIL)
            .commit()
    }

    fun lockoutRemainingMs(): Long {
        val until = prefs.getLong(KEY_LOCKOUT_UNTIL, 0L)
        val left = until - wallClock()
        // Kellon siirto taaksepäin ei saa pidentää estoa yli enimmäisajan.
        return if (left in 1..MAX_LOCKOUT_MS) left else 0L
    }

    fun verify(pin: String): PinResult {
        if (!isEnabled) return PinResult.Ok
        val wait = lockoutRemainingMs()
        if (wait > 0L) return PinResult.LockedOut(wait)

        val salt = decode(prefs.getString(KEY_SALT, null)) ?: return PinResult.Ok
        val stored = decode(prefs.getString(KEY_HASH, null)) ?: return PinResult.Ok
        val candidate = if (pin.length == PIN_LENGTH && pin.all { it.isDigit() }) hash(pin, salt) else ByteArray(0)

        if (MessageDigest.isEqual(stored, candidate)) {
            prefs.edit().putInt(KEY_FAILURES, 0).putInt(KEY_LOCKOUTS, 0)
                .putLong(KEY_LOCKOUT_UNTIL, 0L).commit()
            return PinResult.Ok
        }

        val failures = prefs.getInt(KEY_FAILURES, 0) + 1
        if (failures < MAX_ATTEMPTS) {
            prefs.edit().putInt(KEY_FAILURES, failures).commit()
            return PinResult.Wrong(MAX_ATTEMPTS - failures)
        }
        val lockouts = prefs.getInt(KEY_LOCKOUTS, 0) + 1
        val duration = lockoutDurationMs(lockouts)
        prefs.edit()
            .putInt(KEY_FAILURES, 0)
            .putInt(KEY_LOCKOUTS, lockouts)
            .putLong(KEY_LOCKOUT_UNTIL, wallClock() + duration)
            .commit()
        return PinResult.LockedOut(duration)
    }

    /** Kertakysymys "Käyttääkö tätä laitetta joku muu?" näytetään vain kerran. */
    var lockOfferShown: Boolean
        get() = prefs.getBoolean(KEY_OFFER_SHOWN, false)
        set(value) { prefs.edit().putBoolean(KEY_OFFER_SHOWN, value).apply() }

    companion object {
        const val PIN_LENGTH = 4
        const val MAX_ATTEMPTS = 5
        const val BASE_LOCKOUT_MS = 30_000L
        const val MAX_LOCKOUT_MS = 15 * 60_000L

        private const val PREFS = "aikapolku_caregiver_lock_v1"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_SALT = "pin_salt"
        private const val KEY_HASH = "pin_hash"
        private const val KEY_FAILURES = "failures"
        private const val KEY_LOCKOUTS = "lockouts"
        private const val KEY_LOCKOUT_UNTIL = "lockout_until_wall"
        private const val KEY_OFFER_SHOWN = "offer_shown"
        private const val ITERATIONS = 20_000

        /** 30 s, 60 s, 2 min, 4 min … enintään 15 min. */
        fun lockoutDurationMs(lockouts: Int): Long {
            val shift = (lockouts - 1).coerceIn(0, 10)
            return (BASE_LOCKOUT_MS shl shift).coerceAtMost(MAX_LOCKOUT_MS)
        }

        /** Palauttaa syyn, miksi PIN ei kelpaa, tai null jos kelpaa. */
        fun pinProblem(pin: String): String? = when {
            pin.length != PIN_LENGTH || !pin.all { it in '0'..'9' } -> "PIN-koodissa on 4 numeroa."
            pin.toSet().size == 1 -> "Liian helppo PIN-koodi (kaikki numerot samoja)."
            isSequence(pin) -> "Liian helppo PIN-koodi (numerot peräkkäin, esim. 1234)."
            else -> null
        }

        private fun isSequence(pin: String): Boolean {
            val d = pin.map { it - '0' }
            val diffs = d.zipWithNext { a, b -> b - a }.toSet()
            return diffs == setOf(1) || diffs == setOf(-1)
        }

        private fun hash(pin: String, salt: ByteArray): ByteArray =
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, 256))
                .encoded

        private fun encode(bytes: ByteArray) = Base64.getEncoder().encodeToString(bytes)
        private fun decode(text: String?): ByteArray? =
            text?.let { runCatching { Base64.getDecoder().decode(it) }.getOrNull() }
    }
}

/**
 * Ohjaajan avaus on muistissa ja voimassa kiinteät 2 minuuttia PIN-koodin syöttämisestä.
 * Kosketukset EIVÄT jatka avausta: muuten asiakkaan oma käyttö pitäisi ohjaajan
 * toiminnot auki loputtomiin (puhelintesti 1.11.2026). Avaus suljetaan myös aina,
 * kun sovellus siirtyy taustalle. Tila on Compose-tilaa, joten näkymät päivittyvät.
 */
internal object CaregiverSession {
    const val TIMEOUT_MS = 120_000L

    private val openUntilState = mutableLongStateOf(0L)
    val openUntil: Long get() = openUntilState.longValue

    fun isOpen(now: Long): Boolean = openUntilState.longValue > now

    /** Avaa ohjaajan toiminnot 2 minuutiksi. Kutsutaan vain oikean PIN-koodin jälkeen. */
    fun open(now: Long) { openUntilState.longValue = now + TIMEOUT_MS }

    /** Jäljellä oleva avausaika (0, kun lukossa). */
    fun remainingMs(now: Long): Long = (openUntilState.longValue - now).coerceAtLeast(0L)

    fun close() { openUntilState.longValue = 0L }

    // Sovelluksen oma järjestelmäikkuna (esim. valokuvan valinta) siirtää sovelluksen
    // hetkeksi taustalle. Se ei saa lukita ohjaajaa ulos kesken muokkauksen.
    @Volatile private var keepOnNextBackground = false

    /** Kutsutaan juuri ennen kuin ohjaaja avaa sovelluksesta järjestelmän valitsimen. */
    fun keepOpenForOwnPicker() { keepOnNextBackground = true }

    /** Sovellus siirtyi taustalle: lukitaan, ellei kyse ole omasta valitsimesta. */
    fun onAppBackground() {
        if (keepOnNextBackground) keepOnNextBackground = false else close()
    }
}
