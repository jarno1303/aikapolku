package fi.aikapolku.app.data

import android.content.Context
import java.time.LocalDate
import org.json.JSONArray
import org.json.JSONObject

/*
 * Ryhmät (esim. päivätoiminnan ryhmä). Ryhmällä on jäsenet (asiakkaat) ja yhteinen
 * viikkorunko. Asiakkaan päivä = ryhmien rungot (kun hän on paikalla) + omat kohdat +
 * päivän muutokset. Ryhmät ovat laitteen yhteisiä tietoja, eivät yhden asiakkaan.
 *
 * Ryhmän tapahtuman tunnus asiakkaan päivässä on "grp:<ryhmä>:<tapahtuma>", joten kuittaukset
 * ja muutokset kohdistuvat asiakaskohtaisesti samaan tapahtumaan kuin omissa kohdissa.
 * Rungossa on vain kortteja: päiväohjelmat (tehtävälistat) ovat asiakaskohtaisia.
 */

/** Asiakkaan toistuva poikkeus ryhmän kohtaan: jää pois tai tilalla on muu kortti. */
internal enum class ExceptionKind(val key: String) {
    SKIP("skip"),
    REPLACE("replace");

    companion object {
        fun from(key: String?): ExceptionKind = entries.firstOrNull { it.key == key } ?: SKIP
    }
}

/** Esim. "tiistain liikunnan aikana Mikolla fysioterapia" (REPLACE) tai "ei aamukokousta" (SKIP). */
internal data class MemberException(
    val eventId: String,
    val memberId: String,
    val kind: ExceptionKind,
    val cardName: String = "",
    val cardPhoto: String = ""
)

internal data class Group(
    val id: String,
    val name: String,
    val members: List<String> = emptyList(),
    val plan: WeekPlan = WeekPlan(),
    val exceptions: List<MemberException> = emptyList(),
    /** Yksikkömalli (Units.kt): ryhmä, yksikkö tai päivän toiminta. */
    val kind: GroupKind = GroupKind.GROUP,
    /** Yksikön yläyksikkö (tyhjä = ylin). Vain yksiköille. */
    val parentId: String = "",
    /** Toiminnan kokoontumiset. Vain toiminnoille. */
    val sessions: List<ActivitySession> = emptyList(),
    /** Asukkaiden omat ajat toiminnassa. */
    val memberTimes: List<MemberTime> = emptyList(),
    /** Toiminnan kortin kuva asukkaan päivässä (esim. "builtin:schoolwork"). */
    val cardPhoto: String = "",
    /** Siirtymäkortit (Y6): ohjaajan käyttöön ottamina; tyhjä = ei korttia. */
    val leaveCard: String = "",
    val returnCard: String = ""
)

/** Ryhmän rungon päättely asiakkaalle: puhdas logiikka, testattavissa ilman laitetta. */
internal object GroupSchedule {
    private const val PREFIX = "grp:"

    fun eventId(groupId: String, eventId: String): String = "$PREFIX$groupId:$eventId"

    fun isGroupEvent(id: String): Boolean = id.startsWith(PREFIX)

    /** (ryhmä, tapahtuma) tunnuksesta "grp:<ryhmä>:<tapahtuma>", tai null. */
    fun parse(id: String): Pair<String, String>? {
        if (!isGroupEvent(id)) return null
        val rest = id.removePrefix(PREFIX)
        val split = rest.indexOf(':')
        if (split <= 0 || split == rest.length - 1) return null
        return rest.substring(0, split) to rest.substring(split + 1)
    }

    /** Ryhmän tapahtuma asiakkaalle: tunnus ja asiakkaan toistuva poikkeus huomioituna. Null = jää pois. */
    fun forMember(group: Group, memberId: String, event: WeekEvent): WeekEvent? {
        if (event.kind != EventKind.CARD) return null
        val base = event.copy(id = eventId(group.id, event.id))
        val exception = group.exceptions.firstOrNull { it.eventId == event.id && it.memberId == memberId }
        return when (exception?.kind) {
            null -> base
            ExceptionKind.SKIP -> null
            ExceptionKind.REPLACE -> base.copy(cardName = exception.cardName, cardPhoto = exception.cardPhoto)
        }
    }

