package game.idle.autopilot.fishing

import game.idle.flow.Counts
import game.idle.flow.Gathers
import game.idle.flow.FlowContext
import game.idle.flow.FlowError
import game.idle.flow.LevelNeed
import game.idle.flow.OwnSpot
import game.idle.flow.Reasons
import game.idle.flow.ResolvedStep
import game.idle.flow.StepAmount
import game.idle.flow.StepField
import game.idle.flow.StepIcon
import game.idle.flow.StepNeeds
import game.idle.flow.StepRadius
import game.idle.flow.StepSettings
import game.idle.flow.StepSpot
import game.idle.flow.StepType
import game.idle.flow.StepWork
import game.idle.flow.ToolNeed
import game.idle.flow.WorkSpot
import game.idle.flow.option.GameNames
import game.idle.flow.option.StepTarget
import game.idle.location.Area
import game.idle.location.Tile
import game.skill.fishing.catchFish.Fish
import game.skill.fishing.catchFish.FishingSpot
import game.skill.fishing.catchFish.Tool
import io.luna.game.model.def.ItemDefinition
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

    /** Everything the method can pull up, a big net's caskets and boots included: what lands in the bag. */
    val catchIds: Set<Int> = tool.fish.map { it.id }.toSet()

    /** The raw fish the method catches, in Luna's order: what a fish step can be for. */
    val fish: List<Fish> = tool.fish.filter { ItemDefinition.ALL.retrieve(it.id).name.startsWith(RAW) }

    /** The tool, which Luna's fishing counts in the bag only. */
    val toolNeed: ToolNeed = ToolNeed(word = null, mapOf(tool.id to 1))

    /** The bait, when the method takes any (S03b: bait like tools), counted in the bag only and not one by one. */
    val baitNeed: ToolNeed? = tool.bait?.let { ToolNeed(word = FEATHERS.takeIf { tool == Tool.FLY_FISHING_ROD }, mapOf(it to 1), countable = false) }

    companion object {

        /** The fly fishing rod's bait is named "Feather", one of many, so its need reads "feathers" as fishing bait reads "fishing bait". */
        private const val FEATHERS = "feathers"

        /** What the cache calls an uncooked fish, which a casket or seaweed is not. */
        const val RAW = "Raw "

        /** Every tool Luna's table has spots for, lowest level first. */
        val ALL: List<FishingMethod> by lazy { Tool.entries.map(::FishingMethod).filter { it.spotIds.isNotEmpty() } }
    }
}

/**
 * What a fish step is for (Maxime, 2026-10-15): one [fish], caught with its [method], which is the only catch the step
 * counts; whatever else the method pulls up lands in the bag as by-catch. [word] is what the fish setting keeps, the
 * fish's name as Luna's table spells it ("shrimp", "anchovy", as the tutorial's flow says).
 */
data class FishingCatch(val fish: Fish, val method: FishingMethod) {

    val word: String = fish.name.lowercase()

    companion object {

        /** Every raw fish of every method, lowest level first. */
        val ALL: List<FishingCatch> by lazy { FishingMethod.ALL.flatMap { method -> method.fish.map { FishingCatch(it, method) } }.sortedBy { it.fish.level } }

        fun named(word: String): FishingCatch? = ALL.firstOrNull { it.word == word.lowercase() }
    }
}

/** Fish: one fish at the spots of its method within a radius of the work spot, a count of it or, without one, until full. */
object FishStepType : StepType {

    const val FISH = "fish"

    override val kind = "fish"

    override val label = "fish"

    override val description = "Fishes one kind of fish at the spots around the work spot."

    override val goal = true

    override fun icon(settings: StepSettings): StepIcon = StepIcon.Skill(Skill.FISHING)

    override fun skill(settings: StepSettings): Int = Skill.FISHING

    override fun target(names: GameNames): StepTarget = StepTarget(FISH, FishOptions(names))

    override fun details(settings: StepSettings, context: FlowContext): List<String> =
        listOf(StepAmount.detail(settings, unbounded = "until the bag is full"))

    override fun fields(names: GameNames): List<StepField> =
        listOf(
            StepField.Search("Catch", target(names), "What would you like to fish?"),
            StepAmount.field(StepAmount.LABEL, unbounded = "until the bag is full", button = "Full"),
            StepSpot.field(),
            StepRadius.field(),
        )

    override fun resolve(settings: StepSettings, context: FlowContext): ResolvedStep {
        val name = settings[FISH] ?: throw FlowError(Reasons.needs("Catch"))
        val catch = FishingCatch.named(name) ?: throw FlowError(Reasons.notA("Catch", name))
        return FishStep(catch, StepRadius.read(settings), StepSpot.of(settings, context), StepAmount.read(settings))
    }
}

/** A fish step resolved. Later steps know it gathers everything its method pulls up. */
data class FishStep(
    val catch: FishingCatch,
    val radius: Int,
    val workSpot: WorkSpot,
    override val amount: Int? = null,
) : ResolvedStep, OwnSpot, Gathers, Counts {

    override val ownArea: Area? get() = workSpot.ownArea(radius)

    override val fills: Set<Int> get() = catch.method.catchIds

    override fun after(context: FlowContext): FlowContext = workSpot.after(context.copy(gathered = context.gathered + fills))

    /** The method's tool, its bait if it takes any, and the fish's level. */
    override fun needs(): List<StepNeeds> =
        listOf(StepNeeds(tools = listOfNotNull(catch.method.toolNeed, catch.method.baitNeed), level = LevelNeed(Skill.FISHING, catch.fish.level)))

    override fun work(player: Player, runTile: Tile): StepWork = FishingWork(LunaFisher(player, catch, Area(workSpot.tile(runTile), radius)))
}
