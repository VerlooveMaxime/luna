package game.idle.autopilot.fishing

import game.idle.flow.capitalised
import game.idle.flow.option.GameNames
import game.idle.flow.option.GatherOptions
import game.idle.flow.option.OptionContext
import game.idle.flow.option.OptionSource
import game.idle.flow.option.StepOption
import io.luna.game.model.mob.Skill

/**
 * The fish a fish step can be for, one row each named by the fish and the tool that catches it ("Anchovies (small
 * fishing net)"), greyed below the fish's level (Maxime, 2026-10-15: the step counts its fish, not its method). Only
 * fish are rows: a big net's caskets and seaweed are junk.
 */
class FishOptions(private val names: GameNames) : OptionSource {

    override fun options(context: OptionContext): List<StepOption> = FishingCatch.ALL.map { option(context, it) }

    private fun option(context: OptionContext, catch: FishingCatch): StepOption =
        GatherOptions.option(
            context,
            value = catch.word,
            label = "${fishName(catch.fish.id)} (${names.item(catch.method.tool.id).lowercase()})",
            item = catch.fish.id,
            skill = Skill.FISHING,
            level = catch.fish.level,
        )

    private fun fishName(id: Int): String = capitalised(names.item(id).removePrefix(FishingMethod.RAW))
}
