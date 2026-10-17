package game.idle.autopilot.travel

import api.bot.webwalk.model.EdgeAction
import api.bot.webwalk.model.EdgeType
import api.bot.webwalk.plan.PlanLeg
import api.bot.webwalk.plan.WebWalkPlan
import game.idle.flow.Meter
import game.idle.flow.Passage
import game.idle.flow.Place
import game.idle.flow.Reason
import game.idle.flow.StepOutcome
import game.idle.flow.UnderWay
import game.idle.location.Tile
import io.luna.game.model.Position
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.CompletableFuture

class TripWorkTest {

    private val start = Position(3200, 3200)
    private val target = Tile(3230, 3200)
    private val doorTile = Position(3210, 3200)
    private val pastDoor = Position(3209, 3200)
    private val door = 1530
    private val ladder = 1748

    private val traveller = FakeTraveller(start)
    private val trip = TripWork(traveller, target, Place.TILE)

    private fun leg(type: EdgeType, from: Position, to: Position, id: Int? = null, at: Position? = null, option: String? = null) =
        PlanLeg(type, from, to, cost = 1.0, action = EdgeAction(objectId = id, objectPosition = at, option = option))

    private fun walkLeg(from: Position, to: Position) = PlanLeg(EdgeType.WALK, from, to, cost = 1.0)

    private fun doorLeg(type: EdgeType = EdgeType.DOOR) = leg(type, doorTile, pastDoor, door, doorTile)

    private fun upstairs(type: EdgeType = EdgeType.LADDER, option: String? = "Climb-up") =
        leg(type, start, Position(3200, 3200, 1), ladder, Position(3201, 3200), option)

    private fun downstairs(type: EdgeType = EdgeType.LADDER) = leg(type, start, Position(3200, 3200, 1), ladder, Position(3201, 3200)).copy(
        from = Position(3200, 3200, 1), to = start,
    )

    /** The plan the trip's first decision makes, and takes at once. */
    private fun planned(vararg legs: PlanLeg) {
        traveller.plans += CompletableFuture.completedFuture(WebWalkPlan(legs.toList(), cost = 1.0))
    }

    @Test
    fun `a trip beside its target is over before it plans`() {
        traveller.position = Position(3229, 3201)
        assertEquals(StepOutcome.Finished, trip.act(null))
    }

    @Test
    fun `a trip told to arrive on its target is not over beside it`() {
        traveller.position = Position(3229, 3201)
        val onTarget = TripWork(traveller, target, Place.TILE, arriveWithin = 0)
        assertEquals(StepOutcome.Approached, onTarget.act(null))
    }

    @Test
    fun `a trip told to arrive on its target walks its last leg all the way`() {
        traveller.position = Position(3229, 3201)
        val onTarget = TripWork(traveller, target, Place.TILE, WebWalkPlan(listOf(walkLeg(start, target.toPosition())), cost = 1.0), arriveWithin = 0)
        onTarget.act(null)
        assertEquals(listOf(target.toPosition()), traveller.walks)
    }

    @Test
    fun `a trip on another floor than its target is not over`() {
        traveller.position = Position(3230, 3200, 1)
        traveller.plans += CompletableFuture()
        assertEquals(StepOutcome.Approached, trip.act(null))
    }

    @Test
    fun `a trip plans to its target first`() {
        traveller.plans += CompletableFuture()
        trip.act(null)
        assertEquals(listOf(listOf(target.toPosition())), traveller.planned)
    }

    @Test
    fun `a trip waits while its plan is being made`() {
        traveller.plans += CompletableFuture()
        trip.act(null)
        assertTrue(trip.isBusy())
    }

    @Test
    fun `a trip is not busy once its plan is made`() {
        planned(walkLeg(start, target.toPosition()))
        trip.act(null)
        assertFalse(trip.isBusy())
    }

    @Test
    fun `a plan made at once is taken in the same decision`() {
        planned(walkLeg(start, target.toPosition()))
        assertEquals(StepOutcome.Approached to listOf(target.toPosition()), trip.act(null) to traveller.walks)
    }

    @Test
    fun `a plan made off the game thread is taken at a later decision`() {
        val making = CompletableFuture<WebWalkPlan?>()
        traveller.plans += making
        trip.act(null)
        making.complete(WebWalkPlan(listOf(walkLeg(start, target.toPosition())), cost = 1.0))
        trip.act(null)
        assertEquals(listOf(target.toPosition()), traveller.walks)
    }

