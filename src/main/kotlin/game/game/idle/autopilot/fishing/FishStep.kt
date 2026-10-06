package game.idle.autopilot.fishing

import game.idle.flow.FlowContext
import game.idle.flow.FlowError
import game.idle.flow.ResolvedStep
import game.idle.flow.StepActivity
import game.idle.flow.StepAmount
import game.idle.flow.StepField
import game.idle.flow.StepRadius
import game.idle.flow.StepType
import game.idle.flow.WorkSpot
import game.idle.location.Area
import game.idle.location.Tile
import game.skill.fishing.catchFish.Tool
import io.luna.game.model.mob.Player

/**
 * How a fish step fishes: the tool, the spots it works at (their first option) and what it catches. Shrimp only for
 * now (small net, level 1); each of Luna's other tools is one more row once checked live, with a level check for the
 * tools above level 1. Luna's [Tool] is read lazily: its fish name
 * themselves from the item definitions, which only a booted world has, and the builder lists methods before that.
 */
enum class FishingMethod(toolOf: () -> Tool, val spotIds: Set<Int>) {
    // 952 is Tutorial Island's spot.
    SHRIMP({ Tool.SMALL_NET }, setOf(316, 319, 320, 327, 330, 952)),
    ;

    val tool: Tool by lazy(toolOf)

    val catchIds: Set<Int> by lazy { tool.fish.map { it.id }.toSet() }

    val word: String = name.lowercase()
}

/** `fish [<n>] <fish> [within <r>]`: n catches or, without n, until the inventory is full. */
object FishStepType : StepType {

    /** The amount field's word for "until the inventory is full". */
    const val FULL = "full"

    override val keyword = "fish"

    override val label = "fish"

    override val usage = "fish [<n>] <fish> [within <r>]"

    override val fields = listOf(
        StepField.Choice("fish") { FishingMethod.entries.map { it.word } },
        StepField.Choice("amount") { listOf(FULL) + StepAmount.COUNTS },
        StepRadius.field(),
    )

    override fun parse(words: List<String>): List<String> {
        val (count, rest) = StepAmount.split(words)
        val fish = rest.firstOrNull() ?: throw FlowError("fish needs a fish: $usage")
        val radius = StepRadius.parse(rest.drop(1), after = "fish", usage)
        return listOf(fish, StepAmount.value(count, FULL), radius.toString())
    }

    override fun line(values: List<String>): String =
        "fish ${StepAmount.prefix(values[AMOUNT])}${values[FISH]}${StepRadius.suffix(values[RADIUS])}"

    override fun resolve(values: List<String>, context: FlowContext): ResolvedStep {
        val method = FishingMethod.entries.firstOrNull { it.word == values[FISH].lowercase() }
            ?: throw FlowError("'${values[FISH]}' is not a fish you can catch yet. Fish: ${FishingMethod.entries.joinToString(", ") { it.word }}")
        return FishStep(method, StepRadius.check(values[RADIUS]), context.workSpot, StepAmount.count(values[AMOUNT]))
    }

    private const val FISH = 0
    private const val AMOUNT = 1
    private const val RADIUS = 2
}

/** A fish step resolved. Later steps know it gathers its catches. */
data class FishStep(val method: FishingMethod, val radius: Int, val workSpot: WorkSpot, val amount: Int? = null) : ResolvedStep {

    override fun after(context: FlowContext): FlowContext = context.copy(gathered = context.gathered + method.catchIds)

    override fun activity(player: Player, runTile: Tile): StepActivity =
        FishingActivity(LunaFisher(player, method, Area(workSpot.tile(runTile), radius)), amount)
}
