package game.idle.autopilot.cooking

import game.idle.flow.Counts
import game.idle.flow.FlowContext
import game.idle.flow.LevelNeed
import game.idle.flow.OwnSpot
import game.idle.flow.ProcessInput
import game.idle.flow.ProcessKinds
import game.idle.flow.ResolvedStep
import game.idle.flow.StepAmount
import game.idle.flow.StepField
import game.idle.flow.StepIcon
import game.idle.flow.StepInput
import game.idle.flow.StepNeeds
import game.idle.flow.StepPick
import game.idle.flow.StepRadius
import game.idle.flow.StepSettings
import game.idle.flow.StepSpot
import game.idle.flow.StepType
import game.idle.flow.StepWork
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
class CookStepType(names: GameNames) : StepType {

    private val food = ProcessKinds(RAW, label = "Raw food", usable = Food.RAW_TO_FOOD.keys, names)

    override val kind = "cook"

    override val label = "cook"

    override val description = "Cooks the raw food picked on a range or fire, from earlier steps or the bank."

    override val goal = true

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
            StepInput.detail(input(settings, context), context, food.ids(settings).toSet()),
            StepAmount.detail(settings, unbounded = "all of them"),
        )

    override fun fields(names: GameNames): List<StepField> =
        listOf(
            ProcessInput.field(food.usable),
            StepAmount.field(StepAmount.LABEL, unbounded = "all of them", button = "All"),
            food.field(RawFoodOptions(names), "What would you like to cook?"),
            StepSpot.field(),
            StepRadius.field(),
        )

    override fun resolve(settings: StepSettings, context: FlowContext): ResolvedStep =
        CookStep(food.resolve(settings, context, input(settings, context)), StepRadius.read(settings), StepSpot.of(settings, context), StepAmount.read(settings))

    companion object {
        const val RAW = "raw"
    }
}

/**
 * A cook step resolved: the raw food it cooks, where, and how much ([amount], null for all). Later steps know it makes
 * the cooked food and the burnt (S07b: the bank's Gathered banks them, a drop step drops them).
 */
data class CookStep(
    val rawIds: Set<Int>,
    val radius: Int,
    val workSpot: WorkSpot,
    override val amount: Int? = null,
) : ResolvedStep, OwnSpot, Counts {

    override val ownArea: Area? get() = workSpot.ownArea(radius)

    override fun after(context: FlowContext): FlowContext {
        val made = rawIds.flatMap { Food.RAW_TO_FOOD.getValue(it).let { food -> listOf(food.cooked, food.burnt) } }
        return workSpot.after(context.copy(gathered = context.gathered + made))
    }

    /** For each kind of raw food, the Cooking level it takes, the easiest first: a block names that one. */
    override fun needs(): List<StepNeeds> =
        rawIds.map { Food.RAW_TO_FOOD.getValue(it) }.sortedBy { it.lvl }.map { food ->
            StepNeeds(inputs = listOf(food.raw), level = LevelNeed(Skill.COOKING, food.lvl))
        }

    override fun work(player: Player, runTile: Tile): StepWork =
        CookingWork(LunaCooker(player, rawIds, Area(workSpot.tile(runTile), radius)))
}
