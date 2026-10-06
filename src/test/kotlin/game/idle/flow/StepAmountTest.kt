package game.idle.flow

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class StepAmountTest {

    @Test
    fun `a line starting with a number has that count`() {
        assertEquals(5 to listOf("oak"), StepAmount.split(listOf("5", "oak")))
    }

    @Test
    fun `a line starting with a word has no count`() {
        assertEquals(null to listOf("oak"), StepAmount.split(listOf("oak")))
    }

    @Test
    fun `an empty line has no count`() {
        assertEquals(null to emptyList<String>(), StepAmount.split(emptyList()))
    }

    @Test
    fun `a count is 1 to 1000`() {
        assertEquals("A step's count is 1 to 1000, not 0", assertThrows<FlowError> { StepAmount.split(listOf("0")) }.message)
        assertEquals("A step's count is 1 to 1000, not 1001", assertThrows<FlowError> { StepAmount.split(listOf("1001")) }.message)
    }

    @Test
    fun `a field value is a count or the step's word for as much as it can`() {
        assertEquals(listOf("5", "full"), listOf(StepAmount.value(5, "full"), StepAmount.value(null, "full")))
        assertEquals(5, StepAmount.count("5"))
        assertNull(StepAmount.count("full"))
    }

    @Test
    fun `a line writes a count before the resource and nothing for as much as it can`() {
        assertEquals(listOf("5 ", ""), listOf(StepAmount.prefix("5"), StepAmount.prefix("full")))
    }
}
