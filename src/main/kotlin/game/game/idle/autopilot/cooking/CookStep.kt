package game.idle.autopilot.cooking

import game.idle.flow.FlowContext
import game.idle.flow.ProcessInput
import game.idle.flow.ProcessKinds
import game.idle.flow.ResolvedStep
import game.idle.flow.StepActivity
import game.idle.flow.StepAmount
import game.idle.flow.StepField
import game.idle.flow.StepIcon
import game.idle.flow.StepInput
import game.idle.flow.StepPick
import game.idle.flow.StepRadius
import game.idle.flow.StepSettings
import game.idle.flow.StepType
import game.idle.flow.WorkSpot
import game.idle.flow.option.GameNames
import game.idle.flow.option.InputSource
import game.idle.location.Area
import game.idle.location.Tile
import game.skill.cooking.cookFood.Food
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.Skill

/**
 * Cook: cooks a count of the kinds of raw food picked or, without one, all of it, on a fire or range within a radius of
 * the work spot. The food comes from earlier steps or the bank (S07a), named with [names].
 */
class CookStepType(private val names: GameNames) : StepType {

    private val food = ProcessKinds(RAW, kind = "cook", what = "raw food", usable = Food.RAW_TO_FOOD.keys, names)

    override val kind = "cook"

    override val label = "cook"

    override val description = "Cooks the raw food picked on a range or fire, from earlier steps or the bank."

    override fun icon(settings: StepSettings): StepIcon = StepIcon.Skill(Skill.COOKING)

    override fun skill(settings: StepSettings): Int = Skill.COOKING

    override fun pick(settings: StepSettings, names: GameNames): StepPick = food.pick(settings)

    override fun input(settings: StepSettings, before: FlowContext): InputSource = ProcessInput.read(settings, before, food.usable)

    override fun newSettings(before: FlowContext): StepSettings {
        val settings = ProcessInput.initial(StepSettings(kind), before, food.usable)
        return food.initial(settings, before, input(settings, before))
    }

    override fun details(settings: StepSettings, context: FlowContext): List<String> =
        listOf(
            StepInput.detail("Raw food", input(settings, context), context, food.ids(settings).toSet()),
            StepAmount.detail(settings, unbounded = "all of them"),
        )

    override fun fields(names: GameNames): List<StepField> =
        listOf(
            ProcessInput.field(food.usable),
            StepAmount.field("Amount", unbounded = "all of them", button = "All"),
            food.field("Raw food", RawFoodOptions(names), "What would you like to cook?"),
            StepRadius.field(),
        )

    override fun summary(settings: StepSettings): String =
        "cook ${StepAmount.prefix(settings)}${food.ids(settings).joinToString(", ") { names.item(it).lowercase() }}".trimEnd() +
            StepRadius.suffix(settings)

    override fun resolve(settings: StepSettings, context: FlowContext): ResolvedStep =
        CookStep(food.resolve(settings, context, input(settings, context)), StepRadius.read(settings), context.workSpot, StepAmount.read(settings))

    companion object {
        const val RAW = "raw"
    }
}

/** A cook step resolved: the raw food it cooks, where, and how much ([amount], null for all). */
data class CookStep(val rawIds: Set<Int>, val radius: Int, val workSpot: WorkSpot, val amount: Int? = null) : ResolvedStep {

    override fun activity(player: Player, runTile: Tile): StepActivity =
        CookingActivity(LunaCooker(player, rawIds, Area(workSpot.tile(runTile), radius)), amount)
}
