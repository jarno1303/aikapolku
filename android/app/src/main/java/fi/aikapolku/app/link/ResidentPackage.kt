package fi.aikapolku.app.link

import android.content.Context
import fi.aikapolku.app.backup.BackupArchive
import fi.aikapolku.app.backup.BackupException
import fi.aikapolku.app.backup.BackupFormat
import fi.aikapolku.app.backup.MediaRefs
import fi.aikapolku.app.data.Group
import fi.aikapolku.app.data.GroupStore
import fi.aikapolku.app.data.Leave
import fi.aikapolku.app.data.ProfileScope
import fi.aikapolku.app.data.ProfileStore
import fi.aikapolku.app.data.EventChange
import fi.aikapolku.app.data.ChangeStore
import fi.aikapolku.app.data.Leaves
import java.io.File
import java.security.MessageDigest
import java.time.LocalDate
import org.json.JSONArray
import org.json.JSONObject

/**
 * Asukkaan osuus paikan tiedoista (A5, docs/PAIKAN_LAITE_SUUNNITELMA.md): asukkaan ryhmät ja
 * yksiköt vain hänen osaltaan. Jäsentunnus on [SELF]; muiden asukkaiden jäsenyydet, poikkeukset,
 * omat ajat, poissaolot ja lomat jäävät pois. Puhdas logiikka, testattavissa ilman laitetta.
 */
internal data class ResidentShare(
    val groups: List<Group>,
    val groupChanges: Map<String, List<EventChange>>,
    val absences: Map<String, Map<LocalDate, Set<String>>>,
    val leaves: List<Leave>
) {
    companion object {
        const val SELF = "@self"

        /** Paikan laitteella: asukkaan [memberId] osuus. */
        fun of(
            all: List<Group>,
            changes: Map<String, List<EventChange>>,
            absences: Map<String, Map<LocalDate, Set<String>>>,
            leaves: List<Leave>,
            memberId: String
        ): ResidentShare {
            // Suorat ryhmät (asukas jäsenenä) ja yksiköiden yläyksiköt sellaisinaan (ilman jäseniä):
            // asukkaan puhelin laskee perityn rungon samalla säännöllä (Units.groupsFor).
            val direct = all.filter { memberId in it.members }
            val ancestorIds = fi.aikapolku.app.data.Units.groupsFor(all, memberId).map { it.id }.toSet() - direct.map { it.id }.toSet()
            val shared = direct.map { g -> only(g, memberId) } +
                all.filter { it.id in ancestorIds }.map { g -> only(g, memberId) }
            val ids = shared.map { it.id }.toSet()
            return ResidentShare(
                groups = shared,
                groupChanges = changes.filterKeys { it in ids }.filterValues { it.isNotEmpty() },
                absences = absences.filterKeys { it in ids }
                    .mapValues { (_, days) -> days.filterValues { memberId in it }.mapValues { setOf(SELF) } }
                    .filterValues { it.isNotEmpty() },
                leaves = leaves.filter { it.memberId == memberId }.map { it.copy(memberId = SELF) }
            )
        }

        /** Ryhmä vain asukkaan osalta: muiden jäsenyys, poikkeukset ja omat ajat pois. */
        private fun only(g: Group, memberId: String): Group = g.copy(
            members = if (memberId in g.members) listOf(SELF) else emptyList(),
            exceptions = g.exceptions.filter { it.memberId == memberId }.map { it.copy(memberId = SELF) },
            memberTimes = g.memberTimes.filter { it.memberId == memberId }.map { it.copy(memberId = SELF) }
        )

        /** Asukkaan puhelimessa: [SELF] → oma profiili [localId]. */
        fun localize(share: ResidentShare, localId: String): ResidentShare = ResidentShare(
            groups = share.groups.map { g ->
                g.copy(
                    members = g.members.map { if (it == SELF) localId else it },
                    exceptions = g.exceptions.map { if (it.memberId == SELF) it.copy(memberId = localId) else it },
                    memberTimes = g.memberTimes.map { if (it.memberId == SELF) it.copy(memberId = localId) else it }
                )
            },
            groupChanges = share.groupChanges,
            absences = share.absences.mapValues { (_, days) -> days.mapValues { (_, s) -> s.map { if (it == SELF) localId else it }.toSet() } },
            leaves = share.leaves.map { if (it.memberId == SELF) it.copy(memberId = localId) else it }
        )

        /** [text] muuntaa kunkin tallennetun JSON-tekstin (median polut tunnisteiksi ja takaisin). */
        fun encode(share: ResidentShare, text: (String) -> String = { it }): JSONObject {
            val changes = JSONObject()
            share.groupChanges.forEach { (id, list) -> changes.put(id, text(ChangeStore.encode(list))) }
            return JSONObject()
                .put("groups", text(GroupStore.encode(share.groups)))
                .put("changes", changes)
                .put("absences", GroupStore.encodeAbsences(share.absences))
                .put("leaves", Leaves.encode(share.leaves))
        }

        fun decode(obj: JSONObject, text: (String) -> String = { it }): ResidentShare {
            val changes = obj.optJSONObject("changes")
            return ResidentShare(
                groups = GroupStore.decode(text(obj.optString("groups", ""))),
                groupChanges = changes?.keys()?.asSequence()?.associateWith { ChangeStore.decode(text(changes.optString(it))) }.orEmpty(),
                absences = GroupStore.decodeAbsences(obj.optString("absences", "")),
                leaves = Leaves.decode(obj.optString("leaves", ""))
            )
        }
    }
}