    @Test
    fun `a plan made off the game thread frees the trip once it is made`() {
        val making = CompletableFuture<WebWalkPlan?>()
        traveller.plans += making
        trip.act(null)
        making.complete(null)
        assertFalse(trip.isBusy())
    }

    @Test
    fun `a trip given its plan starts on it without planning`() {
        val given = TripWork(traveller, target, Place.TILE, WebWalkPlan(listOf(walkLeg(start, target.toPosition())), cost = 1.0))
        given.act(null)
        assertEquals(emptyList<List<Position>>() to listOf(target.toPosition()), traveller.planned to traveller.walks)
    }

    @Test
    fun `a trip is busy while the player is`() {
        traveller.busy = true
        assertTrue(trip.isBusy())
    }

    @Test
    fun `a trip not planned yet waits for nothing`() {
        assertFalse(trip.isBusy())
    }

    @Test
    fun `a trip with no way there blocks`() {
        traveller.noWay = true
        assertEquals(StepOutcome.Blocked(Reason.NoWay(Place.TILE)), trip.act(null))
    }

    @Test
    fun `a walk leg walks to its end`() {
        planned(walkLeg(start, target.toPosition()))
        trip.act(null)
        assertEquals(listOf(target.toPosition()), traveller.walks)
    }

    @Test
    fun `a walk that got no closer blocks`() {
        planned(walkLeg(start, target.toPosition()))
        trip.act(null)
        assertEquals(StepOutcome.Blocked(Reason.NoWay(Place.TILE)), trip.act(null))
    }

    @Test
    fun `a walk is tried again once the player stands elsewhere`() {
        planned(walkLeg(start, target.toPosition()))
        trip.act(null)
        traveller.position = Position(3205, 3200)
        assertEquals(StepOutcome.Approached, trip.act(null))
    }

    @Test
    fun `a door is used from the side the trip comes from`() {
        traveller.position = doorTile
        traveller.standing += door to doorTile
        planned(doorLeg())
        trip.act(null)
        assertEquals(listOf(doorTile), traveller.sides)
    }

    @Test
    fun `a walk with a leg after it is done a few tiles short, so the next leg is clicked on the way`() {
        planned(walkLeg(start, Position(3215, 3200)), walkLeg(Position(3215, 3200), target.toPosition()))
        trip.act(null)
        traveller.position = Position(3210, 3200)
        trip.act(null)
        assertEquals(listOf(Position(3215, 3200), target.toPosition()), traveller.walks)
    }

    @Test
    fun `the last walk is done only beside its end`() {
        planned(walkLeg(start, Position(3215, 3200)), walkLeg(Position(3215, 3200), Position(3225, 3200)))
        trip.act(null)
        traveller.position = Position(3214, 3200)
        trip.act(null)
        traveller.position = Position(3221, 3200)
        traveller.plans += CompletableFuture()
        trip.act(null)
        assertEquals(listOf(Position(3215, 3200), Position(3225, 3200), Position(3225, 3200)), traveller.walks)
    }

    @Test
    fun `a trip clicks ahead in a walk's last tiles when a leg follows`() {
        planned(walkLeg(start, Position(3215, 3200)), doorLeg())
        trip.act(null)
        traveller.busy = true
        traveller.position = Position(3210, 3200)
        assertFalse(trip.isBusy())
    }

    @Test
    fun `a trip far from a walk's end waits for the walk`() {
        planned(walkLeg(start, Position(3215, 3200)), doorLeg())
        trip.act(null)
        traveller.busy = true
        assertTrue(trip.isBusy())
    }

    @Test
    fun `a trip on its last walk waits for the walk`() {
        planned(walkLeg(start, Position(3215, 3200)))
        trip.act(null)
        traveller.busy = true
        traveller.position = Position(3213, 3200)
        assertTrue(trip.isBusy())
    }

    @Test
    fun `a trip waits while a shut door is being opened`() {
        traveller.position = doorTile
        traveller.standing += door to doorTile
        planned(doorLeg(), walkLeg(pastDoor, target.toPosition()))
        trip.act(null)
        traveller.busy = true
        assertTrue(trip.isBusy())
    }

