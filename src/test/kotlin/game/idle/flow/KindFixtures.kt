package game.idle.flow

import game.idle.autopilot.drop.DropWork
import game.idle.autopilot.drop.ItemDropper
import game.idle.autopilot.rest.RestWork
import game.idle.autopilot.walk.FakeWalker
import game.idle.autopilot.walk.WalkWork
import game.idle.location.Tile

/**
 * One kind of step under the shared rules ([StepTurnContractTest]): its real work over its fake adapter, inside a
 * [StepTurn] built as the autopilot builds it. The turn starts with everything the kind needs and something to do.
 */
interface KindFixture {

    val name: String

    /** What the player has on them, which the turn reads; the kind's tools and levels to begin with. */
    val carried: FakeCarried

    /** A fresh turn of the kind, [amount] for one that counts. */
    fun turn(amount: Int? = null): StepTurn

    /** What the kind's adapter was told, in order: "chop 3171,3444", "walk to 3171,3444", "drop", "interrupt". */
    val done: List<String>

    /** The kind's own count rises by [units]: logs cut, ores mined, catches, hitpoints back; a kind that counts nothing ignores it. */
    fun gain(units: Int)
}

/** A kind that works at targets around a work spot and needs a tool and a level: chop, mine, fish. */
interface GatherFixture : KindFixture {

    val tool: ToolNeed

    val level: LevelNeed

    /** The walker every walk back of the turn uses; where it stands is where the player is. */
    val walker: FakeWalker

    val anchor: Tile

    fun adapterBusy(busy: Boolean)

    /** Every target is gone from reach (stumps, empty rocks, spots moved on). */
    fun clearTargets()

    fun restoreTargets()

    /** The player stands outside the step's area. */
    fun leaveArea()

    fun enterArea()
}

/** The turn the autopilot builds for a kind's [work]: no own-spot walk, the walk back over [walker]. */
fun turnOf(work: StepWork, amount: Int?, needs: List<StepNeeds>, carried: FakeCarried, fillsBag: Boolean, walker: FakeWalker): StepTurn =
    StepTurn(work, amount, needs, carried, fillsBag) { to -> WalkWork(walker, to, Place.WORK_SPOT) }

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
}

class DropFixture : KindFixture {

    override val name = "drop"

    override val carried = FakeCarried()

    private val dropper = RecordingDropper()

    override val done: List<String> get() = dropper.done

    override fun turn(amount: Int?): StepTurn =
        turnOf(DropWork(dropper), amount, emptyList(), carried, fillsBag = false, FakeWalker(Tile(3050, 3050)))

    override fun gain(units: Int) = Unit

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
