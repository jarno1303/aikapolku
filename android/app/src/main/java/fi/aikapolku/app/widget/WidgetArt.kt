package fi.aikapolku.app.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import fi.aikapolku.app.cards.CardArt
import fi.aikapolku.app.data.CardClockMode
import fi.aikapolku.app.data.CardClockPrefs
import fi.aikapolku.app.data.Category
import fi.aikapolku.app.data.CategoryStore
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Widgetin korttikuva bittikarttana (RemoteViews ei piirrä Composea): kuva neliöksi rajattuna,
 * pyöristetyt kulmat sekä asukkaan asetusten mukaan luokan merkki ja kello – samat kuin sovelluksessa.
 */
internal object WidgetArt {
    /** Pieni koko pitää widgetin päivityksen kevyenä (järjestelmän siirtoraja). */
    const val SIZE = 220

    private const val INK = 0xFF1F2937.toInt()
    private const val PLACEHOLDER = 0xFFE6F4F1.toInt()

    fun render(context: Context, card: WidgetCard, size: Int = SIZE): Bitmap {
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val radius = size * 0.12f
        val clip = Path().apply { addRoundRect(RectF(0f, 0f, size.toFloat(), size.toFloat()), radius, radius, Path.Direction.CW) }
        canvas.save()
        canvas.clipPath(clip)
        val source = load(context, card.imageName, card.imagePath, size)
        if (source != null) {
            // Rajaus keskeltä neliöksi (kuten sovelluksen kortit).
            val side = min(source.width, source.height)
            val src = Rect((source.width - side) / 2, (source.height - side) / 2, (source.width + side) / 2, (source.height + side) / 2)
            canvas.drawBitmap(source, src, Rect(0, 0, size, size), Paint(Paint.FILTER_BITMAP_FLAG))
        } else {
            canvas.drawColor(PLACEHOLDER)
        }
        val category = if (CategoryStore.enabled(context)) {
            CategoryStore.resolve(context, card.imageName, card.imagePath, card.programTitle) { context.getString(it) }
        } else null
        if (category != null) drawCategory(canvas, category, size.toFloat())
        val clockMode = CardClockPrefs.mode(context)
        val minutes = CardClockPrefs.minutes(card.time)
        if (clockMode != CardClockMode.OFF && minutes != null) drawClock(canvas, minutes, clockMode, size.toFloat())
        canvas.restore()
        return out
    }

