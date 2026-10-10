package game.idle.flow

import game.idle.location.Area
import game.idle.location.Tile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class WorkSpotTest {

    private val runTile = Tile(3200, 3200)
    private val picked = Tile(3101, 3509)

    @Test
    fun `the run tile work spot is wherever the flow was started`() {
        assertEquals(runTile, WorkSpot.RunTile.tile(runTile))
    }

    @Test
    fun `a walked-to work spot is its own tile`() {
        assertEquals(Tile(3086, 3233), WorkSpot.At(Tile(3086, 3233)).tile(runTile))
    }

    @Test
    fun `a step's own work spot is the tile picked`() {
        assertEquals(picked, WorkSpot.Own(picked).tile(runTile))
    }

    @Test
    fun `the run tile is not known before Run`() {
        assertNull(WorkSpot.RunTile.known())
    }

    @Test
    fun `a step's own spot moves the flow there for the steps after it`() {
        assertEquals(FlowContext(WorkSpot.At(picked), gathered = setOf(1511)), WorkSpot.Own(picked).after(FlowContext(gathered = setOf(1511))))
    }

    @Test
    fun `working where the flow went leaves the flow's spot as it is`() {
        val walkedTo = FlowContext(WorkSpot.At(Tile(3086, 3233)))

        assertEquals(walkedTo, WorkSpot.At(Tile(3086, 3233)).after(walkedTo))
    }

    @Test
    fun `a step's own spot gives the area it works in`() {
        assertEquals(Area(picked, 12), WorkSpot.Own(picked).ownArea(12))
    }

    @Test
    fun `a spot that is not the step's own gives no area of its own`() {
        assertEquals(listOf(null, null), listOf(WorkSpot.RunTile.ownArea(12), WorkSpot.At(picked).ownArea(12)))
    }
}
