package fi.aikapolku.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import fi.aikapolku.app.backup.BackupExporter
import fi.aikapolku.app.backup.BackupImporter
import fi.aikapolku.app.backup.BackupScope
import fi.aikapolku.app.backup.ImportTarget
import fi.aikapolku.app.data.AckStore
import fi.aikapolku.app.data.CardItem
import fi.aikapolku.app.data.CardStore
import fi.aikapolku.app.data.DayProgram
import fi.aikapolku.app.data.EventKind
import fi.aikapolku.app.data.ProfileStore
import fi.aikapolku.app.data.ProgramStep
import fi.aikapolku.app.data.ProgramStore
import fi.aikapolku.app.data.WeekEvent
import fi.aikapolku.app.data.WeekPlan
import fi.aikapolku.app.data.WeekStore
import java.io.File
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Vienti ja tuonti kokonaisuutena: kuvat kulkevat mukana, profiili syntyy, mitään ei korvata. */
@RunWith(RobolectricTestRunner::class)
class BackupRoundTripTest {
    private lateinit var context: Context
    private lateinit var photo: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        ProfileStore.update(context, ProfileStore.current(context).copy(name = "Mikko"))
        photo = File(context.filesDir, "activity_images/card_1.image").apply {
            parentFile?.mkdirs()
            writeBytes("kuva".toByteArray())
        }
        ProgramStore(context).save(
            DayProgram("p1", "Aamu", listOf(ProgramStep("s1", "Uinti", photo.absolutePath, "")))
        )
        CardStore(context).upsert(CardItem("c1", "Uinti", "Halli", photo.absolutePath))
        WeekStore(context).save(WeekPlan(listOf(WeekEvent("e1", 1, 0, "08:00", EventKind.PROGRAM, programId = "p1"))))
        AckStore(context).ack(LocalDate.now(), "e1")
    }

    @Test
    fun `profiili tuodaan uutena profiilina kuvineen`() {
        val file = BackupExporter.export(context, BackupScope.PROFILE, emptySet(), anonymize = false, password = "salasana1")
        val preview = BackupImporter.read(file.readBytes(), "salasana1")
        assertEquals("Mikko", preview.profiles.single().name)
        assertEquals(1, preview.profiles.single().programs)

        val result = BackupImporter.apply(context, preview, ImportTarget.NEW_PROFILE, replaceWeek = false)
        assertEquals(1, result.profiles)
        val imported = ProfileStore.list(context).last()
        assertEquals("Mikko (2)", imported.name)
        val program = ProgramStore(context, imported.id).load().single()
        assertEquals("Aamu", program.name)
        val newPhoto = File(program.steps.single().photo)
        assertNotEquals(photo.absolutePath, newPhoto.absolutePath)
        assertTrue(newPhoto.isFile)
        assertEquals(1, WeekStore(context, imported.id).load().events.size)
        // Kuittauksia ei viedä.
        assertEquals(null, AckStore(context, imported.id).ackTime(LocalDate.now(), "e1"))
    }

    @Test
    fun `nykyiseen profiiliin samanlaiset ohitetaan`() {
        val file = BackupExporter.export(context, BackupScope.PROFILE, emptySet(), anonymize = false, password = null)
        val preview = BackupImporter.read(file.readBytes(), null)
        val result = BackupImporter.apply(context, preview, ImportTarget.CURRENT_PROFILE, replaceWeek = false)
        assertEquals(0, result.programs)
        assertEquals(2, result.skipped)
        assertEquals(1, ProgramStore(context).load().size)
    }

    @Test
    fun `ohjelmapohja ilman henkilotietoja ei sisalla omaa kuvaa`() {
        val file = BackupExporter.export(context, BackupScope.PROGRAMS, setOf("p1"), anonymize = true, password = null)
        val preview = BackupImporter.read(file.readBytes(), null)
        assertTrue(preview.anonymized)
        assertTrue(preview.media.isEmpty())
        assertEquals(1, preview.programCount)
    }
}
