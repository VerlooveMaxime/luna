package game.idle.flow

import game.idle.autopilot.drop.DropWork
import game.idle.autopilot.drop.ItemDropper
import game.idle.autopilot.rest.RestWork
import game.idle.autopilot.walk.FakeWalker
import game.idle.autopilot.walk.WalkWork
import game.idle.location.Tile
import io.luna.game.model.Position

/**
 * One kind of step under the shared rules ([StepTurnContractTest]): its real work over its fake adapter, inside a
 * [StepTurn] built as the autopilot builds it. The turn starts with everything the kind needs and something to do.
 */
interface KindFixture {

    val name: String

    /** What the player has on them, which the turn reads; the kind's tools, inputs and levels to begin with. */
    val carried: FakeCarried

    /** A fresh turn of the kind, [amount] for one that counts. */
    fun turn(amount: Int? = null): StepTurn

    /** What the kind's adapter was told, in order: "chop 3171,3444", "walk to 3171,3444", "drop", "interrupt". */
    val done: List<String>

    /**
     * The kind's own count rises by [units]: logs cut, ores mined, catches, bars made, hitpoints back, logs or raw food
     * used up; a kind that counts nothing ignores it.
     */
    fun gain(units: Int)
}

/** A kind whose way of working takes a level, which its `needs()` states. */
interface NeedsFixture : KindFixture {

    val level: LevelNeed
}

/** A kind that needs a tool too (an axe, a tinderbox, a knife, a hammer). */
interface ToolFixture : NeedsFixture {

    val tool: ToolNeed
}

/** A kind whose Amount counts its own work over an adapter that is busy while Luna's action runs. */
interface AmountFixture : KindFixture {

    fun adapterBusy(busy: Boolean)
}

/** A kind that works at targets around a work spot: chop, mine, fish; cook, smelt, smith. */
interface AreaFixture : KindFixture {

    /** The walker every walk back of the turn uses; where it stands is where the player is. */
    val walker: FakeWalker

    val anchor: Tile

    /** Every target is gone from reach (stumps, empty rocks, spots moved on, fires burnt out). */
    fun clearTargets()

    fun restoreTargets()

    /** The player stands outside the step's area. */
    fun leaveArea()

    fun enterArea()
}

/** A kind that gathers around a work spot and fills the bag with what it gets: chop, mine, fish. */
interface GatherFixture : ToolFixture, AreaFixture, AmountFixture

/** A kind that works on input from the bag: light, cook, make, smelt, smith. */
interface ProcessFixture : NeedsFixture, AmountFixture {

    /** No input left in the bag, as before any came or after the last was used. */
    fun emptyInput()

    /** [units] more of the input arrive in the bag. */
    fun supply(units: Int)
}

/** The turn the autopilot builds for a kind's [work]: no own-spot walk, the walk back over [walker]. */
fun turnOf(work: StepWork, amount: Int?, needs: List<StepNeeds>, carried: FakeCarried, fillsBag: Boolean, walker: FakeWalker): StepTurn =
    StepTurn(work, amount, needs, carried, fillsBag) { to -> WalkWork(walker, to, Place.WORK_SPOT) }

/**
 * A kind at its work spot: the player stands on the [anchor] with the [bag] (tools, bait, input) and the [level] it
 * needs, one target in reach on [targetTile]. The fixture states the level itself, so the kind's `needs()` is checked
 * against it, not copied. The levels are above 1, so a level one short still allows the bronze tool and the block is
 * the step's level, not the tool.
 */
abstract class SpotFixture(final override val name: String, final override val level: LevelNeed, bag: Set<Int>) : NeedsFixture, AreaFixture, AmountFixture {

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

/** The kinds that work where they stand with nothing to find: walk, rest, drop. */
fun plainKinds(): List<KindFixture> = listOf(WalkFixture(), RestFixture(), DropFixture())

class WalkFixture : KindFixture {

    override val name = "walk"

    override val carried = FakeCarried()

    private val walker = FakeWalker(Tile(3050, 3050))

    override val done: List<String> get() = List(walker.walks) { "walk" } + List(walker.interrupted) { "interrupt" }

    override fun turn(amount: Int?): StepTurn =
        turnOf(WalkWork(walker, Tile(3100, 3100), Place.TILE), amount, emptyList(), carried, fillsBag = false, walker)

    override fun gain(units: Int) = Unit

    override fun toString(): String = name
}

class RestFixture : KindFixture {

    override val name = "rest"

    override val carried = FakeCarried()

    private var health = Health(10, 20)

    override val done: List<String> get() = emptyList()

    override fun turn(amount: Int?): StepTurn =
        turnOf(RestWork({ health }, percent = 100), amount, emptyList(), carried, fillsBag = false, FakeWalker(Tile(3050, 3050)))

    override fun gain(units: Int) {
        health = Health(health.hitpoints + units, health.full)
    }

    override fun toString(): String = name
}

class DropFixture : KindFixture {

    override val name = "drop"

    override val carried = FakeCarried()

    private val dropper = RecordingDropper()

    override val done: List<String> get() = dropper.done

    override fun turn(amount: Int?): StepTurn =
        turnOf(DropWork(dropper), amount, emptyList(), carried, fillsBag = false, FakeWalker(Tile(3050, 3050)))

    override fun gain(units: Int) = Unit

    override fun toString(): String = name

    private class RecordingDropper : ItemDropper {

        val done = mutableListOf<String>()
        private var items = 3

        override fun isBusy(): Boolean = false

        override fun hasItems(): Boolean = items > 0

        override fun dropItems() {
            done += "drop"
            items = 0
        }

        override fun interrupt() {
            done += "interrupt"
        }
    }
}