/**
 * Asukaspaketti: kaikki, mitä asukkaan puhelin tarvitsee päiväänsä ilman yhteyttä
 * (docs/PAIKAN_LAITE_SUUNNITELMA.md). Paketti on zip: data.json ja media/<sha256>.<pääte>.
 * Kuvat ja äänitteet nimetään sisällön tiivisteellä, ja ne, jotka vastaanottaja jo omistaa
 * ([build]-kutsun known), jätetään pois – ne siirtyvät vain uusina tai muuttuneina.
 */
internal object ResidentPackage {
    const val FORMAT = "aikapolku-resident"
    const val VERSION = 1
    private const val SYNC_DIR = "sync"

    /** Paikalta asukkaan puhelimeen siirtyvät profiilin tiedostot. Paikka on näissä totuus. */
    val BASES = listOf(
        ProfileScope.PROGRAMS,
        ProfileScope.CARDS,
        ProfileScope.RUN_PREFS,
        ProfileScope.TIMER_PREFS,
        ProfileScope.WEEK,
        ProfileScope.CHANGES,
        ProfileScope.WAIT
    )

    /** Laitteen hetkellinen tila, ei siirrettävää sisältöä (Ensin–Sitten-valinta). */
    private val SKIPPED = setOf(ProfileScope.CARDS + "/selection")

    /** Mitä paketti sisältää (ohjaajan näkymä ja testit). */
    internal data class Built(val bytes: ByteArray, val mediaTotal: Int, val mediaSent: Int)

    fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun extension(path: String): String =
        path.substringAfterLast('.', "").lowercase().takeIf { it.length in 1..5 && it.all(Char::isLetterOrDigit) }?.let { ".$it" } ?: ""

    /**
     * Paikan laitteella: asukkaan [profileId] paketti. [known] = tiivisteet, jotka asukkaan puhelin
     * jo omistaa. Salaus hoidetaan siirrossa (LinkProtocol), joten paketti itse on salaamaton zip.
     */
    /** Kuvia ja ääniä enintään näin paljon yhdessä paketissa; loput seuraavalla haulla (muisti). */
    const val MAX_MEDIA_BYTES = 12L * 1024 * 1024

    fun build(context: Context, profileId: String, known: Set<String> = emptySet(), maxMedia: Long = MAX_MEDIA_BYTES): Built {
        val app = context.applicationContext
        val candidates = app.filesDir.walkTopDown().maxDepth(5).filter { it.isFile }.map { it.absolutePath }.toList()
        val names = LinkedHashMap<String, String>()
        val bytesByName = LinkedHashMap<String, ByteArray>()
        fun text(value: String): String {
            if (value.isEmpty()) return value
            candidates.filter { MediaRefs.isReferenced(it, value) }.forEach { path ->
                if (path !in names) {
                    val bytes = runCatching { File(path).readBytes() }.getOrNull() ?: return@forEach
                    val name = sha256(bytes) + extension(path)
                    names[path] = name
                    bytesByName[name] = bytes
                }
            }
            return MediaRefs.toTokens(value, names)
        }

        val prefs = JSONObject()
        BASES.forEach { base ->
            val obj = BackupFormat.prefsToJson(base, ProfileScope.prefs(app, base, profileId), ::text)
            SKIPPED.filter { it.startsWith("$base/") }.forEach { obj.remove(it.substringAfter('/')) }
            prefs.put(base, obj)
        }
        val store = GroupStore(app)
        val share = ResidentShare.of(store.load(), store.allChanges(), store.absences(), store.leaves(), profileId)
        val shareJson = ResidentShare.encode(share, ::text)
        val profile = ProfileStore.list(app).firstOrNull { it.id == profileId }

        val root = JSONObject()
            .put("format", FORMAT)
            .put("version", VERSION)
            .put("name", profile?.name.orEmpty())
            .put("photo", text(profile?.photo.orEmpty()))
            .put("prefs", prefs)
            .put("share", shareJson)
            .put("categories", fi.aikapolku.app.data.CategoryStore.export(app))
            .put("media", JSONArray(bytesByName.keys.toList()))

        val entries = LinkedHashMap<String, ByteArray>()
        entries[BackupFormat.DATA] = root.toString().toByteArray(Charsets.UTF_8)
        var sent = 0
        var size = 0L
        bytesByName.forEach { (name, bytes) ->
            if (name.substringBefore('.') !in known && (sent == 0 || size + bytes.size <= maxMedia)) {
                entries[BackupFormat.MEDIA + name] = bytes
                size += bytes.size
                sent++
            }
        }
        return Built(BackupArchive.zip(entries), bytesByName.size, sent)
    }

