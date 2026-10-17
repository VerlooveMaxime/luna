package game.idle.autopilot.travel

import api.bot.webwalk.model.EdgeType
import api.bot.webwalk.plan.PlanLeg
import api.bot.webwalk.plan.WebWalkPlan
import api.bot.webwalk.walk.LegContext
import api.bot.webwalk.walk.WebWalkExecutor
import engine.obj.Trapdoor
import game.idle.flow.Meter
import game.idle.flow.Passage
import game.idle.flow.Place
import game.idle.flow.Reason
import game.idle.flow.StepOutcome
import game.idle.flow.StepWork
import game.idle.flow.UnderWay
import game.idle.location.Tile
import io.luna.game.model.Position
import java.util.concurrent.CompletableFuture

/** What a trip can see and do for one player. [LunaTraveller] is the in-game one. */
interface Traveller {

    fun position(): Position

    fun isBusy(): Boolean

    /** The cheapest trip from where the player stands to the nearest of [destinations], null for none; planned off the game thread. */
    fun plan(destinations: List<Position>): CompletableFuture<WebWalkPlan?>

    fun walk(to: Position)

    /** Whether an object of [id] stands on [at]. */
    fun standing(id: Int, at: Position): Boolean

    /** Clicks [option] of the object of [id] on [at], its first one when null; false when it is not there or has no such option. */
    fun use(id: Int, at: Position, option: String?): Boolean

    /** Whether a dialogue asks the player to pick an option. */
    fun choosing(): Boolean

    /** Picks the [option]th option of that dialogue, from 1. */
    fun choose(option: Int)

    fun interrupt()
}

/**
 * A trip to [target] on the web-walker's plan, one decision at a time (web-walker movement Part 2): walks, doors, gates and
 * curtains opened when shut, ladders and stairs climbed, trapdoors opened and climbed down, each leg as a player's clicks.
 * The trip is done standing on the target or next to it, as a walk is. It plans again when the player is off the plan (a
 * reflex teleported them, say) or through it without arriving; the same move on the same leg from the same tile twice
 * found no way on, so it blocks, saying which [place] or which passage, and tries again once the player stands elsewhere.
 * Legs that spend (teleports, ships, tolls) are not taken yet: the trips' default allowance plans none of them.
 */
class TripWork(private val traveller: Traveller, private val target: Tile, private val place: Place) : StepWork {

    private enum class Move { PLAN, WALK, OPEN, PASS, CLIMB, CHOOSE }

    private data class Attempt(val leg: Int, val from: Position, val move: Move)

    private var pending: CompletableFuture<WebWalkPlan?>? = null
    private var legs: List<PlanLeg>? = null
    private var index = 0
    private var last: Attempt? = null

    override val meter: Meter = Meter.None

    override fun isBusy(): Boolean = traveller.isBusy() || pending?.isDone == false

    override fun finishing(): Boolean = false

    override fun interrupt() = traveller.interrupt()

    override fun act(left: Int?): StepOutcome {
        val here = traveller.position()
        if (arrived(here)) return StepOutcome.Finished
        pending?.let { planned ->
            pending = null
            legs = planned.join()?.legs ?: return StepOutcome.Blocked(Reason.NoWay(place))
            index = 0
        }
        val trip = legs ?: return plan(here)
        while (index < trip.size && done(trip[index], here)) index++
        val leg = trip.getOrNull(index)
        return if (leg == null || offPlan(leg, here)) plan(here) else take(leg, here)
    }

    private fun arrived(here: Position): Boolean = here.z == target.z && here.computeLongestDistance(target.toPosition()) <= 1

    private fun plan(here: Position): StepOutcome =
        move(Attempt(NO_LEG, here, Move.PLAN), Reason.NoWay(place)) {
            pending = traveller.plan(listOf(target.toPosition()))
            StepOutcome.Approached
        }

    private fun done(leg: PlanLeg, here: Position): Boolean =
        when (leg.type) {
            EdgeType.WALK -> near(here, leg.to, 1)
            EdgeType.LADDER, EdgeType.STAIR, EdgeType.TRAPDOOR -> near(here, leg.to, LegContext.LANDING_SLACK)
            else -> here == leg.to
        }

    /** Away from the leg's start: on another floor, or farther than a leg's walk. */
    private fun offPlan(leg: PlanLeg, here: Position): Boolean = !near(here, leg.from, MAX_LEG)

