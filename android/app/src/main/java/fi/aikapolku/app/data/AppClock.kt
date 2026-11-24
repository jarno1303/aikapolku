package fi.aikapolku.app.data

import java.time.LocalDateTime

/**
 * Asiakkaan päivän kellonaika. Testit voivat lukita ajan ([fixed]), jotta tulos ei riipu
 * siitä, mihin aikaan testi ajetaan (R11-havainto 24.11.2026: etusivun testi meni aiemmin
 * läpi vain iltapäivisin). Sovelluksessa aina laitteen kello.
 */
internal object AppClock {
    @Volatile var fixed: LocalDateTime? = null

    fun now(): LocalDateTime = fixed ?: LocalDateTime.now()
}
