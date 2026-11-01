package fi.aikapolku.app.data

import fi.aikapolku.app.cards.CardArt
import java.util.UUID

/** Templates are editable drafts, never automatically saved or started. */
internal object RoutineTemplates {
    internal data class TemplateStep(
        val cardId: String,
        val durationMinutes: Int? = null
    )
    internal data class Template(
        val name: String,
        val description: String,
        val steps: List<TemplateStep>
    )

    val all: List<Template> = listOf(
        Template("Aamutoimet", "Kotona ennen lähtöä", listOf(
            TemplateStep("breakfast", 15),
            TemplateStep("brush_teeth", 3),
            TemplateStep("dress", 10),
            TemplateStep("leaving")
        )),
        Template("Iltatoimet", "Rauhallinen iltarutiini", listOf(
            TemplateStep("lunch", 20),
            TemplateStep("brush_teeth", 3),
            TemplateStep("reading", 15),
            TemplateStep("sleep")
        )),
        Template("Koulupäivä", "Malli koulupäivän vaiheista", listOf(
            TemplateStep("morning_meeting", 10),
            TemplateStep("schoolwork", 30),
            TemplateStep("break", 10),
            TemplateStep("lunch", 30),
            TemplateStep("outdoors", 20),
            TemplateStep("leaving")
        )),
        Template("Päivätoiminta", "Yhteinen päivän ohjelma", listOf(
            TemplateStep("morning_meeting", 15),
            TemplateStep("crafts", 25),
            TemplateStep("break", 10),
            TemplateStep("lunch", 30),
            TemplateStep("outdoors", 20)
        ))
    )

    fun create(template: Template): DayProgram {
        val steps = template.steps.map { selected ->
            val card = CardArt.templates.first { it.id == selected.cardId }
            ProgramStep(
                id = UUID.randomUUID().toString(),
                name = card.name,
                photo = "builtin:${card.id}",
                description = "",
                mode = if (selected.durationMinutes == null) "manual" else "duration",
                minutes = selected.durationMinutes ?: 5,
                subSteps = SubstepDefaults.forCard(card.name)
            )
        }
        return DayProgram(UUID.randomUUID().toString(), template.name, steps)
    }
}
