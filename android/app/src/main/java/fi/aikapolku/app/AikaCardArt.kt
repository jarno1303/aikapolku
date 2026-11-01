package fi.aikapolku.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.util.Locale

/** UI Refresh 1.0: one built-in picture source across screens and PDF. */
internal object AikaCardArt {
    internal data class Template(
        val id: String,
        val name: String,
        val category: String,
        val drawable: Int
    )

    val templates: List<Template> = listOf(
        Template("breakfast", "Aamupala", "Ruokailu", R.drawable.ap_card_breakfast),
        Template("lunch", "Ruokailu", "Ruokailu", R.drawable.ap_card_lunch),
        Template("snack", "Välipala", "Ruokailu", R.drawable.ap_card_snack),
        Template("dress", "Pukeutuminen", "Omat toimet", R.drawable.ap_card_dress),
        Template("wash_hands", "Käsienpesu", "Omat toimet", R.drawable.ap_card_wash_hands),
        Template("toilet", "WC", "Omat toimet", R.drawable.ap_card_toilet),
        Template("brush_teeth", "Hampaiden pesu", "Omat toimet", R.drawable.ap_card_brush_teeth),
        Template("outdoors", "Ulkoilu", "Liikunta", R.drawable.ap_card_outdoors),
        Template("exercise", "Liikunta", "Liikunta", R.drawable.ap_card_exercise),
        Template("crafts", "Askartelu", "Tekeminen", R.drawable.ap_card_crafts),
        Template("gaming", "Pelaaminen", "Tekeminen", R.drawable.ap_card_gaming),
        Template("reading", "Lukeminen", "Tekeminen", R.drawable.ap_card_reading),
        Template("music", "Musiikki", "Tekeminen", R.drawable.ap_card_music),
        Template("schoolwork", "Koulutyö", "Koulu", R.drawable.ap_card_schoolwork),
        Template("homework", "Kotitehtävät", "Koulu", R.drawable.ap_card_homework),
        Template("break", "Tauko", "Siirtymät", R.drawable.ap_card_break),
        Template("waiting", "Odota", "Siirtymät", R.drawable.ap_card_waiting),
        Template("leaving", "Lähtö", "Siirtymät", R.drawable.ap_card_leaving),
        Template("rest", "Lepo", "Lepo", R.drawable.ap_card_rest),
        Template("sleep", "Nukkumaan", "Lepo", R.drawable.ap_card_sleep),
        Template("morning_meeting", "Aamukokous", "Ryhmätoiminta", R.drawable.ap_card_morning_meeting)
    )

    val names: List<String> get() = templates.map { it.name }

    fun imageRes(name: String, imagePath: String = ""): Int? {
        val key = if (imagePath.startsWith("builtin:")) {
            imagePath.removePrefix("builtin:")
        } else name
        val normalized = key.trim().lowercase(Locale.ROOT)
        val alternative = when (normalized) {
            "tehtävät" -> "schoolwork" // Legacy card label.
            "aamupiiri" -> "morning_meeting"
            "nukkuminen" -> "sleep"
            else -> normalized
        }
        return templates.firstOrNull {
            it.name.lowercase(Locale.ROOT) == alternative || it.id == alternative
        }?.drawable
    }

    fun bitmap(context: Context, name: String, imagePath: String = ""): Bitmap? {
        val res = imageRes(name, imagePath) ?: return null
        return BitmapFactory.decodeResource(context.resources, res)
    }
}
