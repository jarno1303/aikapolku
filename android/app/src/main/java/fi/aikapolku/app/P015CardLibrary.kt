package fi.aikapolku.app

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

internal data class P015Card(
    val id: String,
    val name: String,
    val description: String,
    val photoPath: String,
    val voicePath: String = "",
    val autoPlayVoice: Boolean = false
)

internal data class P015Selection(
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

internal class P015CardStore(context: Context) {
    private val prefs = context.getSharedPreferences(
        "aikapolku_cards_v1",
        Context.MODE_PRIVATE
    )

    fun loadCards(): List<P015Card> {
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
                        P015Card(
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

    private fun writeCards(cards: List<P015Card>) {
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

    fun upsert(card: P015Card) {
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

    fun loadSelection(): P015Selection {
        val obj = runCatching {
            JSONObject(
                prefs.getString("selection", "{}") ?: "{}"
            )
        }.getOrElse { JSONObject() }

        return P015Selection(
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

    fun saveSelection(selection: P015Selection) {
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

private fun p015CopyPhoto(
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

@Composable
internal fun P015CardLibraryDialog(
    onDismiss: () -> Unit,
    onSelect: (P015Card) -> Unit
) {
    val context = LocalContext.current
    val store = remember(context) {
        P015CardStore(context.applicationContext)
    }

    var cards by remember {
        mutableStateOf(store.loadCards())
    }

    var editing by remember { mutableStateOf(false) }
    var editingId by remember { mutableStateOf<String?>(null) }
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var photoPath by remember { mutableStateOf("") }
    var voicePath by remember { mutableStateOf("") }
    var autoPlayVoice by remember { mutableStateOf(false) }
    var showBuiltinPicker by remember { mutableStateOf(false) }
    var fromTemplate by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var deleteCandidate by remember {
        mutableStateOf<P015Card?>(null)
    }

    fun openEditor(card: P015Card?) {
        editingId = card?.id?.takeIf { it.isNotBlank() }
        name = card?.name ?: ""
        description = card?.description ?: ""
        photoPath = card?.photoPath ?: ""
        voicePath = card?.voicePath ?: ""
        autoPlayVoice = card?.autoPlayVoice ?: false
        errorMessage = ""
        editing = true
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            try {
                photoPath = p015CopyPhoto(context, uri)
                errorMessage = ""
            } catch (error: Exception) {
                errorMessage = "Kuvan tallentaminen ei onnistunut."
            }
        }
    }

    if (!editing && deleteCandidate == null) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Omat toimintokortit") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 430.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { openEditor(null) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("+ Uusi toimintokortti")
                    }
                    OutlinedButton(
                        onClick = {
                            fromTemplate = true
                            showBuiltinPicker = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Tee valmiista kortista oma")
                    }

                    if (cards.isEmpty()) {
                        Text(
                            "Ei vielä tallennettuja kortteja. " +
                            "Luo ensimmäinen kortti.",
                            color = Color(0xFF526773)
                        )
                    } else {
                        Column(
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement =
                                Arrangement.spacedBy(12.dp)
                        ) {
                            cards.forEach { card ->
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor =
                                            MaterialTheme.colorScheme.surface
                                    ),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(10.dp)
                                    ) {
                                        Row(
                                            verticalAlignment =
                                                Alignment.CenterVertically,
                                            horizontalArrangement =
                                                Arrangement.spacedBy(10.dp)
                                        ) {
                                            P011Photo(
                                                name = card.name,
                                                imagePath = card.photoPath,
                                                modifier = Modifier
                                                    .size(62.dp)
                                                    .clip(
                                                        RoundedCornerShape(
                                                            10.dp
                                                        )
                                                    )
                                            )

                                            Column(
                                                modifier =
                                                    Modifier.weight(1f)
                                            ) {
                                                Text(
                                                    card.name,
                                                    fontWeight =
                                                        FontWeight.Bold,
                                                    maxLines = 2,
                                                    overflow =
                                                        TextOverflow.Ellipsis
                                                )

                                                if (
                                                    card.description
                                                        .isNotBlank()
                                                ) {
                                                    Text(
                                                        card.description,
                                                        fontSize = 12.sp,
                                                        maxLines = 2,
                                                        overflow =
                                                            TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                        }

                                        Row(
                                            horizontalArrangement =
                                                Arrangement.spacedBy(3.dp)
                                        ) {
                                            TextButton(
                                                onClick = {
                                                    onSelect(card)
                                                }
                                            ) {
                                                Text("Valitse")
                                            }

                                            TextButton(
                                                onClick = {
                                                    openEditor(card)
                                                }
                                            ) {
                                                Text("Muokkaa")
                                            }
                                            TextButton(
                                                onClick = {
                                                    openEditor(card.copy(id = ""))
                                                }
                                            ) {
                                                Text("Kopioi")
                                            }

                                            TextButton(
                                                onClick = {
                                                    deleteCandidate = card
                                                }
                                            ) {
                                                Text("Poista")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onDismiss) {
                    Text("Sulje")
                }
            }
        )
    }

    if (editing) {
        AlertDialog(
            onDismissRequest = { editing = false },
            title = {
                Text(
                    if (editingId == null) "Uusi kortti"
                    else "Muokkaa korttia"
                )
            },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 440.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(80) },
                        label = { Text("Toiminnon nimi") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = description,
                        onValueChange = {
                            description = it.take(240)
                        },
                        label = { Text("Kuvaus (valinnainen)") },
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (photoPath.isNotBlank()) {
                        P011Photo(
                            name = name,
                            imagePath = photoPath,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(110.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            fromTemplate = false
                            showBuiltinPicker = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Valitse valmis kuva")
                    }

                    OutlinedButton(
                        onClick = {
                            picker.launch(
                                PickVisualMediaRequest(
                                    ActivityResultContracts
                                        .PickVisualMedia.ImageOnly
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Valitse valokuva")
                    }

                    if (photoPath.isNotBlank()) {
                        TextButton(
                            onClick = { photoPath = "" }
                        ) {
                            Text("Poista kortin kuva")
                        }
                    }

                    P017VoiceControls(
                        voicePath = voicePath,
                        autoPlay = autoPlayVoice,
                        onVoicePathChange = { voicePath = it },
                        onAutoPlayChange = { autoPlayVoice = it }
                    )

                    if (errorMessage.isNotBlank()) {
                        Text(
                            errorMessage,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = name.isNotBlank(),
                    onClick = {
                        val savedCard = P015Card(
                            id = editingId
                                ?: UUID.randomUUID().toString(),
                            name = name.trim(),
                            description = description.trim(),
                            photoPath = photoPath,
                            voicePath = voicePath,
                            autoPlayVoice = autoPlayVoice
                        )

                        store.upsert(savedCard)
                        cards = store.loadCards()
                        editing = false

                        // Paivitetaan myos aktiivinen kortti.
                        onSelect(savedCard)
                    }
                ) {
                    Text("Tallenna ja valitse")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { editing = false }
                ) {
                    Text("Peruuta")
                }
            }
        )
    }

    if (showBuiltinPicker) {
        P017bBuiltinPicker(
            onDismiss = { showBuiltinPicker = false },
            onSelect = { template ->
                if (fromTemplate) {
                    openEditor(null)
                    name = template
                }
                photoPath = "builtin:$template"
                showBuiltinPicker = false
                fromTemplate = false
            }
        )
    }

    val target = deleteCandidate

    if (target != null) {
        AlertDialog(
            onDismissRequest = {
                deleteCandidate = null
            },
            title = {
                Text("Poistetaanko kortti?")
            },
            text = {
                Text(
                    "Kortti '${target.name}' poistetaan " +
                    "korttikirjastosta."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        store.delete(target.id)
                        cards = store.loadCards()
                        deleteCandidate = null
                    }
                ) {
                    Text("Poista")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        deleteCandidate = null
                    }
                ) {
                    Text("Peruuta")
                }
            }
        )
    }
}