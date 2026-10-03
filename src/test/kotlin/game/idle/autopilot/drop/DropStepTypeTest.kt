package game.idle.autopilot.drop

import game.idle.flow.FlowContext
import game.idle.flow.FlowError
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class DropStepTypeTest {

    @Test
    fun `drop has no values and reads back as drop`() {
        assertEquals(emptyList<String>(), DropStepType.parse(emptyList()))
        assertEquals("drop", DropStepType.line(emptyList()))
    }

    @Test
    fun `drop takes nothing`() {
        val error = assertThrows<FlowError> { DropStepType.parse(listOf("logs")) }

        assertEquals("drop takes nothing after it: it drops what the chop steps before it gathered", error.message)
    }

    @Test
    fun `drop drops what the steps before it gathered`() {
        assertEquals(DropStep(setOf(1511, 1519)), DropStepType.resolve(emptyList(), FlowContext(gathered = setOf(1511, 1519))))
    }

    @Test
    fun `drop before anything was gathered is rejected`() {
        val error = assertThrows<FlowError> { DropStepType.resolve(emptyList(), FlowContext()) }

        assertEquals("drop comes after a chop step, so the flow knows what to drop", error.message)
    }

    @Test
    fun `the steps after a drop step know what the steps before it knew`() {
        val context = FlowContext(gathered = setOf(1511))

        assertEquals(context, DropStep(setOf(1511)).after(context))
    }
}
