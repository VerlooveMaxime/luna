package game.idle.flow

import game.idle.location.Tile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WorkSpotTest {

    private val runTile = Tile(3200, 3200)

    @Test
    fun `the run tile work spot is wherever the flow was started`() {
        assertEquals(runTile, WorkSpot.RunTile.tile(runTile))
    }

    @Test
    fun `a walked-to work spot is its own tile`() {
        assertEquals(Tile(3086, 3233), WorkSpot.At(Tile(3086, 3233)).tile(runTile))
    }
}
