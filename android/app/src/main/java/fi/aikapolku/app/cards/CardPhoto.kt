package fi.aikapolku.app.cards

import android.content.Context
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import fi.aikapolku.app.ui.FlowColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Korttikuvien välimuisti. Kuvat puretaan taustasäikeessä eikä käyttöliittymäsäikeessä,
 * ja sama kuva puretaan vain kerran (esim. päiväohjelman muokkaimen kuvaruudukko,
 * suoritusnäkymä ja Seuraavaksi-rivi käyttävät samaa kuvaa).
 */
internal object CardImageCache {
    private const val MAX_FILE_EDGE = 768

    private val cache = object : LruCache<String, ImageBitmap>(
        (Runtime.getRuntime().maxMemory() / 8 / 1024).toInt().coerceAtLeast(8 * 1024)
    ) {
        override fun sizeOf(key: String, value: ImageBitmap): Int =
            (value.width * value.height * 4 / 1024).coerceAtLeast(1)
    }

    private fun fileKey(path: String) = "file:$path"
    private fun resKey(res: Int) = "res:$res"

    /** Välimuistissa jo oleva kuva: ensin oma valokuva, sitten valmis kuva. */
    fun peek(path: String?, res: Int?): ImageBitmap? =
        path?.let { cache.get(fileKey(it)) } ?: res?.let { cache.get(resKey(it)) }

    /** Purkaa kuvan taustalla. Oma valokuva ensin; jos se puuttuu tai on rikki, valmis kuva. */
    suspend fun load(context: Context, path: String?, res: Int?): ImageBitmap? = withContext(Dispatchers.IO) {
        path?.let { p ->
            cache.get(fileKey(p)) ?: decodeFile(p)?.also { cache.put(fileKey(p), it) }
        } ?: res?.let { r ->
            cache.get(resKey(r)) ?: runCatching {
                BitmapFactory.decodeResource(context.resources, r)?.asImageBitmap()
            }.getOrNull()?.also { it.prepareToDraw(); cache.put(resKey(r), it) }
        }
    }

    private fun decodeFile(path: String): ImageBitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
        var sample = 1
        while (bounds.outWidth / sample > MAX_FILE_EDGE || bounds.outHeight / sample > MAX_FILE_EDGE) sample *= 2
        BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
            ?.asImageBitmap()?.also { it.prepareToDraw() }
    }.getOrNull()
}

private class PhotoState(val done: Boolean, val image: ImageBitmap?)

/**
 * Kortin kuva: oma valokuva, muuten valmis kuva nimen perusteella, muuten tunnus ◇.
 * Purku tapahtuu taustalla; välimuistissa oleva kuva näkyy heti.
 */
@Composable
internal fun CardPhoto(
    name: String,
    imagePath: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val path = imagePath.takeIf { it.isNotBlank() && !it.startsWith("builtin:") }
    val res = CardArt.imageRes(name, imagePath) { context.getString(it) }
    val state by produceState(
        initialValue = CardImageCache.peek(path, res).let { PhotoState(it != null, it) },
        path, res
    ) {
        if (!value.done) value = PhotoState(true, CardImageCache.load(context, path, res))
    }

    val image = state.image
    when {
        image != null -> Image(
            bitmap = image,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier
        )
        // Latautuu: tyhjä pinta samassa koossa, jotta asettelu ei hypi.
        !state.done -> Box(modifier = modifier.background(Color(0xFFE6F4F1)))
        else -> Box(
            modifier = modifier.background(Color(0xFFE6F4F1)),
            contentAlignment = Alignment.Center
        ) {
            Text("◇", fontSize = 50.sp, color = FlowColors.teal)
        }
    }
}

/** Valmis kuva (esim. osavaihe tai viesti) samasta välimuistista kuin korttikuvat. */
@Composable
internal fun ResourceImage(
    res: Int,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    val context = LocalContext.current
    val image by produceState(initialValue = CardImageCache.peek(null, res), res) {
        if (value == null) value = CardImageCache.load(context, null, res)
    }
    val loaded = image
    if (loaded != null) {
        Image(bitmap = loaded, contentDescription = null, contentScale = contentScale, modifier = modifier)
    } else {
        Box(modifier = modifier.background(Color(0xFFE6F4F1)))
    }
}
