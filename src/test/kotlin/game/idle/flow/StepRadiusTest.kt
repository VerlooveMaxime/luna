package game.idle.flow

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class StepRadiusTest {

    private fun within(text: String) = StepSettings("chop", mapOf("within" to text))

    @Test
    fun `a step without a radius looks within the default`() {
        assertEquals(10, StepRadius.read(StepSettings("chop")))
    }

    @Test
    fun `a step looks within its radius`() {
        assertEquals(25, StepRadius.read(within("25")))
    }

    @Test
    fun `a radius is a number of tiles from 1 to 32`() {
        assertRejected("within takes 1 to 32 tiles, not 'far'") { StepRadius.read(within("far")) }
        assertRejected("within takes 1 to 32 tiles, not '0'") { StepRadius.read(within("0")) }
        assertRejected("within takes 1 to 32 tiles, not '33'") { StepRadius.read(within("33")) }
    }

    @Test
    fun `the default radius is left out of a summary, any other is written`() {
        assertEquals(
            listOf("", "", " within 5"),
            listOf(StepRadius.suffix(StepSettings("chop")), StepRadius.suffix(within("10")), StepRadius.suffix(within("5"))),
        )
    }

    @Test
    fun `the configure screen types the radius on the right, from 1 to 32 tiles`() {
        assertEquals(listOf("Within (right): typed within 1..32"), described(listOf(StepRadius.field())))
    }

    @Test
    fun `the configure screen shows the radius in tiles, the default without one`() {
        assertEquals(listOf("5 tiles", "10 tiles"), listOf(StepRadius.field().shown("5"), StepRadius.field().shown(null)))
    }

    @Test
    fun `a typed radius out of range is refused with its rule`() {
        assertEquals("within takes 1 to 32 tiles", StepRadius.field().rule)
    }

    private fun assertRejected(message: String, action: () -> Unit) {
        assertEquals(message, assertThrows<FlowError> { action() }.message)
    }
}
