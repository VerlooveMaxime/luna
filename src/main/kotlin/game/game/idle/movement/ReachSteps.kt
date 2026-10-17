package game.idle.movement

import io.luna.game.model.Direction
import io.luna.game.model.Position

/** One step of a walk, worth [cost] steps. */
sealed interface Step {

    val cost: Int

    /** Onto the next tile, as collision lets a player walk. */
    data object Plain : Step {
        override val cost: Int = 1
    }
}

/**
 * A step through [doorway] from the tile [from], [shut] when the walk opens it first: a shut one costs [SHUT_COST] steps
 * more (Maxime, 2026-10-17: what a trip counts a door, upstream's 3 ticks at a run).
 */
data class DoorStep(val doorway: Doorway, val from: Position, val shut: Boolean) : Step {

    override val cost: Int get() = if (shut) 1 + SHUT_COST else 1

    companion object {
        const val SHUT_COST = 6
    }
}

/**
 * The steps a player's walk takes inside a step's area (web-walker movement Part 4): whatever [traversable] lets
 * through, and straight through a shut one of [doorways], which the walk opens on its way; never from outside the
 * wilderness into it.
 */
class ReachSteps(
    private val traversable: (Position, Direction) -> Boolean,
    private val doorways: Doorways,
    private val inWilderness: (Position) -> Boolean = ::inWilderness,
) {

    /** The step from [from] in [direction], null when the walk cannot take it. */
    fun step(from: Position, direction: Direction): Step? {
        val to = from.translate(1, direction)
        return when {
            !inWilderness(from) && inWilderness(to) -> null
            traversable(from, direction) -> doorways.crossedBy(from, to)?.let { DoorStep(it, from, shut = false) } ?: Step.Plain
            // A diagonal step never goes through a shut door: the walk opens it from straight in front.
            else -> doorways.between(from, to)?.let { DoorStep(it, from, shut = true) }
        }
    }
}
