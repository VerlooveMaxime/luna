package game.idle.autopilot.travel

import api.bot.webwalk.model.EdgeType
import api.bot.webwalk.model.TeleportDefinition
import api.bot.webwalk.plan.PlanLeg
import api.bot.webwalk.plan.WebWalkPlan
import api.bot.webwalk.walk.LegContext
import api.bot.webwalk.walk.WebWalkExecutor
import api.bot.webwalk.walk.crossing.BorderGateCrossing
import api.bot.webwalk.walk.ship.ShipBoarding
import game.content.sailing.ShipRoute
import engine.obj.Trapdoor
import game.idle.flow.Meter
import game.idle.flow.Passage
import game.idle.flow.Place
import game.idle.flow.Reason
import game.idle.flow.Said
import game.idle.flow.StepOutcome
import game.idle.flow.StepWork
import game.idle.flow.TeleportDestination
import game.idle.flow.TeleportStarted
import game.idle.flow.UnderWay
import game.idle.location.Tile
import game.idle.movement.TravelChoice
import game.npc.spawn.alKharid.BorderGate
import io.luna.game.model.Position
import java.util.concurrent.CompletableFuture

/** What planning a trip found (web-walker movement Part 3c): a [PlannedTrip], or [NoTrip]. */
sealed interface TripPlanning

/**
 * A trip's [plan], and the Travel choices a quicker trip would have needed ([longWay]): what it would spend that the
 * flow does not allow, empty when this is the quickest the player could take.
 */
data class PlannedTrip(val plan: WebWalkPlan, val longWay: Set<TravelChoice> = emptySet()) : TripPlanning

/** No trip goes there; with [off], one would if the flow allowed that Travel choice. */
data class NoTrip(val off: TravelChoice? = null) : TripPlanning

/** What a trip can see and do for one player. [LunaTraveller] is the in-game one. */
interface Traveller {

    fun position(): Position

    fun isBusy(): Boolean

    /** The cheapest trip from where the player stands to the nearest of [destinations], or why none; planned off the game thread. */
    fun plan(destinations: List<Position>): CompletableFuture<TripPlanning>

    fun walk(to: Position)

    /** Whether an object of [id] stands on [at]. */
    fun standing(id: Int, at: Position): Boolean

    /**
     * Clicks [option] of the object of [id] on [at], its first one when null, walking first to the tile it is used from
     * nearest [from] (the side the trip comes from) as the client does; false when it is not there, has no such option
     * or no tile reaches it.
     */
    fun use(id: Int, at: Position, option: String?, from: Position): Boolean

    /** Whether a dialogue asks the player to pick an option. */
    fun choosing(): Boolean

    /** Picks the [option]th option of that dialogue, from 1. */
    fun choose(option: Int)

    /** What that dialogue's options say, in order; none without one. */
    fun options(): List<String>

    /** Talks to the nearest of the npcs of [ids] standing within a crew's reach of [near], walking up first; false for none. */
    fun talkTo(ids: Set<Int>, near: Position): Boolean

    /** Whether a dialogue shows a line the player clicks on from. */
    fun talking(): Boolean

    /** Clicks on from that line. */
    fun talkOn()

    /**
     * Starts [teleport] as the spellbook or the jewellery would, telling [starting] how it starts once the game takes it,
     * before the game's own lines, and [notWornAgain] of a worn piece taken off to rub it that could not be worn again;
     * how it started, null when it cannot be done now.
     */
    fun teleport(
        teleport: TeleportDefinition,
        starting: (TeleportStarted) -> Unit,
        notWornAgain: (TeleportDestination.Jewellery) -> Unit,
    ): TeleportStarted?

    /** Tells the player what the trip does, at once: a spend before the game's own lines about it (Maxime, 2026-10-18). */
    fun say(said: Said)

    fun interrupt()
}

