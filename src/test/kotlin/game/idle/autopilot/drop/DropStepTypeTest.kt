package game.idle.autopilot.drop

import game.idle.flow.FlowContext
import game.idle.flow.FlowError
import game.idle.flow.StepIcon
import game.idle.flow.StepSettings
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class DropStepTypeTest {

    private val drop = StepSettings("drop")

    @Test
    fun `drop has no settings and reads as drop`() {
        assertEquals(emptyList<Any>(), DropStepType.fields)
        assertEquals("drop", DropStepType.summary(drop))
    }

    @Test
    fun `drop drops what the steps before it gathered`() {
        assertEquals(DropStep(setOf(1511, 1519)), DropStepType.resolve(drop, FlowContext(gathered = setOf(1511, 1519))))
    }

    @Test
    fun `drop before anything was gathered is rejected`() {
        val error = assertThrows<FlowError> { DropStepType.resolve(drop, FlowContext()) }

        assertEquals("drop comes after a chop step, so the flow knows what to drop", error.message)
    }

    @Test
    fun `the steps after a drop step know what the steps before it knew`() {
        val context = FlowContext(gathered = setOf(1511))

        assertEquals(context, DropStep(setOf(1511)).after(context))
    }

    @Test
    fun `a drop step shows the inventory tab's backpack`() {
        assertEquals(StepIcon.Media("sideicons", 3), DropStepType.icon(drop))
    }
}
