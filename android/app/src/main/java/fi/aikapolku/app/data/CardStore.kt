package fi.aikapolku.app.data

import android.content.Context
import android.net.Uri
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

internal data class CardItem(
    val id: String,
    val name: String,
    val description: String,
    val photoPath: String,
    val voicePath: String = "",
    val autoPlayVoice: Boolean = false
)

internal data class FirstThenSelection(
    val firstName: String = "Askartelu",
    val nextName: String = "Ruokailu",
    val firstPhoto: String = "",
    val nextPhoto: String = "",
    val firstDescription: String = "",
    val nextDescription: String = "",
    val firstVoice: String = "",
    val nextVoice: String = "",
    val firstVoiceAuto: Boolean = false,
    val nextVoiceAuto: Boolean = false
)

internal class CardStore(context: Context) {
    private val prefs = context.getSharedPreferences(
        "aikapolku_cards_v1",
        Context.MODE_PRIVATE
    )

    fun loadCards(): List<CardItem> {
        val array = runCatching {
            JSONArray(prefs.getString("cards", "[]") ?: "[]")
        }.getOrElse { JSONArray() }

        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val id = item.optString("id")
                val name = item.optString("name")

                if (id.isNotBlank() && name.isNotBlank()) {
                    add(
                        CardItem(
                            id = id,
                            name = name,
                            description = item.optString("description"),
                            photoPath = item.optString("photoPath"),
                            voicePath = item.optString("voicePath"),
                            autoPlayVoice = item.optBoolean("autoPlayVoice", false)
                        )
                    )
                }
            }
        }
    }

    private fun writeCards(cards: List<CardItem>) {
        val array = JSONArray()

        cards.forEach { card ->
            array.put(
                JSONObject().apply {
                    put("id", card.id)
                    put("name", card.name)
                    put("description", card.description)
                    put("photoPath", card.photoPath)
                    put("voicePath", card.voicePath)
                    put("autoPlayVoice", card.autoPlayVoice)
                }
            )
        }

        prefs.edit()
            .putString("cards", array.toString())
            .apply()
    }

    fun upsert(card: CardItem) {
        val cards = loadCards().toMutableList()
        val index = cards.indexOfFirst { it.id == card.id }

        if (index >= 0) {
            cards[index] = card
        } else {
            cards.add(card)
        }

        writeCards(cards)
    }

    fun delete(id: String) {
        writeCards(loadCards().filterNot { it.id == id })
    }

    fun loadSelection(): FirstThenSelection {
        val obj = runCatching {
            JSONObject(
                prefs.getString("selection", "{}") ?: "{}"
            )
        }.getOrElse { JSONObject() }

        return FirstThenSelection(
            firstName = obj.optString("firstName", "Askartelu"),
            nextName = obj.optString("nextName", "Ruokailu"),
            firstPhoto = obj.optString("firstPhoto"),
            nextPhoto = obj.optString("nextPhoto"),
            firstDescription = obj.optString("firstDescription"),
            nextDescription = obj.optString("nextDescription"),
            firstVoice = obj.optString("firstVoice"),
            nextVoice = obj.optString("nextVoice"),
            firstVoiceAuto = obj.optBoolean("firstVoiceAuto", false),
            nextVoiceAuto = obj.optBoolean("nextVoiceAuto", false)
        )
    }

    fun saveSelection(selection: FirstThenSelection) {
        val obj = JSONObject().apply {
            put("firstName", selection.firstName)
            put("nextName", selection.nextName)
            put("firstPhoto", selection.firstPhoto)
            put("nextPhoto", selection.nextPhoto)
            put("firstDescription", selection.firstDescription)
            put("nextDescription", selection.nextDescription)
            put("firstVoice", selection.firstVoice)
            put("nextVoice", selection.nextVoice)
            put("firstVoiceAuto", selection.firstVoiceAuto)
            put("nextVoiceAuto", selection.nextVoiceAuto)
        }

        prefs.edit()
            .putString("selection", obj.toString())
            .apply()
    }
}

internal fun importCardPhoto(
    context: Context,
    uri: Uri
): String {
    val folder = File(context.filesDir, "activity_images")

    if (!folder.exists() && !folder.mkdirs()) {
        error("Kuvakansiota ei voitu luoda")
    }

    val destination = File.createTempFile(
        "card_",
        ".image",
        folder
    )

    try {
        val source = context.contentResolver.openInputStream(uri)
            ?: error("Kuvaa ei voitu avata")

        source.use { input ->
            destination.outputStream().use { output ->
                val buffer = ByteArray(8192)
                var total = 0L

                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count

                    if (total > 20L * 1024L * 1024L) {
                        error("Kuva on liian suuri (yli 20 Mt)")
                    }

                    output.write(buffer, 0, count)
                }
            }
        }

        return destination.absolutePath
    } catch (error: Exception) {
        destination.delete()
        throw error
    }
}