    /** Ryhmän päivä asiakkaalle. Tyhjä, jos hän ei ole jäsen tai on poissa. */
    fun eventsFor(group: Group, memberId: String, date: LocalDate, absent: Boolean): List<WeekEvent> {
        if (memberId !in group.members || absent) return emptyList()
        return WeekSchedule.eventsFor(group.plan, date).mapNotNull { forMember(group, memberId, it) }
    }

    /** Ryhmän päivämuutos asiakkaalle: tunnus asiakkaan päivän muotoon. */
    fun toMember(groupId: String, change: EventChange): EventChange = change.copy(eventId = eventId(groupId, change.eventId))

    fun eventIds(group: Group, memberId: String): Set<String> =
        if (memberId !in group.members) emptySet()
        else group.plan.events.map { eventId(group.id, it.id) }.toSet()
}

/**
 * Asiakkaan koko päivä: omat kohdat ja ne ryhmät, joissa hän on jäsen. [absent] kertoo
 * poissaolon ryhmästä päivänä (poissa = ryhmän ohjelmaa ei tule).
 */
internal class ClientDay(
    val own: WeekPlan,
    val groups: List<Group>,
    val memberId: String,
    /** Ryhmien päivämuutokset asiakkaan päivän tunnuksin (koskevat kaikkia paikalla olevia). */
    val groupChanges: List<EventChange> = emptyList(),
    private val absent: (groupId: String, date: LocalDate) -> Boolean = { _, _ -> false },
    /** Mitä asukas näkee toiminnan aikana (Y5). */
    val activityView: ActivityView = ActivityView.CARD,
    /** Asukkaan lomat ja pidemmät poissaolot (Leaves.kt). */
    val leaves: List<Leave> = emptyList(),
    /** Lomakortin teksti ("Lomalla – takaisin su klo 18"). */
    private val leaveTitle: (Leave) -> String = { "" }
) : DaySource {

    /**
     * Asiakkaan muutokset ja ryhmien muutokset yhdessä. Asiakkaan oma muutos samaan kohtaan
     * samana päivänä menee ryhmän muutoksen edelle (esim. ryhmän uinti peruttu, Mikolla oma).
     */
    fun withGroupChanges(ownChanges: List<EventChange>): List<EventChange> =
        ownChanges + groupChanges.filter { g ->
            ownChanges.none { it.date == g.date && it.eventId == g.eventId && it.stepId == g.stepId }
        }

    /** Asukkaan toiminta-ajat päivänä (perutut mukana, ne näkyvät perutuiksi). */
    fun spans(date: LocalDate): List<ActivitySpan> = Units.spans(
        groups, memberId, date,
        absent = { absent(it, date) },
        cancelled = { id -> groupChanges.any { it.date == date && it.eventId == id && !it.isStep && it.type == ChangeType.CANCEL } }
    )

    /**
     * Asukkaan päivä (P10): omat kohdat, ryhmät, yksiköt (paitsi toiminnan aikana) ja toiminnat
     * (yksi kortti tai toiminnan runko, ks. [activityView]).
     */
    override fun eventsFor(date: LocalDate): List<WeekEvent> {
        val spans = spans(date)
        val fromGroups = groups.flatMap { g ->
            when (g.kind) {
                GroupKind.GROUP -> GroupSchedule.eventsFor(g, memberId, date, absent(g.id, date))
                GroupKind.UNIT -> GroupSchedule.eventsFor(g, memberId, date, absent(g.id, date))
                    .filter { e -> Units.at(spans, effectiveTime(e, date)) == null }
                GroupKind.ACTIVITY -> activityEvents(g, spans.filter { it.group.id == g.id }, date)
            }
        }
        // Lomalla: yksiköiden, toimintojen ja ryhmien kohdat eivät koske asukasta; lomakortti tilalle.
        val dayLeaves = leaves.filter { it.touches(date) }
        val shown = if (dayLeaves.isEmpty()) fromGroups
            else fromGroups.filterNot { Leaves.onLeave(dayLeaves, date, effectiveTime(it, date)) }
        val leaveCards = dayLeaves.mapNotNull { Leaves.cardFor(it, date, leaveTitle(it)) }
        if (shown.isEmpty() && leaveCards.isEmpty()) return own.eventsFor(date)
        return (own.eventsFor(date) + shown + leaveCards).sortedWith(compareBy({ WeekSchedule.minutesOf(it.time) }, { it.id }))
    }

    /**
     * Yksikön kohdat, jotka eivät näy asukkaalle, koska hän on silloin toiminnassa: ohjaajalle
     * himmeinä "Ei näy asukkaalle". Kohta → toiminta.
     */
    fun hiddenFor(date: LocalDate): List<Pair<WeekEvent, Group>> {
        val spans = spans(date)
        return groups.filter { it.kind == GroupKind.UNIT }.flatMap { g ->
            GroupSchedule.eventsFor(g, memberId, date, absent(g.id, date))
                .filterNot { e -> groupChange(e, date)?.type == ChangeType.CANCEL }
                .mapNotNull { e -> Units.at(spans, effectiveTime(e, date))?.let { e to it.group } }
        }.sortedBy { WeekSchedule.minutesOf(it.first.time) }
    }

    /** Yksikön kohdan päivän muutos (koko kohta, ei vaihe). */
    private fun groupChange(e: WeekEvent, date: LocalDate): EventChange? =
        groupChanges.firstOrNull { it.date == date && it.eventId == e.id && !it.isStep }

    /** Kohdan aika päivän siirron jälkeen: siirretty lounas ratkaistaan uudella ajallaan. */
    private fun effectiveTime(e: WeekEvent, date: LocalDate): String =
        groupChange(e, date)?.takeIf { it.type == ChangeType.MOVE && it.time.isNotBlank() }?.time ?: e.time

    private fun activityEvents(group: Group, spans: List<ActivitySpan>, date: LocalDate): List<WeekEvent> =
        spans.flatMap { span ->
            val day = date.dayOfWeek.value
            val transitions = if (span.cancelled) emptyList() else listOfNotNull(
                group.leaveCard.takeIf { it.isNotBlank() }?.let { name ->
                    val m = (WeekSchedule.minutesOf(span.start) - TRANSITION_LEAD_MIN).coerceAtLeast(0)
                    WeekEvent(GroupSchedule.eventId(group.id, "leave-$day"), day, WeekSchedule.WEEK_A,
                        WeekSchedule.formatTime(m / 60, m % 60), EventKind.CARD, cardName = name, cardPhoto = "builtin:leaving")
                },
                group.returnCard.takeIf { it.isNotBlank() }?.let { name ->
                    WeekEvent(GroupSchedule.eventId(group.id, "return-$day"), day, WeekSchedule.WEEK_A,
                        span.end, EventKind.CARD, cardName = name, cardPhoto = "builtin:free_time")
                }
            )
            // Toiminnan runko vain, jos asukkaalle on valittu se ja runko on tehty; peruttu näkyy aina korttina.
            val plan = if (activityView == ActivityView.PLAN && !span.cancelled) {
                WeekSchedule.eventsFor(group.plan, date).filter { span.covers(it.time) }
                    .mapNotNull { GroupSchedule.forMember(group, memberId, it) }
            } else emptyList()
            (if (plan.isNotEmpty()) plan else listOf(Units.sessionEvent(span, date))) + transitions
        }

    override fun event(id: String): WeekEvent? {
        own.event(id)?.let { return it }
        if (id.startsWith(Leave.PREFIX)) {
            val leave = leaves.firstOrNull { it.eventId == id } ?: return null
            return Leaves.cardFor(leave, leave.from.toLocalDate(), leaveTitle(leave))
        }
        val (groupId, eventId) = GroupSchedule.parse(id) ?: return null
        val group = groups.firstOrNull { it.id == groupId && memberId in it.members } ?: return null
        if (group.kind == GroupKind.ACTIVITY && (eventId.startsWith("leave-") || eventId.startsWith("return-"))) {
            // Siirtymäkortti: aika kokoontumisesta.
            val day = eventId.substringAfter('-').toIntOrNull() ?: return null
            val (start, end) = Units.sessionsFor(group, memberId, day).firstOrNull() ?: return null
            val leave = eventId.startsWith("leave-")
            val name = if (leave) group.leaveCard else group.returnCard
            if (name.isBlank()) return null
            val m = (WeekSchedule.minutesOf(start) - TRANSITION_LEAD_MIN).coerceAtLeast(0)
            return WeekEvent(id, day, WeekSchedule.WEEK_A, if (leave) WeekSchedule.formatTime(m / 60, m % 60) else end,
                EventKind.CARD, cardName = name, cardPhoto = if (leave) "builtin:leaving" else "builtin:free_time")
        }
        if (group.kind == GroupKind.ACTIVITY && Units.isSessionEvent(eventId)) {
            // Kokoontumiskortti: päivä tunnuksesta, ajat asukkaan omina.
            val day = eventId.removePrefix(Units.SESSION_PREFIX).toIntOrNull() ?: return null
            val (start, end) = Units.sessionsFor(group, memberId, day).firstOrNull() ?: return null
            return WeekEvent(id, day, WeekSchedule.WEEK_A, start, EventKind.CARD, cardName = group.name,
                cardPhoto = group.cardPhoto, ongoing = true, until = end)
        }
        val event = group.plan.event(eventId) ?: return null
        return GroupSchedule.forMember(group, memberId, event)
    }

    override fun eventIds(): Set<String> = own.eventIds() + leaves.map { it.eventId } + groups.flatMap { g ->
        GroupSchedule.eventIds(g, memberId) +
            (if (g.kind == GroupKind.ACTIVITY && memberId in g.members)
                g.sessions.flatMap { s ->
                    listOf(Units.sessionEventId(s.day), "leave-${s.day}", "return-${s.day}").map { GroupSchedule.eventId(g.id, it) }
                } else emptyList())
    }

    companion object {
        /** Lähtökortti näin monta minuuttia ennen toiminnan alkua. */
        const val TRANSITION_LEAD_MIN = 10
    }
}

