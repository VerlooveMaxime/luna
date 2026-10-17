package game.idle.autopilot

import api.bot.webwalk.model.EdgeType
import game.idle.flow.Aim
import game.idle.flow.Passage
import game.idle.flow.StepOutcome
import game.idle.flow.UnderWay
import game.idle.location.Tile
import game.idle.movement.DoorStep
import game.idle.movement.Doorway
import io.luna.game.model.Position
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CandidateTest {

    private fun candidate(x: Int, y: Int, distance: Int, usableFromHere: Boolean = false) =
        Candidate(id = 1276, Position(x, y), distance, usableFromHere, approach = Position(x - 1, y))

    private fun behind(type: EdgeType, shut: Boolean) =
        candidate(3205, 3200, distance = 11).copy(door = DoorStep(Doorway(1530, Position(3203, 3200), type), Position(3202, 3200), shut))

    private fun nearestFirst(vararg candidates: Candidate): List<Candidate> = candidates.sortedWith(Candidate.NEAREST_FIRST)

    @Test
    fun `the aim is the target's id on its tile`() {
        assertEquals(Aim(1276, Tile(3201, 3200)), candidate(3201, 3200, distance = 2).aim)
    }

    @Test
    fun `a candidate neither attacks nor fails to fit unless said`() {
        val candidate = candidate(3201, 3200, distance = 2)

        assertEquals(false to true, candidate.attacking to candidate.fits)
    }

    @Test
    fun `a walk up to a target with no door on the way is a plain approach`() {
        assertEquals(StepOutcome.Approached, candidate(3201, 3200, distance = 2).approached)
    }

    @Test
    fun `a walk up through a shut door says it opens the door`() {
        assertEquals(StepOutcome.OnTheWay(UnderWay.Opening(Passage.DOOR)), behind(EdgeType.DOOR, shut = true).approached)
    }

    @Test
    fun `a walk up through an open door says it goes through the door`() {
        assertEquals(StepOutcome.OnTheWay(UnderWay.Passing(Passage.DOOR)), behind(EdgeType.DOOR, shut = false).approached)
    }

    @Test
    fun `a gate on the way is named a gate`() {
        assertEquals(StepOutcome.OnTheWay(UnderWay.Opening(Passage.GATE)), behind(EdgeType.GATE, shut = true).approached)
    }

    @Test
    fun `a curtain on the way is named a curtain`() {
        assertEquals(StepOutcome.OnTheWay(UnderWay.Passing(Passage.CURTAIN)), behind(EdgeType.CURTAIN, shut = false).approached)
    }

    @Test
    fun `one usable from here comes before an equally near one that is not`() {
        val usable = candidate(3210, 3200, distance = 0, usableFromHere = true)
        val near = candidate(3201, 3200, distance = 0)

        assertEquals(listOf(usable, near), nearestFirst(near, usable))
    }

    @Test
    fun `the nearest by walking comes first`() {
        val near = candidate(3205, 3200, distance = 3)
        val far = candidate(3201, 3200, distance = 8)

        assertEquals(listOf(near, far), nearestFirst(far, near))
    }

    @Test
    fun `equally near candidates come west to east`() {
        val west = candidate(3198, 3203, distance = 3)
        val east = candidate(3203, 3198, distance = 3)

        assertEquals(listOf(west, east), nearestFirst(east, west))
    }

    @Test
    fun `equally near candidates on one column come south to north`() {
        val south = candidate(3203, 3197, distance = 3)
        val north = candidate(3203, 3203, distance = 3)

        assertEquals(listOf(south, north), nearestFirst(north, south))
    }
}
