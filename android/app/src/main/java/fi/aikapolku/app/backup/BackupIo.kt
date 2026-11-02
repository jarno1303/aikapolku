package fi.aikapolku.app.backup

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import fi.aikapolku.app.cards.CardArt
import fi.aikapolku.app.data.CardStore
import fi.aikapolku.app.data.DayProgram
import fi.aikapolku.app.data.ProfileScope
import fi.aikapolku.app.data.ProfileStore
import fi.aikapolku.app.data.ProgramStore
import fi.aikapolku.app.data.WeekStore
import fi.aikapolku.app.data.programFromJson
import fi.aikapolku.app.data.programToJson
import fi.aikapolku.app.week.EventReminders
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDate
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

/** Mitä viedään: kaikki profiilit (varmuuskopio), yksi profiili tai valitut päiväohjelmat. */
internal enum class BackupScope(val key: String) {
    ALL("all"),
    PROFILE("profile"),
    PROGRAMS("programs");

    companion object {
        fun from(key: String?): BackupScope? = entries.firstOrNull { it.key == key }
    }
}

/** Mihin tuodaan: uutena profiilina tai nykyiseen profiiliin (lisätään, ei korvata). */
internal enum class ImportTarget { NEW_PROFILE, CURRENT_PROFILE }

/**
 * Vientitiedoston sisältö: data.json ja media/-kansio. Viedään profiilin päiväohjelmat, omat
 * kortit, viikko-ohjelma ja asetukset sekä niiden kuvat ja äänitteet. EI viedä kuittauksia,
 * päivän muutoksia, suorituksen edistymistä eikä ohjaajan PIN-koodia (laitekohtainen).
 */
internal object BackupFormat {
    const val FORMAT = "aikapolku"
    const val VERSION = 1
    const val DATA = "data.json"
    const val MEDIA = "media/"

    /** Profiilin viedyt tiedostot. Kuittaukset, muutokset ja edistyminen jäävät pois. */
    val PROFILE_BASES = listOf(
        ProfileScope.PROGRAMS,
        ProfileScope.CARDS,
        ProfileScope.RUN_PREFS,
        ProfileScope.TIMER_PREFS,
        ProfileScope.WEEK
    )

    /** Ensin–Sitten-valinta on laitteen hetkellinen tila, ei siirrettävää sisältöä. */
    private val SKIPPED_KEYS = setOf(ProfileScope.CARDS + "/selection")

    fun prefsToJson(base: String, prefs: SharedPreferences, text: (String) -> String): JSONObject {
        val obj = JSONObject()
        prefs.all.forEach { (key, value) ->
            if ("$base/$key" in SKIPPED_KEYS) return@forEach
            val entry = when (value) {
                is String -> JSONObject().put("t", "s").put("v", text(value))
                is Boolean -> JSONObject().put("t", "b").put("v", value)
                is Int -> JSONObject().put("t", "i").put("v", value)
                is Long -> JSONObject().put("t", "l").put("v", value)
                is Float -> JSONObject().put("t", "f").put("v", value.toDouble())
                is Set<*> -> JSONObject().put("t", "ss").put("v", JSONArray(value.filterIsInstance<String>().map(text)))
                else -> null
            }
            if (entry != null) obj.put(key, entry)
        }
        return obj
    }

    fun jsonToPrefs(obj: JSONObject, editor: SharedPreferences.Editor, text: (String) -> String) {
        obj.keys().forEach { key ->
            val e = obj.optJSONObject(key) ?: return@forEach
            when (e.optString("t")) {
                "s" -> editor.putString(key, text(e.optString("v")))
                "b" -> editor.putBoolean(key, e.optBoolean("v"))
                "i" -> editor.putInt(key, e.optInt("v"))
                "l" -> editor.putLong(key, e.optLong("v"))
                "f" -> editor.putFloat(key, e.optDouble("v").toFloat())
                "ss" -> editor.putStringSet(key, e.optJSONArray("v")?.let { a -> (0 until a.length()).map { text(a.optString(it)) }.toSet() } ?: emptySet())
            }
        }
    }

