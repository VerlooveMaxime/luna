package game.idle.autopilot.travel

import api.bot.webwalk.model.EdgeAction
import api.bot.webwalk.model.EdgeType
import api.bot.webwalk.model.TeleportDefinition
import api.bot.webwalk.model.TeleportKind
import api.bot.webwalk.plan.PlanLeg
import api.bot.webwalk.plan.WebWalkPlan
import game.content.sailing.ShipRoute
import game.idle.flow.Meter
import game.idle.flow.Passage
import game.idle.flow.Place
import game.idle.flow.Reason
import game.idle.flow.Said
import game.idle.flow.StepOutcome
import game.idle.flow.TeleportDestination
import game.idle.flow.TeleportStarted
import game.idle.flow.UnderWay
import game.idle.location.Tile
import game.idle.movement.TravelChoice
import game.skill.magic.teleportSpells.TeleportSpell
import io.luna.game.model.Position
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
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
        traveller.plans += CompletableFuture.completedFuture(PlannedTrip(WebWalkPlan(legs.toList(), cost = 1.0)))
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
        val onTarget = TripWork(traveller, target, Place.TILE, PlannedTrip(WebWalkPlan(listOf(walkLeg(start, target.toPosition())), cost = 1.0)), arriveWithin = 0)
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
        val making = CompletableFuture<TripPlanning>()
        traveller.plans += making
        trip.act(null)
        making.complete(PlannedTrip(WebWalkPlan(listOf(walkLeg(start, target.toPosition())), cost = 1.0)))
        trip.act(null)
        assertEquals(listOf(target.toPosition()), traveller.walks)
    }

    @Test
    fun `a plan made off the game thread frees the trip once it is made`() {
        val making = CompletableFuture<TripPlanning>()
        traveller.plans += making
        trip.act(null)
        making.complete(NoTrip())
        assertFalse(trip.isBusy())
    }

    @Test
    fun `a trip given its plan starts on it without planning`() {
        val given = TripWork(traveller, target, Place.TILE, PlannedTrip(WebWalkPlan(listOf(walkLeg(start, target.toPosition())), cost = 1.0)))
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

    private val landing = Position(3212, 3423)
    private val varrockTeleport = TeleportDefinition("spell_varrock", TeleportKind.SPELL, "VARROCK", option = null, landing, "spell_varrock", cost = 5)
    private val varrock = TeleportDestination.Spell(TeleportSpell.VARROCK)
    private val cast = TeleportStarted.Cast(varrock, mapOf(556 to 3, 554 to 1, 563 to 1))

    private fun teleportLeg() = PlanLeg(EdgeType.TELEPORT, start, landing, cost = 5.0, teleport = varrockTeleport)

    @Test
    fun `a teleport leg starts the plan's teleport`() {
        traveller.teleported = cast
        planned(teleportLeg())
        trip.act(null)
        assertEquals(listOf(varrockTeleport), traveller.teleports)
    }

    @Test
    fun `teleporting says where to while it lasts`() {
        traveller.teleported = cast
        planned(teleportLeg())
        assertEquals(StepOutcome.OnTheWay(UnderWay.Teleporting(varrock)), trip.act(null))
    }

    @Test
    fun `a teleport tells what it spent`() {
        traveller.teleported = cast
        planned(teleportLeg())
        trip.act(null)
        assertEquals(listOf(Said.Teleported(cast)), traveller.said)
    }

    @Test
    fun `a teleport is over once the player lands near its destination`() {
        traveller.teleported = cast
        planned(teleportLeg(), walkLeg(landing, Position(3230, 3423)))
        trip.act(null)
        traveller.position = Position(3214, 3425)
        trip.act(null)
        assertEquals(listOf(Position(3230, 3423)), traveller.walks)
    }

    @Test
    fun `a teleport that cannot be done now plans the trip again`() {
        planned(teleportLeg())
        trip.act(null)
        traveller.plans += CompletableFuture()
        trip.act(null)
        assertEquals(2, traveller.planned.size)
    }

    @Test
    fun `the same teleport turned down again from the same tile blocks`() {
        planned(teleportLeg())
        planned(teleportLeg())
        trip.act(null)
        assertEquals(StepOutcome.Blocked(Reason.NoWay(Place.TILE)), trip.act(null))
    }

    @Test
    fun `a teleport leg without its teleport blocks`() {
        planned(PlanLeg(EdgeType.TELEPORT, start, landing, cost = 5.0))
        assertEquals(StepOutcome.Blocked(Reason.NoWay(Place.TILE)), trip.act(null))
    }

    @Test
    fun `a fairy ring leg has no way yet`() {
        planned(PlanLeg(EdgeType.FAIRY_RING, start, landing, cost = 5.0))
        assertEquals(StepOutcome.Blocked(Reason.NoWay(Place.TILE)), trip.act(null))
    }

    private val tollGate = 2882
    private val gateTile = Position(3268, 3227)
    private val lumbridgeSide = Position(3267, 3227)
    private val eastOfGate = Position(3272, 3227)

    private val guardsQuestion = listOf("No thank you, I'll walk around.", "Who does my money go to?", "Yes, ok.")

    private fun tollLeg(handler: String? = "border_gate", id: Int? = tollGate) =
        PlanLeg(EdgeType.CROSSING, gateTile, lumbridgeSide, cost = 8.0, EdgeAction(objectId = id, objectPosition = gateTile, handler = handler))

    @Test
    fun `the toll gate is clicked from the side the trip comes from`() {
        traveller.position = eastOfGate
        planned(tollLeg())
        trip.act(null)
        assertEquals(listOf(Triple(tollGate, gateTile, null as String?)) to listOf(gateTile), traveller.used to traveller.sides)
    }

    @Test
    fun `clicking the toll gate says the toll is being paid`() {
        traveller.position = eastOfGate
        planned(tollLeg())
        assertEquals(StepOutcome.OnTheWay(UnderWay.PayingToll), trip.act(null))
    }

    @Test
    fun `the guard's lines are clicked on, each from the same tile`() {
        traveller.position = eastOfGate
        planned(tollLeg())
        traveller.talking = true
        trip.act(null)
        assertEquals(StepOutcome.OnTheWay(UnderWay.PayingToll) to 2, trip.act(null) to traveller.talkedOn)
    }

    @Test
    fun `the toll is agreed to by the guard's words`() {
        traveller.position = eastOfGate
        planned(tollLeg())
        traveller.choosing = true
        traveller.options = guardsQuestion
        trip.act(null)
        assertEquals(listOf(3), traveller.chosen)
    }

    @Test
    fun `agreeing to the toll tells what it costs`() {
        traveller.position = eastOfGate
        planned(tollLeg())
        traveller.choosing = true
        traveller.options = guardsQuestion
        trip.act(null)
        assertEquals(listOf(Said.PaidToll(10)), traveller.said)
    }

    @Test
    fun `the toll is told once a gate, however often it is agreed to`() {
        traveller.position = eastOfGate
        planned(tollLeg())
        traveller.choosing = true
        traveller.options = guardsQuestion
        trip.act(null)
        trip.act(null)
        assertEquals(listOf(Said.PaidToll(10)) to listOf(3, 3), traveller.said to traveller.chosen)
    }

    @Test
    fun `a question at the toll gate without the guard's words is no way through`() {
        traveller.position = eastOfGate
        planned(tollLeg())
        traveller.choosing = true
        traveller.options = listOf("Climb up", "Climb down")
        assertEquals(StepOutcome.Blocked(Reason.Impassable(Passage.GATE)) to emptyList<Int>(), trip.act(null) to traveller.chosen)
    }

    @Test
    fun `a dialogue that never closes blocks once it took more lines than any passage's talk`() {
        traveller.position = eastOfGate
        traveller.talking = true
        val talky = TripWork(traveller, target, Place.TILE, PlannedTrip(WebWalkPlan(listOf(tollLeg()), cost = 8.0)), dialogueLines = 2)
        talky.act(null)
        talky.act(null)
        assertEquals(StepOutcome.Blocked(Reason.Impassable(Passage.GATE)) to 2, talky.act(null) to traveller.talkedOn)
    }

    @Test
    fun `a dialogue's lines are counted afresh after another move`() {
        traveller.position = eastOfGate
        val talky = TripWork(traveller, target, Place.TILE, PlannedTrip(WebWalkPlan(listOf(tollLeg()), cost = 8.0)), dialogueLines = 1)
        traveller.talking = true
        talky.act(null)
        traveller.talking = false
        talky.act(null)
        traveller.talking = true
        assertEquals(StepOutcome.OnTheWay(UnderWay.PayingToll) to 2, talky.act(null) to traveller.talkedOn)
    }

    @Test
    fun `a toll gate that would not let the player through blocks, naming it`() {
        traveller.position = eastOfGate
        planned(tollLeg())
        trip.act(null)
        assertEquals(StepOutcome.Blocked(Reason.Impassable(Passage.GATE)), trip.act(null))
    }

    @Test
    fun `a toll gate is passed once the player stands beyond it`() {
        planned(tollLeg(), walkLeg(lumbridgeSide, Position(3250, 3227)))
        traveller.position = lumbridgeSide
        trip.act(null)
        assertEquals(listOf(Position(3250, 3227)), traveller.walks)
    }

    @Test
    fun `a crossing no handler of ours takes has no way`() {
        traveller.position = eastOfGate
        planned(tollLeg(handler = "ferry"))
        assertEquals(StepOutcome.Blocked(Reason.NoWay(Place.TILE)), trip.act(null))
    }

    @Test
    fun `a crossing that names no handler has no way`() {
        traveller.position = eastOfGate
        planned(PlanLeg(EdgeType.CROSSING, gateTile, lumbridgeSide, cost = 8.0))
        assertEquals(StepOutcome.Blocked(Reason.NoWay(Place.TILE)), trip.act(null))
    }

    @Test
    fun `a crossing with nothing to click has no way`() {
        traveller.position = eastOfGate
        planned(tollLeg(id = null))
        assertEquals(StepOutcome.Blocked(Reason.NoWay(Place.TILE)), trip.act(null))
    }

    private val dock = Position(3028, 3218)
    private val ashore = Position(2956, 3146)
    private val deck = ShipRoute.PORT_SARIM_TO_KARAMJA.destination
    private val plank = 2082
    private val plankTile = Position(2956, 3144, 1)

    private fun shipLeg(key: String? = "PORT_SARIM_TO_KARAMJA", id: Int? = plank, from: Position = dock, to: Position = ashore) =
        PlanLeg(EdgeType.SHIP, from, to, cost = 18.0, EdgeAction(objectId = id, objectPosition = plankTile, key = key))

    @Test
    fun `boarding a ship starts by talking to its crew by the dock`() {
        traveller.position = dock
        planned(shipLeg())
        trip.act(null)
        assertEquals(listOf(ShipRoute.PORT_SARIM_TO_KARAMJA.crew to dock), traveller.talkedTo)
    }

    @Test
    fun `boarding a ship tells its fare and says where it sails to`() {
        traveller.position = dock
        planned(shipLeg())
        assertEquals(StepOutcome.OnTheWay(UnderWay.Sailing("Karamja")) to listOf(Said.Fare(30, "Karamja")), trip.act(null) to traveller.said)
    }

    @Test
    fun `a free ship tells it takes the ship`() {
        traveller.position = Position(3047, 3235)
        planned(shipLeg(key = "PORT_SARIM_TO_ENTRANA", from = Position(3047, 3235), to = Position(2834, 3335)))
        trip.act(null)
        assertEquals(listOf(Said.Fare(0, "Entrana")), traveller.said)
    }

    @Test
    fun `the fare is told once a ship, however often the crew is talked to`() {
        traveller.position = dock
        planned(shipLeg())
        trip.act(null)
        traveller.position = Position(3029, 3218)
        trip.act(null)
        assertEquals(1 to 2, traveller.said.size to traveller.talkedTo.size)
    }

    @Test
    fun `a ship boarded again on a new plan tells its fare again`() {
        traveller.position = dock
        planned(shipLeg())
        trip.act(null)
        traveller.position = Position(3100, 3300)
        planned(shipLeg(from = Position(3100, 3300)))
        trip.act(null)
        assertEquals(2, traveller.said.size)
    }

    @Test
    fun `no crew by the dock is no way`() {
        traveller.position = dock
        traveller.crewNear = false
        planned(shipLeg())
        assertEquals(StepOutcome.Blocked(Reason.NoWay(Place.TILE)), trip.act(null))
    }

    @Test
    fun `the crew's boarding option is picked by its words`() {
        traveller.position = dock
        traveller.choosing = true
        traveller.options = listOf("Why?", "Search away, I have nothing to hide.", "You're not putting your hands on my things!")
        planned(shipLeg(key = "KARAMJA_TO_PORT_SARIM", from = Position(2953, 3146), to = Position(3029, 3217)))
        traveller.position = Position(2953, 3146)
        trip.act(null)
        assertEquals(listOf(2), traveller.chosen)
    }

    @Test
    fun `a question without a boarding option is no way`() {
        traveller.position = dock
        traveller.choosing = true
        traveller.options = listOf("No, thank you.")
        planned(shipLeg())
        assertEquals(StepOutcome.Blocked(Reason.NoWay(Place.TILE)), trip.act(null))
    }

    @Test
    fun `the crew's lines are clicked on, each from the same tile`() {
        traveller.position = dock
        traveller.talking = true
        planned(shipLeg())
        trip.act(null)
        assertEquals(StepOutcome.OnTheWay(UnderWay.Sailing("Karamja")) to 2, trip.act(null) to traveller.talkedOn)
    }

    @Test
    fun `on the deck at the voyage's end the gangplank is crossed`() {
        planned(walkLeg(start, dock), shipLeg())
        trip.act(null)
        traveller.position = dock
        trip.act(null)
        traveller.position = deck
        trip.act(null)
        assertEquals(listOf(Triple(plank, plankTile, null as String?)) to listOf(deck), traveller.used to traveller.sides)
    }

    @Test
    fun `on the deck the trip is still on its plan`() {
        planned(walkLeg(start, dock), shipLeg())
        trip.act(null)
        traveller.position = dock
        trip.act(null)
        traveller.position = deck
        trip.act(null)
        assertEquals(1, traveller.planned.size)
    }

    @Test
    fun `a gangplank that would not take the player ashore is no way`() {
        traveller.position = deck
        planned(shipLeg(from = deck))
        trip.act(null)
        assertEquals(StepOutcome.Blocked(Reason.NoWay(Place.TILE)), trip.act(null))
    }

    @Test
    fun `ashore at the voyage's end the ship is over`() {
        planned(shipLeg(), walkLeg(ashore, Position(2940, 3146)))
        traveller.position = Position(2957, 3147)
        trip.act(null)
        assertEquals(listOf(Position(2940, 3146)), traveller.walks)
    }

    @Test
    fun `a ship Luna has no route for is no way`() {
        traveller.position = dock
        planned(shipLeg(key = "PORT_SARIM_TO_CRANDOR"))
        assertEquals(StepOutcome.Blocked(Reason.NoWay(Place.TILE)), trip.act(null))
    }

    @Test
    fun `a player far from a ship's dock and off its deck plans again`() {
        planned(walkLeg(start, dock), shipLeg())
        trip.act(null)
        traveller.position = dock
        trip.act(null)
        traveller.position = Position(3100, 3300)
        traveller.plans += CompletableFuture()
        trip.act(null)
        assertEquals(2, traveller.planned.size)
    }

    @Test
    fun `a ship that names no route is no way`() {
        traveller.position = dock
        planned(shipLeg(key = null))
        assertEquals(StepOutcome.Blocked(Reason.NoWay(Place.TILE)), trip.act(null))
    }

    @Test
    fun `a ship with nothing known of it is no way`() {
        traveller.position = dock
        planned(PlanLeg(EdgeType.SHIP, dock, ashore, cost = 18.0))
        assertEquals(StepOutcome.Blocked(Reason.NoWay(Place.TILE)), trip.act(null))
    }

    @Test
    fun `a ship with no gangplank is no way`() {
        traveller.position = dock
        planned(shipLeg(id = null))
        assertEquals(StepOutcome.Blocked(Reason.NoWay(Place.TILE)), trip.act(null))
    }

    @Test
    fun `a trip that took the long way round tells each choice a quicker one needed`() {
        traveller.plans += CompletableFuture.completedFuture(PlannedTrip(WebWalkPlan(listOf(walkLeg(start, target.toPosition())), cost = 1.0), setOf(TravelChoice.RUNES, TravelChoice.COINS)))
        trip.act(null)
        assertEquals(listOf(Said.LongWay(TravelChoice.COINS), Said.LongWay(TravelChoice.RUNES)), traveller.said)
    }

    @Test
    fun `a trip given a long way round tells it`() {
        TripWork(traveller, target, Place.TILE, PlannedTrip(WebWalkPlan(listOf(walkLeg(start, target.toPosition())), cost = 1.0), setOf(TravelChoice.CHARGES)))
        assertEquals(listOf(Said.LongWay(TravelChoice.CHARGES)), traveller.said)
    }

    @Test
    fun `a trip on the quickest way says nothing`() {
        planned(walkLeg(start, target.toPosition()))
        trip.act(null)
        assertEquals(emptyList<Said>(), traveller.said)
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
    fun `a trip only a Travel choice would make names it in its block`() {
        traveller.plans += CompletableFuture.completedFuture(NoTrip(TravelChoice.COINS))
        assertEquals(StepOutcome.Blocked(Reason.NoWay(Place.TILE, TravelChoice.COINS)), trip.act(null))
    }

    @Test
    fun `a trip blocked for want of a Travel choice still names it from the same tile`() {
        traveller.plans += CompletableFuture.completedFuture(NoTrip(TravelChoice.RUNES))
        trip.act(null)
        assertEquals(StepOutcome.Blocked(Reason.NoWay(Place.TILE, TravelChoice.RUNES)), trip.act(null))
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
