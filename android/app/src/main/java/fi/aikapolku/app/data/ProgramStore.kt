package fi.aikapolku.app.data

import android.content.Context
import org.json.JSONArray

internal class ProgramStore(context: Context, profileId: String = ProfileStore.currentId(context)) {
    // Profiilin (oletuksena nykyisen) päiväohjelmat.
    private val prefs = ProfileScope.prefs(context, ProfileScope.PROGRAMS, profileId)

    fun load(): List<DayProgram> {
        val array = runCatching {
            JSONArray(prefs.getString("programs", "[]") ?: "[]")
        }.getOrElse { JSONArray() }

        return (0 until array.length()).mapNotNull { i ->
            array.optJSONObject(i)?.let(::programFromJson)
        }
    }

    private fun write(programs: List<DayProgram>) {
        val array = JSONArray()
        programs.forEach { array.put(programToJson(it)) }

        prefs.edit()
            .putString("programs", array.toString())
            .apply()
    }

    fun save(program: DayProgram) {
        val programs = load().toMutableList()
        val index = programs.indexOfFirst { it.id == program.id }

        if (index >= 0) {
            programs[index] = program
        } else {
            programs.add(program)
        }

        write(programs)
    }

    fun delete(id: String) {
        write(load().filterNot { it.id == id })
    }
}