    fun prefsString(profile: JSONObject, base: String, key: String): String? =
        profile.optJSONObject("prefs")?.optJSONObject(base)?.optJSONObject(key)?.takeIf { it.optString("t") == "s" }?.optString("v")
}

/** Tuotavan tiedoston esikatselu. */
internal class ImportPreview(
    val scope: BackupScope,
    val anonymized: Boolean,
    val profiles: List<ProfileSummary>,
    val programCount: Int,
    internal val data: JSONObject,
    internal val media: Map<String, ByteArray>
) {
    internal data class ProfileSummary(val name: String, val programs: Int, val cards: Int, val weekEvents: Int)

    val hasWeek: Boolean get() = profiles.any { it.weekEvents > 0 }
}

internal data class ImportResult(val profiles: Int, val programs: Int, val cards: Int, val skipped: Int, val week: Boolean)

internal object BackupExporter {
    private const val MAX_IMAGE = 1600
    private const val SMALL_IMAGE_BYTES = 600 * 1024

    /**
     * Tekee vientitiedoston välimuistiin (cache/share) ja palauttaa sen. Kuvat pienennetään
     * (pisin sivu enintään 1600 px), jotta tiedosto mahtuu sähköpostin liitteeksi.
     */
    fun export(
        context: Context,
        scope: BackupScope,
        programIds: Set<String>,
        anonymize: Boolean,
        password: String?
    ): File {
        val app = context.applicationContext
        val currentId = ProfileStore.currentId(app)
        val candidates = app.filesDir.walkTopDown().maxDepth(5).filter { it.isFile }.map { it.absolutePath }.toList()
        val media = LinkedHashMap<String, String>()
        fun text(value: String): String {
            if (value.isEmpty()) return value
            candidates.filter { MediaRefs.isReferenced(it, value) }.forEach { path ->
                media.getOrPut(path) { MediaRefs.safeName(media.size + 1, path) }
            }
            return MediaRefs.toTokens(value, media)
        }

        val root = JSONObject()
            .put("format", BackupFormat.FORMAT)
            .put("version", BackupFormat.VERSION)
            .put("scope", scope.key)
            .put("created", LocalDate.now().toString())
            .put("anonymized", scope == BackupScope.PROGRAMS && anonymize)

        var title = "AikaPolku"
        when (scope) {
            BackupScope.ALL, BackupScope.PROFILE -> {
                val profiles = if (scope == BackupScope.ALL) ProfileStore.list(app) else listOf(ProfileStore.current(app))
                if (scope == BackupScope.PROFILE) title = profiles.first().name.ifBlank { title }
                val array = JSONArray()
                profiles.forEach { p ->
                    val prefs = JSONObject()
                    BackupFormat.PROFILE_BASES.forEach { base ->
                        prefs.put(base, BackupFormat.prefsToJson(base, ProfileScope.prefs(app, base, p.id), ::text))
                    }
                    array.put(
                        JSONObject()
                            .put("name", p.name)
                            .put("photo", text(p.photo))
                            .put("programs", ProgramStore(app, p.id).load().size)
                            .put("cards", CardStore(app, p.id).loadCards().size)
                            .put("weekEvents", WeekStore(app, p.id).load().events.size)
                            .put("prefs", prefs)
                    )
                }
                root.put("profiles", array)
            }
            BackupScope.PROGRAMS -> {
                val programs = ProgramStore(app, currentId).load().filter { it.id in programIds }
                    .map { p -> if (anonymize) BackupMerge.anonymized(p) { name -> CardArt.find(name)?.let { "builtin:${it.id}" } } else p }
                if (programs.size == 1) title = programs.first().name
                val array = JSONArray()
                programs.forEach { array.put(programToJson(it)) }
                root.put("programs", JSONArray(text(array.toString())))
            }
        }

        val entries = LinkedHashMap<String, ByteArray>()
        entries[BackupFormat.DATA] = root.toString().toByteArray(Charsets.UTF_8)
        media.forEach { (path, name) ->
            runCatching { mediaBytes(File(path)) }.getOrNull()?.let { entries[BackupFormat.MEDIA + name] = it }
        }
        val bytes = BackupArchive.pack(entries, password)

        val folder = File(app.cacheDir, "share").apply { mkdirs() }
        folder.listFiles()?.forEach { it.delete() }
        val safe = title.replace(Regex("[^\\p{L}\\p{N} _-]"), "").trim().replace(' ', '_').ifBlank { "AikaPolku" }.take(40)
        val file = File(folder, "AikaPolku_${safe}_${LocalDate.now()}.aikapolku".replace("AikaPolku_AikaPolku", "AikaPolku"))
        file.writeBytes(bytes)
        return file
    }

