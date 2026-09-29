package game.idle.autopilot

import game.idle.IdleState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AutopilotTest {

    private val scheduler = FakeTickScheduler()
    private val activity = FakeActivity()
    private val autopilot = Autopilot<FakeAutopilotPlayer>(scheduler) { AutopilotDriver(activity, decisionDelayTicks = 1) }

    private val switchedOff = FakeAutopilotPlayer("maxime")
    private val switchedOn = FakeAutopilotPlayer("maxime", IdleState(autopilotEnabled = true))

    @Test
    fun `toggling on saves the switch`() {
        autopilot.toggle(switchedOff)

        assertTrue(switchedOff.idleState.autopilotEnabled)
    }

    @Test
    fun `toggling on starts the autopilot`() {
        autopilot.toggle(switchedOff)

        assertEquals(1, scheduler.activeCount)
    }

    @Test
    fun `toggling on tells the player`() {
        autopilot.toggle(switchedOff)

        assertEquals(listOf("Autopilot enabled."), switchedOff.told)
    }

    @Test
    fun `toggling off saves the switch`() {
        autopilot.toggle(switchedOn)

        assertFalse(switchedOn.idleState.autopilotEnabled)
    }

    @Test
    fun `toggling off stops the autopilot`() {
        autopilot.onLogin(switchedOn)

        autopilot.toggle(switchedOn)

        assertEquals(0, scheduler.activeCount)
    }

    @Test
    fun `toggling off tells the player`() {
        autopilot.toggle(switchedOn)

        assertEquals(listOf("Autopilot disabled."), switchedOn.told)
    }

    @Test
    fun `logging in with the switch on starts the autopilot`() {
        autopilot.onLogin(switchedOn)

        assertEquals(1, scheduler.activeCount)
    }

    @Test
    fun `logging in with the switch off leaves the autopilot stopped`() {
        autopilot.onLogin(switchedOff)

        assertEquals(0, scheduler.activeCount)
    }

    @Test
    fun `logging in reads the switch even when it is off`() {
        autopilot.onLogin(switchedOff)

        assertTrue(switchedOff.stateReads > 0)
    }

    @Test
    fun `a second start for a running player keeps a single autopilot`() {
        autopilot.onLogin(switchedOn)

        autopilot.onLogin(switchedOn)

        assertEquals(1, scheduler.activeCount)
    }

    @Test
    fun `logging out stops the autopilot`() {
        autopilot.onLogin(switchedOn)

        autopilot.onLogout(switchedOn)

        assertEquals(0, scheduler.activeCount)
    }

    @Test
    fun `logging out a player without an autopilot is harmless`() {
        autopilot.onLogout(switchedOff)

        assertEquals(0, scheduler.activeCount)
    }

    @Test
    fun `a running autopilot drives its activity every game tick`() {
        autopilot.onLogin(switchedOn)

        scheduler.tick()

        assertEquals(1, activity.steps)
    }

    @Test
    fun `each player gets an autopilot of their own`() {
        autopilot.onLogin(switchedOn)

        autopilot.onLogin(FakeAutopilotPlayer("zezima", IdleState(autopilotEnabled = true)))

        assertEquals(2, scheduler.activeCount)
    }
}
