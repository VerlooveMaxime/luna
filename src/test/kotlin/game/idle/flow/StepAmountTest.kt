package game.idle.flow

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class StepAmountTest {

    private fun amount(text: String) = StepSettings("chop", mapOf("amount" to text))

    @Test
    fun `a step with an amount has that count`() {
        assertEquals(5, StepAmount.read(amount("5")))
    }

    @Test
    fun `a step without an amount does as much as it can`() {
        assertNull(StepAmount.read(StepSettings("chop")))
    }

    @Test
    fun `a count is 1 to 1000`() {
        assertEquals("A step's count is 1 to 1000, not '0'", assertThrows<FlowError> { StepAmount.read(amount("0")) }.message)
        assertEquals("A step's count is 1 to 1000, not '1001'", assertThrows<FlowError> { StepAmount.read(amount("1001")) }.message)
        assertEquals("A step's count is 1 to 1000, not 'lots'", assertThrows<FlowError> { StepAmount.read(amount("lots")) }.message)
    }

    @Test
    fun `a summary writes a count before the resource and nothing for as much as it can`() {
        assertEquals(listOf("5 ", ""), listOf(StepAmount.prefix(amount("5")), StepAmount.prefix(StepSettings("chop"))))
    }

    @Test
    fun `the builder offers no count first, shown as the step's own word, then a few counts`() {
        val field = StepAmount.field(unbounded = "full")

        assertEquals(listOf("", "1", "5", "10"), field.choices(StepSettings("chop")))
        assertEquals(listOf("full", "5"), listOf(field.display(""), field.display("5")))
    }

    @Test
    fun `a slot reads an amount per lap`() {
        assertEquals("5 per lap", StepAmount.detail(amount("5"), unbounded = "all of them"))
    }

    @Test
    fun `a slot reads an amount with what it counts`() {
        assertEquals("5 kills per lap", StepAmount.detail(amount("5"), unbounded = "no end", counted = "kills"))
    }

    @Test
    fun `a slot reads no amount as the step's own word`() {
        assertEquals("no end", StepAmount.detail(StepSettings("fight"), unbounded = "no end", counted = "kills"))
    }
}
