package fi.aikapolku.app

import android.content.Context
import android.content.SharedPreferences
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.WindowManager
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.AnnotatedString
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import fi.aikapolku.app.caregiver.RoleSession
import fi.aikapolku.app.cards.CardArt
import fi.aikapolku.app.data.ActivitySession
import fi.aikapolku.app.data.CardStore
import fi.aikapolku.app.data.ChangeStore
import fi.aikapolku.app.data.ChangeType
import fi.aikapolku.app.data.ChoiceAlt
import fi.aikapolku.app.data.ChoicePrefs
import fi.aikapolku.app.data.DayProgram
import fi.aikapolku.app.data.EventChange
import fi.aikapolku.app.data.EventKind
import fi.aikapolku.app.data.FirstThenSelection
import fi.aikapolku.app.data.GroupStore
import fi.aikapolku.app.data.Leave
import fi.aikapolku.app.data.LeaveReason
import fi.aikapolku.app.data.MemberTime
import fi.aikapolku.app.data.OwnState
import fi.aikapolku.app.data.ProfileStore
import fi.aikapolku.app.data.ProgramStep
import fi.aikapolku.app.data.ProgramStore
import fi.aikapolku.app.data.StepMode
import fi.aikapolku.app.data.SupportLevel
import fi.aikapolku.app.data.SupportLevels
import fi.aikapolku.app.data.TimeDisplayPrefs
import fi.aikapolku.app.data.TimeMode
import fi.aikapolku.app.data.UnitSetup
import fi.aikapolku.app.data.UnitSpec
import fi.aikapolku.app.data.SubUnitSpec
import fi.aikapolku.app.data.ActivitySpec
import fi.aikapolku.app.data.UsageKind
import fi.aikapolku.app.data.UsageLog
import fi.aikapolku.app.data.Waiting
import fi.aikapolku.app.data.WeekEvent
import fi.aikapolku.app.data.WeekPlan
import fi.aikapolku.app.data.WeekSchedule
import fi.aikapolku.app.data.WeekStore
import fi.aikapolku.app.link.LinkStore
import fi.aikapolku.app.link.Peer
import fi.aikapolku.app.timer.BackgroundTimer
import fi.aikapolku.app.week.EventLaunch
import fi.aikapolku.app.week.EventLaunchRequest
import fi.aikapolku.app.week.EventReminders
import fi.aikapolku.app.week.GroupSession
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters
import org.json.JSONArray
import org.json.JSONObject
import android.util.Log
import androidx.test.espresso.IdlingPolicies
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Kuvakaappaukset käyttöoppaaseen ja esitykseen (tools/materiaalit/kuvat.json).
 *
 * 1. Puhelimen sovellustiedot otetaan talteen ja tyhjennetään.
 * 2. Luodaan kuvitteellinen esimerkki: Villa Metsämaja (Omena ja Päärynä), 12 asukasta,
 *    Päivätoiminta Tammi ja Työpaja Verstas; kuvattava asiakas Aino.
 * 3. Jokainen näkymä avataan omassa käynnistyksessään ja kuvataan (screencap) kansioon
 *    /sdcard/Pictures/AikaPolku-kuvat. Epäonnistunut kuva tallentuu nimellä <id>-VIRHE.png.
 * 4. Lopuksi alkuperäiset tiedot palautetaan. Jos ajo keskeytyi, talteenotto palautetaan
 *    seuraavan ajon alussa.
 *
 * Aja: aja-aikapolku.ps1 -branch <haara> -kuvat (hakee kuvat PC:lle).
 */
