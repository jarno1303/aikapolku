package fi.aikapolku.app.firstthen

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fi.aikapolku.app.R
import fi.aikapolku.app.cards.CardArt
import fi.aikapolku.app.cards.CardLibraryDialog
import fi.aikapolku.app.cards.CardPhoto
import fi.aikapolku.app.caregiver.AppAction
import fi.aikapolku.app.caregiver.CaregiverLockButton
import fi.aikapolku.app.caregiver.CaregiverSession
import fi.aikapolku.app.caregiver.LocalCaregiver
import fi.aikapolku.app.data.CardItem
import fi.aikapolku.app.data.CardStore
import fi.aikapolku.app.data.importCardPhoto
import fi.aikapolku.app.ui.AppColors

@Composable
internal fun FirstThenScreen(
    first: String,
    next: String,
    firstPhoto: String,
    nextPhoto: String,
    firstDescription: String,
    nextDescription: String,
    firstVoice: String,
    nextVoice: String,
    firstVoiceAuto: Boolean,
    nextVoiceAuto: Boolean,
    onFirst: (String) -> Unit,
    onNext: (String) -> Unit,
    onFirstPhoto: (String) -> Unit,
    onNextPhoto: (String) -> Unit,
    onFirstDescription: (String) -> Unit,
    onNextDescription: (String) -> Unit,
    onFirstVoice: (String, Boolean) -> Unit,
    onNextVoice: (String, Boolean) -> Unit,
    onDone: () -> Unit,
    onStartTimer: (Int) -> Unit
) {
    val context = LocalContext.current
    var choosing by remember { mutableStateOf<String?>(null) }
    var photoTarget by remember { mutableStateOf("first") }
    var editing by remember { mutableStateOf<String?>(null) }
    var editName by remember { mutableStateOf("") }
    var editDescription by remember { mutableStateOf("") }
    var photoError by remember { mutableStateOf("") }
    var showLibrary by remember { mutableStateOf(false) }
    var libraryTarget by remember { mutableStateOf("first") }
    var libraryNotice by remember { mutableStateOf("") }
    var minutes by rememberSaveable { mutableIntStateOf(5) }
    var showCardHelp by rememberSaveable { mutableStateOf(false) }

    // P0-21e: korttien valinta ja muokkaus kuuluvat ohjaajalle, kun lukitus on päällä.
    val caregiver = LocalCaregiver.current
    val canEditCards = caregiver.allows(AppAction.EDIT_CARDS)
    LaunchedEffect(canEditCards) {
        if (!canEditCards) {
            choosing = null
            editing = null
            showLibrary = false
        }
    }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            try {
                val path = importCardPhoto(context, uri)
                if (photoTarget == "first") {
                    onFirstPhoto(path)
                } else {
                    onNextPhoto(path)
                }
                photoError = ""
            } catch (e: Exception) {
                photoError = context.getString(R.string.kuvan_lisaaminen_epaonnistui)
            }
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FlowHeader(
            modifier = Modifier.weight(1f),
            compact = true
        )

        if (!canEditCards) {
            CaregiverLockButton(iconOnly = true)
        }

        IconButton(
            onClick = { showCardHelp = true },
            modifier = Modifier
                .size(48.dp)
                .semantics {
                    contentDescription = context.getString(R.string.korttien_kayttoohje)
                }
        ) {
            Text("ⓘ", fontSize = 26.sp, color = AppColors.Teal)
        }
    }

    if (showCardHelp) {
        AlertDialog(
            onDismissRequest = { showCardHelp = false },
            title = { Text(stringResource(R.string.korttien_kaytto)) },
            text = {
                Text(
                    stringResource(R.string.napauta_ensin_tai_sitten_korttia)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { showCardHelp = false }
                ) {
                    Text(stringResource(R.string.selva))
                }
            }
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        EditableCard(
            label = stringResource(R.string.first_label),
            name = first,
            description = firstDescription,
            imagePath = firstPhoto,
            modifier = Modifier.weight(1f),
            editable = canEditCards,
            onClick = { if (canEditCards) choosing = "first" }
        )
        EditableCard(
            label = stringResource(R.string.then_label),
            name = next,
            description = nextDescription,
            imagePath = nextPhoto,
            modifier = Modifier.weight(1f),
            editable = canEditCards,
            onClick = { if (canEditCards) choosing = "next" }
        )
    }

    HorizontalDivider()

    var timerMode by rememberSaveable {
        mutableStateOf(true)
    }

    Text(
        stringResource(R.string.miten_haluat_edeta),
        fontSize = 19.sp,
        fontWeight = FontWeight.SemiBold,
        color = AppColors.Ink
    )

    FlowModePicker(
        timerSelected = timerMode,
        onTimer = { timerMode = true },
        onNoLimit = { timerMode = false }
    )

    Button(
        onClick = {
            if (timerMode) {
                onStartTimer(5)
            } else {
                onDone()
            }
        },
        enabled =
            first.isNotBlank() &&
            first != stringResource(R.string.choose_activity) &&
            next.isNotBlank() &&
            next != stringResource(R.string.choose_activity),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = AppColors.Teal
        )
    ) {
        Text(
            if (timerMode) stringResource(R.string.siirry_kellotauluun)
            else stringResource(R.string.aloita_toiminto),
            fontSize = 18.sp
        )
    }
    if (photoError.isNotBlank()) {
        Text(photoError, color = MaterialTheme.colorScheme.error)
    }

    if (choosing != null) {
        AlertDialog(
            onDismissRequest = { choosing = null },
            title = { Text(stringResource(R.string.choose_activity)) },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    TextButton(
                        onClick = {
                            libraryTarget = choosing ?: "first"
                            choosing = null
                            showLibrary = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.omat_toimintokortit))
                    }

                    TextButton(
                        onClick = {
                            val isFirst = choosing == "first"
                            val currentName = if (isFirst) first else next
                            val currentPhoto = if (isFirst)
                                firstPhoto else nextPhoto
                            val currentDescription = if (isFirst)
                                firstDescription else nextDescription

                            if (
                                currentName.isNotBlank() &&
                                currentName != context.getString(R.string.choose_activity)
                            ) {
                                CardStore(
                                    context.applicationContext
                                ).upsert(
                                    CardItem(
                                        id = java.util.UUID.randomUUID().toString(),
                                        name = currentName,
                                        description = currentDescription,
                                        photoPath = currentPhoto,
                                        voicePath = if (isFirst) firstVoice else nextVoice,
                                        autoPlayVoice = if (isFirst) firstVoiceAuto else nextVoiceAuto
                                    )
                                )
                                libraryNotice =
                                    context.getString(R.string.kortti_tallennettu, currentName)
                            }
                            choosing = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.tallenna_nykyinen_kortti_kirjastoon))
                    }

                    TextButton(
                        onClick = {
                            photoTarget = choosing ?: "first"
                            choosing = null
                            CaregiverSession.keepOpenForOwnPicker()
                            photoPicker.launch(
                                PickVisualMediaRequest(
                                    ActivityResultContracts.PickVisualMedia.ImageOnly
                                )
                            )
                        }
                    ) {
                        Text(stringResource(R.string.valitse_oma_valokuva))
                    }

                    TextButton(
                        onClick = {
                            editing = choosing
                            editName = if (choosing == "first") first else next
                            editDescription = if (choosing == "first")
                                firstDescription else nextDescription
                            choosing = null
                        }
                    ) {
                        Text(stringResource(R.string.muokkaa_nimea_ja_kuvausta))
                    }

                    HorizontalDivider()

                    CardArt.templates.map { stringResource(it.nameRes) }.forEach { name ->
                        TextButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                if (choosing == "first") {
                                    onFirst(name)
                                    onFirstPhoto("")
                                    onFirstDescription("")
                                    onFirstVoice("", false)
                                } else {
                                    onNext(name)
                                    onNextPhoto("")
                                    onNextDescription("")
                                    onNextVoice("", false)
                                }
                                choosing = null
                            }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CardPhoto(
                                    name = name,
                                    imagePath = "builtin:$name",
                                    modifier = Modifier.size(48.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                )
                                Text(name, fontSize = 16.sp, color = AppColors.Ink)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { choosing = null }) {
                    Text(stringResource(R.string.close))
                }
            }
        )
    }

    if (showLibrary) {
        CardLibraryDialog(
            onDismiss = { showLibrary = false },
            onSelect = { card ->
                if (libraryTarget == "first") {
                    onFirst(card.name)
                    onFirstPhoto(card.photoPath)
                    onFirstDescription(card.description)
                    onFirstVoice(card.voicePath, card.autoPlayVoice)
                } else {
                    onNext(card.name)
                    onNextPhoto(card.photoPath)
                    onNextDescription(card.description)
                    onNextVoice(card.voicePath, card.autoPlayVoice)
                }

                libraryNotice = context.getString(R.string.valittu, card.name)
                showLibrary = false
            }
        )
    }

    if (libraryNotice.isNotBlank()) {
        Text(
            libraryNotice,
            color = AppColors.Teal,
            fontSize = 13.sp
        )
    }

    if (editing != null) {
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(stringResource(R.string.muokkaa_toimintoa)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text(stringResource(R.string.toiminnon_nimi)) },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = editDescription,
                        onValueChange = { editDescription = it },
                        label = { Text(stringResource(R.string.lyhyt_kuvaus)) },
                        minLines = 2,
                        maxLines = 3
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = editName.isNotBlank(),
                    onClick = {
                        if (editing == "first") {
                            onFirst(editName.trim())
                            onFirstDescription(editDescription.trim())
                        } else {
                            onNext(editName.trim())
                            onNextDescription(editDescription.trim())
                        }
                        editing = null
                    }
                ) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = {
                TextButton(onClick = { editing = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