    @Test
    fun `a door just opened lets the trip click on while the player still acts`() {
        traveller.position = doorTile
        traveller.standing += door to doorTile
        planned(doorLeg(), walkLeg(pastDoor, target.toPosition()))
        trip.act(null)
        traveller.standing.clear()
        traveller.busy = true
        assertFalse(trip.isBusy())
    }

    @Test
    fun `once the walk through an open door is clicked the trip waits for it`() {
        traveller.position = doorTile
        planned(doorLeg(), walkLeg(pastDoor, target.toPosition()))
        trip.act(null)
        traveller.busy = true
        assertTrue(trip.isBusy())
    }

    @Test
    fun `past one door the next one just opened lets the trip click on`() {
        val secondDoor = leg(EdgeType.DOOR, pastDoor, Position(3208, 3200), 1533, pastDoor)
        traveller.position = doorTile
        planned(doorLeg(), secondDoor, walkLeg(Position(3208, 3200), target.toPosition()))
        trip.act(null)
        traveller.position = pastDoor
        traveller.standing += 1533 to pastDoor
        trip.act(null)
        traveller.standing.clear()
        traveller.busy = true
        assertFalse(trip.isBusy())
    }

    @Test
    fun `a door leg with nothing to click keeps the trip waiting for the player`() {
        traveller.position = doorTile
        planned(PlanLeg(EdgeType.DOOR, doorTile, pastDoor, cost = 1.0), walkLeg(pastDoor, target.toPosition()))
        trip.act(null)
        traveller.busy = true
        assertTrue(trip.isBusy())
    }

    @Test
    fun `a trip waits while the player climbs`() {
        planned(upstairs(), walkLeg(Position(3200, 3200, 1), Position(3205, 3200, 1)))
        trip.act(null)
        traveller.busy = true
        assertTrue(trip.isBusy())
    }

    @Test
    fun `a leg walked goes on to the next`() {
        planned(walkLeg(start, doorTile), doorLeg())
        traveller.position = Position(3211, 3200)
        traveller.standing += door to doorTile
        trip.act(null)
        assertEquals(listOf(Triple(door, doorTile, null as String?)), traveller.used)
    }

    @Test
    fun `a shut door is opened first`() {
        traveller.position = doorTile
        traveller.standing += door to doorTile
        planned(doorLeg())
        assertEquals(StepOutcome.OnTheWay(UnderWay.Opening(Passage.DOOR)), trip.act(null))
    }

    @Test
    fun `an open door is walked through`() {
        traveller.position = doorTile
        planned(doorLeg())
        trip.act(null)
        assertEquals(listOf(pastDoor), traveller.walks)
    }

    @Test
    fun `going through an open door says so`() {
        traveller.position = doorTile
        planned(doorLeg())
        assertEquals(StepOutcome.OnTheWay(UnderWay.Passing(Passage.DOOR)), trip.act(null))
    }

    @Test
    fun `a door is passed once the player stands beyond it`() {
        planned(doorLeg(), walkLeg(pastDoor, target.toPosition()))
        traveller.position = pastDoor
        trip.act(null)
        assertEquals(listOf(target.toPosition()), traveller.walks)
    }

    @Test
    fun `a gate is opened as a gate`() {
        traveller.position = doorTile
        traveller.standing += door to doorTile
        planned(doorLeg(EdgeType.GATE))
        assertEquals(StepOutcome.OnTheWay(UnderWay.Opening(Passage.GATE)), trip.act(null))
    }

    @Test
    fun `a curtain is opened as a curtain`() {
        traveller.position = doorTile
        traveller.standing += door to doorTile
        planned(doorLeg(EdgeType.CURTAIN))
        assertEquals(StepOutcome.OnTheWay(UnderWay.Opening(Passage.CURTAIN)), trip.act(null))
    }

    @Test
    fun `a door that would not open blocks, naming it`() {
        traveller.position = doorTile
        traveller.standing += door to doorTile
        planned(doorLeg())
        trip.act(null)
        assertEquals(StepOutcome.Blocked(Reason.Impassable(Passage.DOOR)), trip.act(null))
    }

    @Test
    fun `a door that would not let the player through blocks, naming it`() {
        traveller.position = doorTile
        planned(doorLeg())
        trip.act(null)
        assertEquals(StepOutcome.Blocked(Reason.Impassable(Passage.DOOR)), trip.act(null))
    }

