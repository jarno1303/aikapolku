package fi.aikapolku.app.programs

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.print.pdf.PrintedPdfDocument
import android.widget.Toast
import fi.aikapolku.app.R
import fi.aikapolku.app.cards.CardArt
import fi.aikapolku.app.data.DayProgram
import fi.aikapolku.app.data.ProgramStep
import fi.aikapolku.app.data.StepMode
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.Locale
import kotlin.math.min

internal object ProgramPrint {

    fun open(context: Context, program: DayProgram) {
        val activity = findActivity(context)

        if (activity == null) {
            Toast.makeText(
                context,
                context.getString(R.string.tulostusikkunaa_ei_voitu_avata),
                Toast.LENGTH_LONG
            ).show()
            return
        }

        if (!activity.packageManager.hasSystemFeature(
                PackageManager.FEATURE_PRINTING
            )
        ) {
            Toast.makeText(
                activity,
                context.getString(R.string.androidin_tulostuspalvelua_ei_ole_kaytettavissa),
                Toast.LENGTH_LONG
            ).show()
            return
        }

        try {
            val manager = activity.getSystemService(
                Context.PRINT_SERVICE
            ) as PrintManager

            val attributes = PrintAttributes.Builder()
                .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
                .setMinMargins(
                    PrintAttributes.Margins(400, 400, 400, 400)
                )
                .build()

            manager.print(
                context.getString(R.string.aikapolku, program.name.take(50)),
                ProgramPrintAdapter(activity, program),
                attributes
            )
        } catch (e: Exception) {
            android.util.Log.e(
                "AikaPolkuPrint",
                "Tulostusikkunan avaus epäonnistui",
                e
            )
            Toast.makeText(
                activity,
                context.getString(R.string.tulostusta_ei_voitu_kaynnistaa),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun findActivity(context: Context): Activity? {
        var current: Context = context

        while (current is ContextWrapper) {
            if (current is Activity) return current
            val next = current.baseContext
            if (next === current) break
            current = next
        }

        return current as? Activity
    }
}

private class ProgramPrintAdapter(
    private val context: Context,
    private val program: DayProgram
) : PrintDocumentAdapter() {

    private var attributes: PrintAttributes? = null
    private val rowHeight = 96f

    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes,
        cancellationSignal: CancellationSignal,
        callback: LayoutResultCallback,
        extras: Bundle?
    ) {
        if (cancellationSignal.isCanceled) {
            callback.onLayoutCancelled()
            return
        }

        try {
            attributes = newAttributes

            val pdf = PrintedPdfDocument(context, newAttributes)
            val capacity = try {
                rowsPerPage(pdf)
            } finally {
                pdf.close()
            }

            val pages = maxOf(
                1,
                (program.steps.size + capacity - 1) / capacity
            )

            val info = PrintDocumentInfo.Builder(
                context.getString(R.string.aikapolku_paivaohjelma_pdf)
            )
                .setContentType(
                    PrintDocumentInfo.CONTENT_TYPE_DOCUMENT
                )
                .setPageCount(pages)
                .build()

            callback.onLayoutFinished(
                info,
                oldAttributes != newAttributes
            )
        } catch (e: Exception) {
            callback.onLayoutFailed(
                context.getString(R.string.tulosteen_asettelua_ei_voitu_muodostaa)
            )
        }
    }

    override fun onWrite(
        pages: Array<out PageRange>,
        destination: ParcelFileDescriptor,
        cancellationSignal: CancellationSignal,
        callback: WriteResultCallback
    ) {
        val settings = attributes

        if (settings == null) {
            callback.onWriteFailed(context.getString(R.string.tulostusasetukset_puuttuvat))
            return
        }

        val pdf = PrintedPdfDocument(context, settings)

        try {
            val capacity = rowsPerPage(pdf)
            val count = maxOf(
                1,
                (program.steps.size + capacity - 1) / capacity
            )

            val selected = (0 until count).filter { index ->
                pages.isEmpty() || pages.any { range ->
                    range == PageRange.ALL_PAGES ||
                        index in range.start..range.end
                }
            }

            if (cancellationSignal.isCanceled) {
                callback.onWriteCancelled()
                return
            }

            for (index in selected) {
                if (cancellationSignal.isCanceled) {
                    callback.onWriteCancelled()
                    return
                }

                val page = pdf.startPage(index)
                drawPage(
                    page.canvas,
                    pdf,
                    index,
                    count,
                    capacity
                )
                pdf.finishPage(page)
            }

            FileOutputStream(destination.fileDescriptor).use {
                pdf.writeTo(it)
            }

            callback.onWriteFinished(
                selected.map { PageRange(it, it) }.toTypedArray()
            )
        } catch (e: Exception) {
            android.util.Log.e(
                "AikaPolkuPrint",
                "PDF:n muodostus epäonnistui",
                e
            )
            if (cancellationSignal.isCanceled) {
                callback.onWriteCancelled()
            } else {
                callback.onWriteFailed(
                    context.getString(R.string.pdf_n_muodostus_epaonnistui)
                )
            }
        } finally {
            pdf.close()
        }
    }

    private fun rowsPerPage(pdf: PrintedPdfDocument): Int {
        return maxOf(
            1,
            ((pdf.pageContentRect.height() - 142f) / rowHeight).toInt()
        )
    }

    private fun paint(
        color: Int,
        size: Float = 14f,
        bold: Boolean = false
    ): Paint {
        return Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = size
            typeface = if (bold)
                android.graphics.Typeface.create(
                    android.graphics.Typeface.DEFAULT,
                    android.graphics.Typeface.BOLD
                )
            else android.graphics.Typeface.DEFAULT
        }
    }

    private fun drawPage(
        canvas: Canvas,
        pdf: PrintedPdfDocument,
        pageIndex: Int,
        pageCount: Int,
        capacity: Int
    ) {
        val rect = pdf.pageContentRect
        val left = rect.left + 8f
        val right = rect.right - 8f
        val width = right - left
        val top = rect.top.toFloat()

        val dark = Color.rgb(31, 41, 55)
        val teal = Color.rgb(15, 118, 110)
        val secondary = Color.rgb(71, 85, 105)

        canvas.drawText(
            "AIKAPOLKU",
            left,
            top + 22f,
            paint(teal, 13f, true)
        )

        val titlePaint = paint(dark, 23f, true)

        canvas.drawText(
            fitText(program.name, titlePaint, width),
            left,
            top + 52f,
            titlePaint
        )

        canvas.drawText(
            context.getString(R.string.paivaohjelma),
            left,
            top + 74f,
            paint(secondary, 12f)
        )

        val start = pageIndex * capacity
        val end = min(start + capacity, program.steps.size)

        for (i in start until end) {
            val rowY = top + 92f + (i - start) * rowHeight

            drawStep(
                canvas,
                program.steps[i],
                i + 1,
                left,
                rowY,
                width
            )
        }

        if (program.steps.isEmpty()) {
            canvas.drawText(
                context.getString(R.string.paivaohjelmassa_ei_ole_tehtavia),
                left,
                top + 118f,
                paint(secondary, 14f)
            )
        }

        canvas.drawText(
            context.getString(R.string.aikapolku_sivu, pageIndex + 1, pageCount),
            left,
            rect.bottom - 10f,
            paint(secondary, 10f)
        )
    }

    private fun drawStep(
        canvas: Canvas,
        step: ProgramStep,
        number: Int,
        x: Float,
        y: Float,
        width: Float
    ) {
        val bottom = y + 86f

        val surface = paint(Color.rgb(244, 249, 247))
        canvas.drawRoundRect(
            RectF(x, y, x + width, bottom),
            11f,
            11f,
            surface
        )

        val imageBox = RectF(
            x + 11f, y + 11f,
            x + 75f, y + 75f
        )

        drawPhoto(canvas, step, imageBox)

        val textX = x + 89f
        val checkboxX = x + width - 30f
        val textWidth = maxOf(40f, checkboxX - textX - 8f)

        val titlePaint = paint(
            Color.rgb(31, 41, 55),
            15f,
            true
        )

        canvas.drawText(
            fitText("$number. ${step.name}", titlePaint, textWidth),
            textX,
            y + 30f,
            titlePaint
        )

        val detail = when (step.mode) {
            StepMode.CLOCK -> context.getString(R.string.clock_value, step.clock)
            StepMode.DURATION -> context.getString(R.string.minutes_value, step.minutes)
            else -> ""
        }

        if (detail.isNotBlank()) {
            canvas.drawText(
                detail,
                textX,
                y + 49f,
                paint(Color.rgb(71, 85, 105), 12f)
            )
        }

        if (step.description.isNotBlank()) {
            val descPaint = paint(
                Color.rgb(71, 85, 105), 10f
            )
            canvas.drawText(
                fitText(step.description, descPaint, textWidth),
                textX,
                y + 66f,
                descPaint
            )
        }

        val checkboxPaint = paint(
            Color.rgb(110, 130, 124)
        ).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
        }

        canvas.drawRoundRect(
            RectF(
                checkboxX,
                y + 30f,
                checkboxX + 18f,
                y + 48f
            ),
            3f,
            3f,
            checkboxPaint
        )
    }

