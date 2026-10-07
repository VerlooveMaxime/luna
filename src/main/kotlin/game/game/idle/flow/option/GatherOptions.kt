package game.idle.flow.option

import io.luna.game.model.mob.Skill

/** The rows of a gathering step (chop, mine): what it gathers, with the level of its skill under the label. */
object GatherOptions {

    /** The row for gathering [item] with [level] in [skill], kept under [value]. */
    fun option(context: OptionContext, value: String, label: String, item: Int, skill: Int, level: Int): StepOption =
        StepOption(
            value = value,
            label = label,
            icon = OptionIcon.Item(item),
            note = "${Skill.getName(skill)} $level",
            blocked = context.facts.lacks(skill, level),
            level = level,
        )
}