    @Test
    fun `a door gone when clicked blocks`() {
        traveller.position = doorTile
        traveller.standing += door to doorTile
        traveller.usable = false
        planned(doorLeg())
        assertEquals(StepOutcome.Blocked(Reason.Impassable(Passage.DOOR)), trip.act(null))
    }

    @Test
    fun `a door leg with no object blocks at its start`() {
        traveller.position = doorTile
        planned(PlanLeg(EdgeType.DOOR, doorTile, pastDoor, cost = 1.0))
        assertEquals(StepOutcome.Blocked(Reason.Impassable(Passage.DOOR)), trip.act(null))
    }

    @Test
    fun `a door leg with nothing to click blocks`() {
        traveller.position = doorTile
        planned(PlanLeg(EdgeType.DOOR, doorTile, pastDoor, cost = 1.0, action = EdgeAction()))
        assertEquals(StepOutcome.Blocked(Reason.Impassable(Passage.DOOR)), trip.act(null))
    }

    @Test
    fun `a door leg with no object tile blocks at its start`() {
        traveller.position = doorTile
        planned(leg(EdgeType.DOOR, doorTile, pastDoor, id = door))
        assertEquals(StepOutcome.Blocked(Reason.Impassable(Passage.DOOR)), trip.act(null))
    }

    @Test
    fun `a ladder is climbed by the leg's option`() {
        planned(upstairs())
        trip.act(null)
        assertEquals(listOf(Triple(ladder, Position(3201, 3200), "Climb-up" as String?)), traveller.used)
    }

    @Test
    fun `climbing up a ladder says so`() {
        planned(upstairs())
        assertEquals(StepOutcome.OnTheWay(UnderWay.Passing(Passage.LADDER_UP)), trip.act(null))
    }

    @Test
    fun `climbing down a ladder says so`() {
        traveller.position = Position(3200, 3200, 1)
        planned(downstairs())
        assertEquals(StepOutcome.OnTheWay(UnderWay.Passing(Passage.LADDER_DOWN)), trip.act(null))
    }

    @Test
    fun `climbing up the stairs says so`() {
        planned(upstairs(EdgeType.STAIR))
        assertEquals(StepOutcome.OnTheWay(UnderWay.Passing(Passage.STAIRS_UP)), trip.act(null))
    }

    @Test
    fun `climbing down the stairs says so`() {
        traveller.position = Position(3200, 3200, 1)
        planned(downstairs(EdgeType.STAIR))
        assertEquals(StepOutcome.OnTheWay(UnderWay.Passing(Passage.STAIRS_DOWN)), trip.act(null))
    }

    @Test
    fun `a climb with no option of its own is a Climb`() {
        planned(upstairs(option = null))
        trip.act(null)
        assertEquals("Climb", traveller.used.single().third)
    }

    @Test
    fun `the up-or-down question is answered up on the way up`() {
        planned(upstairs(option = null))
        trip.act(null)
        traveller.choosing = true
        trip.act(null)
        assertEquals(listOf(1), traveller.chosen)
    }

    @Test
    fun `the up-or-down question is answered down on the way down`() {
        traveller.position = Position(3200, 3200, 1)
        traveller.choosing = true
        planned(downstairs())
        trip.act(null)
        assertEquals(listOf(2), traveller.chosen)
    }

    @Test
    fun `answering the up-or-down question says so`() {
        traveller.choosing = true
        planned(upstairs())
        assertEquals(StepOutcome.OnTheWay(UnderWay.Passing(Passage.LADDER_UP)), trip.act(null))
    }

    @Test
    fun `a question that will not go away blocks the climb`() {
        traveller.choosing = true
        planned(upstairs())
        trip.act(null)
        assertEquals(StepOutcome.Blocked(Reason.Impassable(Passage.LADDER_UP)), trip.act(null))
    }

    @Test
    fun `a ladder that would not take the player up blocks, naming it`() {
        planned(upstairs())
        trip.act(null)
        assertEquals(StepOutcome.Blocked(Reason.Impassable(Passage.LADDER_UP)), trip.act(null))
    }

