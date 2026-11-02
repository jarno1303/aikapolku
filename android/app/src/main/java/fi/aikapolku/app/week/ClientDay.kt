package fi.aikapolku.app.week

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fi.aikapolku.app.R
import fi.aikapolku.app.cards.labelRes
import fi.aikapolku.app.data.ClientViewPrefs
import fi.aikapolku.app.data.DayParts
import fi.aikapolku.app.data.EventChange
import fi.aikapolku.app.data.TimelineMode
import fi.aikapolku.app.data.WeekEvent
import fi.aikapolku.app.data.WeekSchedule
import fi.aikapolku.app.firstthen.FirstThenPair
import fi.aikapolku.app.ui.AppColors
import fi.aikapolku.app.ui.ChangeSymbol
import java.time.LocalTime

/** Ohjaajan asiakkaalle valmistelema tai jo käynnissä oleva toiminta (näkyy NYT-kortissa). */
internal sealed class ClientActivity {
    data class FirstThen(
        val first: String,
        val firstPhoto: String,
        val next: String,
        val nextPhoto: String,
        val started: Boolean
    ) : ClientActivity()

    data class Timer(val title: String) : ClientActivity()
}

@Composable
private fun NowLabel(text: String) {
    Surface(color = AppColors.Teal, shape = RoundedCornerShape(10.dp)) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
    }
}

@Composable
private fun BigButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(containerColor = AppColors.Teal)
    ) {
        Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(30.dp))
        Text(text, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 6.dp))
    }
}

/** NYT-kortin runko: korostettu, iso, yksi painike. */
@Composable
private fun NowFrame(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = AppColors.TealSoft,
        modifier = Modifier.fillMaxWidth().border(3.dp, AppColors.Teal, RoundedCornerShape(24.dp))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) { content() }
    }
}

/**
 * Asiakkaan etusivun päivä: iso NYT-kortti (ohjaajan valmistelema toiminta tai päivän
 * nykyinen tapahtuma) ja sen alla päivän aikajana asetusten mukaan.
 */
