package game.idle.autopilot.travel

import game.idle.flow.Passage
import game.idle.flow.Place
import game.idle.flow.StepOutcome
import game.idle.flow.UnderWay
import game.idle.location.Tile
import game.idle.movement.Crossing
import game.idle.movement.DoorStep
import game.idle.movement.Doorway
import game.idle.movement.Doorways
import game.idle.movement.EntranceStep
import game.idle.movement.FleePlanner
import game.idle.movement.ShutOut
import game.idle.movement.Step
import game.idle.movement.Walk
import io.luna.game.model.Direction
import io.luna.game.model.Position

/** The open leaf of a door, object [id] on [position], and the tiles a click on it can be made from. */
data class OpenLeaf(val id: Int, val position: Position, val clickedFrom: List<Position>)

/**
 * What a flight reads of the world around the player: the doorways, how the player walks (through doors and entrances
 * too) and the attackers walk, open doors.
 */
interface FlightTerrain : Walk {

    val doorways: Doorways

    /** Whether an attacker can step from [from] in [direction]. */
    fun npcStep(from: Position, direction: Direction): Boolean

    /** Whether nothing but a mob stops a step from [from] in [direction]: an attacker beside a tile gets at it so. */
    fun onto(from: Position, direction: Direction): Boolean

    /** The open leaf a click shuts [door] by; null when none stands open. */
    fun openLeaf(door: Doorway): OpenLeaf?
}

/**
 * The player's run from what attacks them, one decision at a time (web-walker movement Part 5, Maxime 2026-10-18): to
 * the nearest tile behind a door that keeps every attacker out once shut or past an entrance that keeps them out (entrance
 * legs Part 2), else away from them ([FleePlanner.pick]), walked as a [TripWork] along that route as a walk up is, so a
 * door or an entrance on the way is opened or gone through with a trip's words. At the next decision with the player
 * through such a door, it is shut behind them when that keeps every attacker out (Q4), clicked from a tile on the
 * player's side: a leaf that swung the other way is left open, so the player never steps back to the attackers' side to
 * shut it. "Closing the door" while the click lands, then [shut] once it stands shut; an entrance shuts nothing. A door
 * this flight clicked twice that stayed shut, an entrance clicked twice that left the player on its near side, or either
 * that could not be clicked, is left out of its routes: one click may come to nothing when a hit turns the player to
 * fight back on the way to it.
 */
class Flight(private val traveller: Traveller) {

    private var trip: TripWork? = null
    private var underWay: UnderWay? = null

    /** The door or entrance the last decision sent the player through. */
    private var through: Crossing? = null

    /** The shut door the last decision clicked to open. */
    private var opening: Doorway? = null

    /** The open door the last decision clicked to shut. */
    private var shutting: Doorway? = null

    private var shut: Passage? = null

    /** The clicks on each door that left it shut, or on each entrance that left the player on its near side. */
    private val fruitless = mutableMapOf<Doorway, Int>()

    /**
     * Busy only while a door's or an entrance's click lands, and while the entrance holds the player before it moves them:
     * the run itself is looked at again at every decision, as the attackers move.
     */
    fun isBusy(): Boolean = trip?.isBusy() ?: traveller.isBusy()

    fun underWay(): UnderWay? = underWay

    /** The passage this flight shut behind the player since last asked, once. */
    fun shut(): Passage? = shut.also { shut = null }

    /** One decision's worth of running from attackers standing on [threats]. */
    fun flee(threats: List<Position>, terrain: FlightTerrain) {
        settle()
        val here = traveller.position()
        val shutOut = ShutOut(here, threats, terrain::npcStep, terrain::onto, terrain.doorways)
        val passed = through
        through = null
        if (passed is DoorStep && here == passed.to && shutBehind(passed.doorway, here, shutOut, terrain)) return
        run(here, threats, terrain, shutOut)
    }

    /** The flight is over: a door it shut is said, the rest forgotten. */
    fun calm() {
        settle()
        trip = null
        underWay = null
        through = null
        fruitless.clear()
    }

    /**
     * What the last decision's click came to: a door it shut is said, a door it left shut or an entrance that left the
     * player on its near side counted.
     */
    private fun settle() {
        shutting?.takeIf(::standing)?.let { shut = WalkUps.passageOf(it.type) }
        opening?.takeIf(::standing)?.let { fruitless.merge(it, 1, Int::plus) }
        (through as? EntranceStep)?.takeUnless(::landed)?.let { fruitless.merge(it.doorway, 1, Int::plus) }
        shutting = null
        opening = null
    }

    /** Whether the player stands nearer where [entrance] lands than where it is used from. */
    private fun landed(entrance: EntranceStep): Boolean {
        val here = traveller.position()
        return here.computeLongestDistance(entrance.to) < here.computeLongestDistance(entrance.from)
    }

    private fun stuck(door: Doorway): Boolean = (fruitless[door] ?: 0) >= MAX_FRUITLESS_CLICKS

    private fun standing(door: Doorway): Boolean = traveller.standing(door.id, door.position)

    private fun shutBehind(door: Doorway, here: Position, shutOut: ShutOut, terrain: FlightTerrain): Boolean {
        if (!shutOut.keepsOut(door, here)) return false
        val leaf = terrain.openLeaf(door) ?: return false
        val side = leaf.clickedFrom.firstOrNull { shutOut.keepsOut(door, it) } ?: return false
        if (!traveller.use(leaf.id, leaf.position, option = null, from = side)) return false
        trip = null
        shutting = door
        underWay = UnderWay.Closing(WalkUps.passageOf(door.type))
        return true
    }

    private fun run(here: Position, threats: List<Position>, terrain: FlightTerrain, shutOut: ShutOut) {
        val unstuck = object : Walk {
            override fun step(from: Position, direction: Direction): Step? =
                terrain.step(from, direction)?.takeUnless { it is DoorStep && it.shut && stuck(it.doorway) }

            override fun entrances(from: Position): List<EntranceStep> = terrain.entrances(from).filterNot { stuck(it.doorway) }
        }
        val route = FleePlanner.pick(here, threats, unstuck, shutOut)
        if (route == null) {
            trip = null
            underWay = null
            return
        }
        // The place is never said: no block shows while the player runs clear.
        val walk = TripWork(traveller, Tile.of(route.tile), Place.WORK_SPOT, PlannedTrip(WalkUps.plan(here, route.crossings, route.tile)), arriveWithin = 0, stops = false)
        trip = walk
        val outcome = walk.act(left = null)
        underWay = (outcome as? StepOutcome.OnTheWay)?.underWay
        // A trip from here takes the first crossing of the route first.
        val first = route.crossings.firstOrNull() ?: return
        when {
            outcome is StepOutcome.Blocked -> fruitless[first.doorway] = MAX_FRUITLESS_CLICKS
            underWay is UnderWay.Opening -> opening = first.doorway
            underWay is UnderWay.Passing -> through = first
        }
    }

    private companion object {
        const val MAX_FRUITLESS_CLICKS = 2
    }
}
