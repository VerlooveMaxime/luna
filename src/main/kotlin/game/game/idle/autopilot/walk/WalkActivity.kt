package game.idle.autopilot.walk

import game.idle.flow.Place
import game.idle.flow.Reason
import game.idle.flow.Said
import game.idle.flow.StepActivity
import game.idle.flow.StepStop
import game.idle.location.Tile

/** What the walk step can see and do for one player. [LunaWalker] is the in-game one. */
interface Walker {

    fun isBusy(): Boolean

    fun position(): Tile

    /** Starts walking towards the target, or as close to it as a path goes. */
    fun walk()
}

/**
 * The walk step: walks until the player stands on the [target] or next to it (a target taken by a wall or a tree
 * cannot be stood on). A walk that ends where it started found no way closer, so the step blocks with the reason; it
 * tries again once the player stands somewhere else. The reasons say what [place] the target is: a walk step's tile,
 * or the work spot of a step walking to its own (S07e).
 */
class WalkActivity(private val walker: Walker, private val target: Tile, private val place: Place) : StepActivity {

    private var walkedFrom: Tile? = null
    private var reason: Reason? = null
    private var done = false

    override fun isBusy(): Boolean = walker.isBusy()

    override fun isDone(): Boolean = done

    override fun blocked(): Reason? = reason

    override fun stopping(): StepStop? = null

    override fun said(): Said? = null

    override fun act() {
        val here = walker.position()
        when {
            here.z != target.z -> reason = Reason.OtherFloor(place)
            arrived(here) -> done = true
            here == walkedFrom -> reason = Reason.NoWay(place)
            else -> {
                walkedFrom = here
                reason = null
                walker.walk()
            }
        }
    }

    private fun arrived(here: Tile): Boolean = maxOf(Math.abs(here.x - target.x), Math.abs(here.y - target.y)) <= 1
}
