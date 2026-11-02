package fi.aikapolku.app.firstthen

import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.ui.semantics.Role
import fi.aikapolku.app.ui.AppIcons
import fi.aikapolku.app.ui.ChoiceRow
import fi.aikapolku.app.ui.InfoButton
import fi.aikapolku.app.ui.PrimaryButton
import fi.aikapolku.app.ui.SecondaryButton
import fi.aikapolku.app.ui.SegmentedChoice
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
    var saveToLibrary by remember { mutableStateOf(false) }
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

        InfoButton(stringResource(R.string.korttien_kayttoohje)) { showCardHelp = true }
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

    var timerMode by rememberSaveable {
        mutableStateOf(true)
    }

    // Eteneminen: ajastimella tai omaan tahtiin. Yksi valitsin ja lyhyt selitys.
    SegmentedChoice(
        options = listOf(stringResource(R.string.ajastimella), stringResource(R.string.omaan_tahtiin)),
        selected = if (timerMode) 0 else 1,
        onSelect = { timerMode = it == 0 }
    )
    Text(
        if (timerMode) stringResource(R.string.flow_mode_timer_hint) else stringResource(R.string.siirry_eteenpain_kun_olet_valmis),
        fontSize = 14.sp,
        color = AppColors.Muted
    )

    val placeholder = stringResource(R.string.choose_activity)
    PrimaryButton(
        text = if (timerMode) stringResource(R.string.siirry_kellotauluun) else stringResource(R.string.aloita_toiminto),
        onClick = { if (timerMode) onStartTimer(5) else onDone() },
        enabled = first.isNotBlank() && first != placeholder && next.isNotBlank() && next != placeholder
    )
    if (photoError.isNotBlank()) {
        Text(photoError, color = MaterialTheme.colorScheme.error)
    }

    // Toiminnon valinta: oma valokuva ja omat kortit ylhäällä samanlaisina painikkeina,
    // niiden alla valmiit kortit kuvineen. Nykyisen kortin muokkaus kynästä.
    val chooseTarget = choosing
    if (chooseTarget != null) {
        val isFirst = chooseTarget == "first"
        val currentName = if (isFirst) first else next
        val hasCurrent = currentName.isNotBlank() && currentName != stringResource(R.string.choose_activity)
        AlertDialog(
            onDismissRequest = { choosing = null },
            title = { Text(stringResource(R.string.choose_activity)) },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 460.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (hasCurrent) {
                        ChoiceRow(
                            title = currentName,
                            subtitle = stringResource(R.string.edit_current_card),
                            onClick = {
                                editing = chooseTarget
                                editName = currentName
                                editDescription = if (isFirst) firstDescription else nextDescription
                                saveToLibrary = false
                                choosing = null
                            },
                            leading = {
                                CardPhoto(
                                    name = currentName,
                                    imagePath = if (isFirst) firstPhoto else nextPhoto,
                                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp))
                                )
                            },
                            trailing = { Icon(Icons.Rounded.Edit, contentDescription = null, tint = AppColors.Teal) }
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SecondaryButton(
                            text = stringResource(R.string.photo_short),
                            icon = AppIcons.Camera,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                photoTarget = chooseTarget
                                choosing = null
                                CaregiverSession.keepOpenForOwnPicker()
                                photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            }
                        )
                        SecondaryButton(
                            text = stringResource(R.string.omat_kortit),
                            icon = Icons.AutoMirrored.Rounded.List,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                libraryTarget = chooseTarget
                                choosing = null
                                showLibrary = true
                            }
                        )
                    }
                    CardArt.templates.map { stringResource(it.nameRes) }.forEach { name ->
                        ChoiceRow(
                            title = name,
                            onClick = {
                                if (isFirst) {
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
                            },
                            leading = {
                                CardPhoto(
                                    name = name,
                                    imagePath = "builtin:$name",
                                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp))
                                )
                            }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { choosing = null }) {
                    Text(stringResource(R.string.cancel))
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

                showLibrary = false
            }
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .toggleable(value = saveToLibrary, role = Role.Checkbox) { saveToLibrary = it }
                    ) {
                        Checkbox(checked = saveToLibrary, onCheckedChange = null)
                        Text(stringResource(R.string.save_to_own_cards), modifier = Modifier.padding(start = 8.dp))
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = editName.isNotBlank(),
                    onClick = {
                        val isFirst = editing == "first"
                        if (isFirst) {
                            onFirst(editName.trim())
                            onFirstDescription(editDescription.trim())
                        } else {
                            onNext(editName.trim())
                            onNextDescription(editDescription.trim())
                        }
                        // Valinnaisesti myös omiin kortteihin (aiemmin erillinen "Tallenna kirjastoon").
                        if (saveToLibrary) {
                            CardStore(context.applicationContext).upsert(
                                CardItem(
                                    id = java.util.UUID.randomUUID().toString(),
                                    name = editName.trim(),
                                    description = editDescription.trim(),
                                    photoPath = if (isFirst) firstPhoto else nextPhoto,
                                    voicePath = if (isFirst) firstVoice else nextVoice,
                                    autoPlayVoice = if (isFirst) firstVoiceAuto else nextVoiceAuto
                                )
                            )
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

