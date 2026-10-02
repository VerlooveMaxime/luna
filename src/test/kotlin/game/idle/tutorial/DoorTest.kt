package game.idle.tutorial

import io.luna.game.model.Position
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DoorTest {

    private val leaf = DoorLeaf(3014, Position(3205, 3200), WallSide.WEST)
    private val gate = TutorialFixtures.gate

    @Test
    fun `the tile across a west wall is the one to the west`() {
        assertEquals(Position(3204, 3200), leaf.across)
    }

    @Test
    fun `the tile across a north wall is the one to the north`() {
        assertEquals(Position(3205, 3201), leaf.copy(side = WallSide.NORTH).across)
    }

    @Test
    fun `a closed leaf is drawn turned to its wall`() {
        assertEquals(DoorPiece(3014, leaf.tile, WallSide.WEST.rotation), leaf.piece)
    }

    @Test
    fun `going through from the leaf's tile ends across the wall`() {
        assertEquals(leaf.across, leaf.destination(leaf.tile))
    }

    @Test
    fun `going through from across the wall ends on the leaf's tile`() {
        assertEquals(leaf.tile, leaf.destination(leaf.across))
    }

    @Test
    fun `opening takes every closed leaf away and draws the open pieces`() {
        assertEquals(gate.closed.map { it.piece } to gate.open, gate.change(open = true))
    }

    @Test
    fun `shutting takes the open pieces away and draws the closed leaves again`() {
        assertEquals(gate.open to gate.closed.map { it.piece }, gate.change(open = false))
    }
}
