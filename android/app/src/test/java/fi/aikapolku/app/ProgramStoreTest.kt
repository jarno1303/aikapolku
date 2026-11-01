package fi.aikapolku.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Päiväohjelmien tallennus (AP16Store).
 * Tärkein yksittäinen asia käyttäjälle: tehdyt ohjelmat eivät saa kadota.
 */
@RunWith(RobolectricTestRunner::class)
class ProgramStoreTest {

    private lateinit var context: Context
    private lateinit var store: AP16Store

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        store = AP16Store(context)
    }

    private fun rawPrefs() =
        context.getSharedPreferences("aikapolku_programs_v1", Context.MODE_PRIVATE)

    private fun step(id: String, name: String = "Vaihe $id") = AP16Step(
        id = id,
        name = name,
        photo = "builtin:dress",
        description = "Kuvaus $id",
        mode = "duration",
        minutes = 12,
        clock = "09:30",
        voicePath = "/data/voice_$id.m4a",
        autoPlayVoice = true,
        subSteps = listOf("Sukat", "Housut")
    )

    @Test
    fun `tyhja tallennus palauttaa tyhjan listan`() {
        assertEquals(emptyList<AP16Program>(), store.load())
    }

    @Test
    fun `ohjelma sailyy tallennuksessa kaikkine kenttineen`() {
        val program = AP16Program(
            "p1", "Aamutoimet äöå", listOf(step("a", "Pukeutuminen"), step("b", "Hampaiden pesu"))
        )
        store.save(program)

        assertEquals(listOf(program), store.load())
    }

    @Test
    fun `ohjelma sailyy uudessa store-instanssissa kuten sovelluksen uudelleenkaynnistyksessa`() {
        val program = AP16Program("p1", "Koulupäivä", listOf(step("a")))
        store.save(program)

        assertEquals(listOf(program), AP16Store(context).load())
    }

    @Test
    fun `sama id korvaa vanhan ohjelman eika luo kopiota`() {
        store.save(AP16Program("p1", "Vanha nimi", listOf(step("a"))))
        store.save(AP16Program("p1", "Uusi nimi", listOf(step("a"), step("b"))))

        val loaded = store.load()
        assertEquals(1, loaded.size)
        assertEquals("Uusi nimi", loaded[0].name)
        assertEquals(2, loaded[0].steps.size)
    }

    @Test
    fun `useampi ohjelma sailyttaa jarjestyksen`() {
        store.save(AP16Program("p1", "Yksi", emptyList()))
        store.save(AP16Program("p2", "Kaksi", emptyList()))
        store.save(AP16Program("p3", "Kolme", emptyList()))

        assertEquals(listOf("p1", "p2", "p3"), store.load().map { it.id })
    }

    @Test
    fun `poisto poistaa vain valitun ohjelman`() {
        store.save(AP16Program("p1", "Yksi", emptyList()))
        store.save(AP16Program("p2", "Kaksi", emptyList()))

        store.delete("p1")

        assertEquals(listOf("p2"), store.load().map { it.id })
    }

    @Test
    fun `tuntemattoman id-n poisto ei muuta mitaan`() {
        store.save(AP16Program("p1", "Yksi", emptyList()))

        store.delete("ei-ole")

        assertEquals(listOf("p1"), store.load().map { it.id })
    }

    @Test
    fun `vanha tallennus ilman osavaiheita ja uusia kenttia latautuu oletusarvoin`() {
        // Muoto ennen P0-21:tä: ei subSteps-, voicePath- eikä autoPlayVoice-kenttiä.
        rawPrefs().edit().putString(
            "programs",
            """[{"id":"vanha","name":"Vanha ohjelma","steps":[
                {"id":"s1","name":"Ruokailu","photo":"builtin:lunch","description":""}
            ]}]"""
        ).commit()

        val loaded = AP16Store(context).load()

        assertEquals(1, loaded.size)
        val s = loaded[0].steps.single()
        assertEquals("Ruokailu", s.name)
        assertEquals("manual", s.mode)
        assertEquals(5, s.minutes)
        assertEquals("08:00", s.clock)
        assertEquals("", s.voicePath)
        assertEquals(false, s.autoPlayVoice)
        assertEquals(emptyList<String>(), s.subSteps)
    }

    @Test
    fun `rikkinainen tallennus ei kaada sovellusta`() {
        rawPrefs().edit().putString("programs", "{ tämä ei ole JSONia").commit()

        assertEquals(emptyList<AP16Program>(), AP16Store(context).load())
    }

    @Test
    fun `rikkinainen vaihe ohitetaan ja muut vaiheet sailyvat`() {
        rawPrefs().edit().putString(
            "programs",
            """[{"id":"p","name":"P","steps":["ei objekti",{"id":"ok","name":"Lepo"}]}, 42]"""
        ).commit()

        val loaded = AP16Store(context).load()

        assertEquals(1, loaded.size)
        assertEquals(listOf("ok"), loaded[0].steps.map { it.id })
    }

    @Test
    fun `osavaiheita tallennetaan enintaan 16`() {
        val many = (1..20).map { "Osavaihe $it" }
        store.save(AP16Program("p", "P", listOf(step("a").copy(subSteps = many))))

        assertEquals(many.take(16), store.load()[0].steps[0].subSteps)
    }

    @Test
    fun `osavaiheet siistitaan ja tyhjat poistetaan ja pitkat lyhennetaan 72 merkkiin`() {
        val long = "x".repeat(100)
        store.save(
            AP16Program("p", "P", listOf(step("a").copy(subSteps = listOf("  Sukat  ", "", "   ", long))))
        )

        val subSteps = store.load()[0].steps[0].subSteps

        assertEquals(listOf("Sukat", "x".repeat(72)), subSteps)
        assertTrue(subSteps.none { it.isBlank() })
    }

    @Test
    fun `P0-23 aikatapa aloitusaika ja kiintea aika sailyvat`() {
        val program = AP16Program(
            "p23", "Koulupäivä",
            listOf(step("a"), step("b").copy(fixedClock = true, clock = "11:00")),
            timeMode = ProgramTiming.CLOCK,
            startClock = "07:45"
        )
        store.save(program)
        assertEquals(listOf(program), AP16Store(context).load())
    }

    @Test
    fun `vanha tallennus ilman uusia kenttia latautuu oletusarvoilla`() {
        rawPrefs().edit().putString(
            "programs",
            """[{"id":"v","name":"Vanha","steps":[{"id":"s","name":"Aamupala","mode":"clock","clock":"07:15","minutes":15}]}]"""
        ).commit()
        val loaded = store.load().single()
        assertEquals("", loaded.timeMode)
        assertEquals(ProgramTiming.DEFAULT_START, loaded.startClock)
        assertEquals(false, loaded.steps.single().fixedClock)
        assertEquals("07:15", ProgramTiming.apply(ProgramTiming.infer(loaded)).steps.single().clock)
    }
}
