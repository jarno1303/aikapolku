package fi.aikapolku.app

import android.content.Context
import android.content.SharedPreferences
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/**
 * Sovelluksen asetustiedostojen talteenotto ja palautus (kaikki SharedPreferences-tiedostot).
 * Talteenotto tallentuu myös tiedostoon, joten keskeytynyt ajo palautetaan seuraavalla kerralla.
 */
internal object PrefsSnapshot {
    private const val FILE = "kuvakaappaus-talteen.json"

    private fun file(ctx: Context) = File(ctx.filesDir, FILE)
    private fun names(ctx: Context): List<String> =
        File(ctx.applicationInfo.dataDir, "shared_prefs").listFiles()
            ?.filter { it.name.endsWith(".xml") }?.map { it.name.removeSuffix(".xml") }.orEmpty()

    private fun prefs(ctx: Context, name: String): SharedPreferences = ctx.getSharedPreferences(name, Context.MODE_PRIVATE)

    fun restoreLeftover(ctx: Context) {
        if (file(ctx).exists()) restore(ctx)
    }

    /** Ottaa talteen ja tyhjentää kaikki asetustiedostot. */
    fun save(ctx: Context) {
        val all = JSONObject()
        names(ctx).forEach { name ->
            val values = JSONObject()
            prefs(ctx, name).all.forEach { (key, v) ->
                val typed = when (v) {
                    is String -> JSONObject().put("t", "s").put("v", v)
                    is Int -> JSONObject().put("t", "i").put("v", v)
                    is Long -> JSONObject().put("t", "l").put("v", v)
                    is Boolean -> JSONObject().put("t", "b").put("v", v)
                    is Float -> JSONObject().put("t", "f").put("v", v.toDouble())
                    is Set<*> -> JSONObject().put("t", "set").put("v", JSONArray(v.map { it.toString() }))
                    else -> null
                }
                if (typed != null) values.put(key, typed)
            }
            all.put(name, values)
        }
        file(ctx).writeText(all.toString())
        names(ctx).forEach { prefs(ctx, it).edit().clear().commit() }
    }

    /** Palauttaa talteen otetut arvot; esimerkin luomat tiedostot tyhjennetään ja poistetaan. */
    fun restore(ctx: Context) {
        val f = file(ctx)
        if (!f.exists()) return
        val all = JSONObject(f.readText())
        names(ctx).forEach { name ->
            prefs(ctx, name).edit().clear().commit()
            if (!all.has(name)) ctx.deleteSharedPreferences(name)
        }
        all.keys().forEach { name ->
            val values = all.getJSONObject(name)
            val e = prefs(ctx, name).edit().clear()
            values.keys().forEach { key ->
                val o = values.getJSONObject(key)
                when (o.getString("t")) {
                    "s" -> e.putString(key, o.getString("v"))
                    "i" -> e.putInt(key, o.getInt("v"))
                    "l" -> e.putLong(key, o.getLong("v"))
                    "b" -> e.putBoolean(key, o.getBoolean("v"))
                    "f" -> e.putFloat(key, o.getDouble("v").toFloat())
                    "set" -> {
                        val arr = o.getJSONArray("v")
                        e.putStringSet(key, (0 until arr.length()).map { arr.getString(it) }.toSet())
                    }
                }
            }
            e.commit()
        }
        f.delete()
    }
}
