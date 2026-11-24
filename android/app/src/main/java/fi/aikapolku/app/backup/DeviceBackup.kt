package fi.aikapolku.app.backup

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import fi.aikapolku.app.link.KeyVault
import fi.aikapolku.app.link.LinkStore
import java.io.File
import java.time.LocalDate
import org.json.JSONObject

/**
 * Koko laitteen salattu varmuuskopio (paikan laite): kaikki asetustiedostot ja omat kuvat ja
 * äänitteet tunnuksineen, jotta hajonneen laitteen voi korvata uudella. Toisin kuin vienti
 * (BackupExporter), tämä säilyttää tunnukset, yksiköt ja liitokset, ja palautus korvaa kaiken.
 * Laiteliitosten avaimet kirjoitetaan pakettiin avattuina (Keystore on laitekohtainen), joten
 * paketti on aina salasanalla salattu.
 */
internal object DeviceBackup {
    const val FORMAT = "aikapolku-device"
    const val VERSION = 1
    private const val DATA = "device.json"
    private const val FILES = "files/"
    /** Ei mukaan: automaattisen varmuuskopion omat asetukset (salasana ja kansio) ja tilapäiset. */
    private val SKIPPED_PREFS = setOf(AutoBackup.PREFS)

    private fun prefNames(context: Context): List<String> =
        File(context.dataDir, "shared_prefs").listFiles()
            ?.filter { it.name.endsWith(".xml") }?.map { it.name.removeSuffix(".xml") }.orEmpty()

    fun create(context: Context, password: String): ByteArray {
        require(password.length >= BackupArchive.MIN_PASSWORD)
        val app = context.applicationContext
        val prefs = JSONObject()
        prefNames(app).filter { it !in SKIPPED_PREFS }.forEach { name ->
            val obj = BackupFormat.prefsToJson(name, app.getSharedPreferences(name, Context.MODE_PRIVATE)) { it }
            if (name == LinkStore.PREFS) {
                // Avaimet avattuina (Keystore ei siirry laitteelta toiselle). Jos avaus ei onnistu,
                // liitokset jätetään pois (ne tehdään uudelleen) eikä mukaan tule avaamatonta salausta.
                obj.optJSONObject("peers")?.let { e ->
                    val plain = KeyVault.open(e.optString("v"))
                    if (plain != null) e.put("v", plain) else obj.remove("peers")
                }
                obj.remove("peers_broken")
            }
            prefs.put(name, obj)
        }
        val root = JSONObject()
            .put("format", FORMAT)
            .put("version", VERSION)
            .put("created", LocalDate.now().toString())
            .put("filesDir", app.filesDir.absolutePath)
            .put("prefs", prefs)
        val entries = LinkedHashMap<String, ByteArray>()
        entries[DATA] = root.toString().toByteArray(Charsets.UTF_8)
        val base = app.filesDir.canonicalFile
        var total = 0L
        base.walkTopDown().filter { it.isFile && !it.name.endsWith(".tmp") }.forEach { f ->
            val rel = f.canonicalFile.relativeTo(base).invariantSeparatorsPath
            if (rel.startsWith("..")) return@forEach
            total += f.length()
            if (total > BackupArchive.MAX_TOTAL_BYTES || entries.size >= BackupArchive.MAX_ENTRIES - 1) {
                throw BackupException(BackupException.Reason.TOO_LARGE)
            }
            entries[FILES + rel] = f.readBytes()
        }
        return BackupArchive.pack(entries, password)
    }

    /** Onko tiedosto koko laitteen varmuuskopio (eikä tavallinen vienti). Heittää salasanavirheen. */
    fun read(bytes: ByteArray, password: String): Map<String, ByteArray> {
        // Koko laitteen kopio on aina salattu: salaamatonta ei palauteta.
        if (!BackupArchive.isEncrypted(bytes)) throw BackupException(BackupException.Reason.NOT_AIKAPOLKU)
        val entries = BackupArchive.open(bytes, password)
        val root = entries[DATA]?.let { runCatching { JSONObject(String(it, Charsets.UTF_8)) }.getOrNull() }
            ?: throw BackupException(BackupException.Reason.NOT_AIKAPOLKU)
        if (root.optString("format") != FORMAT) throw BackupException(BackupException.Reason.NOT_AIKAPOLKU)
        if (root.optInt("version", 0) > VERSION) throw BackupException(BackupException.Reason.TOO_NEW)
        return entries
    }

