package game.idle.autopilot.woodcutting

import game.idle.flow.option.GameNames
import game.idle.flow.option.GatherOptions
import game.idle.flow.option.OptionContext
import game.idle.flow.option.OptionSource
import game.idle.flow.option.StepOption
import game.skill.woodcutting.cutTree.TreeStump
import io.luna.game.model.mob.Skill

/** The trees a chop step can cut, named as the cache names the standing tree, shown with their logs. */
class TreeOptions(private val names: GameNames) : OptionSource {

    override fun options(context: OptionContext): List<StepOption> =
        ChopStepType.CUTTABLE.map { tree ->
            GatherOptions.option(
                context,
                value = tree.name.lowercase(),
                label = names.obj(TreeStump.ALIVE_TREE_MAP.get(tree).min()),
                item = tree.logId,
                skill = Skill.WOODCUTTING,
                level = tree.level,
            )
        }
}
