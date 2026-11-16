package fi.aikapolku.app.cards

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fi.aikapolku.app.R
import fi.aikapolku.app.data.Category
import fi.aikapolku.app.data.CategoryStore
import fi.aikapolku.app.settings.SettingsRevision
import fi.aikapolku.app.ui.AppColors

/** Luokan väri. */
internal val Category.composeColor: Color get() = Color(color)

/** Luokan nimi (ohjaajan antama tai oletus); päivittyy, kun nimeä muutetaan. */
@Composable
internal fun categoryName(category: Category): String {
    val context = LocalContext.current
    CategoryStore.revision
    return CategoryStore.name(context, category)
}

/**
 * Näytettävän kortin luokka, jos värikoodaus on asukkaalla päällä. [title] = päiväohjelman nimi,
 * kun kuva on ohjelman ensimmäinen kortti.
 */
@Composable
internal fun shownCategory(name: String, imagePath: String, title: String? = null): Category? {
    val context = LocalContext.current
    // Luetaan tilat, jotta näkymä päivittyy valinnan, asetuksen tai profiilin vaihtuessa.
    CategoryStore.revision
    SettingsRevision.value
    if (!CategoryStore.enabled(context)) return null
    return CategoryStore.resolve(context, name, imagePath, title) { context.getString(it) }
}

/**
 * Luokan merkintä kuvan päälle: värillinen alareuna ja merkki vasemmassa yläkulmassa.
 * Piirretään kuvan rajauksen sisään, joten pyöristetyt kulmat säilyvät. Pienissä kuvissa vain reuna.
 */
internal fun Modifier.categoryMark(category: Category?): Modifier =
    if (category == null) this else drawWithContent {
        drawContent()
        val color = category.composeColor
        val small = size.minDimension
        val bar = (small * 0.07f).coerceIn(3.dp.toPx(), 10.dp.toPx())
        drawRect(color, topLeft = Offset(0f, size.height - bar), size = Size(size.width, bar))
        if (small >= 40.dp.toPx()) {
            val d = (small * 0.22f).coerceIn(18.dp.toPx(), 44.dp.toPx())
            val inset = (small * 0.06f).coerceAtLeast(4.dp.toPx())
            translate(inset, inset) { drawBadge(category, d) }
        }
    }

/** Pyöreä merkki: valkoinen reunus (erottuu kuvasta), luokan väri ja valkoinen muoto. */
internal fun DrawScope.drawBadge(category: Category, d: Float) {
    val r = d / 2f
    val center = Offset(r, r)
    drawCircle(Color.White, radius = r, center = center)
    drawCircle(category.composeColor, radius = r * 0.86f, center = center)
    drawSymbol(category, Rect(center = center, radius = r * 0.48f))
}

/** Luokan muoto valkoisena annetun alueen sisään. Muodot eroavat toisistaan ilman väriäkin. */
private fun DrawScope.drawSymbol(category: Category, box: Rect) {
    val w = box.width
    val white = Color.White
    when (category) {
        // Lautanen: rengas ja keskus.
        Category.MEAL -> {
            drawCircle(white, radius = w * 0.5f, center = box.center, style = Stroke(width = w * 0.16f))
            drawCircle(white, radius = w * 0.2f, center = box.center)
        }
        // Pisara.
        Category.HYGIENE -> drawPath(Path().apply {
            moveTo(box.center.x, box.top)
            cubicTo(box.center.x + w * 0.1f, box.top + w * 0.25f, box.right, box.top + w * 0.45f, box.right - w * 0.08f, box.top + w * 0.68f)
            cubicTo(box.right - w * 0.2f, box.bottom + w * 0.02f, box.left + w * 0.2f, box.bottom + w * 0.02f, box.left + w * 0.08f, box.top + w * 0.68f)
            cubicTo(box.left, box.top + w * 0.45f, box.center.x - w * 0.1f, box.top + w * 0.25f, box.center.x, box.top)
            close()
        }, white)
        // Kuusi / vuori: kolmio.
        Category.OUTDOOR -> drawPath(Path().apply {
            moveTo(box.center.x, box.top)
            lineTo(box.right, box.bottom - w * 0.06f)
            lineTo(box.left, box.bottom - w * 0.06f)
            close()
        }, white)
        // Kuunsirppi.
        Category.REST -> drawPath(Path().apply {
            addOval(Rect(center = box.center, radius = w * 0.5f))
            val cut = Path().apply { addOval(Rect(center = Offset(box.center.x + w * 0.28f, box.center.y - w * 0.18f), radius = w * 0.42f)) }
            op(this, cut, androidx.compose.ui.graphics.PathOperation.Difference)
        }, white)
        // Palikka: neliö.
        Category.ACTIVITY -> drawRect(white, topLeft = Offset(box.left + w * 0.1f, box.top + w * 0.1f), size = Size(w * 0.8f, w * 0.8f))
        // Risti (terveys).
        Category.HEALTH -> {
            val t = w * 0.32f
            drawRect(white, topLeft = Offset(box.center.x - t / 2, box.top), size = Size(t, w))
            drawRect(white, topLeft = Offset(box.left, box.center.y - t / 2), size = Size(w, t))
        }
    }
}

/** Merkki yksinään (valinnat, asetukset). */
@Composable
internal fun CategoryBadge(category: Category, modifier: Modifier = Modifier.size(28.dp)) {
    Canvas(modifier) { drawBadge(category, size.minDimension) }
}

/**
 * Luokan valinta (ohjaaja): kuusi luokkaa ja "ei luokkaa". [selected] null = ei luokkaa.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CategoryChooser(selected: Category?, onSelect: (Category?) -> Unit) {
    val noneLabel = stringResource(R.string.category_none)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        (Category.entries + listOf<Category?>(null)).forEach { option ->
            val isSelected = option == selected
            val shape = RoundedCornerShape(12.dp)
            Row(
                modifier = Modifier
                    .clip(shape)
                    .border(if (isSelected) 3.dp else 1.dp, if (isSelected) AppColors.Teal else AppColors.Muted.copy(alpha = 0.4f), shape)
                    .selectable(selected = isSelected, role = Role.RadioButton) { onSelect(option) }
                    .heightIn(min = 44.dp)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (option != null) CategoryBadge(option, Modifier.size(24.dp))
                Text(
                    if (option != null) categoryName(option) else noneLabel,
                    fontSize = 14.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

/** Ohjaajan asetukset: luokkien nimet (koko laitteelle ja paikan kautta asukkaiden puhelimiin). */
@Composable
internal fun CategoryNamesSettings() {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.category_names_info), fontSize = 13.sp, color = AppColors.Muted)
        Category.entries.forEach { category ->
            val current = categoryName(category)
            var text by remember(category) { mutableStateOf(current) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CategoryBadge(category, Modifier.size(32.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = {
                        text = it.take(30)
                        CategoryStore.rename(context, category, text)
                    },
                    singleLine = true,
                    label = { Text(stringResource(category.nameRes)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
