package game.idle.autopilot

import game.idle.AutopilotJob
import game.idle.IdleState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AutopilotTest {

    private val job = AutopilotJob("varrock_west", listOf("normal"))
    private val goneJob = AutopilotJob("atlantis", listOf("normal"))

    private val scheduler = FakeTickScheduler()
    private val activity = FakeActivity()
    private val autopilot = Autopilot<FakeAutopilotPlayer>(scheduler) { _, requested ->
        if (requested == goneJob) null else AutopilotDriver(activity, decisionDelayTicks = 1)
    }

    private val idle = FakeAutopilotPlayer("maxime")
    private val chopping = FakeAutopilotPlayer("maxime", IdleState(job = job))

    @Test
    fun `starting saves the job`() {
        autopilot.start(idle, job)

        assertEquals(job, idle.idleState.job)
    }

    @Test
    fun `starting runs the autopilot`() {
        assertTrue(autopilot.start(idle, job))
        assertTrue(autopilot.isRunning(idle))
        assertEquals(1, scheduler.activeCount)
    }

    @Test
    fun `starting again replaces the running autopilot`() {
        autopilot.start(idle, job)

        autopilot.start(idle, job.copy(trees = listOf("oak")))

        assertEquals(1, scheduler.activeCount)
        assertEquals(listOf("oak"), idle.idleState.job?.trees)
    }

    @Test
    fun `a job that does not resolve stops the player and is not saved`() {
        autopilot.start(chopping, job)

        assertFalse(autopilot.start(chopping, goneJob))
        assertFalse(autopilot.isRunning(chopping))
        assertNull(chopping.idleState.job)
    }

    @Test
    fun `stopping clears the job`() {
        autopilot.start(idle, job)

        autopilot.stop(idle)

        assertNull(idle.idleState.job)
        assertFalse(autopilot.isRunning(idle))
        assertEquals(0, scheduler.activeCount)
    }

    @Test
    fun `stopping a player without an autopilot is harmless`() {
        autopilot.stop(idle)

        assertEquals(0, scheduler.activeCount)
    }

    @Test
    fun `logging in with a saved job resumes it`() {
        autopilot.onLogin(chopping)

        assertEquals(1, scheduler.activeCount)
        assertEquals(emptyList<String>(), chopping.told)
    }

    @Test
    fun `logging in with a job that no longer resolves tells the player and clears it`() {
        val stale = FakeAutopilotPlayer("maxime", IdleState(job = goneJob))

        autopilot.onLogin(stale)

        assertEquals(0, scheduler.activeCount)
        assertNull(stale.idleState.job)
        assertEquals(listOf("Autopilot: could not resume at 'atlantis'. Use ::idle to start again."), stale.told)
    }

    @Test
    fun `logging in without a job leaves the autopilot stopped`() {
        autopilot.onLogin(idle)

        assertEquals(0, scheduler.activeCount)
    }

    @Test
    fun `logging in reads the state even without a job`() {
        autopilot.onLogin(idle)

        assertTrue(idle.stateReads > 0)
    }

    @Test
    fun `a second login for a running player keeps a single autopilot`() {
        autopilot.onLogin(chopping)

        autopilot.onLogin(chopping)

        assertEquals(1, scheduler.activeCount)
    }

    @Test
    fun `logging out stops the autopilot but keeps the job for next time`() {
        autopilot.onLogin(chopping)

        autopilot.onLogout(chopping)

        assertEquals(0, scheduler.activeCount)
        assertEquals(job, chopping.idleState.job)
    }

    @Test
    fun `logging out a player without an autopilot is harmless`() {
        autopilot.onLogout(idle)

        assertEquals(0, scheduler.activeCount)
    }

    @Test
    fun `a running autopilot drives its activity every game tick`() {
        autopilot.onLogin(chopping)

        scheduler.tick()

        assertEquals(1, activity.steps)
    }

    @Test
    fun `each player gets an autopilot of their own`() {
        autopilot.onLogin(chopping)

        autopilot.onLogin(FakeAutopilotPlayer("zezima", IdleState(job = job)))

        assertEquals(2, scheduler.activeCount)
    }
}
