package game.idle.autopilot.travel

import api.bot.webwalk.model.EdgeAction
import api.bot.webwalk.model.EdgeType
import api.bot.webwalk.plan.PlanLeg
import api.bot.webwalk.plan.WebWalkPlan
import game.idle.autopilot.Candidate
import game.idle.flow.Aim
import game.idle.flow.Passage
import game.idle.flow.Place
import game.idle.flow.Said
import game.idle.flow.StepOutcome
import game.idle.flow.Structure
import game.idle.flow.UnderWay
import game.idle.location.Tile
import game.idle.movement.Crossing
import game.idle.movement.Doorway
import game.idle.movement.EntranceStep
import io.luna.game.model.Position
import io.luna.game.model.mob.Player

/**
 * A step's walks up to the targets it picks (web-walker movement Part 4b, Maxime 2026-10-18: one way to carry out every
 * walk): each a [TripWork] along the route the step's look found, its doors and entrances the trip's door and entrance
 * legs, so they are opened and passed with a trip's words and pace. The trip is made afresh at every decision from that
 * decision's look, so the step still looks again each time and the retry rule, not the trip, sets aside a target that
 * gets no nearer. A target set aside while its walk up was stuck at a door or an entrance says so once each: "Autopilot:
 * step 1 could not get through the door at 3101, 3509." (Maxime), "... could not get up the rocks at 3239, 9497."
 * (entrance legs Part 2).
 */
class WalkUps(private val traveller: Traveller) {

    private var trip: TripWork? = null

    /** The target the last walk up went for and the door or entrance it was clicking, while it was at one. */
    private var atDoor: Pair<Aim, Doorway>? = null

    private val told = mutableSetOf<Doorway>()
    private var said: Said? = null

    /**
     * Whether the step is busy, by its kind's own rule [kindBusy] (found live 2026-10-18: a trip's own rule counts the
     * combat action Luna leaves queued after a kill, and the fight step stood ten seconds at a door), except while the
     * walk up under way clicks on in a walk's last tiles or at an opened door, as any trip does.
     */
    fun isBusy(kindBusy: Boolean): Boolean = trip?.busyWhile(kindBusy) ?: kindBusy

    /** Walks one decision's worth towards [target]; a trip that gets nowhere is the retry rule's to judge, not a block. */
    fun walkTo(target: Candidate): StepOutcome {
        // The place is never said: a walk up's block goes to the retry rule.
        val walk = TripWork(traveller, Tile.of(target.approach), Place.WORK_SPOT, PlannedTrip(plan(traveller.position(), target.crossings, target.approach)), arriveWithin = 0, stops = false)
        trip = walk
        val outcome = walk.act(left = null)
        // A trip from here takes the first crossing of the route first: that is the one being clicked.
        atDoor = target.crossings.firstOrNull()?.takeIf { clicking(it, outcome) }?.let { target.aim to it.doorway }
        return if (outcome is StepOutcome.Blocked || outcome == StepOutcome.Finished) StepOutcome.Approached else outcome
    }

    /** Whether [outcome] is a click on [crossing] or a block at it: a shut door opened, an entrance gone through. */
    private fun clicking(crossing: Crossing, outcome: StepOutcome): Boolean {
        val underWay = (outcome as? StepOutcome.OnTheWay)?.underWay
        return outcome is StepOutcome.Blocked || underWay is UnderWay.Opening || crossing is EntranceStep && underWay is UnderWay.Passing
    }

    /** The step decided something else, or was broken off: no walk up is under way. */
    fun stop() {
        trip = null
        atDoor = null
    }

    /**
     * The retry rule set [aim] aside: when the walk up to it was stuck at a door or an entrance, it is told in the words
     * of what the player sees there, once a turn.
     */
    fun setAside(aim: Aim) {
        val (stuck, door) = atDoor ?: return
        if (stuck != aim || door in told) return
        val passage = if (door.type == EdgeType.ENTRANCE) traveller.entrance(door.id, door.position) ?: return else passageOf(door.type)
        told += door
        said = Said.NotThrough(passage, Tile.of(door.position))
    }

    fun said(): Said? = said.also { said = null }

    companion object {

        /**
         * The legs of a walk from [here] to [to] through [crossings], a route a local search found: a walk to each door's
         * or entrance's near side, the door or entrance from there to its far side, and a walk on to [to]. A flight walks
         * the same way.
         */
        fun plan(here: Position, crossings: List<Crossing>, to: Position): WebWalkPlan {
            var at = here
            val legs = mutableListOf<PlanLeg>()
            for (crossing in crossings) {
                if (at != crossing.from) legs += PlanLeg(EdgeType.WALK, at, crossing.from, cost = 0.0)
                legs += leg(crossing)
                at = crossing.to
            }
            legs += PlanLeg(EdgeType.WALK, at, to, cost = 0.0)
            return WebWalkPlan(legs, cost = 0.0)
        }

        private fun leg(crossing: Crossing): PlanLeg {
            val doorway = crossing.doorway
            return PlanLeg(doorway.type, crossing.from, crossing.to, cost = 0.0, EdgeAction(objectId = doorway.id, objectPosition = doorway.position))
        }

        fun passageOf(type: EdgeType): Passage =
            when (type) {
                EdgeType.GATE -> Structure.GATE
                EdgeType.CURTAIN -> Structure.CURTAIN
                else -> Structure.DOOR
            }
    }
}

/** The walks up to targets for [player], over the server's travel web, spending what their flow allows. */
fun walkUpsFor(player: Player): WalkUps = WalkUps(travellerFor(player))
