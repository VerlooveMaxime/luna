package game.idle.autopilot

import game.idle.IdleState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AutopilotTest {

    private val scheduler = FakeTickScheduler()
    private val activity = FakeActivity()
    private val autopilot = Autopilot<FakeAutopilotPlayer>(scheduler) { player ->
        if ("broken" in player.idleState.flow) null else AutopilotDriver(activity, decisionDelayTicks = 1)
    }

    private val idle = FakeAutopilotPlayer("maxime", IdleState(flow = listOf("loop")))
    private val chopping = FakeAutopilotPlayer("maxime", IdleState(flow = listOf("loop"), running = true))
    private val broken = FakeAutopilotPlayer("maxime", IdleState(flow = listOf("broken"), running = true))

    @Test
    fun `starting saves the switch and runs the autopilot`() {
        assertTrue(autopilot.start(idle))

        assertTrue(idle.idleState.running)
        assertTrue(autopilot.isRunning(idle))
        assertEquals(1, scheduler.activeCount)
    }

    @Test
    fun `starting again replaces the running autopilot`() {
        autopilot.start(idle)

        autopilot.start(idle)

        assertEquals(1, scheduler.activeCount)
    }

    @Test
    fun `a flow that does not resolve leaves the player stopped`() {
        autopilot.start(broken)

        assertFalse(autopilot.start(broken))
        assertFalse(autopilot.isRunning(broken))
        assertFalse(broken.idleState.running)
    }

    @Test
    fun `stopping clears the switch`() {
        autopilot.start(idle)

        autopilot.stop(idle)

        assertFalse(idle.idleState.running)
        assertFalse(autopilot.isRunning(idle))
        assertEquals(0, scheduler.activeCount)
    }

    @Test
    fun `stopping a player without an autopilot is harmless`() {
        autopilot.stop(idle)

        assertEquals(0, scheduler.activeCount)
    }

    @Test
    fun `logging in with the switch on resumes`() {
        autopilot.onLogin(chopping)

        assertEquals(1, scheduler.activeCount)
        assertEquals(emptyList<String>(), chopping.told)
    }

    @Test
    fun `logging in with a flow that no longer resolves tells the player`() {
        autopilot.onLogin(broken)

        assertEquals(0, scheduler.activeCount)
        assertFalse(broken.idleState.running)
        assertEquals(listOf("Autopilot: could not resume your flow. Check it with ::flow list."), broken.told)
    }

    @Test
    fun `logging in with the switch off leaves the autopilot stopped`() {
        autopilot.onLogin(idle)

        assertEquals(0, scheduler.activeCount)
    }

    @Test
    fun `logging in reads the state even when off`() {
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
    fun `logging out stops the ticks but keeps the switch for next time`() {
        autopilot.onLogin(chopping)

        autopilot.onLogout(chopping)

        assertEquals(0, scheduler.activeCount)
        assertTrue(chopping.idleState.running)
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

        autopilot.onLogin(FakeAutopilotPlayer("zezima", IdleState(flow = listOf("loop"), running = true)))

        assertEquals(2, scheduler.activeCount)
    }
}