    private fun load(context: Context, name: String, imagePath: String, size: Int): Bitmap? = runCatching {
        val file = imagePath.takeIf { it.isNotBlank() && !it.startsWith("builtin:") }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        if (file != null) BitmapFactory.decodeFile(file, bounds) else {
            val res = CardArt.imageRes(name, imagePath) { context.getString(it) } ?: return null
            BitmapFactory.decodeResource(context.resources, res, bounds)
        }
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= size && bounds.outHeight / (sample * 2) >= size) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        if (file != null) BitmapFactory.decodeFile(file, opts)
        else BitmapFactory.decodeResource(context.resources, CardArt.imageRes(name, imagePath) { context.getString(it) }!!, opts)
    }.getOrNull() ?: if (imagePath.isNotBlank() && !imagePath.startsWith("builtin:")) {
        // Oma kuva puuttuu tai on rikki: valmis kuva nimen mukaan (kuten sovelluksessa).
        load(context, name, "", size)
    } else null

    private fun paint(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }

    /** Värillinen alareuna ja merkki vasemmassa yläkulmassa (vrt. CategoryUi.categoryMark). */
    private fun drawCategory(canvas: Canvas, category: Category, size: Float) {
        val color = category.color.toInt()
        val bar = size * 0.07f
        canvas.drawRect(0f, size - bar, size, size, paint(color))
        val d = size * 0.24f
        val inset = size * 0.07f
        val cx = inset + d / 2
        val cy = inset + d / 2
        val r = d / 2
        canvas.drawCircle(cx, cy, r, paint(0xFFFFFFFF.toInt()))
        canvas.drawCircle(cx, cy, r * 0.86f, paint(color))
        val w = r * 0.96f
        val left = cx - w / 2
        val top = cy - w / 2
        val white = paint(0xFFFFFFFF.toInt())
        when (category) {
            Category.MEAL -> {
                canvas.drawCircle(cx, cy, w * 0.5f, Paint(white).apply { style = Paint.Style.STROKE; strokeWidth = w * 0.16f })
                canvas.drawCircle(cx, cy, w * 0.2f, white)
            }
            Category.HYGIENE -> canvas.drawPath(Path().apply {
                moveTo(cx, top)
                cubicTo(cx + w * 0.1f, top + w * 0.25f, left + w, top + w * 0.45f, left + w - w * 0.08f, top + w * 0.68f)
                cubicTo(left + w - w * 0.2f, top + w * 1.02f, left + w * 0.2f, top + w * 1.02f, left + w * 0.08f, top + w * 0.68f)
                cubicTo(left, top + w * 0.45f, cx - w * 0.1f, top + w * 0.25f, cx, top)
                close()
            }, white)
            Category.OUTDOOR -> canvas.drawPath(Path().apply {
                moveTo(cx, top)
                lineTo(left + w, top + w * 0.94f)
                lineTo(left, top + w * 0.94f)
                close()
            }, white)
            Category.REST -> {
                val moon = Path().apply { addCircle(cx, cy, w * 0.5f, Path.Direction.CW) }
                val cut = Path().apply { addCircle(cx + w * 0.28f, cy - w * 0.18f, w * 0.42f, Path.Direction.CW) }
                moon.op(cut, Path.Op.DIFFERENCE)
                canvas.drawPath(moon, white)
            }
            Category.ACTIVITY -> canvas.drawRect(left + w * 0.1f, top + w * 0.1f, left + w * 0.9f, top + w * 0.9f, white)
            Category.HEALTH -> {
                val t = w * 0.32f
                canvas.drawRect(cx - t / 2, top, cx + t / 2, top + w, white)
                canvas.drawRect(left, cy - t / 2, left + w, cy + t / 2, white)
            }
        }
    }

    /** Kellotaulu oikeassa alakulmassa luokan reunan yläpuolella (vrt. CardClock.clockMark). */
    private fun drawClock(canvas: Canvas, minutes: Int, mode: CardClockMode, size: Float) {
        val d = size * 0.3f
        val inset = size * 0.06f
        val cx = size - inset - d / 2
        val cy = size - size * 0.07f - inset - d / 2
        val r = d / 2
        canvas.drawCircle(cx, cy, r, paint(0xFFFFFFFF.toInt()))
        canvas.drawCircle(cx, cy, r * 0.95f, paint(INK).apply { style = Paint.Style.STROKE; strokeWidth = r * 0.1f })
        val text = paint(INK).apply { textSize = r * 0.3f; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
        listOf(0 to "12", 90 to "3", 180 to "6", 270 to "9").forEach { (deg, label) ->
            val a = Math.toRadians(deg.toDouble())
            val x = cx + (r * 0.72f * sin(a)).toFloat()
            val y = cy - (r * 0.72f * cos(a)).toFloat() - (text.ascent() + text.descent()) / 2
            canvas.drawText(label, x, y, text)
        }
        val (hourDeg, minuteDeg) = CardClockPrefs.handAngles(minutes)
        fun hand(deg: Float, length: Float, width: Float) {
            val a = Math.toRadians(deg.toDouble())
            canvas.drawLine(cx, cy, cx + (length * sin(a)).toFloat(), cy - (length * cos(a)).toFloat(),
                paint(INK).apply { strokeWidth = width; strokeCap = Paint.Cap.ROUND })
        }
        hand(hourDeg, r * 0.36f, r * 0.14f)
        hand(minuteDeg, r * 0.5f, r * 0.09f)
        canvas.drawCircle(cx, cy, r * 0.1f, paint(INK))
        if (mode == CardClockMode.BOTH) {
            val label = "%d.%02d".format(minutes / 60, minutes % 60)
            val p = paint(INK).apply { textSize = d * 0.3f; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
            val w = p.measureText(label) + d * 0.2f
            val h = p.textSize * 1.3f
            val top = cy - h / 2
            val left = (cx - r - d * 0.06f - w).coerceAtLeast(0f)
            canvas.drawRoundRect(RectF(left, top, left + w, top + h), h / 2, h / 2, paint(0xFFFFFFFF.toInt()))
            canvas.drawText(label, left + w / 2, top + h / 2 - (p.ascent() + p.descent()) / 2, p)
        }
    }
}