    @Test
    fun `a climb leg with no object blocks at its start`() {
        planned(PlanLeg(EdgeType.LADDER, start, Position(3200, 3200, 1), cost = 1.0))
        assertEquals(StepOutcome.Blocked(Reason.Impassable(Passage.LADDER_UP)), trip.act(null))
    }

    @Test
    fun `a climb is over once the player lands near its end`() {
        planned(upstairs(), walkLeg(Position(3200, 3200, 1), Position(3205, 3200, 1)))
        traveller.position = Position(3203, 3203, 1)
        trip.act(null)
        assertEquals(listOf(Position(3205, 3200, 1)), traveller.walks)
    }

    @Test
    fun `a shut trapdoor is opened first`() {
        traveller.standing += 1568 to Position(3201, 3200)
        planned(leg(EdgeType.TRAPDOOR, start, Position(3201, 9600), 1568, Position(3201, 3200)))
        trip.act(null)
        assertEquals(listOf(Triple(1568, Position(3201, 3200), null as String?)), traveller.used)
    }

    @Test
    fun `opening a trapdoor says so`() {
        traveller.standing += 1568 to Position(3201, 3200)
        planned(leg(EdgeType.TRAPDOOR, start, Position(3201, 9600), 1568, Position(3201, 3200)))
        assertEquals(StepOutcome.OnTheWay(UnderWay.Opening(Passage.TRAPDOOR)), trip.act(null))
    }

    @Test
    fun `an open trapdoor is climbed down`() {
        planned(leg(EdgeType.TRAPDOOR, start, Position(3201, 9600), 1568, Position(3201, 3200)))
        trip.act(null)
        assertEquals(listOf(Triple(1570, Position(3201, 3200), null as String?)), traveller.used)
    }

    @Test
    fun `a trapdoor that never shuts is climbed down`() {
        planned(leg(EdgeType.TRAPDOOR, start, Position(3007, 9549), 9472, Position(3201, 3200)))
        assertEquals(StepOutcome.OnTheWay(UnderWay.Passing(Passage.TRAPDOOR)), trip.act(null))
    }

    @Test
    fun `a trapdoor Luna does not know blocks`() {
        planned(leg(EdgeType.TRAPDOOR, start, Position(3201, 9600), 1, Position(3201, 3200)))
        assertEquals(StepOutcome.Blocked(Reason.Impassable(Passage.TRAPDOOR)), trip.act(null))
    }

    @Test
    fun `a trapdoor leg with no object blocks at its start`() {
        planned(PlanLeg(EdgeType.TRAPDOOR, start, Position(3201, 9600), cost = 1.0))
        assertEquals(StepOutcome.Blocked(Reason.Impassable(Passage.TRAPDOOR)), trip.act(null))
    }

    @Test
    fun `a leg that spends is not taken yet`() {
        planned(PlanLeg(EdgeType.TELEPORT, start, Position(3212, 3423), cost = 5.0))
        assertEquals(StepOutcome.Blocked(Reason.NoWay(Place.TILE)), trip.act(null))
    }

    @Test
    fun `a player taken off the plan plans again`() {
        planned(walkLeg(start, Position(3220, 3200)))
        trip.act(null)
        traveller.position = Position(3200, 3300)
        traveller.plans += CompletableFuture()
        trip.act(null)
        assertEquals(2, traveller.planned.size)
    }

    @Test
    fun `a plan walked through without arriving plans again`() {
        planned(walkLeg(start, Position(3220, 3200)))
        trip.act(null)
        traveller.position = Position(3220, 3200)
        traveller.plans += CompletableFuture()
        trip.act(null)
        assertEquals(2, traveller.planned.size)
    }

    @Test
    fun `planning again from where the last plan was made blocks`() {
        planned()
        assertEquals(StepOutcome.Blocked(Reason.NoWay(Place.TILE)), trip.act(null))
    }

    @Test
    fun `a work spot's trip says the work spot`() {
        traveller.noWay = true
        assertEquals(StepOutcome.Blocked(Reason.NoWay(Place.WORK_SPOT)), TripWork(traveller, target, Place.WORK_SPOT).act(null))
    }

    @Test
    fun `interrupting a trip breaks off the player's walk`() {
        trip.interrupt()
        assertEquals(1, traveller.interrupted)
    }

    @Test
    fun `a trip counts nothing and has nothing to settle`() {
        assertEquals(Meter.None to false, trip.meter to trip.finishing())
    }
}