@Composable
internal fun ClientDayView(
    prefs: ClientViewPrefs,
    activity: ClientActivity?,
    onOpenActivity: () -> Unit,
    onOpenProgram: (WeekEvent) -> Unit,
    onOpenCard: (WeekEvent) -> Unit,
    onOpenChange: (EventChange) -> Unit
) {
    val state = rememberTodayState()
    fun open(item: DayItem) {
        val change = item.change
        when {
            change != null && item.cancelled -> onOpenChange(change)
            item.resolved.isProgram -> onOpenProgram(item.event)
            else -> onOpenCard(item.event)
        }
    }

    // NYT: ohjaajan valmistelema toiminta ensin, muuten päivän nykyinen tapahtuma.
    when {
        activity is ClientActivity.FirstThen -> NowFrame {
            NowLabel(stringResource(R.string.now_label))
            FirstThenPair(
                first = activity.first, firstPhoto = activity.firstPhoto, firstDescription = "",
                next = activity.next, nextPhoto = activity.nextPhoto, nextDescription = "",
                editable = false, compact = true
            )
            BigButton(stringResource(if (activity.started) R.string.client_continue else R.string.today_start), onOpenActivity)
        }
        activity is ClientActivity.Timer -> NowFrame {
            NowLabel(stringResource(R.string.now_label))
            Text(activity.title, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = AppColors.Ink, textAlign = TextAlign.Center)
            Text(stringResource(R.string.timer_running_row), fontSize = 17.sp, color = AppColors.Muted)
            BigButton(stringResource(R.string.client_continue), onOpenActivity)
        }
        state != null && state.current >= 0 -> {
            val item = state.items[state.current]
            val upcoming = WeekSchedule.minutesOf(item.event.time) > LocalTime.now().let { it.hour * 60 + it.minute }
            NowFrame {
                NowLabel(stringResource(if (upcoming) R.string.client_next else R.string.now_label))
                Box {
                    EventImage(item.resolved, Modifier.fillMaxWidth().sizeIn(maxWidth = 220.dp, maxHeight = 220.dp).aspectRatio(1f).clip(RoundedCornerShape(22.dp)))
                    if (item.change != null || item.entry.stepChanges.isNotEmpty()) {
                        ChangeSymbol(Modifier.size(40.dp).align(Alignment.TopEnd).offset(x = 8.dp, y = (-8).dp))
                    }
                }
                Text(item.resolved.title, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = AppColors.Ink, textAlign = TextAlign.Center)
                if (prefs.showTimes) {
                    Text(stringResource(R.string.change_time_now, item.event.time.replace(':', '.')), fontSize = 18.sp, color = AppColors.Muted)
                }
                BigButton(stringResource(R.string.today_start)) { open(item) }
            }
        }
        state != null -> NowFrame {
            Icon(Icons.Rounded.Check, contentDescription = null, tint = AppColors.Teal, modifier = Modifier.size(56.dp))
            Text(stringResource(R.string.client_all_done), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = AppColors.Ink, textAlign = TextAlign.Center)
        }
        else -> Text(stringResource(R.string.client_no_events), fontSize = 17.sp, color = AppColors.Muted)
    }

    if (state == null || prefs.timeline == TimelineMode.NONE) return
    val skipped = state.items.indices.filter { state.items[it].cancelled || state.ackTimes[state.items[it].event.id] != null }.toSet()
    val rows = DayParts.visible(state.items.size, state.current, { it in skipped }, prefs.timeline)
    if (rows.isEmpty()) return
    Text(
        stringResource(R.string.client_my_day),
        modifier = Modifier.padding(top = 8.dp).semantics { heading() },
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        color = AppColors.Ink
    )
    var part: fi.aikapolku.app.data.DayTime? = null
    rows.forEach { index ->
        val item = state.items[index]
        if (prefs.timeline == TimelineMode.FULL) {
            val p = DayParts.of(item.event.time)
            if (p != part) {
                part = p
                Text(stringResource(p.labelRes()), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = AppColors.Teal, modifier = Modifier.padding(top = 4.dp))
            }
        }
        TimelineRow(
            item = item,
            isCurrent = index == state.current,
            isDone = state.ackTimes[item.event.id] != null,
            isPast = index in skipped || (state.current >= 0 && index < state.current),
            showTime = prefs.showTimes,
            onClick = { open(item) }
        )
    }
}

/** Aikajanan rivi: aika, kuva, nimi; nyt korostettuna, tehdyt kuitattuina ja himmeinä. */
@Composable
private fun TimelineRow(item: DayItem, isCurrent: Boolean, isDone: Boolean, isPast: Boolean, showTime: Boolean, onClick: () -> Unit) {
    val change = item.change ?: item.entry.stepChanges.firstOrNull()
    val changeText = change?.let { changeSummary(it, item.original, item.resolved) }
    val nowLabel = stringResource(R.string.now_label)
    val doneLabel = stringResource(R.string.client_done)
    val a11y = listOfNotNull(
        if (showTime) item.event.time else null,
        item.resolved.title,
        if (isCurrent) nowLabel else null,
        if (isDone) doneLabel else null,
        changeText
    ).joinToString(", ")
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (isCurrent) AppColors.TealSoft else Color.White,
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (isPast && !isCurrent) 0.5f else 1f)
            .then(if (isCurrent) Modifier.border(2.dp, AppColors.Teal, RoundedCornerShape(16.dp)) else Modifier)
            .semantics(mergeDescendants = true) { contentDescription = a11y }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (showTime) {
                Text(
                    item.event.time.replace(':', '.'),
                    modifier = Modifier.width(48.dp),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isCurrent) AppColors.Teal else AppColors.Muted
                )
            }
            Box {
                EventImage(item.resolved, Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)))
                if (change != null) ChangeSymbol(Modifier.size(20.dp).align(Alignment.TopEnd).offset(x = 6.dp, y = (-6).dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.resolved.title,
                    fontSize = 18.sp,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                    color = AppColors.Ink,
                    textDecoration = if (item.cancelled) TextDecoration.LineThrough else null,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (changeText != null && !item.cancelled) {
                    Text(changeText, fontSize = 14.sp, color = AppColors.Change, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            if (isDone) Icon(Icons.Rounded.Check, contentDescription = null, tint = AppColors.Teal, modifier = Modifier.size(28.dp))
        }
    }
}
