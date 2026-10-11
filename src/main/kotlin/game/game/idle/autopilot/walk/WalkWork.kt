package game.idle.autopilot.walk

import game.idle.flow.Meter
import game.idle.flow.Place
import game.idle.flow.Reason
import game.idle.flow.StepOutcome
import game.idle.flow.StepWork
import game.idle.location.Tile

/** What the walk step can see and do for one player. [LunaWalker] is the in-game one. */
interface Walker {

    fun isBusy(): Boolean

    fun position(): Tile

    /** Starts walking towards the target, or as close to it as a path goes. */
    fun walk()

    /** Breaks off the walk in progress. */
    fun interrupt()
}

/**
 * The walk step's work, also the walk to a step's own spot and every walk back to a work area: walks until the player
 * stands on the [target] or next to it (a target taken by a wall or a tree cannot be stood on). A walk that ends where
 * it started found no way closer, so it blocks with the reason; it tries again once the player stands somewhere else.
 * The reasons say what [place] the target is: a walk step's tile, or a step's work spot (S07e).
 */
class WalkWork(private val walker: Walker, private val target: Tile, private val place: Place) : StepWork {

    private var walkedFrom: Tile? = null

    override val meter: Meter = Meter.None

    override fun isBusy(): Boolean = walker.isBusy()

    override fun finishing(): Boolean = false

    override fun interrupt() = walker.interrupt()

    override fun act(left: Int?): StepOutcome {
        val here = walker.position()
        return when {
            here.z != target.z -> StepOutcome.Blocked(Reason.OtherFloor(place))
            arrived(here) -> StepOutcome.Finished
            here == walkedFrom -> StepOutcome.Blocked(Reason.NoWay(place))
            else -> walk(here)
        }
    }

    private fun walk(here: Tile): StepOutcome {
        walkedFrom = here
        walker.walk()
        return StepOutcome.Approached
    }

    private fun arrived(here: Tile): Boolean = maxOf(Math.abs(here.x - target.x), Math.abs(here.y - target.y)) <= 1
}
