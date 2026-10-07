package game.idle.autopilot.fishing

import game.idle.flow.FlowContext
import game.idle.flow.FlowError
import game.idle.flow.ResolvedStep
import game.idle.flow.StepActivity
import game.idle.flow.StepAmount
import game.idle.flow.StepField
import game.idle.flow.StepRadius
import game.idle.flow.StepSettings
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

/** Fish: one fishing method at spots within a radius of the work spot, a count of catches or, without one, until full. */
object FishStepType : StepType {

    const val FISH = "fish"

    override val kind = "fish"

    override val label = "fish"

    override val fields = listOf(
        StepField.Choice(FISH, "fish") { FishingMethod.entries.map { it.word } },
        StepAmount.field(unbounded = "full"),
        StepRadius.field(),
    )

    override fun summary(settings: StepSettings): String =
        "fish ${StepAmount.prefix(settings)}${settings[FISH] ?: "?"}${StepRadius.suffix(settings)}"

    override fun resolve(settings: StepSettings, context: FlowContext): ResolvedStep {
        val name = settings[FISH] ?: throw FlowError("fish needs a fish")
        val method = FishingMethod.entries.firstOrNull { it.word == name.lowercase() }
            ?: throw FlowError("'$name' is not a fish you can catch yet. Fish: ${FishingMethod.entries.joinToString(", ") { it.word }}")
        return FishStep(method, StepRadius.read(settings), context.workSpot, StepAmount.read(settings))
    }
}

/** A fish step resolved. Later steps know it gathers its catches. */
data class FishStep(val method: FishingMethod, val radius: Int, val workSpot: WorkSpot, val amount: Int? = null) : ResolvedStep {

    override fun after(context: FlowContext): FlowContext = context.copy(gathered = context.gathered + method.catchIds)

    override fun activity(player: Player, runTile: Tile): StepActivity =
        FishingActivity(LunaFisher(player, method, Area(workSpot.tile(runTile), radius)), amount)
}