    private fun take(leg: PlanLeg, here: Position): StepOutcome =
        when (leg.type) {
            EdgeType.WALK -> move(Attempt(index, here, Move.WALK), Reason.NoWay(place)) {
                traveller.walk(leg.to)
                StepOutcome.Approached
            }
            EdgeType.DOOR -> door(leg, here, Passage.DOOR)
            EdgeType.GATE -> door(leg, here, Passage.GATE)
            EdgeType.CURTAIN -> door(leg, here, Passage.CURTAIN)
            EdgeType.LADDER -> climb(leg, here, if (up(leg)) Passage.LADDER_UP else Passage.LADDER_DOWN)
            EdgeType.STAIR -> climb(leg, here, if (up(leg)) Passage.STAIRS_UP else Passage.STAIRS_DOWN)
            EdgeType.TRAPDOOR -> trapdoor(leg, here)
            else -> StepOutcome.Blocked(Reason.NoWay(place))
        }

    private fun door(leg: PlanLeg, here: Position, passage: Passage): StepOutcome {
        val (id, at) = objectOf(leg) ?: return impassable(passage, leg.from)
        val stuck = Reason.Impassable(passage, Tile.of(at))
        return if (traveller.standing(id, at)) {
            move(Attempt(index, here, Move.OPEN), stuck) { use(id, at, null, UnderWay.Opening(passage), stuck) }
        } else {
            move(Attempt(index, here, Move.PASS), stuck) {
                traveller.walk(leg.to)
                StepOutcome.OnTheWay(UnderWay.Passing(passage))
            }
        }
    }

    /** "Climb" asks up or down in a dialogue, up first, as Luna's ladders and stairs do. */
    private fun climb(leg: PlanLeg, here: Position, passage: Passage): StepOutcome {
        val (id, at) = objectOf(leg) ?: return impassable(passage, leg.from)
        val stuck = Reason.Impassable(passage, Tile.of(at))
        return if (traveller.choosing()) {
            move(Attempt(index, here, Move.CHOOSE), stuck) {
                traveller.choose(if (up(leg)) 1 else 2)
                StepOutcome.OnTheWay(UnderWay.Passing(passage))
            }
        } else {
            move(Attempt(index, here, Move.CLIMB), stuck) { use(id, at, leg.action?.option ?: CLIMB, UnderWay.Passing(passage), stuck) }
        }
    }

    /** A shut trapdoor is opened first, then the open one climbed down, both by their first option. */
    private fun trapdoor(leg: PlanLeg, here: Position): StepOutcome {
        val (id, at) = objectOf(leg) ?: return impassable(Passage.TRAPDOOR, leg.from)
        val stuck = Reason.Impassable(Passage.TRAPDOOR, Tile.of(at))
        val trapdoor = Trapdoor.entries.firstOrNull { it.closed == id || it.open == id } ?: return StepOutcome.Blocked(stuck)
        val shut = trapdoor.closed?.takeIf { traveller.standing(it, at) }
        return if (shut != null) {
            move(Attempt(index, here, Move.OPEN), stuck) { use(shut, at, null, UnderWay.Opening(Passage.TRAPDOOR), stuck) }
        } else {
            move(Attempt(index, here, Move.CLIMB), stuck) { use(trapdoor.open, at, null, UnderWay.Passing(Passage.TRAPDOOR), stuck) }
        }
    }

    private fun use(id: Int, at: Position, option: String?, doing: UnderWay, stuck: Reason): StepOutcome =
        if (traveller.use(id, at, option)) StepOutcome.OnTheWay(doing) else StepOutcome.Blocked(stuck)

    /** Makes [attempt] unless it is the last one again, which found no way on: then the trip blocks on [stuck]. */
    private fun move(attempt: Attempt, stuck: Reason, make: () -> StepOutcome): StepOutcome {
        if (attempt == last) return StepOutcome.Blocked(stuck)
        last = attempt
        return make()
    }

    private fun impassable(passage: Passage, at: Position): StepOutcome = StepOutcome.Blocked(Reason.Impassable(passage, Tile.of(at)))

    private fun objectOf(leg: PlanLeg): Pair<Int, Position>? {
        val id = leg.action?.objectId ?: return null
        val at = leg.action?.objectPosition ?: return null
        return id to at
    }

    private fun up(leg: PlanLeg): Boolean = WebWalkExecutor.isUp(leg.from, leg.to)

    private fun near(here: Position, tile: Position, tiles: Int): Boolean = here.z == tile.z && here.computeLongestDistance(tile) <= tiles

    private companion object {
        const val NO_LEG = -1

        /** Farther than this from a leg's start, the player is off the plan: the planner's walks are at most 56 tiles. */
        const val MAX_LEG = 64

        const val CLIMB = "Climb"
    }
}