    /**
     * Korvaa laitteen kaikki tiedot varmuuskopiolla [entries] (luettu [read]illä). Kutsujan
     * vastuulla on käynnistää sovellus uudelleen ([restart]).
     */
    fun restore(context: Context, entries: Map<String, ByteArray>) {
        val app = context.applicationContext
        // Taustapalvelut eivät saa kirjoittaa palautuksen aikana.
        fi.aikapolku.app.link.LinkReceiverService.stop(app)
        fi.aikapolku.app.link.PlaceSync.stop()
        val root = JSONObject(String(entries.getValue(DATA), Charsets.UTF_8))
        val oldDir = root.optString("filesDir")
        val newDir = app.filesDir.absolutePath
        fun text(value: String): String =
            if (oldDir.isEmpty() || oldDir == newDir) value
            else value.replace(MediaRefs.jsonEscaped(oldDir), MediaRefs.jsonEscaped(newDir)).replace(oldDir, newDir)

        // Ensin tiedostot: polku tarkistetaan kansion sisälle (ei ../ -polkuja).
        val base = app.filesDir.canonicalFile
        base.listFiles()?.forEach { it.deleteRecursively() }
        entries.filterKeys { it.startsWith(FILES) }.forEach { (key, data) ->
            val target = File(base, key.removePrefix(FILES)).canonicalFile
            if (!target.path.startsWith(base.path + File.separator)) return@forEach
            target.parentFile?.mkdirs()
            target.writeBytes(data)
        }
        val prefs = root.optJSONObject("prefs") ?: JSONObject()
        prefNames(app).filter { it !in SKIPPED_PREFS }.forEach { name ->
            app.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
        }
        // Liitosten avaimet ovat varmuuskopiossa avattuina: ne otetaan erikseen, jotta salaamaton
        // arvo ei koskaan päädy levylle, ja salataan suoraan tämän laitteen Keystoren avaimella.
        var restoredPeers: String? = null
        prefs.keys().forEach { name ->
            if (name in SKIPPED_PREFS || !SAFE_PREF.matches(name)) return@forEach
            val obj = prefs.optJSONObject(name) ?: return@forEach
            if (name == LinkStore.PREFS) {
                restoredPeers = obj.optJSONObject("peers")?.takeIf { it.optString("t") == "s" }?.optString("v")
                obj.remove("peers")
                obj.remove("peers_broken")
            }
            val editor = app.getSharedPreferences(name, Context.MODE_PRIVATE).edit()
            BackupFormat.jsonToPrefs(obj, editor, ::text)
            editor.commit()
        }
        LinkStore(app).restorePeers(restoredPeers)
    }

    private val SAFE_PREF = Regex("[A-Za-z0-9_.-]{1,120}")

    /** Sovellus alusta (tiedot luetaan uudelleen). */
    fun restart(context: Context) {
        val app = context.applicationContext
        val intent = app.packageManager.getLaunchIntentForPackage(app.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        if (intent != null) {
            // Käynnistys hälytyksellä hetken päästä: prosessin lopetus ei peru sitä.
            val pending = android.app.PendingIntent.getActivity(
                app, 4711, intent, android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_CANCEL_CURRENT
            )
            app.getSystemService(android.app.AlarmManager::class.java)
                ?.set(android.app.AlarmManager.RTC, System.currentTimeMillis() + 400, pending)
        }
        Runtime.getRuntime().exit(0)
    }

    /** T6: poistaa kaikki sovelluksen tiedot tästä laitteesta (Android sulkee sovelluksen). */
    fun eraseAll(context: Context): Boolean =
        context.getSystemService(ActivityManager::class.java)?.clearApplicationUserData() == true
}

/**
 * Automaattinen varmuuskopio (paikan laite): kerran vuorokaudessa koko laitteen salattu kopio
 * ohjaajan valitsemaan kansioon (esim. muistikortti tai USB-tikku). Seitsemän uusinta säilyy.
 */
internal object AutoBackup {
    const val PREFS = "aikapolku_autobackup_v1"
    private const val KEY_TREE = "tree"
    private const val KEY_PW = "pw"
    private const val KEY_LAST = "last"
    private const val KEY_ERROR = "error"
    private const val KEY_ATTEMPT = "attempt"
    /** Epäonnistuneen yrityksen jälkeen uusi yritys aikaisintaan tunnin päästä. */
    private const val RETRY_MS = 60L * 60 * 1000
    private val running = java.util.concurrent.atomic.AtomicBoolean(false)
    private const val INTERVAL_MS = 20L * 60 * 60 * 1000
    private const val KEEP = 7
    private const val PREFIX = "AikaPolku_laite_"

