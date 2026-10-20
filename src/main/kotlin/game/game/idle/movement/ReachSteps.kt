package game.idle.movement

import io.luna.game.model.Direction
import io.luna.game.model.Position
import kotlin.math.roundToInt

/** One step of a walk, worth [cost] steps. */
sealed interface Step {

    val cost: Int

    /** Onto the next tile, as collision lets a player walk. */
    data object Plain : Step {
        override val cost: Int = 1
    }
}

/** A step through a [doorway] of the travel web, from the tile [from] to [to]: a door, gate, curtain or entrance. */
sealed interface Crossing : Step {
    val doorway: Doorway
    val from: Position
    val to: Position
}

/**
 * A step through [doorway] from the tile [from] to [to], [shut] when the walk opens it first: a shut one costs
 * [SHUT_COST] steps more (Maxime, 2026-10-17: what a trip counts a door, upstream's 3 ticks at a run).
 */
data class DoorStep(override val doorway: Doorway, override val from: Position, override val to: Position, val shut: Boolean) : Crossing {

    override val cost: Int get() = if (shut) 1 + SHUT_COST else 1

    companion object {
        const val SHUT_COST = 6
    }
}

/**
 * Through the entrance [doorway] used from [from], landing on [to] on the same floor, in [ticks]: worth those ticks at a
 * run, as a shut door is (Maxime, 2026-10-20).
 */
data class EntranceStep(override val doorway: Doorway, override val from: Position, override val to: Position, val ticks: Double) : Crossing {

    override val cost: Int get() = (ticks * STEPS_A_TICK).roundToInt()

    /** How many tiles the entrance moves the player. */
    val length: Int get() = from.computeLongestDistance(to)

    companion object {
        /** Tiles a running player covers in a tick. */
        const val STEPS_A_TICK = 2
    }
}

/** How a walk goes on from a tile: a step in each direction, and the entrances that land it elsewhere. */
fun interface Walk {

    /** The step from [from] in [direction], null when the walk cannot take it. */
    fun step(from: Position, direction: Direction): Step?

    /** The entrances a walk standing on [from] goes through. */
    fun entrances(from: Position): List<EntranceStep> = emptyList()
}

/**
 * The steps a player's walk takes inside a step's area (web-walker movement Part 4): whatever [traversable] lets
 * through, and straight through a shut one of [doorways], which the walk opens on its way; through the same-floor
 * entrances of [doorways] the player sees an option on ([showsOption], the trips' rule; entrance legs Part 2); never from
 * outside the wilderness into it.
 */
class ReachSteps(
    private val traversable: (Position, Direction) -> Boolean,
    private val doorways: Doorways,
    private val inWilderness: (Position) -> Boolean = ::inWilderness,
    private val showsOption: (Int) -> Boolean = ObjectForms.SERVER::showsOption,
) : Walk {

    override fun step(from: Position, direction: Direction): Step? {
        val to = from.translate(1, direction)
        return when {
            entersWilderness(from, to) -> null
            traversable(from, direction) -> doorways.crossedBy(from, to)?.let { DoorStep(it, from, to, shut = false) } ?: Step.Plain
            // A diagonal step never goes through a shut door: the walk opens it from straight in front.
            else -> doorways.between(from, to)?.let { DoorStep(it, from, to, shut = true) }
        }
    }

    override fun entrances(from: Position): List<EntranceStep> =
        doorways.entrancesFrom(from).filter { !entersWilderness(from, it.to) && showsOption(it.doorway.id) }

    private fun entersWilderness(from: Position, to: Position): Boolean = !inWilderness(from) && inWilderness(to)
}
