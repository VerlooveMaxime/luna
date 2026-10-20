package game.harness

import io.luna.game.model.Position
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class MoveCommandTest {

    @Test
    fun `a move with two numbers keeps the player's floor`() {
        assertEquals(Position(3222, 3218, 1), moveTarget("::move 3222 3218", floor = 1))
    }

    @Test
    fun `a move with three numbers goes to that floor`() {
        assertEquals(Position(3103, 3509, 0), moveTarget("move 3103 3509 0", floor = 2))
    }

    @Test
    fun `a move with four numbers keeps the player's floor, as Luna's does`() {
        assertEquals(Position(3222, 3218, 1), moveTarget("move 3222 3218 0 5", floor = 1))
    }

    @Test
    fun `the command name is read in any case, around spaces`() {
        assertEquals(Position(3222, 3218, 0), moveTarget("  ::MOVE 3222 3218 ", floor = 0))
    }

    @Test
    fun `another command is no move`() {
        assertNull(moveTarget("::item 1351 1", floor = 0))
    }

    @Test
    fun `a command whose name only starts with move is no move`() {
        assertNull(moveTarget("::moveto 3222 3218", floor = 0))
    }

    @Test
    fun `a move with nothing after it moves nobody`() {
        assertNull(moveTarget("::move", floor = 0))
    }

    @Test
    fun `a move whose x is no number moves nobody`() {
        assertNull(moveTarget("::move here 3218", floor = 0))
    }

    @Test
    fun `a move without its y moves nobody`() {
        assertNull(moveTarget("::move 3222", floor = 0))
    }

    @Test
    fun `a move whose floor is no number moves nobody`() {
        assertNull(moveTarget("::move 3222 3218 up", floor = 0))
    }
}