    private fun drawPhoto(
        canvas: Canvas,
        step: ProgramStep,
        box: RectF
    ) {
        val bitmap = readPhoto(step.photo)
            ?: CardArt.bitmap(context, step.name, step.photo)

        canvas.drawRoundRect(
            box,
            9f,
            9f,
            paint(Color.rgb(230, 244, 241))
        )

        if (bitmap != null) {
            try {
                val scale = min(
                    box.width() / bitmap.width,
                    box.height() / bitmap.height
                )

                val w = bitmap.width * scale
                val h = bitmap.height * scale

                val target = RectF(
                    box.centerX() - w / 2f,
                    box.centerY() - h / 2f,
                    box.centerX() + w / 2f,
                    box.centerY() + h / 2f
                )

                canvas.drawBitmap(
                    bitmap, null, target,
                    Paint(Paint.FILTER_BITMAP_FLAG)
                )
            } finally {
                bitmap.recycle()
            }
        } else {
            canvas.drawText(
                emojiFor(
                    if (step.photo.startsWith("builtin:"))
                        step.photo.removePrefix("builtin:")
                    else step.name
                ),
                box.left + 13f,
                box.top + 45f,
                paint(Color.rgb(15, 118, 110), 30f)
            )
        }
    }

    private fun readPhoto(path: String): Bitmap? {
        if (path.isBlank() || path.startsWith("builtin:")) {
            return null
        }

        fun openStream(): InputStream? {
            return when {
                path.startsWith("content://") ->
                    context.contentResolver.openInputStream(
                        Uri.parse(path)
                    )

                path.startsWith("file://") ->
                    FileInputStream(
                        Uri.parse(path).path ?: return null
                    )

                path.startsWith("/") ->
                    FileInputStream(path)

                else ->
                    File(context.filesDir, path).inputStream()
            }
        }

        return runCatching {
            val bounds = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }

            openStream()?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }

            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                return@runCatching null
            }

            var sample = 1
            while (
                maxOf(bounds.outWidth, bounds.outHeight) / sample > 512
            ) {
                sample *= 2
            }

            val options = BitmapFactory.Options().apply {
                inSampleSize = sample
            }

            openStream()?.use {
                BitmapFactory.decodeStream(it, null, options)
            }
        }.getOrNull()
    }

    private fun emojiFor(name: String): String {
        val text = name.lowercase(Locale.ROOT)

        return when {
            "aamupala" in text || "ruokail" in text -> "🍽"
            "pukeutu" in text -> "👕"
            "ulkoil" in text || "retki" in text -> "🌳"
            "hampai" in text -> "🪥"
            "lukemi" in text -> "📖"
            "musiik" in text -> "🎵"
            "askart" in text -> "🎨"
            "pel" in text -> "🎮"
            "lep" in text || "tauko" in text -> "☕"
            "wc" in text -> "🚻"
            else -> "●"
        }
    }

    private fun fitText(
        value: String,
        textPaint: Paint,
        width: Float
    ): String {
        var text = value
            .replace('\n', ' ')
            .replace('\r', ' ')
            .trim()

        if (textPaint.measureText(text) <= width) {
            return text
        }

        while (
            text.isNotEmpty() &&
            textPaint.measureText("$text…") > width
        ) {
            text = text.dropLast(1)
        }

        return "$text…"
    }
}
