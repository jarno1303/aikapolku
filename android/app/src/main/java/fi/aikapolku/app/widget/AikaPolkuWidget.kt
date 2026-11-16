package fi.aikapolku.app.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import fi.aikapolku.app.MainActivity
import fi.aikapolku.app.R
import java.time.ZoneId

/**
 * Kotinäytön widgetit (ajatus Tiimosta): asukas näkee "Nyt" ja "Seuraavaksi" avaamatta sovellusta.
 * Vain kotinäytölle (ei lukitusnäytölle), ei vointia eikä muuta arkaluonteista. Napautus avaa
 * sovelluksen (asukkaan oma etusivu). Päivitys tapahtumien rajoilla hälytyksellä sekä aina, kun
 * sovellus suljetaan, tiedot tulevat paikan laitteelta tai viikko-ohjelma muuttuu.
 */
internal object WidgetUpdater {
    private const val ACTION_TICK = "fi.aikapolku.app.widget.TICK"
    private const val REQUEST = 7310

    /** Päivittää kaikki widgetit ja ajastaa seuraavan päivityksen. Turvallinen kutsua mistä vain. */
    fun update(context: Context) {
        val app = context.applicationContext
        val manager = AppWidgetManager.getInstance(app) ?: return
        val small = manager.getAppWidgetIds(ComponentName(app, SmallWidgetProvider::class.java))
        val wide = manager.getAppWidgetIds(ComponentName(app, WideWidgetProvider::class.java))
        if (small.isEmpty() && wide.isEmpty()) {
            cancel(app)
            return
        }
        val content = runCatching { WidgetDay.compute(app) }.getOrElse { WidgetContent(WidgetState.EMPTY) }
        // Kuvat piirretään kerran kutakin kokoa kohden, vaikka widgettejä olisi useita.
        if (small.isNotEmpty()) runCatching { views(app, content, wide = false) }.getOrNull()?.let { v -> small.forEach { runCatching { manager.updateAppWidget(it, v) } } }
        if (wide.isNotEmpty()) runCatching { views(app, content, wide = true) }.getOrNull()?.let { v -> wide.forEach { runCatching { manager.updateAppWidget(it, v) } } }
        schedule(app)
    }

    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context, REQUEST,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    internal fun views(context: Context, content: WidgetContent, wide: Boolean): RemoteViews {
        val now = content.now
        if (now == null || content.state == WidgetState.OFF || content.state == WidgetState.DONE || content.state == WidgetState.EMPTY) {
            return RemoteViews(context.packageName, R.layout.widget_message).apply {
                setTextViewText(R.id.widget_message, context.getString(
                    when (content.state) {
                        WidgetState.DONE -> R.string.widget_day_done
                        WidgetState.EMPTY -> R.string.widget_nothing
                        else -> R.string.app_name
                    }
                ))
                setOnClickPendingIntent(R.id.widget_root, openApp(context))
            }
        }
        val layout = if (wide) R.layout.widget_wide else R.layout.widget_small
        return RemoteViews(context.packageName, layout).apply {
            setImageViewBitmap(R.id.widget_now_image, WidgetArt.render(context, now))
            setTextViewText(R.id.widget_now_title, now.title)
            val until = if (!content.showTimes) null else when {
                content.until != null -> context.getString(R.string.widget_until, content.until.replace(':', '.'))
                now.time.isNotBlank() -> context.getString(R.string.widget_at, now.time.replace(':', '.'))
                else -> null
            }
            setTextViewText(R.id.widget_now_until, until ?: "")
            setViewVisibility(R.id.widget_now_until, if (until == null) View.GONE else View.VISIBLE)
            if (wide) {
                val next = content.next
                if (next != null) {
                    setImageViewBitmap(R.id.widget_next_image, WidgetArt.render(context, next))
                    setTextViewText(R.id.widget_next_title, next.title)
                    setTextViewText(R.id.widget_next_at, if (content.showTimes) context.getString(R.string.widget_at, next.time.replace(':', '.')) else "")
                    setViewVisibility(R.id.widget_next_at, if (content.showTimes) View.VISIBLE else View.GONE)
                    setViewVisibility(R.id.widget_next_box, View.VISIBLE)
                    setViewVisibility(R.id.widget_arrow, View.VISIBLE)
                } else {
                    setViewVisibility(R.id.widget_next_box, View.GONE)
                    setViewVisibility(R.id.widget_arrow, View.GONE)
                }
            }
            setOnClickPendingIntent(R.id.widget_root, openApp(context))
        }
    }

    private fun tick(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, REQUEST,
        Intent(context, WidgetTickReceiver::class.java).setAction(ACTION_TICK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    /** Seuraava päivitys, kun Nyt tai Seuraavaksi vaihtuu. Ei tarkkaa hälytystä: minuutin viive riittää. */
    private fun schedule(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val at = WidgetDay.nextChange(context).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        runCatching { am.setWindow(AlarmManager.RTC, at, 60_000L, tick(context)) }
    }

    private fun cancel(context: Context) {
        (context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager)?.cancel(tick(context))
    }
}

/** Pieni widget: Nyt ja milloin se päättyy. */
class SmallWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        WidgetUpdater.update(context)
    }
}

/** Leveä widget: Nyt ja Seuraavaksi. */
class WideWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        WidgetUpdater.update(context)
    }
}

/** Hälytys tapahtuman rajalla: widgetit päivitetään. */
class WidgetTickReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        WidgetUpdater.update(context)
    }
}