/** Asiakkaan päivä tallennetuista tiedoista. */
internal object ClientWeek {
    fun load(
        context: Context,
        profileId: String = ProfileStore.currentId(context),
        own: WeekPlan = WeekStore(context, profileId).load()
    ): ClientDay {
        val store = GroupStore(context)
        val all = store.load()
        // Omat ryhmät ja yksiköiden yläyksiköt (yläyksikön runko kuuluu kaikille sen asukkaille).
        val groups = Units.groupsFor(all, profileId)
        val leaves = store.leaves().filter { it.memberId == profileId }
        val title: (Leave) -> String = { leaveTitle(context, it) }
        if (groups.isEmpty()) return ClientDay(own, emptyList(), profileId, leaves = leaves, leaveTitle = title)
        val absences = store.absences()
        val changes = store.allChanges()
        fun absentFrom(groupId: String, date: LocalDate): Boolean {
            if (profileId in absences[groupId]?.get(date).orEmpty()) return true
            // Yläyksikkö: poissaolo merkitään asukkaan omaan yksikköön.
            val home = if (all.any { it.id == groupId && profileId !in it.members }) Units.homeUnitOf(all, profileId, groupId) else null
            return home != null && profileId in absences[home.id]?.get(date).orEmpty()
        }
        return ClientDay(
            own, groups, profileId,
            absent = ::absentFrom,
            groupChanges = groups.flatMap { g -> changes[g.id].orEmpty().map { GroupSchedule.toMember(g.id, it) } },
            activityView = ClientViewPrefs.activityView(context, profileId),
            leaves = leaves,
            leaveTitle = title
        )
    }

