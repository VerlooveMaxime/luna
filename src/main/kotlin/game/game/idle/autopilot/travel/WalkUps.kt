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
import game.idle.flow.UnderWay
import game.idle.location.Tile
import game.idle.movement.DoorStep
import game.idle.movement.Doorway
import game.idle.movement.TravelWebs
import io.luna.game.model.Position
import io.luna.game.model.mob.Player

/**
 * A step's walks up to the targets it picks (web-walker movement Part 4b, Maxime 2026-10-18: one way to carry out every
 * walk): each a [TripWork] along the route the step's look found, its doors the trip's door legs, so they are opened and
 * passed with a trip's words and pace. The trip is made afresh at every decision from that decision's look, so the step
 * still looks again each time and the retry rule, not the trip, sets aside a target that gets no nearer. A target set
 * aside while its walk up was stuck at a door says so once a door: "Autopilot: step 1 could not get through the door at
 * 3101, 3509." (Maxime).
 */
class WalkUps(private val traveller: Traveller) {

    private var trip: TripWork? = null

    /** The target the last walk up went for and the door it was opening, while it was at one. */
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
        val walk = TripWork(traveller, Tile.of(target.approach), Place.WORK_SPOT, plan(traveller.position(), target), arriveWithin = 0)
        trip = walk
        val outcome = walk.act(left = null)
        val opening = outcome is StepOutcome.Blocked || (outcome as? StepOutcome.OnTheWay)?.underWay is UnderWay.Opening
        // A trip from here takes the first door of the route first: that is the one being opened.
        atDoor = target.doors.firstOrNull()?.takeIf { opening }?.let { target.aim to it.doorway }
        return if (outcome is StepOutcome.Blocked || outcome == StepOutcome.Finished) StepOutcome.Approached else outcome
    }

    /** The step decided something else, or was broken off: no walk up is under way. */
    fun stop() {
        trip = null
        atDoor = null
    }

    /** The retry rule set [aim] aside: when the walk up to it was stuck at a door, the door is told, once a turn. */
    fun setAside(aim: Aim) {
        val (stuck, door) = atDoor ?: return
        if (stuck == aim && told.add(door)) said = Said.DoorShut(passageOf(door.type), Tile.of(door.position))
    }

    fun said(): Said? = said.also { said = null }

    companion object {

        /**
         * The legs of a walk from [here] up to [target] along its route: a walk to each door's near side, the door from
         * there to its far side, and a walk on to the tile [target] is used from.
         */
        fun plan(here: Position, target: Candidate): WebWalkPlan {
            var at = here
            val legs = mutableListOf<PlanLeg>()
            for (door in target.doors) {
                if (at != door.from) legs += PlanLeg(EdgeType.WALK, at, door.from, cost = 0.0)
                legs += doorLeg(door)
                at = door.to
            }
            legs += PlanLeg(EdgeType.WALK, at, target.approach, cost = 0.0)
            return WebWalkPlan(legs, cost = 0.0)
        }

        private fun doorLeg(door: DoorStep): PlanLeg =
            PlanLeg(door.doorway.type, door.from, door.to, cost = 0.0, EdgeAction(objectId = door.doorway.id, objectPosition = door.doorway.position))

        private fun passageOf(type: EdgeType): Passage =
            when (type) {
                EdgeType.GATE -> Passage.GATE
                EdgeType.CURTAIN -> Passage.CURTAIN
                else -> Passage.DOOR
            }
    }
}

/** The walks up to targets for [player], over the server's travel web. */
fun walkUpsFor(player: Player): WalkUps = WalkUps(LunaTraveller(player, TravelWebs.SERVER.of(player.world)))
