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
    val exceptions: List<MemberException> = emptyList()
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
    private val absent: (groupId: String, date: LocalDate) -> Boolean = { _, _ -> false }
) : DaySource {

    /**
     * Asiakkaan muutokset ja ryhmien muutokset yhdessä. Asiakkaan oma muutos samaan kohtaan
     * samana päivänä menee ryhmän muutoksen edelle (esim. ryhmän uinti peruttu, Mikolla oma).
     */
    fun withGroupChanges(ownChanges: List<EventChange>): List<EventChange> =
        ownChanges + groupChanges.filter { g ->
            ownChanges.none { it.date == g.date && it.eventId == g.eventId && it.stepId == g.stepId }
        }

    override fun eventsFor(date: LocalDate): List<WeekEvent> {
        val fromGroups = groups.flatMap { GroupSchedule.eventsFor(it, memberId, date, absent(it.id, date)) }
        if (fromGroups.isEmpty()) return own.eventsFor(date)
        return (own.eventsFor(date) + fromGroups).sortedWith(compareBy({ WeekSchedule.minutesOf(it.time) }, { it.id }))
    }

    override fun event(id: String): WeekEvent? {
        own.event(id)?.let { return it }
        val (groupId, eventId) = GroupSchedule.parse(id) ?: return null
        val group = groups.firstOrNull { it.id == groupId && memberId in it.members } ?: return null
        val event = group.plan.event(eventId) ?: return null
        return GroupSchedule.forMember(group, memberId, event)
    }

    override fun eventIds(): Set<String> = own.eventIds() + groups.flatMap { GroupSchedule.eventIds(it, memberId) }
}

/** Asiakkaan päivä tallennetuista tiedoista. */
internal object ClientWeek {
    fun load(
        context: Context,
        profileId: String = ProfileStore.currentId(context),
        own: WeekPlan = WeekStore(context, profileId).load()
    ): ClientDay {
        val store = GroupStore(context)
        val groups = store.load().filter { profileId in it.members }
        if (groups.isEmpty()) return ClientDay(own, emptyList(), profileId)
        val absences = store.absences()
        val changes = store.allChanges()
        return ClientDay(
            own, groups, profileId,
            absent = { groupId, date -> profileId in absences[groupId]?.get(date).orEmpty() },
            groupChanges = groups.flatMap { g -> changes[g.id].orEmpty().map { GroupSchedule.toMember(g.id, it) } }
        )
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
        val groups = load()
        if (groups.none { memberId in it.members || it.exceptions.any { e -> e.memberId == memberId } }) return
        save(groups.map { g ->
            g.copy(members = g.members - memberId, exceptions = g.exceptions.filterNot { it.memberId == memberId })
        })
        saveAbsences(absences().mapValues { (_, days) -> days.mapValues { it.value - memberId }.filterValues { it.isNotEmpty() } })
    }

    private fun saveAbsences(value: Map<String, Map<LocalDate, Set<String>>>) {
        prefs.edit().putString(KEY_ABSENCES, encodeAbsences(value)).commit()
    }

    companion object {
        private const val PREFS = "aikapolku_groups_v1"
        private const val KEY_GROUPS = "groups"
        private const val KEY_ABSENCES = "absences"
        private const val KEY_CHANGES = "changes"
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
                Group(
                    id = id,
                    name = o.optString("name"),
                    members = members.distinct(),
                    plan = WeekStore.decode(o.optJSONObject("plan")?.toString()),
                    exceptions = exceptions
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
