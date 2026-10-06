package game.idle.flow

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class StepRadiusTest {

    private val usage = "chop [<n>] <tree> [within <r>]"

    @Test
    fun `no words after the resource is the default radius`() {
        assertEquals(10, StepRadius.parse(emptyList(), after = "tree", usage))
    }

    @Test
    fun `within sets the radius`() {
        assertEquals(25, StepRadius.parse(listOf("within", "25"), after = "tree", usage))
    }

    @Test
    fun `anything else after the resource is rejected with the usage`() {
        assertRejected("Unexpected 'within' after the tree: $usage") { StepRadius.parse(listOf("within"), after = "tree", usage) }
        assertRejected("Unexpected 'near 5' after the tree: $usage") { StepRadius.parse(listOf("near", "5"), after = "tree", usage) }
        assertRejected("Unexpected 'a b c' after the tree: $usage") { StepRadius.parse(listOf("a", "b", "c"), after = "tree", usage) }
    }

    @Test
    fun `a radius is a number of tiles from 1 to 32`() {
        assertRejected("within takes a number of tiles from 1 to 32, not 'far'") { StepRadius.check("far") }
        assertRejected("within takes a number of tiles from 1 to 32, not '0'") { StepRadius.check("0") }
        assertRejected("within takes a number of tiles from 1 to 32, not '33'") { StepRadius.check("33") }
    }

    @Test
    fun `the default radius is left out of a line, any other is written`() {
        assertEquals(listOf("", " within 5"), listOf(StepRadius.suffix("10"), StepRadius.suffix("5")))
    }

    @Test
    fun `the builder offers a few radii, starting at the default`() {
        val field = StepRadius.field()

        assertEquals(listOf("5", "10", "15", "20", "30"), field.choices(emptyList()))
        assertEquals("10", field.default)
    }

    private fun assertRejected(message: String, action: () -> Unit) {
        assertEquals(message, assertThrows<FlowError> { action() }.message)
    }
}
