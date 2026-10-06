package game.idle.tutorial

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LessonGoalTest {

    private val chop = StepSummary("chop", 1)
    private val light = StepSummary("light", 1)
    private val chopAndLight = LessonGoal(running = true, steps = listOf(StepSummary("chop", 1), StepSummary("light", null)), laps = 1)
    private val stopped = LessonGoal(running = false)

    @Test
    fun `a flow line or a goal step reads as its keyword and count`() {
        assertEquals(StepSummary("chop", 1), StepSummary.of("  Chop 1   normal within 5"))
        assertEquals(StepSummary("chop", null), StepSummary.of("chop normal"))
        assertEquals(StepSummary("light", null), StepSummary.of("light"))
    }

    @Test
    fun `a step matches a spec with its keyword and count, or with its keyword when the spec has no count`() {
        assertTrue(chop.matches(StepSummary("chop", 1)))
        assertTrue(chop.matches(StepSummary("chop", null)))
        assertFalse(StepSummary("chop", null).matches(StepSummary("chop", 1)))
        assertFalse(chop.matches(StepSummary("chop", 5)))
        assertFalse(chop.matches(StepSummary("light", 1)))
    }

    @Test
    fun `a running flow with the steps and the laps meets the goal`() {
        assertTrue(chopAndLight.met(FlowProgress(running = true, steps = listOf(StepSummary("walk", null), chop, light), laps = 1)))
    }

    @Test
    fun `a flow whose chop has another count does not`() {
        assertFalse(chopAndLight.met(FlowProgress(running = true, steps = listOf(StepSummary("chop", null), light), laps = 4)))
    }

    @Test
    fun `a flow missing a step does not`() {
        assertFalse(chopAndLight.met(FlowProgress(running = true, steps = listOf(chop), laps = 4)))
    }

    @Test
    fun `a flow short of its laps does not`() {
        assertFalse(chopAndLight.met(FlowProgress(running = true, steps = listOf(chop, light), laps = 0)))
    }

    @Test
    fun `a stopped flow does not meet a running goal`() {
        assertFalse(chopAndLight.met(FlowProgress(running = false, steps = listOf(chop, light), laps = 1)))
    }

    @Test
    fun `a stopped autopilot meets a stopped goal, a running one does not`() {
        assertTrue(stopped.met(FlowProgress(running = false, steps = listOf(chop), laps = 0)))
        assertFalse(stopped.met(FlowProgress(running = true, steps = emptyList(), laps = 0)))
    }
}
