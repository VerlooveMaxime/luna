package game.idle.flow

import game.idle.location.Tile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class StepSpotTest {

    private val walkedTo = FlowContext(WorkSpot.At(Tile(3086, 3233)))

    private fun spot(text: String) = StepSettings("chop", mapOf("spot" to text))

    @Test
    fun `a step without a picked spot works where the flow went`() {
        assertEquals(walkedTo.workSpot, StepSpot.of(StepSettings("chop"), walkedTo))
    }

    @Test
    fun `a step with a picked spot works there, its own`() {
        assertEquals(WorkSpot.Own(Tile(3101, 3509)), StepSpot.of(spot("3101 3509"), walkedTo))
    }

    @Test
    fun `a picked spot may be upstairs`() {
        assertEquals(Tile(3101, 3509, 1), StepSpot.read(spot("3101 3509 1")))
    }

    @Test
    fun `no picked spot reads as none`() {
        assertNull(StepSpot.read(StepSettings("chop")))
    }

    @Test
    fun `a picked spot that is no tile is refused`() {
        val error = assertThrows<FlowError> { StepSpot.read(spot("by the yews")) }

        assertEquals("the work spot needs a tile: x y, or x y floor, not 'by the yews'", error.message)
    }

    @Test
    fun `a summary ends with the picked spot, nothing for from the flow`() {
        assertEquals(listOf(" at 3101 3509", ""), listOf(StepSpot.suffix(spot("3101 3509")), StepSpot.suffix(StepSettings("chop"))))
    }

    @Test
    fun `the configure screen picks the spot on the map on the right, from the flow when none is picked`() {
        assertEquals(listOf("Work spot (right): map spot, none 'From the flow'"), described(listOf(StepSpot.field())))
    }
}
