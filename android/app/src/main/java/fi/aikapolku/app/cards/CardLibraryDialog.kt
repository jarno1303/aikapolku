package fi.aikapolku.app.cards

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fi.aikapolku.app.R
import fi.aikapolku.app.caregiver.CaregiverSession
import fi.aikapolku.app.data.CardItem
import fi.aikapolku.app.data.CardStore
import fi.aikapolku.app.data.importCardPhoto
import fi.aikapolku.app.voice.VoiceControls
import java.util.UUID

@Composable
internal fun CardLibraryDialog(
    onDismiss: () -> Unit,
    onSelect: (CardItem) -> Unit
) {
    val context = LocalContext.current
    val store = remember(context) {
        CardStore(context.applicationContext)
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
        mutableStateOf<CardItem?>(null)
    }

    fun openEditor(card: CardItem?) {
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
                photoPath = importCardPhoto(context, uri)
                errorMessage = ""
            } catch (error: Exception) {
                errorMessage = context.getString(R.string.kuvan_tallentaminen_ei_onnistunut)
            }
        }
    }

    if (!editing && deleteCandidate == null) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.omat_toimintokortit)) },
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
                        Text(stringResource(R.string.uusi_toimintokortti))
                    }
                    OutlinedButton(
                        onClick = {
                            fromTemplate = true
                            showBuiltinPicker = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.tee_valmiista_kortista_oma))
                    }

                    if (cards.isEmpty()) {
                        Text(
                            stringResource(R.string.ei_viela_tallennettuja_kortteja_luo),
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
                                            CardPhoto(
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
                                                Text(stringResource(R.string.valitse))
                                            }

                                            TextButton(
                                                onClick = {
                                                    openEditor(card)
                                                }
                                            ) {
                                                Text(stringResource(R.string.muokkaa))
                                            }
                                            TextButton(
                                                onClick = {
                                                    openEditor(card.copy(id = ""))
                                                }
                                            ) {
                                                Text(stringResource(R.string.kopioi))
                                            }

                                            TextButton(
                                                onClick = {
                                                    deleteCandidate = card
                                                }
                                            ) {
                                                Text(stringResource(R.string.poista))
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
                    Text(stringResource(R.string.sulje))
                }
            }
        )
    }

    if (editing) {
        AlertDialog(
            onDismissRequest = { editing = false },
            title = {
                Text(
                    if (editingId == null) stringResource(R.string.uusi_kortti)
                    else stringResource(R.string.muokkaa_korttia)
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
                        label = { Text(stringResource(R.string.toiminnon_nimi)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = description,
                        onValueChange = {
                            description = it.take(240)
                        },
                        label = { Text(stringResource(R.string.kuvaus_valinnainen)) },
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (photoPath.isNotBlank()) {
                        CardPhoto(
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
                        Text(stringResource(R.string.valitse_valmis_kuva))
                    }

                    OutlinedButton(
                        onClick = {
                            CaregiverSession.keepOpenForOwnPicker()
                            picker.launch(
                                PickVisualMediaRequest(
                                    ActivityResultContracts
                                        .PickVisualMedia.ImageOnly
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.valitse_valokuva))
                    }

                    if (photoPath.isNotBlank()) {
                        TextButton(
                            onClick = { photoPath = "" }
                        ) {
                            Text(stringResource(R.string.poista_kortin_kuva))
                        }
                    }

                    VoiceControls(
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
                        val savedCard = CardItem(
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
                    Text(stringResource(R.string.tallenna_ja_valitse))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { editing = false }
                ) {
                    Text(stringResource(R.string.peruuta))
                }
            }
        )
    }

    if (showBuiltinPicker) {
        BuiltinCardPicker(
            onDismiss = { showBuiltinPicker = false },
            onSelect = { templateId, templateName ->
                if (fromTemplate) {
                    openEditor(null)
                    name = templateName
                }
                photoPath = "builtin:$templateId"
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
                Text(stringResource(R.string.poistetaanko_kortti))
            },
            text = {
                Text(
                    stringResource(R.string.kortti_poistetaan_korttikirjastosta, target.name)
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
                    Text(stringResource(R.string.poista))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        deleteCandidate = null
                    }
                ) {
                    Text(stringResource(R.string.peruuta))
                }
            }
        )
    }
}