    /**
     * Asukkaan tietojen versio paikan laitteella: muuttuu, kun jokin pakettiin kuuluva muuttuu.
     * Kevyt (ei lue kuvia): kuvan vaihto muuttaa polkua, joten versio muuttuu silloinkin.
     */
    fun version(context: Context, profileId: String): String {
        val app = context.applicationContext
        val text = StringBuilder()
        BASES.forEach { base ->
            ProfileScope.prefs(app, base, profileId).all.toSortedMap().forEach { (key, value) ->
                if ("$base/$key" in SKIPPED) return@forEach
                val v = if (value is Set<*>) value.map { it.toString() }.sorted().toString() else value.toString()
                text.append(base).append('/').append(key).append('=').append(v).append('\n')
            }
        }
        val store = GroupStore(app)
        text.append(ResidentShare.encode(ResidentShare.of(store.load(), store.allChanges(), store.absences(), store.leaves(), profileId)))
        ProfileStore.list(app).firstOrNull { it.id == profileId }?.let { text.append(it.name).append(it.photo) }
        // Luokat ja niiden nimet (värikoodaus) ovat laitteen yhteisiä: muutos menee kaikille.
        text.append(fi.aikapolku.app.data.CategoryStore.export(app).toString())
        return sha256(text.toString().toByteArray(Charsets.UTF_8)).take(20)
    }

    /** Asukkaan puhelimessa jo olevat kuvat ja äänet (tiivisteet), kerrotaan pyynnössä. */
    fun known(context: Context): Set<String> =
        File(context.applicationContext.filesDir, SYNC_DIR).listFiles()?.filterNot { it.name.endsWith(".tmp") }
            ?.map { it.name.substringBefore('.') }?.toSet().orEmpty()

    /** Puretun paketin tulos. [missing] = kuvat, joita ei ollut paketissa eikä laitteessa. */
    internal data class Applied(val name: String, val groups: Int, val missing: Int)