    /** Kuva pienennettynä JPEG:ksi, jos se on suuri; muut tiedostot (äänet) sellaisenaan. */
    private fun mediaBytes(file: File): ByteArray {
        val raw = file.readBytes()
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(raw, 0, raw.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return raw
        if (raw.size <= SMALL_IMAGE_BYTES && maxOf(bounds.outWidth, bounds.outHeight) <= MAX_IMAGE) return raw
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_IMAGE) sample *= 2
        val decoded = BitmapFactory.decodeByteArray(raw, 0, raw.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return raw
        val scale = MAX_IMAGE.toFloat() / maxOf(decoded.width, decoded.height)
        val bitmap = if (scale < 1f) {
            Bitmap.createScaledBitmap(decoded, (decoded.width * scale).toInt().coerceAtLeast(1), (decoded.height * scale).toInt().coerceAtLeast(1), true)
        } else decoded
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
        if (bitmap !== decoded) bitmap.recycle()
        decoded.recycle()
        return out.toByteArray().takeIf { it.size < raw.size } ?: raw
    }
}

internal object BackupImporter {
    /** Avaa tiedoston ja lukee esikatselun. Heittää [BackupException]. */
    fun read(bytes: ByteArray, password: String?): ImportPreview {
        val entries = BackupArchive.open(bytes, password)
        val dataBytes = entries[BackupFormat.DATA] ?: throw BackupException(BackupException.Reason.NOT_AIKAPOLKU)
        val data = runCatching { JSONObject(String(dataBytes, Charsets.UTF_8)) }
            .getOrElse { throw BackupException(BackupException.Reason.BROKEN) }
        if (data.optString("format") != BackupFormat.FORMAT) throw BackupException(BackupException.Reason.NOT_AIKAPOLKU)
        if (data.optInt("version", 0) > BackupFormat.VERSION) throw BackupException(BackupException.Reason.TOO_NEW)
        val scope = BackupScope.from(data.optString("scope")) ?: throw BackupException(BackupException.Reason.BROKEN)
        val profiles = data.optJSONArray("profiles")?.let { a ->
            (0 until a.length()).mapNotNull { a.optJSONObject(it) }.map { p ->
                ImportPreview.ProfileSummary(p.optString("name"), p.optInt("programs"), p.optInt("cards"), p.optInt("weekEvents"))
            }
        }.orEmpty()
        val media = entries.filterKeys { it.startsWith(BackupFormat.MEDIA) }.mapKeys { it.key.removePrefix(BackupFormat.MEDIA) }
        return ImportPreview(
            scope = scope,
            anonymized = data.optBoolean("anonymized", false),
            profiles = profiles,
            programCount = data.optJSONArray("programs")?.length() ?: profiles.sumOf { it.programs },
            data = data,
            media = media
        )
    }

    /** Kirjoittaa tekstin viittaamat mediat omaan kansioonsa ja palauttaa tekstin uusin poluin. */
    private class Materializer(context: Context, private val media: Map<String, ByteArray>) {
        private val folder = File(context.filesDir, "imported/" + UUID.randomUUID().toString().take(8))
        private val written = mutableMapOf<String, String>()

