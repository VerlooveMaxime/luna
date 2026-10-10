package game.idle.flow

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FlowIdsTest {

    @Test
    fun `steps without an id get the next ones after the highest, in order`() {
        val steps = listOf(StepSettings("chop"), StepSettings("bank", id = 4), StepSettings("drop"))

        assertEquals(listOf(5, 4, 6), FlowIds.steps(steps).map { it.id })
    }

    @Test
    fun `the first steps of a flow are numbered from 1`() {
        assertEquals(listOf(1, 2), FlowIds.steps(listOf(StepSettings("chop"), StepSettings("drop"))).map { it.id })
    }

    @Test
    fun `steps with ids keep them`() {
        val steps = listOf(StepSettings("chop", id = 2), StepSettings("drop", id = 1))

        assertEquals(steps, FlowIds.steps(steps))
    }

    @Test
    fun `reflexes without an id get the next ones after the highest`() {
        val reflexes = listOf(ReflexSettings(id = 3), ReflexSettings())

        assertEquals(listOf(3, 4), FlowIds.reflexes(reflexes).map { it.id })
    }

    @Test
    fun `no reflexes stay none`() {
        assertEquals(emptyList<ReflexSettings>(), FlowIds.reflexes(emptyList()))
    }
}
