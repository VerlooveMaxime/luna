package game.idle.flow.option

import game.idle.flow.StepIcon
import game.idle.flow.StepSettings
import game.idle.flow.StepTypes

/**
 * The steps of a flow a reflex can jump to (S07c), any of them, by id: "Step 3: Bank" over what the step picked as its
 * slot shows it ("Al Kharid bank"), with its kind's icon. [steps] are the flow's, worded with [types] and [names].
 */
class JumpTargetOptions(private val steps: List<StepSettings>, private val types: StepTypes, private val names: GameNames) : OptionSource {

    override fun options(context: OptionContext): List<StepOption> =
        steps.withIndex().mapNotNull { (index, step) ->
            types.find(step.kind)?.let { type ->
                StepOption(
                    value = step.id.toString(),
                    label = "Step ${index + 1}: ${type.label.take(1).uppercase()}${type.label.drop(1)}",
                    icon = icon(type.icon(step)),
                    note = type.pick(step, names)?.label.orEmpty(),
                    group = index,
                )
            }
        }

    private fun icon(icon: StepIcon): OptionIcon =
        when (icon) {
            is StepIcon.Skill -> OptionIcon.Skill(icon.skill)
            is StepIcon.Media -> OptionIcon.Media(icon.name, icon.index)
            is StepIcon.Item -> OptionIcon.Item(icon.id)
        }
}
