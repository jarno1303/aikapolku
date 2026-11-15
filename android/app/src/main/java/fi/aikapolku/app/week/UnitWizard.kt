package fi.aikapolku.app.week

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fi.aikapolku.app.R
import fi.aikapolku.app.cards.CardArt
import fi.aikapolku.app.cards.CardPhoto
import fi.aikapolku.app.data.ActivitySpec
import fi.aikapolku.app.data.ActivityView
import fi.aikapolku.app.data.ClientDay
import fi.aikapolku.app.data.ClientViewPrefs
import fi.aikapolku.app.data.GroupStore
import fi.aikapolku.app.data.MealSpec
import fi.aikapolku.app.data.ProfileScope
import fi.aikapolku.app.data.ProfileStore
import fi.aikapolku.app.data.SubUnitSpec
import fi.aikapolku.app.data.TimelineMode
import fi.aikapolku.app.data.UnitSetup
import fi.aikapolku.app.data.UnitSpec
import fi.aikapolku.app.data.Units
import fi.aikapolku.app.data.WeekPlan
import fi.aikapolku.app.data.WeekSchedule
import fi.aikapolku.app.ui.AppColors
import fi.aikapolku.app.ui.BackOverride
import fi.aikapolku.app.ui.PrimaryButton
import fi.aikapolku.app.ui.SecondaryButton
import fi.aikapolku.app.ui.SegmentedChoice
import java.time.LocalDate
import java.util.UUID

/** Asukas ohjatussa aloituksessa: uusi (nimi) tai olemassa oleva profiili; [key] = tunnus tai "new:n". */
private data class WizardResident(val key: String, val name: String, val unitIndex: Int)

/** Asukkaan tuki: kuinka pitkälle hän näkee ja mitä toiminnan aikana (Y5). */
private data class WizardSupport(val timeline: TimelineMode = TimelineMode.NEXT, val activity: ActivityView = ActivityView.CARD)

private data class WizardActivity(
    val name: String,
    val photo: String,
    val days: Set<Int> = (1..5).toSet(),
    val start: String = "09:00",
    val end: String = "14:00",
    val members: Set<String> = emptySet()
)