    var revision by mutableIntStateOf(0)
        private set

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun folder(context: Context): Uri? = prefs(context).getString(KEY_TREE, null)?.let(Uri::parse)
    fun last(context: Context): Long = prefs(context).getLong(KEY_LAST, 0L)
    fun lastError(context: Context): String = prefs(context).getString(KEY_ERROR, "") ?: ""
    fun enabled(context: Context): Boolean = folder(context) != null && prefs(context).contains(KEY_PW)

    /** false = salasanaa ei voitu salata: automaattista kopiota ei otettu käyttöön (salasanaa ei tallenneta salaamatta). */
    fun configure(context: Context, tree: Uri, password: String): Boolean {
        val sealed = KeyVault.seal(password) ?: return false
        prefs(context).edit().putString(KEY_TREE, tree.toString()).putString(KEY_PW, sealed).commit()
        revision++
        return true
    }

    fun disable(context: Context) {
        prefs(context).edit().clear().commit()
        revision++
    }

    private fun due(context: Context): Boolean {
        val now = System.currentTimeMillis()
        return enabled(context) && now - last(context) >= INTERVAL_MS &&
            now - prefs(context).getLong(KEY_ATTEMPT, 0L) >= RETRY_MS
    }

    /** Sovelluksen avautuessa: kopio, jos edellisestä on yli 20 tuntia (taustasäikeessä, yksi kerrallaan). */
    fun runIfDue(context: Context) {
        val app = context.applicationContext
        if (!due(app) || !running.compareAndSet(false, true)) return
        Thread({
            try {
                if (due(app)) runNow(app)
            } catch (_: Throwable) {
            } finally {
                running.set(false)
            }
        }, "aikapolku-autobackup").apply { isDaemon = true }.start()
    }

    @Synchronized
    fun runNow(context: Context): Boolean {
        val app = context.applicationContext
        val tree = folder(app) ?: return false
        prefs(app).edit().putLong(KEY_ATTEMPT, System.currentTimeMillis()).commit()
        val password = prefs(app).getString(KEY_PW, null)?.let { KeyVault.open(it) }
        val ok = runCatching {
            requireNotNull(password)
            val bytes = DeviceBackup.create(app, password)
            val resolver = app.contentResolver
            val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            val name = PREFIX + LocalDate.now() + ".aikapolku"
            // Ensin uusi kopio väliaikaisella nimellä; vasta onnistuneen kirjoituksen jälkeen saman
            // päivän vanha pois ja nimi oikeaksi (epäonnistuminen ei vie hyvää kopiota).
            val part = DocumentsContract.createDocument(resolver, parent, "application/octet-stream", "$name.part")
                ?: error("createDocument")
            val written = runCatching { resolver.openOutputStream(part)?.use { it.write(bytes) } ?: error("openOutputStream") }
            if (written.isFailure) {
                runCatching { DocumentsContract.deleteDocument(resolver, part) }
                throw written.exceptionOrNull()!!
            }
            children(app, tree).filter { it.second == name }.forEach { DocumentsContract.deleteDocument(resolver, it.first) }
            DocumentsContract.renameDocument(resolver, part, name)
            // Seitsemän uusinta säilyy.
            children(app, tree).filter { it.second.startsWith(PREFIX) && it.second.endsWith(".aikapolku") }
                .sortedByDescending { it.second }.drop(KEEP)
                .forEach { DocumentsContract.deleteDocument(resolver, it.first) }
        }
        prefs(app).edit().apply {
            if (ok.isSuccess) putLong(KEY_LAST, System.currentTimeMillis()).putString(KEY_ERROR, "")
            else putString(KEY_ERROR, ok.exceptionOrNull()?.javaClass?.simpleName ?: "virhe")
        }.commit()
        revision++
        return ok.isSuccess
    }

    private fun children(context: Context, tree: Uri): List<Pair<Uri, String>> {
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val result = mutableListOf<Pair<Uri, String>>()
        context.contentResolver.query(
            uri,
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null, null, null
        )?.use { c ->
            while (c.moveToNext()) {
                result += DocumentsContract.buildDocumentUriUsingTree(tree, c.getString(0)) to (c.getString(1) ?: "")
            }
        }
        return result
    }
}