        fun text(value: String): String {
            val names = MediaRefs.tokens(value)
            names.forEach { name ->
                if (name in written) return@forEach
                val bytes = media[name] ?: return@forEach
                folder.mkdirs()
                val file = File(folder, name)
                file.writeBytes(bytes)
                written[name] = file.absolutePath
            }
            return MediaRefs.fromTokens(value, written, json = MediaRefs.looksLikeJson(value))
        }
    }

    fun apply(context: Context, preview: ImportPreview, target: ImportTarget, replaceWeek: Boolean): ImportResult {
        val app = context.applicationContext
        val files = Materializer(app, preview.media)
        val result = when {
            preview.scope == BackupScope.PROGRAMS -> {
                val programs = preview.data.optJSONArray("programs")?.toString()?.let { files.text(it) }
                    ?.let { JSONArray(it) }
                    ?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it) }.map(::programFromJson) }
                    .orEmpty()
                mergePrograms(app, programs).let { ImportResult(0, it.added.size, 0, it.skipped, false) }
            }
            target == ImportTarget.NEW_PROFILE || preview.scope == BackupScope.ALL -> {
                val profiles = preview.data.optJSONArray("profiles") ?: JSONArray()
                var programs = 0
                var cards = 0
                for (i in 0 until profiles.length()) {
                    val p = profiles.optJSONObject(i) ?: continue
                    val name = UniqueNames.unique(p.optString("name"), ProfileStore.list(app).map { it.name }.filter { it.isNotBlank() })
                    val profile = ProfileStore.add(app, name, files.text(p.optString("photo")))
                    val prefs = p.optJSONObject("prefs") ?: JSONObject()
                    BackupFormat.PROFILE_BASES.forEach { base ->
                        val obj = prefs.optJSONObject(base) ?: return@forEach
                        val editor = ProfileScope.prefs(app, base, profile.id).edit()
                        BackupFormat.jsonToPrefs(obj, editor, files::text)
                        editor.commit()
                    }
                    programs += ProgramStore(app, profile.id).load().size
                    cards += CardStore(app, profile.id).loadCards().size
                }
                ImportResult(profiles.length(), programs, cards, 0, true)
            }
            else -> {
                // Yksi profiili nykyiseen profiiliin: ohjelmat ja kortit lisätään, viikko vain pyydettäessä.
                val p = preview.data.optJSONArray("profiles")?.optJSONObject(0) ?: JSONObject()
                val programsText = BackupFormat.prefsString(p, ProfileScope.PROGRAMS, "programs")?.let(files::text)
                val programs = programsText?.let { runCatching { JSONArray(it) }.getOrNull() }
                    ?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it) }.map(::programFromJson) }
                    .orEmpty()
                val merged = mergePrograms(app, programs)
                val incomingCards = BackupFormat.prefsString(p, ProfileScope.CARDS, CardStore.KEY_CARDS)
                    ?.let { CardStore.decodeCards(files.text(it)) }.orEmpty()
                val cardStore = CardStore(app)
                val mergedCards = BackupMerge.cards(cardStore.loadCards(), incomingCards) { UUID.randomUUID().toString() }
                mergedCards.added.forEach(cardStore::upsert)
                var week = false
                if (replaceWeek) {
                    val weekText = BackupFormat.prefsString(p, ProfileScope.WEEK, "week_plan")
                    if (weekText != null) {
                        val plan = WeekStore.decode(weekText)
                        WeekStore(app).save(BackupMerge.week(plan, merged.idMap) { UUID.randomUUID().toString() })
                        week = true
                    }
                }
                ImportResult(0, merged.added.size, mergedCards.added.size, merged.skipped + mergedCards.skipped, week)
            }
        }
        EventReminders.reschedule(app)
        return result
    }

    private fun mergePrograms(app: Context, incoming: List<DayProgram>): BackupMerge.Result<DayProgram> {
        val store = ProgramStore(app)
        val merged = BackupMerge.programs(store.load(), incoming) { UUID.randomUUID().toString() }
        merged.added.forEach(store::save)
        return merged
    }
}
