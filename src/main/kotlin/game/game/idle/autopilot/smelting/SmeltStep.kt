package game.idle.autopilot.smelting

import game.idle.flow.FlowContext
import game.idle.flow.FlowError
import game.idle.flow.ProcessInput
import game.idle.flow.ResolvedStep
import game.idle.flow.StepActivity
import game.idle.flow.StepAmount
import game.idle.flow.StepField
import game.idle.flow.StepIcon
import game.idle.flow.StepInput
import game.idle.flow.StepNeeds
import game.idle.flow.StepRadius
import game.idle.flow.StepSettings
import game.idle.flow.StepType
import game.idle.flow.Uses
import game.idle.flow.WorkSpot
import game.idle.flow.option.GameNames
import game.idle.flow.option.InputSource
import game.idle.flow.option.StepTarget
import game.idle.location.Area
import game.idle.location.Tile
import game.skill.smithing.BarType
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.Skill

/**
 * Smelt: smelts a count of bars or, without one, every bar the ores carried make, at a furnace within a radius of the
 * work spot. The ores come from earlier steps or the bank (S07a), named with [names].
 */
class SmeltStepType(private val names: GameNames) : StepType {

    override val kind = "smelt"

    override val label = "smelt"

    override val description = "Smelts ores into bars at a furnace, from earlier steps or the bank."

    override fun icon(settings: StepSettings): StepIcon = StepIcon.Skill(Skill.SMITHING)

    override fun skill(settings: StepSettings): Int = Skill.SMITHING

    override fun target(names: GameNames): StepTarget = StepTarget(BAR, BarOptions(names))

    override fun input(settings: StepSettings, before: FlowContext): InputSource = ProcessInput.read(settings, before, ORE_IDS)

    override fun newSettings(before: FlowContext): StepSettings = ProcessInput.initial(StepSettings(kind), before, ORE_IDS)

    override fun details(settings: StepSettings, context: FlowContext): List<String> =
        listOf(
            StepInput.detail("Ores", input(settings, context), context, bar(settings)?.let(::ores).orEmpty().map { it.id }.toSet()),
            StepAmount.detail(settings, unbounded = "all of them"),
        )

    override fun fields(names: GameNames): List<StepField> =
        listOf(
            ProcessInput.field(ORE_IDS),
            StepField.Search("Bar", target(names), "Which bar would you like to smelt?"),
            StepField.Note("Uses") { settings, _ -> uses(settings) },
            StepAmount.field("Amount", unbounded = "all of them", button = "All"),
            StepRadius.field(),
        )

    override fun summary(settings: StepSettings): String =
        "smelt ${StepAmount.prefix(settings)}${settings[BAR] ?: "?"}${StepRadius.suffix(settings)}"

    override fun resolve(settings: StepSettings, context: FlowContext): ResolvedStep {
        val name = settings[BAR] ?: throw FlowError("smelt needs a bar")
        val bar = bar(settings) ?: throw FlowError("'$name' is not a bar to smelt")
        ProcessInput.requireGathered(input(settings, context), context, ores(bar).map { it.id }, names)
        return SmeltStep(bar, StepRadius.read(settings), context.workSpot, StepAmount.read(settings))
    }

    private fun bar(settings: StepSettings): BarType? = BarType.entries.firstOrNull { it.name.equals(settings[BAR], ignoreCase = true) }

    private fun uses(settings: StepSettings): String =
        bar(settings)?.let { bar -> Uses.text(ores(bar).associate { it.id to it.amount }, names) } ?: Uses.NOTHING_PICKED

    private fun ores(bar: BarType): List<Item> = listOfNotNull(bar.oreRequired.first, bar.oreRequired.second)

    companion object {
        const val BAR = "bar"

        private val ORE_IDS: Set<Int> = BarType.entries.flatMap { bar -> listOfNotNull(bar.oreRequired.first, bar.oreRequired.second) }
            .map { it.id }.toSet()
    }
}

/** A smelt step resolved: the bar, where, and how many ([amount], null for all). Later steps know it makes that bar. */
data class SmeltStep(val bar: BarType, val radius: Int, val workSpot: WorkSpot, val amount: Int? = null) : ResolvedStep {

    override fun after(context: FlowContext): FlowContext = context.copy(gathered = context.gathered + bar.id)

    override fun needs(): List<StepNeeds> =
        listOf(StepNeeds(inputs = listOfNotNull(bar.oreRequired.first, bar.oreRequired.second).map { it.id }))

    override fun activity(player: Player, runTile: Tile): StepActivity =
        SmeltingActivity(LunaSmelter(player, bar, Area(workSpot.tile(runTile), radius)), bar, amount)
}