    /** "Lomalla – takaisin su 22.11. klo 18.00". */
    fun leaveTitle(context: Context, leave: Leave): String {
        val back = leave.until
        val day = back.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT_STANDALONE, java.util.Locale.getDefault()).trimEnd('.').lowercase()
        val date = "$day ${back.dayOfMonth}.${back.monthValue}."
        val time = WeekSchedule.formatTime(back.hour, back.minute).replace(':', '.')
        val res = when (leave.reason) {
            LeaveReason.HOLIDAY -> fi.aikapolku.app.R.string.leave_title_holiday
            LeaveReason.HOME -> fi.aikapolku.app.R.string.leave_title_home
            LeaveReason.HOSPITAL -> fi.aikapolku.app.R.string.leave_title_hospital
            LeaveReason.OTHER -> fi.aikapolku.app.R.string.leave_title_other
        }
        return context.getString(res, date, time)
    }

    /** Asiakkaan päivän muutokset: omat ja ryhmien (oma edellä). */
    fun changes(context: Context, profileId: String = ProfileStore.currentId(context)): List<EventChange> =
        load(context, profileId).withGroupChanges(ChangeStore(context, profileId).load())
}

/** Ryhmät ja poissaolot tässä laitteessa (kaikille asiakkaille yhteiset). */
internal class GroupStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(): List<Group> = decode(prefs.getString(KEY_GROUPS, null))

    fun save(groups: List<Group>) {
        prefs.edit().putString(KEY_GROUPS, encode(groups)).commit()
    }

    fun upsert(group: Group) = save(load().filterNot { it.id == group.id } + group)

    fun delete(groupId: String) {
        save(load().filterNot { it.id == groupId })
        saveAbsences(absences() - groupId)
        saveChanges(allChanges() - groupId)
    }

    /** Ryhmän päivämuutokset (tunnuksena ryhmän rungon kohta). Ryhmä → muutokset. */
    fun allChanges(): Map<String, List<EventChange>> {
        val text = prefs.getString(KEY_CHANGES, null) ?: return emptyMap()
        val root = runCatching { JSONObject(text) }.getOrElse { return emptyMap() }
        return root.keys().asSequence().associateWith { ChangeStore.decode(root.optString(it)) }
    }

    fun changes(groupId: String): List<EventChange> = allChanges()[groupId].orEmpty()

    /** Ryhmän muutos tälle päivälle; samalle kohdalle ja päivälle vain yksi. Yli viikon vanhat pois. */
    fun upsertChange(groupId: String, change: EventChange, today: LocalDate = LocalDate.now()) {
        val all = allChanges().toMutableMap()
        all[groupId] = DayChanges.cleaned(DayChanges.upsert(all[groupId].orEmpty(), change), today, null)
        saveChanges(all)
    }

    fun deleteChange(groupId: String, changeId: String) {
        val all = allChanges().toMutableMap()
        all[groupId] = all[groupId].orEmpty().filterNot { it.id == changeId }
        saveChanges(all)
    }

    private fun saveChanges(value: Map<String, List<EventChange>>) {
        val root = JSONObject()
        value.filterValues { it.isNotEmpty() }.forEach { (groupId, list) -> root.put(groupId, ChangeStore.encode(list)) }
        prefs.edit().putString(KEY_CHANGES, root.toString()).commit()
    }

    /** Asukkaiden lomat ja pidemmät poissaolot (yli viikon sitten päättyneet siivotaan). */
    fun leaves(): List<Leave> = Leaves.decode(prefs.getString(KEY_LEAVES, null))

    fun upsertLeave(leave: Leave, now: java.time.LocalDateTime = java.time.LocalDateTime.now()) {
        val kept = leaves().filterNot { it.id == leave.id || it.until.isBefore(now.minusDays(KEEP_DAYS)) }
        prefs.edit().putString(KEY_LEAVES, Leaves.encode(kept + leave)).commit()
    }

    fun deleteLeave(id: String) {
        prefs.edit().putString(KEY_LEAVES, Leaves.encode(leaves().filterNot { it.id == id })).commit()
    }

    /** Ryhmä → päivä → poissa olevat asiakkaat. */
    fun absences(): Map<String, Map<LocalDate, Set<String>>> = decodeAbsences(prefs.getString(KEY_ABSENCES, null))

    fun isAbsent(groupId: String, memberId: String, date: LocalDate): Boolean =
        memberId in absences()[groupId]?.get(date).orEmpty()

    /** Merkitsee poissaolon tai paluun. Yli viikon vanhat poissaolot siivotaan samalla. */
    fun setAbsent(groupId: String, memberId: String, date: LocalDate, absent: Boolean, today: LocalDate = LocalDate.now()) {
        val all = absences().toMutableMap()
        val days = all[groupId].orEmpty().toMutableMap()
        val members = days[date].orEmpty().let { if (absent) it + memberId else it - memberId }
        if (members.isEmpty()) days.remove(date) else days[date] = members
        all[groupId] = days.filterKeys { !it.isBefore(today.minusDays(KEEP_DAYS)) }
        saveAbsences(all.filterValues { it.isNotEmpty() })
    }

    /** Asiakas poistettiin: pois ryhmistä, poikkeuksista ja poissaoloista. */
    fun removeMember(memberId: String) {
        if (leaves().any { it.memberId == memberId }) {
            prefs.edit().putString(KEY_LEAVES, Leaves.encode(leaves().filterNot { it.memberId == memberId })).commit()
        }
        val groups = load()
        if (groups.none { memberId in it.members || it.exceptions.any { e -> e.memberId == memberId } }) return
        save(groups.map { g ->
            g.copy(
                members = g.members - memberId,
                exceptions = g.exceptions.filterNot { it.memberId == memberId },
                memberTimes = g.memberTimes.filterNot { it.memberId == memberId }
            )
        })
        saveAbsences(absences().mapValues { (_, days) -> days.mapValues { it.value - memberId }.filterValues { it.isNotEmpty() } })
    }

    /**
     * Asukkaan puhelimessa (A5): paikan paketin muutokset ja poissaolot korvaavat ryhmien
     * [removeIds] aiemmat; asukkaan [memberId] lomat korvataan, muiden profiilien lomat säilyvät.
     */
    fun replaceShared(
        removeIds: Set<String>,
        changes: Map<String, List<EventChange>>,
        absences: Map<String, Map<LocalDate, Set<String>>>,
        leaves: List<Leave>,
        memberId: String
    ) {
        saveChanges(allChanges().filterKeys { it !in removeIds } + changes)
        saveAbsences(absences().filterKeys { it !in removeIds } + absences)
        prefs.edit().putString(KEY_LEAVES, Leaves.encode(leaves().filterNot { it.memberId == memberId } + leaves)).commit()
    }

    private fun saveAbsences(value: Map<String, Map<LocalDate, Set<String>>>) {
        prefs.edit().putString(KEY_ABSENCES, encodeAbsences(value)).commit()
    }

    companion object {
        private const val PREFS = "aikapolku_groups_v1"
        private const val KEY_GROUPS = "groups"
        private const val KEY_ABSENCES = "absences"
        private const val KEY_CHANGES = "changes"
        private const val KEY_LEAVES = "leaves"
        private const val KEEP_DAYS = 7L

        fun encode(groups: List<Group>): String {
            val array = JSONArray()
            groups.forEach { g ->
                val exceptions = JSONArray()
                g.exceptions.forEach { e ->
                    exceptions.put(
                        JSONObject()
                            .put("eventId", e.eventId)
                            .put("memberId", e.memberId)
                            .put("kind", e.kind.key)
                            .put("cardName", e.cardName)
                            .put("cardPhoto", e.cardPhoto)
                    )
                }
                array.put(
                    JSONObject()
                        .put("id", g.id)
                        .put("name", g.name)
                        .put("members", JSONArray(g.members))
                        .put("plan", JSONObject(WeekStore.encode(g.plan)))
                        .put("exceptions", exceptions)
                        .put("kind", g.kind.key)
                        .put("parentId", g.parentId)
                        .put("sessions", JSONArray().apply {
                            g.sessions.forEach { put(JSONObject().put("day", it.day).put("start", it.start).put("end", it.end)) }
                        })
                        .put("memberTimes", JSONArray().apply {
                            g.memberTimes.forEach {
                                put(JSONObject().put("memberId", it.memberId).put("days", JSONArray(it.days.sorted()))
                                    .put("start", it.start).put("end", it.end))
                            }
                        })
                        .put("cardPhoto", g.cardPhoto)
                        .put("leaveCard", g.leaveCard)
                        .put("returnCard", g.returnCard)
                )
            }
            return JSONObject().put("version", 1).put("groups", array).toString()
        }

        fun decode(text: String?): List<Group> {
            if (text.isNullOrBlank()) return emptyList()
            val root = runCatching { JSONObject(text) }.getOrElse { return emptyList() }
            val array = root.optJSONArray("groups") ?: return emptyList()
            return (0 until array.length()).mapNotNull { i ->
                val o = array.optJSONObject(i) ?: return@mapNotNull null
                val id = o.optString("id")
                if (id.isBlank()) return@mapNotNull null
                val members = o.optJSONArray("members")?.let { m -> (0 until m.length()).map { m.optString(it) }.filter { it.isNotBlank() } }.orEmpty()
                val exceptions = o.optJSONArray("exceptions")?.let { ex ->
                    (0 until ex.length()).mapNotNull { j ->
                        val e = ex.optJSONObject(j) ?: return@mapNotNull null
                        val eventId = e.optString("eventId")
                        val memberId = e.optString("memberId")
                        if (eventId.isBlank() || memberId.isBlank()) null
                        else MemberException(eventId, memberId, ExceptionKind.from(e.optString("kind")), e.optString("cardName"), e.optString("cardPhoto"))
                    }
                }.orEmpty()
                fun time(t: String) = WeekSchedule.minutesOf(t).let { WeekSchedule.formatTime(it / 60, it % 60) }
                val sessions = o.optJSONArray("sessions")?.let { a ->
                    (0 until a.length()).mapNotNull { j ->
                        val s = a.optJSONObject(j) ?: return@mapNotNull null
                        val day = s.optInt("day", 0)
                        if (day !in 1..7) null else ActivitySession(day, time(s.optString("start")), time(s.optString("end")))
                    }
                }.orEmpty()
                val memberTimes = o.optJSONArray("memberTimes")?.let { a ->
                    (0 until a.length()).mapNotNull { j ->
                        val t = a.optJSONObject(j) ?: return@mapNotNull null
                        val memberId = t.optString("memberId")
                        if (memberId.isBlank()) return@mapNotNull null
                        val days = t.optJSONArray("days")?.let { d -> (0 until d.length()).map { d.optInt(it) }.filter { it in 1..7 }.toSet() }.orEmpty()
                        MemberTime(memberId, days, time(t.optString("start")), time(t.optString("end")))
                    }
                }.orEmpty()
                Group(
                    id = id,
                    name = o.optString("name"),
                    members = members.distinct(),
                    plan = WeekStore.decode(o.optJSONObject("plan")?.toString()),
                    exceptions = exceptions,
                    kind = GroupKind.from(o.optString("kind")),
                    parentId = o.optString("parentId"),
                    sessions = sessions,
                    memberTimes = memberTimes,
                    cardPhoto = o.optString("cardPhoto"),
                    leaveCard = o.optString("leaveCard"),
                    returnCard = o.optString("returnCard")
                )
            }
        }

        fun encodeAbsences(value: Map<String, Map<LocalDate, Set<String>>>): String {
            val root = JSONObject()
            value.forEach { (groupId, days) ->
                val o = JSONObject()
                days.forEach { (date, members) -> o.put(date.toString(), JSONArray(members.sorted())) }
                root.put(groupId, o)
            }
            return root.toString()
        }

        fun decodeAbsences(text: String?): Map<String, Map<LocalDate, Set<String>>> {
            if (text.isNullOrBlank()) return emptyMap()
            val root = runCatching { JSONObject(text) }.getOrElse { return emptyMap() }
            return root.keys().asSequence().associateWith { groupId ->
                val days = root.optJSONObject(groupId) ?: JSONObject()
                days.keys().asSequence().mapNotNull { key ->
                    val date = runCatching { LocalDate.parse(key) }.getOrNull() ?: return@mapNotNull null
                    val m = days.optJSONArray(key) ?: return@mapNotNull null
                    date to (0 until m.length()).map { m.optString(it) }.filter { it.isNotBlank() }.toSet()
                }.toMap()
            }
        }
    }
}
