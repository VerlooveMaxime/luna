package game.idle.autopilot.travel

import game.idle.flow.EntrancePassage
import game.idle.flow.Way
import game.testworld.TestWorld
import io.luna.game.model.Position
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** On [TestWorld], whose definitions are the cache's. */
class LunaEntrancesTest {

    private val anywhere = Position(3200, 3200)

    @BeforeEach
    fun cache() {
        TestWorld.context
    }

    @Test
    fun `an entrance reads as its first option and its name in the cache`() {
        assertEquals(EntrancePassage("enter", Way.THROUGH, wayNamed = false, "cave entrance"), LunaEntrances.passage(4499, anywhere))
    }

    @Test
    fun `a climb Luna plays the climb-down animation for goes down`() {
        assertEquals(Way.DOWN, LunaEntrances.passage(4383, Position(2509, 3644))?.way)
    }

    @Test
    fun `a climb Luna plays another animation for goes up`() {
        assertEquals(Way.UP, LunaEntrances.passage(5946, anywhere)?.way)
    }

    @Test
    fun `a climb Luna has no entrance for goes up`() {
        assertEquals(Way.UP, LunaEntrances.passage(1748, anywhere)?.way)
    }

    @Test
    fun `an entrance that changes form reads as the form the player sees`() {
        assertEquals(EntrancePassage("open", Way.THROUGH, wayNamed = false, "door"), LunaEntrances.passage(6310, Position(2720, 4913)))
    }

    @Test
    fun `an entrance the player sees no option on reads as none`() {
        assertNull(LunaEntrances.passage(6560, Position(2547, 3421)))
    }
}
