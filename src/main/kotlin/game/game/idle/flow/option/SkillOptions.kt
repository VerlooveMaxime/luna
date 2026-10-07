package game.idle.flow.option

import io.luna.game.model.mob.Skill

/** Every skill, with the player's level, for an "until" goal on a skill level. */
object SkillOptions : OptionSource {

    override fun options(context: OptionContext): List<StepOption> =
        Skill.NAMES.mapIndexed { id, name ->
            StepOption(
                value = name.lowercase(),
                label = name,
                icon = OptionIcon.Skill(id),
                note = "level ${context.facts.level(id)}",
            )
        }
}
