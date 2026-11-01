package fi.aikapolku.app.data

import fi.aikapolku.app.R
import fi.aikapolku.app.cards.CardArt
import fi.aikapolku.app.cards.Texts
import java.util.UUID

/** Templates are editable drafts, never automatically saved or started. */
internal object RoutineTemplates {
    internal data class TemplateStep(
        val cardId: String,
        val durationMinutes: Int? = null
    )
    internal data class Template(
        val nameRes: Int,
        val descriptionRes: Int,
        val steps: List<TemplateStep>
    )

    val all: List<Template> = listOf(
        Template(R.string.template_morning, R.string.template_morning_desc, listOf(
            TemplateStep("breakfast", 15),
            TemplateStep("brush_teeth", 3),
            TemplateStep("dress", 10),
            TemplateStep("leaving")
        )),
        Template(R.string.template_evening, R.string.template_evening_desc, listOf(
            TemplateStep("lunch", 20),
            TemplateStep("brush_teeth", 3),
            TemplateStep("reading", 15),
            TemplateStep("sleep")
        )),
        Template(R.string.template_school_day, R.string.template_school_day_desc, listOf(
            TemplateStep("morning_meeting", 10),
            TemplateStep("schoolwork", 30),
            TemplateStep("break", 10),
            TemplateStep("lunch", 30),
            TemplateStep("outdoors", 20),
            TemplateStep("leaving")
        )),
        Template(R.string.template_day_activity, R.string.template_day_activity_desc, listOf(
            TemplateStep("morning_meeting", 15),
            TemplateStep("crafts", 25),
            TemplateStep("break", 10),
            TemplateStep("lunch", 30),
            TemplateStep("outdoors", 20)
        ))
    )

    /** Luo muokattavan luonnoksen sovelluksen nykyisellä kielellä ([texts] = `{ context.getString(it) }`). */
    fun create(template: Template, texts: Texts): DayProgram {
        val steps = template.steps.map { selected ->
            val card = CardArt.templates.first { it.id == selected.cardId }
            ProgramStep(
                id = UUID.randomUUID().toString(),
                name = texts(card.nameRes),
                photo = "builtin:${card.id}",
                description = "",
                mode = if (selected.durationMinutes == null) StepMode.MANUAL else StepMode.DURATION,
                minutes = selected.durationMinutes ?: 5,
                subSteps = SubstepDefaults.forCardId(card.id, texts)
            )
        }
        return DayProgram(UUID.randomUUID().toString(), texts(template.nameRes), steps)
    }
}
