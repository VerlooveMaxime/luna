package game.idle.autopilot.drop

import game.idle.flow.FlowContext
import game.idle.flow.FlowError
import game.idle.flow.StepIcon
import game.idle.flow.StepSettings
import game.idle.flow.option.FakeNames
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
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

        assertEquals("drop needs a gathering step before it", error.message)
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

    @Test
    fun `a drop step's slot says it drops what was gathered`() {
        assertEquals(listOf("what was gathered"), DropStepType.details(drop, FlowContext()))
    }

    @Test
    fun `a drop step has no target`() {
        assertNull(DropStepType.target(FakeNames()))
    }
}
