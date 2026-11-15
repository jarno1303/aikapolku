package fi.aikapolku.app

import fi.aikapolku.app.data.LevelHint
import fi.aikapolku.app.data.LevelPreset
import fi.aikapolku.app.data.PromptSettings
import fi.aikapolku.app.data.RunPreferences
import fi.aikapolku.app.data.SupportKind
import fi.aikapolku.app.data.SupportLevel
import fi.aikapolku.app.data.SupportLevels
import fi.aikapolku.app.data.UsageKind
import fi.aikapolku.app.data.UsageLog
import fi.aikapolku.app.link.LinkInbox
import fi.aikapolku.app.link.LinkMessage
import fi.aikapolku.app.link.LinkOutbox
import fi.aikapolku.app.link.QueuedMessage
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Palaute 15.11.2026: käytön seuranta, tuen taso, viestijono ja En ymmärrä. Robolectric vain
 * org.json-toteutusta varten; testit eivät kirjoita Composen tilaa.
 */
@RunWith(RobolectricTestRunner::class)
class PalauteMuutoksetTest {
    private val today = LocalDate.of(2026, 11, 15)

    @Test
    fun `seuranta laskee paivittain ja sailyttaa 180 paivaa`() {
        var days = UsageLog.increment(emptyMap(), today, UsageKind.HELP)
        days = UsageLog.increment(days, today, UsageKind.HELP)
        days = UsageLog.increment(days, today.minusDays(1), UsageKind.BREAK)
        days = UsageLog.increment(days, today.minusDays(200), UsageKind.HELP)
        assertEquals(2, days.getValue(today)[UsageKind.HELP])
        val pruned = UsageLog.prune(days, today)
        assertFalse(pruned.containsKey(today.minusDays(200)))
        assertTrue(pruned.containsKey(today.minusDays(1)))
        assertEquals(mapOf(UsageKind.HELP to 2, UsageKind.BREAK to 1), UsageLog.total(pruned, today.minusDays(6), today))
        assertEquals(2, UsageLog.activeDays(pruned, today.minusDays(6), today))
        assertEquals(pruned, UsageLog.decode(UsageLog.encode(pruned)))
    }

    @Test
    fun `kooste on tunnuksin eika sisalla nimia`() {
        val days = UsageLog.increment(emptyMap(), today, UsageKind.PROGRAM_START)
        val csv = UsageLog.csv(listOf("A1" to days))
        val lines = csv.trim().lines()
        assertTrue(lines[0].startsWith("tunnus;paiva;program_start"))
        assertTrue(lines[1].startsWith("A1;2026-11-15;1;"))
        assertEquals(UsageKind.entries.size + 2, lines[1].split(';').size)
    }

    @Test
    fun `jokainen asiakkaan viesti kirjataan omana lajinaan`() {
        assertEquals(UsageKind.NOT_UNDERSTAND, UsageKind.of(SupportKind.NOT_UNDERSTAND))
        assertEquals(UsageKind.CHOICE_NONE, UsageKind.of(SupportKind.CHOICE_NONE))
        assertFalse(UsageKind.NOT_UNDERSTAND in UsageKind.REQUESTS)
        assertTrue(UsageKind.HELP in UsageKind.REQUESTS)
        assertFalse(UsageKind.PROGRAM_START in UsageKind.REQUESTS)
    }