@RunWith(AndroidJUnit4::class)
class KuvakaappauksetDeviceTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val ctx: Context get() = instrumentation.targetContext
    private fun t(id: Int, vararg args: Any) = ctx.getString(id, *args)

    private val ok = mutableListOf<String>()
    private var rotation: Pair<String, String>? = null
    private val failed = mutableListOf<String>()

    // --- Kuvaus ja komennot ---

    private fun shell(command: String): String {
        val pfd = instrumentation.uiAutomation.executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(pfd).use { String(it.readBytes()) }
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        Thread.sleep(900)
        shell("screencap -p $DIR/$name.png")
    }

    /**
     * Yksi kuva: virhe ei kaada ajoa, vaan ruutu tallentuu nimellä <id>-VIRHE.
     * [screen] = false: [block] tallentaa kuvan itse (esim. widget piirrettynä).
     */
    private fun shot(id: String, screen: Boolean = true, block: () -> Unit) {
        Log.i(TAG, "alku $id")
        val started = SystemClock.elapsedRealtime()
        try {
            block()
            if (screen) capture(id)
            ok += id
            Log.i(TAG, "ok $id ${(SystemClock.elapsedRealtime() - started) / 1000} s")
        } catch (e: Throwable) {
            val reason = "${e.javaClass.simpleName}: ${e.message?.lineSequence()?.firstOrNull()?.take(160)}"
            failed += "$id: $reason"
            Log.w(TAG, "virhe $id ${(SystemClock.elapsedRealtime() - started) / 1000} s $reason")
            // Kuva virheestä ilman Composen odotusta (näkymä ei ehkä rauhoitu koskaan).
            runCatching { shell("screencap -p $DIR/$id-VIRHE.png") }
        }
    }

    /** Uusi käynnistys roolissa (tila alusta, ettei edellinen kuva vaikuta). */
    private fun launch(caregiver: Boolean, block: () -> Unit) {
        Log.i(TAG, "kaynnistys ${if (caregiver) "ohjaaja" else "asiakas"}")
        if (caregiver) RoleSession.toCaregiver() else RoleSession.toClient()
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity {
                    it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    it.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                }
                compose.waitForIdle()
                Thread.sleep(1_000)
                compose.waitForIdle()
                block()
            }
        } catch (e: Throwable) {
            failed += "käynnistys: ${e.javaClass.simpleName}: ${e.message?.lineSequence()?.firstOrNull()?.take(160)}"
        }
    }

    // --- Näkymän käyttö ---

    private fun nodes(m: SemanticsMatcher) = compose.onAllNodes(m)
    private fun shown(m: SemanticsMatcher) = nodes(m).fetchSemanticsNodes().isNotEmpty()

    private fun waitFor(m: SemanticsMatcher, timeoutMs: Long = 10_000L) {
        compose.waitUntil(timeoutMs) { shown(m) }
    }

    private fun waitText(text: String, timeoutMs: Long = 10_000L, substring: Boolean = false) =
        waitFor(hasText(text, substring = substring), timeoutMs)

    private fun clickable(text: String, index: Int = 0, substring: Boolean = false): SemanticsNodeInteraction {
        val m = hasText(text, substring = substring) and hasClickAction()
        waitFor(m)
        return nodes(m)[index]
    }

    private fun click(text: String, index: Int = 0, substring: Boolean = false) {
        val node = clickable(text, index, substring)
        runCatching { node.performScrollTo() }
        node.performClick()
        compose.waitForIdle()
        Thread.sleep(400)
    }

    private fun clickDescription(description: String) {
        val m = hasContentDescription(description) and hasClickAction()
        waitFor(m)
        nodes(m).onFirst().performClick()
        compose.waitForIdle()
        Thread.sleep(400)
    }

    private fun setText(node: SemanticsNodeInteraction, value: String) {
        node.performSemanticsAction(SemanticsActions.SetText) { it(AnnotatedString(value)) }
        compose.waitForIdle()
    }

    /** Vierittää niin, että [m] on ruudun yläosassa (asetusosio, kortti). */
    private fun bringToTop(m: SemanticsMatcher) {
        waitFor(m)
        val node = nodes(m).onFirst()
        runCatching { node.performScrollTo() }
        compose.waitForIdle()
        val scrollers = nodes(hasScrollAction()).fetchSemanticsNodes()
        val scroller = scrollers.maxByOrNull { it.boundsInRoot.height } ?: return
        val top = node.fetchSemanticsNode().boundsInRoot.top
        val dy = top - scroller.boundsInRoot.top - 24f * ctx.resources.displayMetrics.density
        if (dy > 1f) {
            nodes(SemanticsMatcher("vierityssäiliö") { it.id == scroller.id }).onFirst()
                .performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, dy) }
        }
        compose.waitForIdle()
    }

    private fun pause(ms: Long) {
        Thread.sleep(ms)
        compose.waitForIdle()
    }

    private fun waitReal(timeoutMs: Long, condition: () -> Boolean) {
        val end = SystemClock.elapsedRealtime() + timeoutMs
        while (!condition()) {
            if (SystemClock.elapsedRealtime() > end) throw AssertionError("Odotus ylittyi (${timeoutMs / 1000} s)")
            Thread.sleep(500)
            // Ohjelman ajastin laskee laitteen kellolla; Composen testikello siirretään perässä.
            compose.mainClock.advanceTimeBy(500L)
            compose.waitForIdle()
        }
    }

    // --- Ajo ---

    @Test
    fun kuvakaappaukset_oppaaseen_ja_esitykseen() {
        // Näkymä, joka ei rauhoitu, ei saa jumittaa koko ajoa: odotus enintään 20 s, sitten virhekuva.
        IdlingPolicies.setMasterPolicyTimeout(20, TimeUnit.SECONDS)
        IdlingPolicies.setIdlingResourceTimeout(20, TimeUnit.SECONDS)
        Log.i(TAG, "ajo alkaa")
        PrefsSnapshot.restoreLeftover(ctx)
        PrefsSnapshot.save(ctx)
        try {
            shell("rm -rf $DIR")
            shell("mkdir -p $DIR")
            rotation = shell("settings get system accelerometer_rotation").trim() to shell("settings get system user_rotation").trim()
            shell("settings put system accelerometer_rotation 0")
            shell("settings put system user_rotation 0")
            // Tilapalkin korkeus kuvan rajausta varten (materiaalien generaattori rajaa sen pois).
            val res = ctx.resources
            val id = res.getIdentifier("status_bar_height", "dimen", "android")
            if (id > 0) shell("touch $DIR/tilapalkki-${res.getDimensionPixelSize(id)}.txt")
            demoStatusBar(true)
            Demo.create(ctx)
            caregiverShots()
            newSettingShots()
            clientShots()
        } finally {
            runCatching { demoStatusBar(false) }
            rotation?.let { (auto, user) ->
                if (auto == "0" || auto == "1") shell("settings put system accelerometer_rotation $auto")
                if (user.toIntOrNull() != null) shell("settings put system user_rotation $user")
            }
            RoleSession.toClient()
            Thread.sleep(1_500)
            PrefsSnapshot.restore(ctx)
            EventReminders.reschedule(ctx)
        }
        val report = "Kuvat OK ${ok.size}: ${ok.joinToString(", ")}" +
            if (failed.isEmpty()) "" else "\nEPÄONNISTUI ${failed.size}:\n" + failed.joinToString("\n")
        Log.i(TAG, "ajo valmis: ok ${ok.size}, virheet ${failed.size}")
        failed.forEach { Log.w(TAG, "EPÄONNISTUI $it") }
        println(report)
        assertTrue(report, failed.isEmpty())
    }

    private fun demoStatusBar(on: Boolean) {
        if (!on) {
            shell("am broadcast -a com.android.systemui.demo -e command exit")
            return
        }
        val now = java.time.LocalTime.now()
        val hhmm = String.format("%02d%02d", now.hour, now.minute)
        shell("settings put global sysui_demo_allowed 1")
        shell("am broadcast -a com.android.systemui.demo -e command enter")
        shell("am broadcast -a com.android.systemui.demo -e command clock -e hhmm $hhmm")
        shell("am broadcast -a com.android.systemui.demo -e command battery -e level 100 -e plugged false")
        shell("am broadcast -a com.android.systemui.demo -e command network -e wifi show -e level 4")
        shell("am broadcast -a com.android.systemui.demo -e command network -e mobile show -e datatype none -e level 4")
        shell("am broadcast -a com.android.systemui.demo -e command notifications -e visible false")
    }

    private fun caregiverShots() {
        // Etusivu, Tänään yksikössä ja loma.
        launch(caregiver = true) {
            shot("o01-ohjaajan-etusivu") { waitText(t(R.string.caregiver_home_title)) }
            shot("o10-tanaan-yksikossa") { bringToTop(hasText(t(R.string.unit_today_title), substring = true)) }
            shot("o11-loma") { click(t(R.string.leave_add)) }
        }
        // Yksiköt ja ryhmät, toiminnan oma aika.
        launch(caregiver = true) {
            shot("o02-yksikot-ryhmat") {
                click(t(R.string.groups_title))
                waitText(Demo.UNIT)
            }
            shot("o09-toiminta-oma-aika") { click(Demo.DAY_ACTIVITY) }
        }
        // Ohjattu aloitus vaihe vaiheelta (tallentamatta: esimerkki on jo luotu).
        launch(caregiver = true) { wizardShots() }
        // Viikko ja tapahtuma.
        launch(caregiver = true) {
            shot("o12-asiakkaan-viikko") {
                click(t(R.string.home_week))
                waitText(t(R.string.card_reading))
            }
            shot("o13-tapahtuma") { click(t(R.string.card_reading)) }
            shot("o30-luokka-tapahtumassa") { bringToTop(hasText(t(R.string.category_label))) }
        }
        // Päiväohjelman editori ja valinta.
        launch(caregiver = true) {
            shot("o14-paivaohjelma-editori") {
                click(t(R.string.home_programs))
                clickDescription(t(R.string.muut_toiminnot) + ": " + Demo.PROGRAM)
                click(t(R.string.edit))
                waitText(t(R.string.card_wash_hands))
            }
            shot("o15-valinta-editori") { bringToTop(hasText(t(R.string.choice_or, t(R.string.card_music)))) }
        }
        // Ensin–Sitten, ajastin ohjaajalla ja asiakkaalle annettuna.
        BackgroundTimer.stop(ctx, "timer", 300_000L)
        launch(caregiver = true) {
            shot("o16-ensin-sitten") {
                click(t(R.string.ensin_sitten))
                waitText(t(R.string.card_homework))
            }
            shot("o17-ajastin-ohjaaja") {
                click(t(R.string.siirry_kellotauluun))
                waitText(t(R.string.timer_hand_to_client))
            }
            shot("a09-ajastin") {
                click(t(R.string.timer_hand_to_client))
                pause(1_500)
                // Asiakas aloittaa valmistellun toiminnan NYT-kortista (jos kortti odottaa Aloita-painallusta).
                runCatching { click(t(R.string.today_start)) }
                pause(2_500)
            }
        }
        BackgroundTimer.stop(ctx, "timer", 300_000L)
        launch(caregiver = true) {
            shot("o18-tee-muutos") { click(t(R.string.quick_title)) }
        }
        launch(caregiver = true) {
            shot("o19-odotetaan") { click(t(R.string.wait_start_title)) }
        }
        // Asetukset: kukin osio auki ja ruudun yläreunassa.
        listOf(
            "o20-asetukset-nakyma" to R.string.settings_home,
            "o21-asetukset-viestit" to R.string.settings_messages,
            "o22-asetukset-aika" to R.string.settings_time,
            "o26-tuen-taso" to R.string.support_level_title,
            "o23-lukitus" to R.string.ohjaajan_lukitus,
            "o24-yhteys" to R.string.link_settings_title,
            "o25-vienti" to R.string.backup_title,
            "o27-kayton-seuranta" to R.string.usage_title,
            "o31-luokat-ja-varit" to R.string.category_names_title,
        ).forEach { (id, title) ->
            launch(caregiver = true) {
                shot(id) {
                    click(t(R.string.home_settings))
                    click(t(title))
                    bringToTop(hasText(t(title)) and hasClickAction())
                }
            }
        }
    }

    /** Asukkaan näkymäasetuksista värikoodaus, widget ja kello korteissa. */
    private fun newSettingShots() {
        launch(caregiver = true) {
            shot("o32-asetukset-varit-kello-widget") {
                click(t(R.string.home_settings))
                click(t(R.string.settings_home))
                bringToTop(hasText(t(R.string.category_colors_title)))
            }
        }
    }

    /**
     * Kotinäytön widget piirrettynä (testi ei pääse puhelimen kotinäytölle): sama RemoteViews kuin
     * kotinäytöllä, vaaleaa taustaa vasten. Kuva ei sisällä tilapalkkia (ei rajata).
     */
    private fun widgetShot(id: String, wide: Boolean) = shot(id, screen = false) {
        val content = fi.aikapolku.app.widget.WidgetDay.compute(ctx)
        val views = fi.aikapolku.app.widget.WidgetUpdater.views(ctx, content, wide)
        val d = ctx.resources.displayMetrics.density
        val w = ((if (wide) 340 else 170) * d).toInt()
        val h = (170 * d).toInt()
        val pad = (20 * d).toInt()
        var out: android.graphics.Bitmap? = null
        instrumentation.runOnMainSync {
            val frame = android.widget.FrameLayout(ctx)
            val view = views.apply(ctx, frame)
            frame.addView(view, android.widget.FrameLayout.LayoutParams(w, h))
            val spec = { size: Int -> android.view.View.MeasureSpec.makeMeasureSpec(size, android.view.View.MeasureSpec.EXACTLY) }
            frame.measure(spec(w), spec(h))
            frame.layout(0, 0, w, h)
            val bitmap = android.graphics.Bitmap.createBitmap(w + 2 * pad, h + 2 * pad, android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            canvas.drawColor(0xFFCFE3DF.toInt())
            canvas.translate(pad.toFloat(), pad.toFloat())
            frame.draw(canvas)
            out = bitmap
        }
        // Sovellus ei kirjoita kuvakansioon suoraan: oma ulkoinen kansio, josta komentorivi kopioi.
        val file = File(ctx.getExternalFilesDir(null), "$id.png")
        file.outputStream().use { out!!.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        shell("cp ${file.absolutePath} $DIR/$id.png")
        file.delete()
    }

    private fun wizardShots() {
        val next = t(R.string.wizard_next)
        shot("o03-ohjattu-1-yksikko") {
            click(t(R.string.groups_title))
            click(t(R.string.wizard_open))
            waitText(t(R.string.wizard_unit_title))
            setText(nodes(hasSetTextAction())[0], Demo.UNIT)
            click(t(R.string.wizard_add_sub_unit))
            click(t(R.string.wizard_add_sub_unit))
            setText(nodes(hasSetTextAction())[1], Demo.SUB_A)
            setText(nodes(hasSetTextAction())[2], Demo.SUB_B)
            bringToTop(hasText(t(R.string.wizard_unit_title)))
        }
        shot("o04-ohjattu-2-asukkaat") {
            click(next)
            waitText(t(R.string.wizard_residents_title))
            // Olemassa olevat asiakkaat valitaan alayksikön kortista (0 = Omena, 1 = Päärynä).
            Demo.RESIDENTS_A.forEach { click(it, index = 0) }
            Demo.RESIDENTS_B.forEach { click(it, index = 1) }
            bringToTop(hasText(t(R.string.wizard_residents_title)))
        }
        shot("o05-ohjattu-3-ruokailut") {
            click(next)
            waitText(t(R.string.wizard_meals_title))
            bringToTop(hasText(t(R.string.wizard_meals_title)))
        }
        shot("o06-ohjattu-4-toiminnat") {
            click(next)
            waitText(t(R.string.wizard_activities_title))
            click("+ " + t(R.string.wizard_day_activity))
            click("+ " + t(R.string.wizard_workshop))
            setText(nodes(hasSetTextAction() and hasText(t(R.string.wizard_day_activity)))[0], Demo.DAY_ACTIVITY)
            setText(nodes(hasSetTextAction() and hasText(t(R.string.wizard_workshop)))[0], Demo.WORKSHOP)
            Demo.DAY_MEMBERS.forEach { click(it, index = 0) }
            Demo.WORKSHOP_MEMBERS.forEach { click(it, index = 1) }
            bringToTop(hasText(t(R.string.wizard_activities_title)))
        }
        shot("o07-ohjattu-5-tuki") {
            click(next)
            waitText(t(R.string.wizard_support_title))
        }
        shot("o28-ohjattu-yhteiset") {
            click(next)
            waitText(t(R.string.wizard_common_title))
        }
        shot("o29-ohjattu-rutiinit") {
            click(next)
            waitText(t(R.string.wizard_routines_title))
        }
        shot("o08-ohjattu-6-esikatselu") {
            click(next)
            waitText(t(R.string.wizard_preview_title))
            click(Demo.DAY_MEMBERS.first())
        }
    }

    private fun clientShots() {
        // Ennen päiväohjelman läpikäyntiä, jotta Nyt on vielä päiväohjelma.
        // Kotinäytön widget (värit ja asukkaan asetukset kuten sovelluksessa).
        widgetShot("w01-widget-levea", wide = true)
        widgetShot("w02-widget-pieni", wide = false)
        // Kello korteissa: Nyt-kortti kellotaululla ja numeroilla (asetus vain tätä kuvaa varten).
        fi.aikapolku.app.data.CardClockPrefs.setMode(ctx, fi.aikapolku.app.data.CardClockMode.BOTH)
        launch(caregiver = false) {
            shot("a12-kello-kortissa") { waitText(Demo.PROGRAM) }
        }
        fi.aikapolku.app.data.CardClockPrefs.setMode(ctx, fi.aikapolku.app.data.CardClockMode.OFF)
        // Päiväohjelma alusta loppuun: tehtävä, tauko, oma tila, En ymmärrä, ennakkovaroitus, valinta, valmis.
        launch(caregiver = false) {
            shot("a01-nyt-kortti") { waitText(Demo.PROGRAM) }
            shot("a02-paivaohjelma") {
                click(t(R.string.today_start))
                waitText(t(R.string.program_start_count, 4))
                click(t(R.string.today_start))
                waitText(t(R.string.card_wash_hands))
                pause(1_600)
            }
            shot("a04-tauko") {
                click(t(R.string.support_break))
                waitText(t(R.string.own_state_open))
            }
            shot("a06-oma-tila") {
                click(t(R.string.own_state_open))
                waitText(t(R.string.own_state_question_faces))
            }
            shot("a11-en-ymmarra") {
                click(t(R.string.own_state_skip))
                click(t(R.string.support_continue))
                click(t(R.string.support_help))
                click(t(R.string.support_not_understand_button))
                waitText(t(R.string.support_not_understand))
            }
            shot("a03-ennakkovaroitus") {
                click(t(R.string.support_continue))
                pause(1_600)
                click(t(R.string.run_done_button))
                waitText(t(R.string.card_snack))
                // Välipala 3 min, ennakkovaroitus 2 min ennen: kortti näkyy noin minuutin kuluttua.
                waitReal(100_000L) { shown(hasText(t(R.string.transition_soon_title), substring = true)) }
                pause(1_000)
            }
            shot("a05-valinta") {
                waitReal(150_000L) {
                    shown(hasText(t(R.string.time_ended))) || shown(hasContentDescription(t(R.string.a11y_time_ended), substring = true))
                }
                pause(1_600)
                click(t(R.string.run_done_button))
                waitText(t(R.string.choice_title))
                click(t(R.string.card_music))
            }
            shot("a10-valmis") {
                click(t(R.string.choice_start))
                waitFor(hasText(t(R.string.run_done_button)) and hasClickAction())
                pause(2_000)
                click(t(R.string.run_done_button))
                waitText(t(R.string.card_rest))
                pause(2_000)
                click(t(R.string.run_done_button))
                waitText(t(R.string.kaikki_valmista))
            }
        }
        // Odotetaan asiakkaalla.
        Waiting.start(ctx, t(R.string.card_leaving), "builtin:leaving", 0)
        launch(caregiver = false) {
            shot("a07-odotetaan") { waitText(t(R.string.wait_you_can)) }
        }
        Waiting.stop(ctx)
        // Muutoskortti: Lukeminen korvataan Ulkoilulla (avautuu kuin ilmoituksesta).
        val change = Demo.change(ctx)
        EventLaunch.pending.value = EventLaunchRequest(ProfileStore.currentId(ctx), change.eventId, change.date)
        launch(caregiver = false) {
            shot("a08-muutoskortti") { waitText(t(R.string.card_outdoors)) }
        }
        EventLaunch.pending.value = null
    }

    companion object {
        const val DIR = "/sdcard/Pictures/AikaPolku-kuvat"
        const val TAG = "AikaPolkuKuvat"
    }
}

/** Kuvitteellinen esimerkkiyksikkö (sama kuin käyttöoppaassa). */
private object Demo {
    const val UNIT = "Villa Metsämaja"
    const val SUB_A = "Omena"
    const val SUB_B = "Päärynä"
    const val DAY_ACTIVITY = "Päivätoiminta Tammi"
    const val WORKSHOP = "Työpaja Verstas"
    const val PROGRAM = "Iltapäivän ohjelma"
    val RESIDENTS_A = listOf("Aino", "Eero", "Helmi", "Ilmari", "Kaisla", "Lauri")
    val RESIDENTS_B = listOf("Mikael", "Noora", "Oskari", "Pihla", "Rasmus", "Siiri")
    val DAY_MEMBERS = listOf("Eero", "Helmi", "Mikael", "Noora", "Pihla")
    val WORKSHOP_MEMBERS = listOf("Ilmari", "Oskari", "Rasmus")
    private const val LATER_EVENT = "kuva-lukeminen"

    private fun name(ctx: Context, cardId: String) =
        CardArt.templates.firstOrNull { it.id == cardId }?.let { ctx.getString(it.nameRes) } ?: cardId

    fun create(ctx: Context) {
        val app = ctx.applicationContext
        val ids = (RESIDENTS_A + RESIDENTS_B).associateWith { ProfileStore.add(app, it).id }
        // Nimetön oletusprofiili pois (sen tiedot tyhjennettiin talteenoton jälkeen).
        ProfileStore.list(app).filter { it.name.isBlank() }.forEach { ProfileStore.delete(app, it.id) }
        val aino = ids.getValue("Aino")
        ProfileStore.switchTo(app, aino)

        val meals = UnitSetup.defaultMeals { name(app, it) }.map { it.copy(notifyBefore = 10) }
        val weekdays = (1..5).map { ActivitySession(it, "09:00", "14:00") }
        val workshop = listOf(2, 4).map { ActivitySession(it, "09:00", "15:00") }
        var n = 0
        val groups = UnitSetup.build(
            UnitSpec(
                name = UNIT,
                subUnits = listOf(SubUnitSpec(SUB_A, RESIDENTS_A.map(ids::getValue)), SubUnitSpec(SUB_B, RESIDENTS_B.map(ids::getValue))),
                members = emptyList(),
                meals = meals,
                activities = listOf(
                    ActivitySpec(DAY_ACTIVITY, "builtin:schoolwork", weekdays, DAY_MEMBERS.map(ids::getValue).toSet()),
                    ActivitySpec(WORKSHOP, "builtin:crafts", workshop, WORKSHOP_MEMBERS.map(ids::getValue).toSet())
                )
            )
        ) { "kuva-${n++}" }.map { g ->
            // Helmillä oma aika päivätoiminnassa (lyhyempi päivä).
            if (g.name == DAY_ACTIVITY) g.copy(memberTimes = listOf(MemberTime(ids.getValue("Helmi"), (1..5).toSet(), "10:00", "13:00"))) else g
        }
        val store = GroupStore(app)
        store.save(groups)
        // Lomat: Siiri lomalla nyt, Lauri tulevana viikonloppuna.
        val now = LocalDateTime.now()
        store.upsertLeave(Leave("kuva-loma-1", ids.getValue("Siiri"), now.minusDays(1).withHour(12).withMinute(0), now.plusDays(2).withHour(18).withMinute(0), LeaveReason.HOLIDAY))
        val friday = LocalDate.now().plusDays(1).with(TemporalAdjusters.nextOrSame(DayOfWeek.FRIDAY))
        store.upsertLeave(Leave("kuva-loma-2", ids.getValue("Lauri"), friday.atTime(15, 0), friday.plusDays(2).atTime(18, 0), LeaveReason.HOME))

        // Ainon päivä: ohjelma alkoi juuri, Lukeminen myöhemmin.
        val program = DayProgram(
            id = "kuva-ohjelma",
            name = PROGRAM,
            steps = listOf(
                ProgramStep("k1", name(app, "wash_hands"), "builtin:wash_hands", ""),
                ProgramStep("k2", name(app, "snack"), "builtin:snack", "", mode = StepMode.DURATION, minutes = 3),
                ProgramStep("k3", name(app, "outdoors"), "builtin:outdoors", "", alternative = ChoiceAlt(name(app, "music"), "builtin:music")),
                ProgramStep("k4", name(app, "rest"), "builtin:rest", "")
            ),
            timeMode = TimeMode.FREE
        )
        ProgramStore(app, aino).save(program)
        val today = LocalDate.now()
        val week = WeekSchedule.weekOf(WeekPlan(), today)
        val minutes = now.hour * 60 + now.minute
        val start = (minutes - 1).coerceIn(0, 23 * 60 + 50)
        val later = (minutes + 45).coerceIn(start + 1, 23 * 60 + 59)
        WeekStore(app, aino).save(
            WeekPlan(
                listOf(
                    WeekEvent("kuva-ohjelma-e", today.dayOfWeek.value, week, WeekSchedule.formatTime(start / 60, start % 60), EventKind.PROGRAM, programId = program.id),
                    WeekEvent(LATER_EVENT, today.dayOfWeek.value, week, WeekSchedule.formatTime(later / 60, later % 60), EventKind.CARD,
                        cardName = name(app, "reading"), cardPhoto = "builtin:reading", notify = true, notifyBefore = 5)
                )
            )
        )

        // Ainon asetukset: valinta ja oma tila (ennakkovaroitus tuen tason jälkeen).
        ChoicePrefs.setAllowNone(app, true)
        OwnState.setEnabled(app, true)
        CardStore(app, aino).saveSelection(
            FirstThenSelection(
                firstName = name(app, "homework"), nextName = name(app, "gaming"),
                firstPhoto = "builtin:homework", nextPhoto = "builtin:gaming",
                nextAltName = name(app, "outdoors"), nextAltPhoto = "builtin:outdoors"
            )
        )
        // Liitetty ohjaajan puhelin (vain näkymää varten; ei lähetetä).
        LinkStore(app).setName("Asukkaan puhelin (Aino)")
        LinkStore(app).addPeer(Peer("kuva-ohjaaja", "Ohjaajan puhelin, Omena", ByteArray(32), receiver = true))

        // Tuen taso ja käytön seuranta: muutaman viikon historia.
        RoleSession.setTestMode(app, false)
        // Ohjaajan näkymien kuvat: FLAG_SECURE pois ajon ajaksi (asetus palautuu talteenotosta).
        fi.aikapolku.app.caregiver.PrivacyPrefs.setSecureScreens(app, false)
        SupportLevels.apply(app, SupportLevel.MUCH, today.minusDays(45))
        SupportLevels.apply(app, SupportLevel.MODERATE, today.minusDays(20))
        // Taso asettaa ennakkovaroitukseksi 5 min; kuvan 3 min tehtävässä varoitus näkyy, kun se on 2 min.
        TimeDisplayPrefs.setWarnMinutes(app, 2)
        UsageLog.setEnabled(app, true, today.minusDays(28))
        (27 downTo 0).forEach { back ->
            val d = today.minusDays(back.toLong())
            if (d.dayOfWeek.value <= 6 && back != 0) {
                repeat(2) { UsageLog.record(app, aino, UsageKind.PROGRAM_START, d) }
                repeat(2) { UsageLog.record(app, aino, UsageKind.PROGRAM_DONE, d) }
                repeat(7) { UsageLog.record(app, aino, UsageKind.STEP_DONE, d) }
                repeat(if (back >= 14) 3 else 1) { UsageLog.record(app, aino, UsageKind.HELP, d) }
                if (back % 3 == 0) UsageLog.record(app, aino, UsageKind.BREAK, d)
                if (back % 4 == 0) UsageLog.record(app, aino, UsageKind.CHOICE, d)
            }
            if (back % 2 == 0 && back != 0) {
                UsageLog.record(app, ids.getValue("Eero"), UsageKind.PROGRAM_START, d)
                UsageLog.record(app, ids.getValue("Eero"), UsageKind.STEP_DONE, d)
            }
        }
        BackgroundTimer.stop(app, "timer", 300_000L)
        GroupSession.changed()
        fi.aikapolku.app.profiles.ProfileSession.changed()
    }

    /** Päivän muutos: Lukeminen korvataan Ulkoilulla, syy kerrotaan. */
    fun change(ctx: Context): EventChange {
        val app = ctx.applicationContext
        val change = EventChange(
            id = "kuva-muutos", date = LocalDate.now(), eventId = LATER_EVENT, type = ChangeType.REPLACE,
            kind = EventKind.CARD, cardName = name(app, "outdoors"), cardPhoto = "builtin:outdoors",
            reason = "Sää on hyvä, lähdetään ulos."
        )
        ChangeStore(app).upsert(change)
        return change
    }
}
