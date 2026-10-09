package game.idle.autopilot.fishing

import game.idle.flow.FlowContext
import game.idle.flow.FlowError
import game.idle.flow.ResolvedStep
import game.idle.flow.StepActivity
import game.idle.flow.StepAmount
import game.idle.flow.StepField
import game.idle.flow.StepIcon
import game.idle.flow.StepRadius
import game.idle.flow.StepSettings
import game.idle.flow.StepType
import game.idle.flow.WorkSpot
import game.idle.flow.option.GameNames
import game.idle.flow.option.StepTarget
import game.idle.location.Area
import game.idle.location.Tile
import game.skill.fishing.catchFish.FishingSpot
import game.skill.fishing.catchFish.Tool
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.Skill

/**
 * How a fish step fishes: one of Luna's tools at the spots Luna's [FishingSpot] table fishes with it, each spot clicked
 * on the option that uses the tool. [ALL] reads Luna's [Tool] lazily: its fish name themselves from the item
 * definitions, which only a booted world has.
 */
data class FishingMethod(val tool: Tool) {

    /** The spots fished with this method on their second option; the others on their first. */
    val secondClickSpots: Set<Int> = FishingSpot.secondClickIds(tool)

    val spotIds: Set<Int> = FishingSpot.firstClickIds(tool) + secondClickSpots

    val catchIds: Set<Int> = tool.fish.map { it.id }.toSet()

    /** What the fish setting keeps: the method's first fish, `shrimp` for the small net as the tutorial says. */
    val word: String = tool.fish.first().name.lowercase()

    companion object {

        /** Every tool Luna's table has spots for, lowest level first. */
        val ALL: List<FishingMethod> by lazy { Tool.entries.map(::FishingMethod).filter { it.spotIds.isNotEmpty() } }

        fun named(word: String): FishingMethod? = ALL.firstOrNull { it.word == word.lowercase() }
    }
}

/** Fish: one fishing method at spots within a radius of the work spot, a count of catches or, without one, until full. */
object FishStepType : StepType {

    const val FISH = "fish"

    override val kind = "fish"

    override val label = "fish"

    override val description = "Fishes one catch at the spots around the work spot."

    override fun icon(settings: StepSettings): StepIcon = StepIcon.Skill(Skill.FISHING)

    override fun skill(settings: StepSettings): Int = Skill.FISHING

    override fun target(names: GameNames): StepTarget = StepTarget(FISH, FishOptions(names))

    override fun details(settings: StepSettings, context: FlowContext): List<String> =
        listOf(StepAmount.detail(settings, unbounded = "until the bag is full"))

    override fun fields(names: GameNames): List<StepField> =
        listOf(
            StepField.Search("Catch", target(names), "What would you like to fish?"),
            StepAmount.field("Amount", unbounded = "until the bag is full", button = "Full"),
            StepRadius.field(),
        )

    override fun summary(settings: StepSettings): String =
        "fish ${StepAmount.prefix(settings)}${settings[FISH] ?: "?"}${StepRadius.suffix(settings)}"

    override fun resolve(settings: StepSettings, context: FlowContext): ResolvedStep {
        val name = settings[FISH] ?: throw FlowError("fish needs a fish")
        val method = FishingMethod.named(name)
            ?: throw FlowError("'$name' is not a fish you can catch yet. Fish: ${FishingMethod.ALL.joinToString(", ") { it.word }}")
        return FishStep(method, StepRadius.read(settings), context.workSpot, StepAmount.read(settings))
    }
}

/** A fish step resolved. Later steps know it gathers its catches. */
data class FishStep(val method: FishingMethod, val radius: Int, val workSpot: WorkSpot, val amount: Int? = null) : ResolvedStep {

    override fun after(context: FlowContext): FlowContext = context.copy(gathered = context.gathered + method.catchIds)

    override fun activity(player: Player, runTile: Tile): StepActivity =
        FishingActivity(LunaFisher(player, method, Area(workSpot.tile(runTile), radius)), amount)
}