    @Test
    fun `tuen taso vahentaa kehotuksia ja sailyttaa oman aanitteen`() {
        val before = PromptSettings(5, RunPreferences.AUDIO_RECORDED, RunPreferences.NEXT_CARD)
        assertEquals(RunPreferences.AUDIO_RECORDED, SupportLevels.settingsFor(SupportLevel.MUCH.preset, before).soonVoiceMode)
        val silentBefore = before.copy(soonVoiceMode = RunPreferences.AUDIO_SILENT)
        assertEquals(RunPreferences.AUDIO_TTS, SupportLevels.settingsFor(SupportLevel.MUCH.preset, silentBefore).soonVoiceMode)
        val independent = SupportLevels.settingsFor(SupportLevel.INDEPENDENT.preset, before)
        assertEquals(PromptSettings(0, RunPreferences.AUDIO_SILENT, RunPreferences.NEXT_TEXT), independent)
        // Tasot vähenevät järjestyksessä: varoitus ei koskaan kasva.
        SupportLevel.entries.zipWithNext().forEach { (a, b) -> assertTrue(b.preset.warnMinutes <= a.preset.warnMinutes) }
        assertNull(SupportLevel.INDEPENDENT.next)
        assertEquals(SupportLevel.MODERATE, SupportLevel.MUCH.next)
    }

    @Test
    fun `palautus ja historia sailyvat tallennuksessa`() {
        val s = PromptSettings(2, RunPreferences.AUDIO_TTS, RunPreferences.NEXT_TEXT)
        assertEquals(s to SupportLevel.LIGHT, SupportLevels.decodePrevious(SupportLevels.encodePrevious(s, SupportLevel.LIGHT)))
        assertEquals(s to null, SupportLevels.decodePrevious(SupportLevels.encodePrevious(s, null)))
        val h = listOf(fi.aikapolku.app.data.LevelChange(today, SupportLevel.MODERATE))
        assertEquals(h, SupportLevels.decodeHistory(SupportLevels.encodeHistory(h)))
    }

    @Test
    fun `huomio seurannasta ei reagoi pieniin maariin`() {
        assertEquals(LevelHint.CONSIDER_NEXT, SupportLevels.hint(recent = 3, previous = 10, daysSinceChange = 30, hasNext = true))
        assertEquals(LevelHint.NONE, SupportLevels.hint(recent = 1, previous = 3, daysSinceChange = 30, hasNext = true))
        assertEquals(LevelHint.NONE, SupportLevels.hint(recent = 3, previous = 10, daysSinceChange = 5, hasNext = true))
        assertEquals(LevelHint.NONE, SupportLevels.hint(recent = 3, previous = 10, daysSinceChange = 30, hasNext = false))
        assertEquals(LevelHint.CONSIDER_BACK, SupportLevels.hint(recent = 9, previous = 4, daysSinceChange = 10, hasNext = true))
        assertEquals(LevelHint.NONE, SupportLevels.hint(recent = 9, previous = 8, daysSinceChange = 10, hasNext = true))
    }

    @Test
    fun `jono pudottaa vanhat ja valmiit viestit`() {
        val now = 10_000_000L
        val fresh = QueuedMessage("1", "help", "Tarvitsen apua", "Aino", now - 60_000L, listOf("p1"))
        val old = fresh.copy(id = "2", at = now - LinkOutbox.MAX_AGE_MS - 1)
        val done = fresh.copy(id = "3", peers = emptyList())
        assertEquals(listOf(fresh), LinkOutbox.prune(listOf(fresh, old, done), now))
        assertEquals(listOf(fresh), LinkOutbox.decode(LinkOutbox.encode(listOf(fresh))))
        // Vastaanottaja hylkää yli 30 min vanhat: jono ei saa pitää viestiä niin kauan.
        assertTrue(LinkOutbox.MAX_AGE_MS < 30L * 60 * 1000)
    }

    @Test
    fun `uudelleen lahetetty viesti ei tule kahdesti`() {
        val sentAt = 5_000L
        val stored = LinkMessage("help", "Tarvitsen apua", "Aino", 9_000L, "Aino", sentAt = sentAt)
        assertTrue(LinkInbox.isDuplicate(listOf(stored), LinkMessage("help", "Tarvitsen apua", "Aino", sentAt, "Aino")))
        assertFalse(LinkInbox.isDuplicate(listOf(stored), LinkMessage("help", "Tarvitsen apua", "Aino", sentAt + 1, "Aino")))
        assertEquals(listOf(stored), LinkInbox.decode(LinkInbox.encode(listOf(stored))))
    }
}
