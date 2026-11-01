package fi.aikapolku.app

import java.util.UUID

/** Templates are editable drafts, never automatically saved or started. */
internal object P020RoutineTemplates {
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

    fun create(template: Template): AP16Program {
        val steps = template.steps.map { selected ->
            val card = AikaCardArt.templates.first { it.id == selected.cardId }
            AP16Step(
                id = UUID.randomUUID().toString(),
                name = card.name,
                photo = "builtin:${card.id}",
                description = "",
                mode = if (selected.durationMinutes == null) "manual" else "duration",
                minutes = selected.durationMinutes ?: 5,
                subSteps = P021SubstepDefaults.forCard(card.name)
            )
        }
        return AP16Program(UUID.randomUUID().toString(), template.name, steps)
    }
}
