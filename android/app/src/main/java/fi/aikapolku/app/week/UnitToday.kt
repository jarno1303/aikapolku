package fi.aikapolku.app.week

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fi.aikapolku.app.R
import fi.aikapolku.app.data.ClientWeek
import fi.aikapolku.app.data.Group
import fi.aikapolku.app.data.GroupKind
import fi.aikapolku.app.data.GroupStore
import fi.aikapolku.app.data.ProfileStore
import fi.aikapolku.app.data.Units
import fi.aikapolku.app.data.WeekSchedule
import fi.aikapolku.app.ui.AppColors
import java.time.LocalDate
import java.time.LocalTime

/** Asukkaan tilanne nyt yksikössä (V3). */
internal data class ResidentNow(val memberId: String, val activity: Group?, val until: String?, val absent: Boolean)

/** Yksikön tilanne laskettuna: puhdas logiikka (testattavissa ilman näkymää). */
internal object UnitToday {
    /** Yksikön ja sen alayksiköiden asukkaat. */
    fun residents(unit: Group, all: List<Group>): List<String> {
        val out = linkedSetOf<String>()
        fun walk(g: Group, seen: Set<String>) {
            if (g.id in seen) return
            out += g.members
            Units.children(g, all).forEach { walk(it, seen + g.id) }
        }
        walk(unit, emptySet())
        return out.toList()
    }

    fun now(
        memberId: String,
        all: List<Group>,
        date: LocalDate,
        time: String,
        absentFromUnit: (String) -> Boolean,
        day: (String) -> fi.aikapolku.app.data.ClientDay
    ): ResidentNow {
        if (absentFromUnit(memberId)) return ResidentNow(memberId, null, null, true)
        val span = Units.at(day(memberId).spans(date), time)
        return ResidentNow(memberId, span?.group, span?.end, false)
    }
}

/**
 * Ohjaajan etusivulle "Tänään yksikössä" (V3): kuka on missä ja milloin palaa, ja montako on
 * kotona yksikön seuraavassa kohdassa (esim. lounas). Vain ohjaajalle: asukkaat eivät näe toisiaan.
 */
@Composable
internal fun UnitTodayCard() {
    val context = LocalContext.current
    val app = context.applicationContext
    val revision = GroupSession.revision
    val all = remember(revision) { GroupStore(app).load() }
    val tops = all.filter { it.kind == GroupKind.UNIT && (it.parentId.isBlank() || all.none { p -> p.id == it.parentId }) }
    if (tops.isEmpty()) return
    val profiles = remember(revision) { ProfileStore.list(app) }
    fun name(id: String) = profiles.firstOrNull { it.id == id }?.name?.ifBlank { null } ?: context.getString(R.string.profile_unnamed)
    val today = LocalDate.now()
    val nowTime = LocalTime.now().let { WeekSchedule.formatTime(it.hour, it.minute) }
    val absences = remember(revision) { GroupStore(app).absences() }
    tops.forEach { unit ->
        val residents = UnitToday.residents(unit, all)
        if (residents.isEmpty()) return@forEach
        val unitIds = listOf(unit.id) + all.filter { g -> g.kind == GroupKind.UNIT && Units.ancestors(g, all).any { it.id == unit.id } }.map { it.id }
        val days = remember(revision, residents) { residents.associateWith { ClientWeek.load(app, it) } }
        val status = residents.map { id ->
            UnitToday.now(id, all, today, nowTime, { m -> unitIds.any { u -> m in absences[u]?.get(today).orEmpty() } }) { days.getValue(it) }
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(R.string.unit_today_title) + " · " + unit.name,
                    fontSize = 18.sp, fontWeight = FontWeight.Bold, color = AppColors.Ink,
                    modifier = Modifier.semantics { heading() }
                )
                Text(stringResource(R.string.unit_today_home, status.count { !it.absent && it.activity == null }), fontSize = 16.sp, color = AppColors.Ink)
                status.filter { it.activity != null }.forEach {
                    Text(stringResource(R.string.unit_today_at, name(it.memberId), it.activity!!.name, formatClock(it.until.orEmpty())), fontSize = 15.sp, color = AppColors.Ink)
                }
                status.filter { it.absent }.forEach {
                    Text(stringResource(R.string.unit_today_absent, name(it.memberId)), fontSize = 15.sp, color = AppColors.Muted)
                }
                // Yksikön seuraava yhteinen kohta: montako on silloin kotona.
                val next = WeekSchedule.eventsFor(unit.plan, today).firstOrNull { WeekSchedule.minutesOf(it.time) >= WeekSchedule.minutesOf(nowTime) }
                if (next != null) {
                    val home = residents.count { id ->
                        status.first { it.memberId == id }.absent.not() && Units.at(days.getValue(id).spans(today), next.time) == null
                    }
                    Text(stringResource(R.string.unit_today_next, next.cardName, formatClock(next.time), home), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = AppColors.Teal)
                }
            }
        }
    }
}

/** Ohjaajalle asukkaan päivän alla: yksikön kohdat, joita asukas ei näe, koska on silloin toiminnassa. */
@Composable
internal fun HiddenForClient() {
    val context = LocalContext.current
    val app = context.applicationContext
    val revision = GroupSession.revision
    val hidden = remember(revision) { ClientWeek.load(app).hiddenFor(LocalDate.now()) }
    if (hidden.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(stringResource(R.string.unit_hidden_title), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AppColors.Muted)
        hidden.forEach { (event, group) ->
            Text(
                stringResource(R.string.unit_hidden_line, formatClock(event.time), event.cardName, group.name),
                fontSize = 14.sp, color = AppColors.Muted
            )
        }
    }
}
