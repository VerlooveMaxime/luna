package game.idle.flow

import game.idle.autopilot.fishing.FakeFisher
import game.idle.autopilot.fishing.FishStep
import game.idle.autopilot.fishing.FishingMethod
import game.idle.autopilot.fishing.FishingView
import game.idle.autopilot.fishing.FishingWork
import game.idle.autopilot.fishing.SpotCandidate
import game.idle.autopilot.mining.FakeMiner
import game.idle.autopilot.mining.MineStep
import game.idle.autopilot.mining.MiningView
import game.idle.autopilot.mining.MiningWork
import game.idle.autopilot.mining.RockCandidate
import game.idle.autopilot.walk.FakeWalker
import game.idle.autopilot.woodcutting.ChopAction
import game.idle.autopilot.woodcutting.ChopStep
import game.idle.autopilot.woodcutting.FakeWoodcutter
import game.idle.autopilot.woodcutting.TreeCandidate
import game.idle.autopilot.woodcutting.WoodcuttingView
import game.idle.autopilot.woodcutting.WoodcuttingWork
import game.idle.location.Tile
import game.skill.fishing.catchFish.Tool
import game.skill.mining.Pickaxe
import game.skill.mining.Ore
import game.skill.woodcutting.cutTree.Axe
import game.skill.woodcutting.cutTree.Tree
import game.testworld.TestWorld
import io.luna.game.model.Position
import io.luna.game.model.mob.Skill

/** The kinds that gather around a work spot: chop, mine, fish. */
fun gatherKinds(): List<GatherFixture> = listOf(ChopFixture(), MineFixture(), FishFixture())

private const val BRONZE_AXE = 1351
private const val BRONZE_PICKAXE = 1265
private const val FLY_FISHING_ROD = 309
private const val FEATHERS = 314

/** Any axe at its level, counting when wielded, as Luna's woodcutting takes it. */
private val AXE = ToolNeed("axe", Axe.entries.associate { it.id to it.level }, Skill.WOODCUTTING, worn = true)

private val PICKAXE = ToolNeed("pickaxe", Pickaxe.entries.associate { it.id to it.level }, Skill.MINING, worn = true)

/**
 * A gather kind at its work spot: the player stands on the [anchor] with the kind's [tool] and bait ([bag]) and the
 * [level] it needs, one target in reach. The fixture states the tool and the level itself, so the kind's `needs()` is
 * checked against them, not copied. The levels are above 1, so a level one short still allows the bronze tool and the
 * block is the step's level, not the tool.
 */
abstract class SpotFixture(
    final override val name: String,
    final override val tool: ToolNeed,
    final override val level: LevelNeed,
    bag: Set<Int>,
) : GatherFixture {

    final override val anchor = Tile(3200, 3200)

    final override val walker = FakeWalker(anchor)

    final override val carried = FakeCarried().apply {
        this.bag += bag
        levels[level.skill] = level.level
    }

    /** One tile next to the anchor, where the target stands. */
    protected val targetTile = Position(anchor.x + 1, anchor.y)

    protected abstract fun standInArea(inside: Boolean)

    override fun leaveArea() {
        standInArea(inside = false)
        walker.here = Tile(anchor.x + 20, anchor.y)
    }

    override fun enterArea() {
        standInArea(inside = true)
        walker.here = anchor
    }

    override fun toString(): String = name

    protected companion object {
        const val RADIUS = 10
    }
}

class ChopFixture : SpotFixture("chop", AXE, LevelNeed(Skill.WOODCUTTING, 15), bag = setOf(BRONZE_AXE)) {

    private val step = ChopStep(Tree.OAK, RADIUS, WorkSpot.At(anchor))
    private val tree = TreeCandidate(1281, targetTile, Tree.OAK, distance = 0, usableFromHere = true, anchor.toPosition())
    private val woodcutter = FakeWoodcutter(WoodcuttingView(atLocation = true, listOf(tree)), anchor)

    override val done: List<String> get() = woodcutter.steps

    override fun turn(amount: Int?): StepTurn =
        turnOf(WoodcuttingWork(woodcutter, ChopAction(setOf(step.tree))), amount, step.needs(), carried, fillsBag = true, walker)

    override fun gain(units: Int) {
        woodcutter.logs += units
    }

    override fun adapterBusy(busy: Boolean) {
        woodcutter.busy = busy
    }

    override fun clearTargets() {
        woodcutter.view = woodcutter.view.copy(trees = emptyList())
    }

    override fun restoreTargets() {
        woodcutter.view = woodcutter.view.copy(trees = listOf(tree))
    }

    override fun standInArea(inside: Boolean) {
        woodcutter.view = woodcutter.view.copy(atLocation = inside)
    }
}

class MineFixture : SpotFixture("mine", PICKAXE, LevelNeed(Skill.MINING, 15), bag = setOf(BRONZE_PICKAXE)) {

    /** Luna's ores name themselves from the item definitions, which need the cache. */
    init {
        TestWorld.world
    }

    private val step = MineStep(Ore.IRON, RADIUS, WorkSpot.At(anchor))
    private val rock = RockCandidate(2092, targetTile, distance = 0, usableFromHere = true, anchor.toPosition())
    private val miner = FakeMiner(MiningView(atLocation = true, listOf(rock)), anchor)

    override val done: List<String> get() = miner.steps

    override fun turn(amount: Int?): StepTurn = turnOf(MiningWork(miner), amount, step.needs(), carried, fillsBag = true, walker)

    override fun gain(units: Int) {
        miner.ores += units
    }

    override fun adapterBusy(busy: Boolean) {
        miner.busy = busy
    }

    override fun clearTargets() {
        miner.view = miner.view.copy(rocks = emptyList())
    }

    override fun restoreTargets() {
        miner.view = miner.view.copy(rocks = listOf(rock))
    }

    override fun standInArea(inside: Boolean) {
        miner.view = miner.view.copy(atLocation = inside)
    }
}

/** The fly fishing rod, so a baited method runs through the contract. */
class FishFixture : SpotFixture("fish", ToolNeed(word = null, mapOf(FLY_FISHING_ROD to 1)), LevelNeed(Skill.FISHING, 20), bag = setOf(FLY_FISHING_ROD, FEATHERS)) {

    /** Luna's fish name themselves from the item definitions, which need the cache. */
    init {
        TestWorld.world
    }

    private val step = FishStep(FishingMethod(Tool.FLY_FISHING_ROD), RADIUS, WorkSpot.At(anchor))
    private val spot = SpotCandidate(npcIndex = 1, targetTile, distance = 0, usableFromHere = true, anchor.toPosition())
    private val fisher = FakeFisher(FishingView(atLocation = true, listOf(spot)), anchor)

    override val done: List<String> get() = fisher.steps

    override fun turn(amount: Int?): StepTurn = turnOf(FishingWork(fisher), amount, step.needs(), carried, fillsBag = true, walker)

    override fun gain(units: Int) {
        fisher.catches += units
    }

    override fun adapterBusy(busy: Boolean) {
        fisher.busy = busy
    }

    override fun clearTargets() {
        fisher.view = fisher.view.copy(spots = emptyList())
    }

    override fun restoreTargets() {
        fisher.view = fisher.view.copy(spots = listOf(spot))
    }

    override fun standInArea(inside: Boolean) {
        fisher.view = fisher.view.copy(atLocation = inside)
    }
}