/**
 * A trip to [target] on the web-walker's plan, one decision at a time (web-walker movement Part 2): walks, doors, gates and
 * curtains opened when shut, ladders and stairs climbed, trapdoors opened and climbed down, each leg as a player's clicks;
 * and the legs that spend what the flow allows (Part 3c): teleports cast or rubbed, the Al Kharid toll gate paid, ships
 * boarded. The
 * trip is done within [arriveWithin] tiles of the target, next to it as a walk is unless told otherwise (a walk up
 * to a step's target ends on the tile it is used from). It starts on [plan] when one was made already (the
 * bank step's, which picked the bank by it), and plans again when the player is off the plan (a reflex teleported them,
 * say) or through it without arriving; a plan made at once is taken in the same decision. The same move on the same leg
 * from the same tile twice found no way on, so it blocks, saying which [place] or which passage, and tries again once the
 * player stands elsewhere. Every spend is told, and so is a long way round for want of a Travel choice (Q6).
 */
class TripWork(
    private val traveller: Traveller,
    private val target: Tile,
    private val place: Place,
    plan: PlannedTrip? = null,
    private val arriveWithin: Int = 1,
    private val dialogueLines: Int = DIALOGUE_LINES,
) : StepWork {

    private enum class Move { PLAN, WALK, OPEN, PASS, CLIMB, CHOOSE, TELEPORT, TALK, BOARD, LAND }

    /**
     * A move on the [leg]th leg from [from]; a dialogue's moves ([TALK] and its [CHOOSE]) count their [line], so several
     * from one tile differ, up to [dialogueLines] in a row before the trip blocks: a dialogue that never closes.
     */
    private data class Attempt(val leg: Int, val from: Position, val move: Move, val line: Int = 0)

    private var pending: CompletableFuture<TripPlanning>? = null
    private var legs: List<PlanLeg>? = plan?.plan?.legs
    private var index = 0
    private var last: Attempt? = null
    private var passedThrough: Int? = null
    private var lines = 0
    private var refused: Pair<Position, String>? = null
    private var noWay = Reason.NoWay(place)

    /** The legs of the plan under way whose spend was told: a toll or a fare once a leg. */
    private val told = mutableSetOf<Int>()

    init {
        plan?.let(::sayLongWay)
    }

    override val meter: Meter = Meter.None

    /**
     * Busy while the plan is made and while the player is, except where a player clicks on without waiting (Maxime,
     * 2026-10-17: a stop between two walks looked like a stutter, and a door like waiting for nothing): in a walk's last
     * [LEAD] tiles when a leg follows, and at a door once it is open, until the walk through it is clicked: that walk's
     * path lands a tick later, and a decision before then would find the player still on the same tile.
     */
    override fun isBusy(): Boolean = busyWhile(traveller.isBusy())

    /** [isBusy] with [playerBusy] for the player's own busy rule: a step's walk up keeps its kind's (fight's ignores combat's lingering action). */
    fun busyWhile(playerBusy: Boolean): Boolean = pending?.isDone == false || (playerBusy && !clickingAhead())

    private fun clickingAhead(): Boolean {
        val trip = legs ?: return false
        if (index >= trip.lastIndex) return false
        val leg = trip[index]
        return when (leg.type) {
            EdgeType.WALK -> near(traveller.position(), leg.to, LEAD)
            EdgeType.DOOR, EdgeType.GATE, EdgeType.CURTAIN -> passedThrough != index && objectOf(leg)?.let { (id, at) -> !traveller.standing(id, at) } == true
            else -> false
        }
    }

    override fun finishing(): Boolean = false

    private fun sayLongWay(trip: PlannedTrip) {
        TravelChoice.entries.filter { it in trip.longWay }.forEach { traveller.say(Said.LongWay(it)) }
    }

    override fun interrupt() = traveller.interrupt()

    override fun act(left: Int?): StepOutcome {
        val here = traveller.position()
        return if (arrived(here)) StepOutcome.Finished else go(here)
    }

    private fun go(here: Position): StepOutcome {
        pending?.let { planned ->
            if (!planned.isDone) return StepOutcome.Approached
            pending = null
            val trip = when (val planning = planned.join()) {
                is NoTrip -> {
                    noWay = Reason.NoWay(place, planning.off)
                    return StepOutcome.Blocked(noWay)
                }
                is PlannedTrip -> planning
            }
            sayLongWay(trip)
            legs = trip.plan.legs
            index = 0
            told.clear()
        }
        val trip = legs ?: return plan(here)
        while (index < trip.size && done(trip[index], here, last = index == trip.lastIndex)) index++
        val leg = trip.getOrNull(index)
        return if (leg == null || offPlan(leg, here)) plan(here) else take(leg, here)
    }

    private fun arrived(here: Position): Boolean = near(here, target.toPosition(), arriveWithin)

    private fun plan(here: Position): StepOutcome =
        move(Attempt(NO_LEG, here, Move.PLAN), noWay) {
            pending = traveller.plan(listOf(target.toPosition()))
            go(here)
        }

    /** A walk with a leg after it is done [LEAD] tiles short, so the next leg is clicked on the way. */
    private fun done(leg: PlanLeg, here: Position, last: Boolean): Boolean =
        when (leg.type) {
            EdgeType.WALK -> near(here, leg.to, if (last) arriveWithin else LEAD)
            EdgeType.LADDER, EdgeType.STAIR, EdgeType.TRAPDOOR, EdgeType.TELEPORT -> near(here, leg.to, LegContext.LANDING_SLACK)
            EdgeType.SHIP -> near(here, leg.to, DOCK_SLACK)
            else -> here == leg.to
        }

    /** Away from the leg's start: on another floor, or farther than a leg's walk; a ship's deck at its end is on its leg. */
    private fun offPlan(leg: PlanLeg, here: Position): Boolean = !near(here, leg.from, MAX_LEG) && routeOf(leg)?.let { onDeck(it, here) } != true

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
            EdgeType.TELEPORT -> teleport(leg, here)
            EdgeType.CROSSING -> crossing(leg, here)
            EdgeType.SHIP -> ship(leg, here)
            else -> StepOutcome.Blocked(Reason.NoWay(place))
        }

    private fun door(leg: PlanLeg, here: Position, passage: Passage): StepOutcome {
        val (id, at) = objectOf(leg) ?: return StepOutcome.Blocked(Reason.Impassable(passage))
        val stuck = Reason.Impassable(passage)
        return if (traveller.standing(id, at)) {
            move(Attempt(index, here, Move.OPEN), stuck) { use(id, at, null, leg.from, UnderWay.Opening(passage), stuck) }
        } else {
            move(Attempt(index, here, Move.PASS), stuck) {
                passedThrough = index
                traveller.walk(leg.to)
                StepOutcome.OnTheWay(UnderWay.Passing(passage))
            }
        }
    }

    /** "Climb" asks up or down in a dialogue, up first, as Luna's ladders and stairs do. */
    private fun climb(leg: PlanLeg, here: Position, passage: Passage): StepOutcome {
        val (id, at) = objectOf(leg) ?: return StepOutcome.Blocked(Reason.Impassable(passage))
        val stuck = Reason.Impassable(passage)
        return if (traveller.choosing()) {
            move(Attempt(index, here, Move.CHOOSE), stuck) {
                traveller.choose(if (up(leg)) 1 else 2)
                StepOutcome.OnTheWay(UnderWay.Passing(passage))
            }
        } else {
            move(Attempt(index, here, Move.CLIMB), stuck) { use(id, at, leg.action?.option ?: CLIMB, leg.from, UnderWay.Passing(passage), stuck) }
        }
    }

    /** A shut trapdoor is opened first, then the open one climbed down, both by their first option. */
    private fun trapdoor(leg: PlanLeg, here: Position): StepOutcome {
        val (id, at) = objectOf(leg) ?: return StepOutcome.Blocked(Reason.Impassable(Passage.TRAPDOOR))
        val stuck = Reason.Impassable(Passage.TRAPDOOR)
        val trapdoor = Trapdoor.entries.firstOrNull { it.closed == id || it.open == id } ?: return StepOutcome.Blocked(stuck)
        val shut = trapdoor.closed?.takeIf { traveller.standing(it, at) }
        return if (shut != null) {
            move(Attempt(index, here, Move.OPEN), stuck) { use(shut, at, null, leg.from, UnderWay.Opening(Passage.TRAPDOOR), stuck) }
        } else {
            move(Attempt(index, here, Move.CLIMB), stuck) { use(trapdoor.open, at, null, leg.from, UnderWay.Passing(Passage.TRAPDOOR), stuck) }
        }
    }

    /**
     * A teleport the plan takes, cast or rubbed as the reflexes do. One that cannot be done now (the spellbook's click
     * throttle, say) plans the trip again; the same teleport turned down again from the same tile blocks.
     */
    private fun teleport(leg: PlanLeg, here: Position): StepOutcome {
        val teleport = leg.teleport ?: return StepOutcome.Blocked(Reason.NoWay(place))
        return move(Attempt(index, here, Move.TELEPORT), Reason.NoWay(place)) {
            val started = traveller.teleport(teleport, { traveller.say(Said.Teleported(it)) }) { traveller.say(Said.NotWornAgain(it)) }
            when {
                started != null -> StepOutcome.OnTheWay(UnderWay.Teleporting(started.destination))
                refused == here to teleport.id -> StepOutcome.Blocked(Reason.NoWay(place))
                else -> {
                    refused = here to teleport.id
                    legs = null
                    StepOutcome.Approached
                }
            }
        }
    }

    /**
     * The Al Kharid toll gate (the crossing its data hands to `border_gate`), as a player pays it: the gate clicked, the
     * guard's lines clicked on, "Yes, ok." picked by its words, and the gate walks the player through. Any other crossing
     * has no way, and so has a question without those words.
     */
    private fun crossing(leg: PlanLeg, here: Position): StepOutcome {
        if (leg.action?.handler != BorderGateCrossing.ID) return StepOutcome.Blocked(Reason.NoWay(place))
        val (id, at) = objectOf(leg) ?: return StepOutcome.Blocked(Reason.NoWay(place))
        val stuck = Reason.Impassable(Passage.GATE)
        return when {
            traveller.choosing() -> {
                val agree = traveller.options().indexOf(TOLL_AGREED)
                if (agree < 0) return StepOutcome.Blocked(stuck)
                talk(here, Move.CHOOSE, stuck) {
                    if (told.add(index)) traveller.say(Said.PaidToll(BorderGate.TOLL))
                    traveller.choose(agree + 1)
                    StepOutcome.OnTheWay(UnderWay.PayingToll)
                }
            }
            traveller.talking() -> talk(here, Move.TALK, stuck) {
                traveller.talkOn()
                StepOutcome.OnTheWay(UnderWay.PayingToll)
            }
            else -> move(Attempt(index, here, Move.OPEN), stuck) { use(id, at, null, leg.from, UnderWay.PayingToll, stuck) }
        }
    }

    /**
     * A ship as a player takes it (Luna's `ShipRoute`, upstream's `ShipBoarding` options): the crew talked to, their lines
     * clicked on and the boarding options picked, the voyage sat out (Luna holds the player), "The ship arrives" clicked
     * on, then the gangplank crossed from the deck. The fare is told as boarding starts (Maxime, 2026-10-18).
     */
    private fun ship(leg: PlanLeg, here: Position): StepOutcome {
        val boarding = routeOf(leg)?.let(ShipBoarding::of) ?: return StepOutcome.Blocked(Reason.NoWay(place))
        val plank = objectOf(leg) ?: return StepOutcome.Blocked(Reason.NoWay(place))
        val route = boarding.route
        val sailing = StepOutcome.OnTheWay(UnderWay.Sailing(route.place))
        val stuck = Reason.NoWay(place)
        return when {
            traveller.choosing() -> {
                val option = traveller.options().indexOfFirst { it in boarding.options }
                if (option < 0) return StepOutcome.Blocked(stuck)
                talk(here, Move.CHOOSE, stuck) {
                    traveller.choose(option + 1)
                    sailing
                }
            }
            traveller.talking() -> talk(here, Move.TALK, stuck) {
                traveller.talkOn()
                sailing
            }
            onDeck(route, here) -> move(Attempt(index, here, Move.LAND), stuck) { use(plank.first, plank.second, null, here, UnderWay.Sailing(route.place), stuck) }
            else -> move(Attempt(index, here, Move.BOARD), stuck) {
                if (told.add(index)) traveller.say(Said.Fare(route.fare, route.place))
                if (traveller.talkTo(route.crew, leg.from)) sailing else StepOutcome.Blocked(stuck)
            }
        }
    }

    private fun routeOf(leg: PlanLeg): ShipRoute? {
        if (leg.type != EdgeType.SHIP) return null
        val key = leg.action?.key ?: return null
        return ShipRoute.entries.firstOrNull { it.name == key }
    }

    /** On the deck the voyage ends on, where the gangplank ashore is crossed from. */
    private fun onDeck(route: ShipRoute, here: Position): Boolean = near(here, route.destination, DOCK_SLACK)

    private fun use(id: Int, at: Position, option: String?, from: Position, doing: UnderWay, stuck: Reason): StepOutcome =
        if (traveller.use(id, at, option, from)) StepOutcome.OnTheWay(doing) else StepOutcome.Blocked(stuck)

    /** Makes [attempt] unless it is the last one again, which found no way on: then the trip blocks on [stuck]. */
    private fun move(attempt: Attempt, stuck: Reason, make: () -> StepOutcome): StepOutcome {
        if (attempt == last) return StepOutcome.Blocked(stuck)
        last = attempt
        if (attempt.line == 0) lines = 0
        return make()
    }

    /** A dialogue's next move, its lines counted since the last other move; one too many blocks on [stuck]. */
    private fun talk(here: Position, move: Move, stuck: Reason, make: () -> StepOutcome): StepOutcome =
        if (lines >= dialogueLines) StepOutcome.Blocked(stuck) else move(Attempt(index, here, move, ++lines), stuck, make)

    private fun objectOf(leg: PlanLeg): Pair<Int, Position>? {
        val action = leg.action ?: return null
        val id = action.objectId ?: return null
        val at = action.objectPosition ?: return null
        return id to at
    }

    private fun up(leg: PlanLeg): Boolean = WebWalkExecutor.isUp(leg.from, leg.to)

    private fun near(here: Position, tile: Position, tiles: Int): Boolean = here.z == tile.z && here.computeLongestDistance(tile) <= tiles

    private companion object {
        const val NO_LEG = -1

        /** Farther than this from a leg's start, the player is off the plan: the planner's walks are at most 56 tiles. */
        const val MAX_LEG = 64

        /** The autopilot decides after up to three idle ticks, six tiles at a run, so the next leg is clicked this far ahead. */
        const val LEAD = 6

        const val CLIMB = "Climb"

        /** How far from a ship's deck or its dock the player may stand, as upstream's `ShipVoyage` allows. */
        const val DOCK_SLACK = 2

        /** The longest talk a passage takes is a customs officer's, nine lines and questions: twice that is a dialogue stuck. */
        const val DIALOGUE_LINES = 18

        /** The toll guard's third option, `BorderGate.talk`'s, which pays. */
        const val TOLL_AGREED = "Yes, ok."
    }
}
