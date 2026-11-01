package fi.aikapolku.app.week

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fi.aikapolku.app.R
import fi.aikapolku.app.cards.CardPhoto
import fi.aikapolku.app.data.ProgramStore
import fi.aikapolku.app.data.WeekSchedule
import fi.aikapolku.app.data.WeekStore
import fi.aikapolku.app.ui.AppColors
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.delay

/**
 * Aloitusnäkymän "Tänään": päivän tapahtumat kellonajan mukaan. Näkyy vain, kun päivälle
 * on tapahtumia. Nykyinen (tai ensimmäinen tuleva) tapahtuma on korostettu ja alkaa
 * yhdellä napautuksella; menneet ovat himmeitä.
 */
@Composable
internal fun TodaySection(onOpenProgram: (String) -> Unit, onOpenCard: (String, String) -> Unit) {
    val context = LocalContext.current
    val plan = remember(context) { WeekStore(context.applicationContext).load() }
    val programs = remember(context) { ProgramStore(context.applicationContext).load() }
    var now by remember { mutableStateOf(LocalTime.now()) }
    var date by remember { mutableStateOf(LocalDate.now()) }

    val events = WeekSchedule.eventsFor(plan, date)
        .map { resolveEvent(it, programs) }
        .filter { it.title.isNotBlank() }
    if (events.isEmpty()) return
    // Korostus siirtyy ajan kuluessa (puolen minuutin välein). Vain kun listassa on jotain.
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000L)
            now = LocalTime.now()
            date = LocalDate.now()
        }
    }
    val current = WeekSchedule.currentIndex(events.map { it.event }, now)

    fun open(item: ResolvedEvent) {
        if (item.isProgram) onOpenProgram(item.event.programId) else onOpenCard(item.event.cardName, item.event.cardPhoto)
    }

    Text(
        stringResource(R.string.today_title, dayName(date.dayOfWeek.value)),
        modifier = Modifier.semantics { heading() },
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        color = AppColors.Ink
    )
    val nowLabel = stringResource(R.string.now_label)
    events.forEachIndexed { index, item ->
        val isCurrent = index == current
        val isPast = index < current
        val a11y = item.event.time + " " + item.title + if (isCurrent) ", $nowLabel" else ""
        Surface(
            onClick = { open(item) },
            shape = RoundedCornerShape(18.dp),
            color = if (isCurrent) AppColors.TealSoft else Color.White,
            modifier = Modifier
                .fillMaxWidth()
                .alpha(if (isPast) 0.45f else 1f)
                .then(if (isCurrent) Modifier.border(2.dp, AppColors.Teal, RoundedCornerShape(18.dp)) else Modifier)
                .semantics(mergeDescendants = true) { contentDescription = a11y }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = if (isCurrent) 12.dp else 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    item.event.time,
                    modifier = Modifier.width(54.dp),
                    fontSize = if (isCurrent) 18.sp else 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isCurrent) AppColors.Teal else AppColors.Muted
                )
                EventImage(
                    item,
                    Modifier.size(if (isCurrent) 64.dp else 44.dp).clip(RoundedCornerShape(if (isCurrent) 16.dp else 12.dp))
                )
                Text(
                    item.title,
                    modifier = Modifier.weight(1f),
                    fontSize = if (isCurrent) 20.sp else 17.sp,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                    color = AppColors.Ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (isCurrent) {
                    // Koko rivi on painike; "Aloita" näyttää, mistä päivä jatkuu.
                    Surface(color = AppColors.Teal, shape = RoundedCornerShape(14.dp)) {
                        Text(
                            stringResource(R.string.today_start),
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

/** Yksittäisen kortin tapahtuma isona: kuva, nimi ja Valmis. */
@Composable
internal fun EventCardScreen(name: String, photo: String, onDone: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        CardPhoto(
            name,
            photo,
            Modifier
                .fillMaxWidth()
                .sizeIn(maxWidth = 340.dp, maxHeight = 340.dp)
                .aspectRatio(1f)
                .clip(RoundedCornerShape(28.dp))
        )
        Text(
            name,
            modifier = Modifier.semantics { heading() },
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = AppColors.Ink,
            textAlign = TextAlign.Center
        )
        Button(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AppColors.Teal)
        ) { Text(stringResource(R.string.run_done_button), fontSize = 21.sp, fontWeight = FontWeight.Bold) }
    }
}
