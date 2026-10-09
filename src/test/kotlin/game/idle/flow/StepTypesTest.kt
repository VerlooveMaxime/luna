package game.idle.flow

import game.idle.flow.FlowContext
import game.idle.flow.FakeStepType.Companion.step
import game.idle.flow.option.FakeNames
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class StepTypesTest {

    private val walk = FakeStepType("walk")
    private val rest = FakeStepType("rest")
    private val types = StepTypes(listOf(walk, rest))

    @Test
    fun `a kind of step is found by its name`() {
        assertSame(rest, types.find("rest"))
    }

    @Test
    fun `an unknown kind finds nothing`() {
        assertNull(types.find("fly"))
    }

    @Test
    fun `a step reads as its kind of step words it`() {
        assertEquals("walk north", types.summary(step("walk", "north")))
    }

    @Test
    fun `a step of an unknown kind reads as its kind`() {
        assertEquals("fly", types.summary(step("fly", "away")))
    }

    @Test
    fun `a flow needs a kind of step`() {
        assertThrows<IllegalArgumentException> { StepTypes(emptyList()) }
    }

    @Test
    fun `two kinds of step cannot share a name`() {
        assertThrows<IllegalArgumentException> { StepTypes(listOf(walk, FakeStepType("walk"))) }
    }

    @Test
    fun `a kind of step has no target unless it picks one`() {
        assertNull(rest.target(FakeNames()))
    }

    @Test
    fun `a kind of step shows no details unless it has some`() {
        assertEquals(emptyList<String>(), rest.details(step("rest"), FlowContext()))
    }
}
