package game.idle.tutorial

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LessonGoalTest {

    private val chop = StepSummary("chop", 1)
    private val light = StepSummary("light", null)
    private val chopAndLight = LessonGoal(running = true, steps = listOf(StepSpec("chop", 1), StepSpec("light", anyCount = true)), laps = 1)
    private val stopped = LessonGoal(running = false)
    private val idle = FlowProgress(running = false, steps = emptyList(), laps = 0)

    @Test
    fun `a flow line reads as its keyword and count`() {
        assertEquals(StepSummary("chop", 1), StepSummary.of("  Chop 1   normal within 5"))
        assertEquals(StepSummary("chop", null), StepSummary.of("chop normal"))
        assertEquals(StepSummary("light", null), StepSummary.of("light"))
    }

    @Test
    fun `a goal step reads as any count, a count, or no count`() {
        assertEquals(StepSpec("make", anyCount = true), StepSpec.of("make"))
        assertEquals(StepSpec("make", 5), StepSpec.of("Make 5"))
        assertEquals(StepSpec("make", null), StepSpec.of("make all"))
    }

    @Test
    fun `a spec with a count wants that count`() {
        assertTrue(StepSpec("chop", 1).matches(chop))
        assertFalse(StepSpec("chop", 1).matches(StepSummary("chop", 5)))
        assertFalse(StepSpec("chop", 1).matches(StepSummary("chop", null)))
    }

    @Test
    fun `a spec of all wants no count`() {
        assertTrue(StepSpec("make", null).matches(StepSummary("make", null)))
        assertFalse(StepSpec("make", null).matches(StepSummary("make", 1)))
    }

    @Test
    fun `a spec without a count takes any, but only its own keyword`() {
        assertTrue(StepSpec("chop", anyCount = true).matches(chop))
        assertTrue(StepSpec("chop", anyCount = true).matches(StepSummary("chop", null)))
        assertFalse(StepSpec("chop", anyCount = true).matches(light))
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
    fun `a lesson goal reads the autopilot out of the player's progress`() {
        assertTrue(stopped.met(PlayerProgress(idle)))
    }

    @Test
    fun `carrying the item meets a carrying goal`() {
        val progress = PlayerProgress(idle, carried = setOf(2307))

        assertTrue(StepGoal.Carries(2307).met(progress))
        assertFalse(StepGoal.Carries(2309).met(progress))
    }

    @Test
    fun `run turned on meets the run goal`() {
        assertTrue(StepGoal.RunOn.met(PlayerProgress(idle, runOn = true)))
        assertFalse(StepGoal.RunOn.met(PlayerProgress(idle, runOn = false)))
    }

    @Test
    fun `a stopped autopilot meets a stopped goal, a running one does not`() {
        assertTrue(stopped.met(FlowProgress(running = false, steps = listOf(chop), laps = 0)))
        assertFalse(stopped.met(FlowProgress(running = true, steps = emptyList(), laps = 0)))
    }
}
