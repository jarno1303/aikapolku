package fi.aikapolku.app.cards

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import fi.aikapolku.app.caregiver.CaregiverSession
import fi.aikapolku.app.data.importCardPhoto
import java.io.File

/**
 * "Ota kuva": avaa puhelimen oman kamerasovelluksen ja tallentaa kuvan vain sovellukseen
 * (activity_images), ei puhelimen galleriaan. Kameran käyttölupaa ei tarvita, koska kuvan ottaa
 * kamerasovellus. Väliaikainen tiedosto poistetaan heti tallennuksen jälkeen.
 *
 * Palauttaa funktion, joka käynnistää kuvauksen. [onSaved] saa tallennetun kuvan polun;
 * [onError] kutsutaan, jos kameraa ei voitu avata tai kuvaa tallentaa. Peruutus ei ole virhe.
 */
@Composable
internal fun rememberTakePhoto(onSaved: (String) -> Unit, onError: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val saved by rememberUpdatedState(onSaved)
    val failed by rememberUpdatedState(onError)
    // Säilyy, vaikka järjestelmä sulkisi sovelluksen kameran ollessa auki.
    var pending by rememberSaveable { mutableStateOf<String?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val path = pending
        pending = null
        if (path != null) {
            val file = File(path)
            if (ok && file.length() > 0L) {
                try {
                    saved(importCardPhoto(context, Uri.fromFile(file)))
                } catch (_: Exception) {
                    failed()
                }
            }
            file.delete()
        }
    }
    return {
        try {
            val dir = File(context.cacheDir, "camera")
            check(dir.exists() || dir.mkdirs())
            val file = File.createTempFile("photo_", ".jpg", dir)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
            pending = file.absolutePath
            CaregiverSession.keepOpenForOwnPicker()
            launcher.launch(uri)
        } catch (_: Exception) {
            pending?.let { File(it).delete() }
            pending = null
            failed()
        }
    }
}