    /**
     * Asukkaan puhelimessa: purkaa paketin profiiliin [localId]. Paketin tiedostot korvataan
     * kokonaan (paikka on totuus); asukkaan omat tiedot (oma tila, käytön seuranta, kuittaukset,
     * edistyminen) jäävät koskematta. Paikan [placeId] aiemmin tuomat ryhmät korvataan; muiden
     * paikkojen ja laitteen omat ryhmät säilyvät.
     */
    fun apply(context: Context, bytes: ByteArray, placeId: String, localId: String = ProfileStore.currentId(context)): Applied {
        val app = context.applicationContext
        val entries = BackupArchive.unzip(bytes)
        val root = entries[BackupFormat.DATA]?.let { runCatching { JSONObject(String(it, Charsets.UTF_8)) }.getOrNull() }
            ?: throw BackupException(BackupException.Reason.BROKEN)
        if (root.optString("format") != FORMAT) throw BackupException(BackupException.Reason.NOT_AIKAPOLKU)
        if (root.optInt("version", 0) > VERSION) throw BackupException(BackupException.Reason.TOO_NEW)

        // Kuvat ja äänet sync-kansioon tiivisteen nimellä; tarkistetaan, että sisältö vastaa nimeä.
        val folder = File(app.filesDir, SYNC_DIR).apply { mkdirs() }
        entries.filterKeys { it.startsWith(BackupFormat.MEDIA) }.forEach { (key, data) ->
            val name = key.removePrefix(BackupFormat.MEDIA)
            if (!SAFE_NAME.matches(name) || sha256(data) != name.substringBefore('.')) return@forEach
            val file = File(folder, name)
            if (!file.exists()) {
                // Ensin väliaikaiseen ja sitten nimetään: keskeytynyt kirjoitus ei jää "valmiiksi".
                val tmp = File(folder, "$name.tmp")
                tmp.writeBytes(data)
                if (!tmp.renameTo(file)) tmp.delete()
            }
        }
        val paths = HashMap<String, String>()
        var missing = 0
        val listed = root.optJSONArray("media")?.let { a -> (0 until a.length()).map { a.optString(it) } }.orEmpty()
        listed.filter { SAFE_NAME.matches(it) }.forEach { name ->
            val file = File(folder, name)
            if (file.exists()) paths[name] = file.absolutePath else missing++
        }
        fun text(value: String): String = MediaRefs.fromTokens(value, paths, json = MediaRefs.looksLikeJson(value))

        val prefs = root.optJSONObject("prefs") ?: JSONObject()
        BASES.forEach { base ->
            val target = ProfileScope.prefs(app, base, localId)
            val editor = target.edit()
            // Ensin–Sitten-valinta säilyy (laitteen hetkellinen tila); muu korvataan.
            val keep = SKIPPED.filter { it.startsWith("$base/") }.map { it.substringAfter('/') }.toSet()
            target.all.keys.filter { it !in keep }.forEach { editor.remove(it) }
            prefs.optJSONObject(base)?.let { BackupFormat.jsonToPrefs(it, editor, ::text) }
            editor.commit()
        }

        val share = ResidentShare.localize(
            ResidentShare.decode(root.optJSONObject("share") ?: JSONObject()) { MediaRefs.fromTokens(it, paths, json = true) },
            localId
        )
        SyncState.replacePlaceGroups(app, placeId, share, localId)
        fi.aikapolku.app.data.CategoryStore.importFrom(app, root.optJSONObject("categories"))

        val name = root.optString("name")
        val profile = ProfileStore.list(app).firstOrNull { it.id == localId }
        if (profile != null && name.isNotBlank()) {
            ProfileStore.update(app, profile.copy(name = name, photo = text(root.optString("photo"))))
        }
        fi.aikapolku.app.week.EventReminders.reschedule(app)
        return Applied(name, share.groups.size, missing)
    }

    /** Vain tiivisteen nimi ja lyhyt pääte: ei polkuja eikä erikoismerkkejä. */
    private val SAFE_NAME = Regex("[0-9a-f]{64}(\\.[a-z0-9]{1,5})?")
}

/**
 * Asukkaan puhelimessa: mitkä ryhmät tulivat miltäkin paikalta, ja paikan ryhmien korvaus
 * uudella paketilla. Laitteen omat (paikattomat) ryhmät säilyvät.
 */
internal object SyncState {
    private const val PREFS = "aikapolku_sync_v1"
    private const val KEY_ORIGIN = "origin"

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Paikka → sen tuomat ryhmätunnukset. */
    fun origins(context: Context): Map<String, Set<String>> {
        val obj = runCatching { JSONObject(prefs(context).getString(KEY_ORIGIN, null) ?: "{}") }.getOrElse { JSONObject() }
        return obj.keys().asSequence().associateWith { k ->
            obj.optJSONArray(k)?.let { a -> (0 until a.length()).map { a.optString(it) }.toSet() }.orEmpty()
        }
    }

    private fun saveOrigins(context: Context, value: Map<String, Set<String>>) {
        val obj = JSONObject()
        value.forEach { (k, v) -> obj.put(k, JSONArray(v.toList())) }
        prefs(context).edit().putString(KEY_ORIGIN, obj.toString()).commit()
    }

    fun replacePlaceGroups(context: Context, placeId: String, share: ResidentShare, localId: String = ProfileStore.currentId(context)) {
        val store = GroupStore(context)
        val origins = origins(context)
        val previous = origins[placeId].orEmpty()
        val incoming = share.groups.map { it.id }.toSet()
        // Toisen paikan tai laitteen oman (paikattoman) ryhmän samalla tunnuksella ei korvata.
        val fromPlaces = origins.values.flatten().toSet()
        val foreign = origins.filterKeys { it != placeId }.values.flatten().toSet() +
            (store.load().map { it.id }.toSet() - fromPlaces)
        val accepted = share.groups.filter { it.id !in foreign }
        store.save(store.load().filterNot { it.id in previous || it.id in incoming && it.id !in foreign } + accepted)
        val ids = accepted.map { it.id }.toSet()
        store.replaceShared(
            removeIds = previous + ids,
            changes = share.groupChanges.filterKeys { it in ids },
            absences = share.absences.filterKeys { it in ids },
            leaves = share.leaves,
            memberId = localId
        )
        saveOrigins(context, origins + (placeId to ids))
    }
}