/**
 * Ohjattu aloitus (V4, Y8): yksikkö ja alayksiköt → asukkaat → yhteiset ruokailut → päivän
 * toiminnat → tuen taso → esikatselu asukkaana. Kerran tehtävä; hienosäätö myöhemmin
 * Yksiköt ja ryhmät -näkymässä.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun UnitWizard(onDone: () -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext
    val existing = remember { ProfileStore.list(app) }
    fun cardName(id: String) = CardArt.templates.firstOrNull { it.id == id }?.let { context.getString(it.nameRes) } ?: id

    var step by remember { mutableIntStateOf(0) }
    var unitName by remember { mutableStateOf("") }
    var subUnits by remember { mutableStateOf(listOf<String>()) }
    var residents by remember { mutableStateOf(listOf<WizardResident>()) }
    var newCount by remember { mutableIntStateOf(0) }
    var meals by remember { mutableStateOf(UnitSetup.defaultMeals(::cardName)) }
    var activities by remember { mutableStateOf(listOf<WizardActivity>()) }
    var support by remember { mutableStateOf(mapOf<String, WizardSupport>()) }
    val steps = 6

    fun back() { if (step == 0) onCancel() else step-- }
    BackOverride(::back)
    BackHandler(onBack = ::back)

    // Asukas, jonka alayksikköä ei ole (poistettu tai nimetön), kuuluu suoraan yksikköön.
    fun inSubUnit(r: WizardResident) = r.unitIndex in subUnits.indices && subUnits[r.unitIndex].isNotBlank()
    fun spec(): UnitSpec = UnitSpec(
        name = unitName,
        subUnits = subUnits.mapIndexed { i, n -> SubUnitSpec(n, residents.filter { it.unitIndex == i }.map { it.key }) },
        members = residents.filterNot(::inSubUnit).map { it.key },
        meals = meals,
        activities = activities.map { ActivitySpec(it.name, it.photo, it.days, it.start, it.end, it.members) }
    )

    Text(stringResource(R.string.wizard_step, step + 1, steps), fontSize = 14.sp, color = AppColors.Muted)
    val titles = listOf(
        R.string.wizard_unit_title, R.string.wizard_residents_title, R.string.wizard_meals_title,
        R.string.wizard_activities_title, R.string.wizard_support_title, R.string.wizard_preview_title
    )
    Text(stringResource(titles[step]), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = AppColors.Ink, modifier = Modifier.semantics { heading() })

    when (step) {
        // 1. Yksikkö ja alayksiköt.
        0 -> {
            Text(stringResource(R.string.wizard_unit_info), fontSize = 15.sp, color = AppColors.Muted)
            OutlinedTextField(
                value = unitName, onValueChange = { unitName = it.take(40) },
                label = { Text(stringResource(R.string.wizard_unit_name)) },
                placeholder = { Text(stringResource(R.string.wizard_unit_name_hint)) },
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            Text(stringResource(R.string.wizard_sub_units), fontWeight = FontWeight.SemiBold)
            subUnits.forEachIndexed { i, n ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = n, onValueChange = { v -> subUnits = subUnits.toMutableList().also { it[i] = v.take(30) } },
                        singleLine = true, modifier = Modifier.weight(1f),
                        placeholder = { Text(stringResource(R.string.wizard_sub_unit_hint)) }
                    )
                    TextButton(onClick = {
                        subUnits = subUnits.filterIndexed { j, _ -> j != i }
                        // Asukkaat siirtyvät yksikköön (ei alayksikköä).
                        residents = residents.map { r -> if (r.unitIndex == i) r.copy(unitIndex = -1) else if (r.unitIndex > i) r.copy(unitIndex = r.unitIndex - 1) else r }
                    }) { Text(stringResource(R.string.wizard_remove)) }
                }
            }
            SecondaryButton(stringResource(R.string.wizard_add_sub_unit), { subUnits = subUnits + "" }, modifier = Modifier.fillMaxWidth())
        }

        // 2. Asukkaat alayksiköittäin.
        1 -> {
            Text(stringResource(R.string.wizard_residents_info), fontSize = 15.sp, color = AppColors.Muted)
            val unitsShown = subUnits.indices.filter { subUnits[it].isNotBlank() } +
                (if (subUnits.none { it.isNotBlank() } || residents.any { !inSubUnit(it) }) listOf(-1) else emptyList())
            unitsShown.forEach { ui ->
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(if (ui >= 0) subUnits[ui] else unitName.ifBlank { stringResource(R.string.unit_kind_unit) }, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        residents.filter { if (ui >= 0) it.unitIndex == ui else !inSubUnit(it) }.forEach { r ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (r.key.startsWith("new:")) {
                                    OutlinedTextField(
                                        value = r.name,
                                        onValueChange = { v -> residents = residents.map { if (it.key == r.key) it.copy(name = v.take(30)) else it } },
                                        singleLine = true, modifier = Modifier.weight(1f),
                                        placeholder = { Text(stringResource(R.string.profile_name_label)) }
                                    )
                                } else {
                                    Text(r.name, fontSize = 16.sp, modifier = Modifier.weight(1f))
                                }
                                TextButton(onClick = { residents = residents.filterNot { it.key == r.key } }) { Text(stringResource(R.string.wizard_remove)) }
                            }
                        }
                        SecondaryButton(stringResource(R.string.wizard_add_resident), {
                            residents = residents + WizardResident("new:${newCount++}", "", ui)
                        }, modifier = Modifier.fillMaxWidth())
                        val free = existing.filter { p -> residents.none { it.key == p.id } }
                        if (free.isNotEmpty()) {
                            Text(stringResource(R.string.wizard_existing), fontSize = 13.sp, color = AppColors.Muted)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                free.forEach { p ->
                                    FilterChip(selected = false, onClick = { residents = residents + WizardResident(p.id, p.name, ui) },
                                        label = { Text(p.name.ifBlank { stringResource(R.string.profile_unnamed) }) })
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. Yhteiset ruokailut (arki / viikonloppu).
        2 -> {
            Text(stringResource(R.string.wizard_meals_info), fontSize = 15.sp, color = AppColors.Muted)
            meals.forEachIndexed { i, meal ->
                fun update(m: MealSpec) { meals = meals.toMutableList().also { it[i] = m } }
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            CardPhoto(meal.name, "builtin:${meal.cardId}", Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)))
                            Text(meal.name, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Switch(checked = meal.weekday != null || meal.weekend != null, onCheckedChange = { on ->
                                update(if (on) meal.copy(weekday = meal.weekday ?: "12:00", weekend = meal.weekend ?: "12:00") else meal.copy(weekday = null, weekend = null))
                            })
                        }
                        if (meal.weekday != null || meal.weekend != null) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { pickTime(context, meal.weekday ?: "12:00") { update(meal.copy(weekday = it)) } }, modifier = Modifier.heightIn(min = 48.dp)) {
                                    Text(stringResource(R.string.wizard_weekdays, meal.weekday?.let(::formatClock) ?: "–"))
                                }
                                OutlinedButton(onClick = { pickTime(context, meal.weekend ?: "12:00") { update(meal.copy(weekend = it)) } }, modifier = Modifier.heightIn(min = 48.dp)) {
                                    Text(stringResource(R.string.wizard_weekend, meal.weekend?.let(::formatClock) ?: "–"))
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. Päivän toiminnat ja kuka käy missä.
        3 -> {
            Text(stringResource(R.string.wizard_activities_info), fontSize = 15.sp, color = AppColors.Muted)
            activities.forEachIndexed { i, a ->
                fun update(x: WizardActivity) { activities = activities.toMutableList().also { it[i] = x } }
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(value = a.name, onValueChange = { update(a.copy(name = it.take(30))) }, singleLine = true,
                                modifier = Modifier.weight(1f), label = { Text(stringResource(R.string.wizard_activity_name)) })
                            TextButton(onClick = { activities = activities.filterIndexed { j, _ -> j != i } }) { Text(stringResource(R.string.wizard_remove)) }
                        }
                        ActivitySessionsEditor(a.days.sorted().map { fi.aikapolku.app.data.ActivitySession(it, a.start, a.end) }) { s ->
                            update(a.copy(days = s.map { it.day }.toSet(), start = s.firstOrNull()?.start ?: a.start, end = s.firstOrNull()?.end ?: a.end))
                        }
                        Text(stringResource(R.string.wizard_who), fontWeight = FontWeight.SemiBold)
                        residents.forEach { r ->
                            val checked = r.key in a.members
                            Row(
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Checkbox) {
                                    update(a.copy(members = if (checked) a.members - r.key else a.members + r.key))
                                },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(checked = checked, onCheckedChange = null)
                                Text(r.name.ifBlank { stringResource(R.string.profile_unnamed) }, fontSize = 16.sp)
                            }
                        }
                    }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(R.string.wizard_day_activity to "builtin:schoolwork", R.string.wizard_workshop to "builtin:crafts", R.string.wizard_other_activity to "builtin:schoolwork")
                    .forEach { (label, photo) ->
                        val name = stringResource(label)
                        OutlinedButton(onClick = { activities = activities + WizardActivity(if (label == R.string.wizard_other_activity) "" else name, photo) },
                            modifier = Modifier.heightIn(min = 48.dp)) { Text("+ $name") }
                    }
            }
        }

        // 5. Tuen taso: kuinka pitkälle asukas näkee ja mitä toiminnan aikana.
        4 -> {
            Text(stringResource(R.string.wizard_support_info), fontSize = 15.sp, color = AppColors.Muted)
            residents.forEach { r ->
                val s = support[r.key] ?: WizardSupport()
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(r.name.ifBlank { stringResource(R.string.profile_unnamed) }, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        val modes = listOf(TimelineMode.FULL, TimelineMode.NEXT, TimelineMode.NONE)
                        SegmentedChoice(
                            options = listOf(stringResource(R.string.wizard_sees_full), stringResource(R.string.wizard_sees_next), stringResource(R.string.wizard_sees_now)),
                            selected = modes.indexOf(s.timeline),
                            onSelect = { support = support + (r.key to s.copy(timeline = modes[it])) }
                        )
                        if (activities.any { r.key in it.members }) {
                            SegmentedChoice(
                                options = listOf(stringResource(R.string.client_view_activity_card), stringResource(R.string.client_view_activity_plan)),
                                selected = if (s.activity == ActivityView.CARD) 0 else 1,
                                onSelect = { support = support + (r.key to s.copy(activity = if (it == 0) ActivityView.CARD else ActivityView.PLAN)) }
                            )
                        }
                    }
                }
            }
        }

        // 6. Esikatselu asukkaana.
        else -> {
            Text(stringResource(R.string.wizard_preview_info), fontSize = 15.sp, color = AppColors.Muted)
            var who by remember { mutableStateOf(residents.firstOrNull()?.key) }
            var day by remember { mutableIntStateOf(1) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                residents.forEach { r ->
                    FilterChip(selected = who == r.key, onClick = { who = r.key }, label = { Text(r.name.ifBlank { stringResource(R.string.profile_unnamed) }) })
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (1..7).forEach { d -> FilterChip(selected = day == d, onClick = { day = d }, label = { Text(dayShort(d)) }) }
            }
            val key = who
            if (key != null) {
                val groups = remember(unitName, subUnits, residents, meals, activities) { var k = 0; UnitSetup.build(spec()) { "preview${k++}" } }
                val date = LocalDate.now().let { it.plusDays(((day - it.dayOfWeek.value + 7) % 7).toLong()) }
                val view = (support[key] ?: WizardSupport()).activity
                val events = ClientDay(WeekPlan(), Units.groupsFor(groups, key), key, activityView = view).eventsFor(date)
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = AppColors.TealSoft)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (events.isEmpty()) Text(stringResource(R.string.client_no_events), color = AppColors.Muted)
                        events.forEach { e ->
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(if (e.until.isNotBlank()) rangeText(e.time, e.until) else formatClock(e.time), fontSize = 15.sp, color = AppColors.Muted)
                                CardPhoto(e.cardName, e.cardPhoto, Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)))
                                Text(e.cardName, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    }

    val nameMissing = step == 0 && unitName.isBlank()
    val residentsMissing = step == 1 && (residents.isEmpty() || residents.any { it.name.isBlank() })
    if (residentsMissing && residents.isNotEmpty()) Text(stringResource(R.string.wizard_names_needed), color = AppColors.Change, fontSize = 13.sp)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        SecondaryButton(stringResource(if (step == 0) R.string.cancel else R.string.wizard_back), ::back, modifier = Modifier.weight(1f))
        if (step < steps - 1) {
            PrimaryButton(stringResource(R.string.wizard_next), { step++ }, modifier = Modifier.weight(1f), enabled = !nameMissing && !residentsMissing)
        } else {
            PrimaryButton(stringResource(R.string.wizard_save), {
                // Uudet asukkaat profiileiksi, sitten ryhmät oikeilla tunnuksilla ja asukkaiden asetukset.
                val ids = residents.associate { r -> r.key to (if (r.key.startsWith("new:")) ProfileStore.add(app, r.name.trim()).id else r.key) }
                val real = spec().let { s ->
                    s.copy(
                        subUnits = s.subUnits.map { u -> u.copy(members = u.members.map { ids.getValue(it) }) },
                        members = s.members.map { ids.getValue(it) },
                        activities = s.activities.map { a -> a.copy(members = a.members.map { ids.getValue(it) }.toSet()) }
                    )
                }
                val store = GroupStore(app)
                store.save(store.load() + UnitSetup.build(real) { UUID.randomUUID().toString() })
                residents.forEach { r ->
                    val s = support[r.key] ?: WizardSupport()
                    val id = ids.getValue(r.key)
                    ProfileScope.prefs(app, ProfileScope.RUN_PREFS, id).edit().putString("client_timeline", s.timeline.key).apply()
                    ClientViewPrefs.setActivityView(app, s.activity, id)
                }
                EventReminders.reschedule(app)
                GroupSession.changed()
                onDone()
            }, modifier = Modifier.weight(1f))
        }
    }
}
