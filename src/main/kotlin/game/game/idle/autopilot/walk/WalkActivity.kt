package game.idle.autopilot.walk

import game.idle.flow.StepActivity
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
 * tries again once the player stands somewhere else. The reasons name the target as [place] and the step walking as
 * [walking]: a step walking to its own work spot words them its way (S07e).
 */
class WalkActivity(
    private val walker: Walker,
    private val target: Tile,
    private val place: String = target.text(),
    private val walking: String = "the walk step",
) : StepActivity {

    private var walkedFrom: Tile? = null
    private var reason: String? = null
    private var done = false

    override fun isBusy(): Boolean = walker.isBusy()

    override fun isDone(): Boolean = done

    override fun blocked(): String? = reason

    override fun act() {
        val here = walker.position()
        when {
            here.z != target.z -> reason = "Autopilot: $walking cannot change floors to reach $place."
            arrived(here) -> done = true
            here == walkedFrom -> reason = "Autopilot: there is no way to walk to $place from here."
            else -> {
                walkedFrom = here
                reason = null
                walker.walk()
            }
        }
    }

    private fun arrived(here: Tile): Boolean = maxOf(Math.abs(here.x - target.x), Math.abs(here.y - target.y)) <= 1
}
