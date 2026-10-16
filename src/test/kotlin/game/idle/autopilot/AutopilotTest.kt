package game.idle.autopilot

import game.idle.flow.RunProgress
import game.idle.IdleState
import game.idle.flow.FakeClock
import game.idle.flow.StepSettings
import game.idle.flow.StepWords
import game.idle.location.Tile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AutopilotTest {

    private val scheduler = FakeTickScheduler()
    private val clock = FakeClock(now = 1_000)
    private val activity = FakeActivity()
    private val autopilot = Autopilot<FakeAutopilotPlayer>(scheduler, clock) { player ->
        if (BROKEN in player.idleState.steps) null else AutopilotDriver(activity, decisionDelayTicks = 1)
    }

    private val idle = FakeAutopilotPlayer("maxime", IdleState(steps = listOf(LOOP)))
    private val chopping = FakeAutopilotPlayer("maxime", IdleState(steps = listOf(LOOP), running = true))
    private val broken = FakeAutopilotPlayer("maxime", IdleState(steps = listOf(BROKEN), running = true))

    @Test
    fun `starting saves the switch and runs the autopilot`() {
        assertTrue(autopilot.start(idle))

        assertTrue(idle.idleState.running)
        assertTrue(autopilot.isRunning(idle))
        assertEquals(1, scheduler.activeCount)
    }

    @Test
    fun `an activity's reason to stop stops the autopilot`() {
        autopilot.start(idle)
        activity.stop = "Autopilot: stopped, reflex 1 fired at 9/20 hitpoints."

        scheduler.tick()

        assertFalse(autopilot.isRunning(idle))
    }

    @Test
    fun `an activity's reason to stop is told to the player`() {
        autopilot.start(idle)
        activity.stop = "Autopilot: stopped, reflex 1 fired at 9/20 hitpoints."

        scheduler.tick()

        assertEquals(listOf("Autopilot: stopped, reflex 1 fired at 9/20 hitpoints."), idle.told)
    }

    @Test
    fun `an activity's reason to stop saves the flow as stopped`() {
        autopilot.start(idle)
        activity.stop = "Autopilot: stopped, reflex 1 fired at 9/20 hitpoints."

        scheduler.tick()

        assertFalse(idle.idleState.running)
    }

    @Test
    fun `a step's block goes into the state`() {
        autopilot.start(idle)
        activity.blocked = "Needs an axe you can use"

        scheduler.tick()

        assertEquals("Needs an axe you can use", idle.idleState.blocked)
    }

    @Test
    fun `a block that did not change is not written again`() {
        autopilot.start(idle)
        activity.blocked = "Needs an axe you can use"
        scheduler.tick()
        val writes = idle.stateWrites

        scheduler.tick()

        assertEquals(writes, idle.stateWrites)
    }

    @Test
    fun `a block leaves the state once the step goes on`() {
        autopilot.start(idle)
        activity.blocked = "Needs an axe you can use"
        scheduler.tick()
        activity.blocked = null

        scheduler.tick()

        assertEquals(null, idle.idleState.blocked)
    }

    @Test
    fun `a step that starts being blocked says so in the chat box, pointing at its slot`() {
        val second = FakeAutopilotPlayer("maxime", IdleState(steps = listOf(LOOP, LOOP), stepIndex = 1))
        autopilot.start(second)
        activity.blocked = "Needs an axe you can use"

        scheduler.tick()

        assertEquals(listOf("Autopilot: step 2 cannot work yet. See its slot warning in the builder."), second.told)
    }

    @Test
    fun `a block that goes on is told once`() {
        autopilot.start(idle)
        activity.blocked = "Needs an axe you can use"
        scheduler.tick()

        scheduler.tick()

        assertEquals(1, idle.told.size)
    }

    @Test
    fun `another reason while still blocked tells nothing more`() {
        autopilot.start(idle)
        activity.blocked = "Needs an axe you can use"
        scheduler.tick()
        activity.blocked = "The bag is full"

        scheduler.tick()

        assertEquals(1, idle.told.size)
    }

    @Test
    fun `a block that clears tells nothing`() {
        autopilot.start(idle)
        activity.blocked = "Needs an axe you can use"
        scheduler.tick()
        activity.blocked = null

        scheduler.tick()

        assertEquals(1, idle.told.size)
    }

    @Test
    fun `a new block after one cleared is told again`() {
        autopilot.start(idle)
        activity.blocked = "Needs an axe you can use"
        scheduler.tick()
        activity.blocked = null
        scheduler.tick()
        activity.blocked = "Needs an axe you can use"

        scheduler.tick()

        assertEquals(2, idle.told.size)
    }

    @Test
    fun `what a step waits for goes into the state`() {
        autopilot.start(idle)
        activity.waiting = "No tree in reach"

        scheduler.tick()

        assertEquals("No tree in reach", idle.idleState.waiting)
    }

    @Test
    fun `a wait tells nothing in the chat box`() {
        autopilot.start(idle)
        activity.waiting = "No tree in reach"

        scheduler.tick()

        assertEquals(emptyList<String>(), idle.told)
    }

    @Test
    fun `a wait that did not change is not written again`() {
        autopilot.start(idle)
        activity.waiting = "No tree in reach"
        scheduler.tick()
        val writes = idle.stateWrites

        scheduler.tick()

        assertEquals(writes, idle.stateWrites)
    }

    @Test
    fun `a wait leaves the state once the step goes on`() {
        autopilot.start(idle)
        activity.waiting = "No tree in reach"
        scheduler.tick()
        activity.waiting = null

        scheduler.tick()

        assertEquals(null, idle.idleState.waiting)
    }

    @Test
    fun `a block after a wait is told`() {
        autopilot.start(idle)
        activity.waiting = "No tree in reach"
        scheduler.tick()
        activity.waiting = null
        activity.blocked = "Needs an axe you can use"

        scheduler.tick()

        assertEquals(1, idle.told.size)
    }

    @Test
    fun `a flow started without a run tile takes the player's tile`() {
        autopilot.start(idle)

        assertEquals(idle.tile, idle.idleState.runTile)
    }

    @Test
    fun `a flow resuming keeps its run tile`() {
        val resuming = FakeAutopilotPlayer("maxime", IdleState(steps = listOf(LOOP), runTile = Tile(1, 2)))

        autopilot.start(resuming)

        assertEquals(Tile(1, 2), resuming.idleState.runTile)
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
    fun `stopping ends the activity, so it lets go of what it holds`() {
        autopilot.start(idle)

        autopilot.stop(idle)

        assertEquals(1, activity.ended)
    }

    @Test
    fun `an activity's reason to stop ends it before the player is told`() {
        autopilot.start(idle)
        activity.stop = "Autopilot: stopped, you died."
        activity.onEnd = { idle.tell("Autopilot: reflex 1 stops praying Thick skin.") }

        scheduler.tick()

        assertEquals(listOf("Autopilot: reflex 1 stops praying Thick skin.", "Autopilot: stopped, you died."), idle.told)
    }

    @Test
    fun `starting again ends the activity it replaces`() {
        autopilot.start(idle)

        autopilot.start(idle)

        assertEquals(1, activity.ended)
    }

    @Test
    fun `logging out ends the activity`() {
        autopilot.onLogin(chopping)

        autopilot.onLogout(chopping)

        assertEquals(1, activity.ended)
    }

    @Test
    fun `stopping a running flow ends the walk it was taking the player on`() {
        autopilot.start(idle)

        autopilot.stop(idle)

        assertEquals(1, idle.walksEnded)
    }

    @Test
    fun `stopping when no flow runs leaves the player's walk alone`() {
        autopilot.stop(idle)

        assertEquals(0, idle.walksEnded)
    }

    @Test
    fun `an activity's reason to stop leaves its walk going`() {
        autopilot.start(idle)
        activity.stop = "Autopilot: stopped, reflex 1 fired at 9/20 hitpoints."

        scheduler.tick()

        assertEquals(0, idle.walksEnded)
    }

    @Test
    fun `starting again ends the walk of the flow it replaces`() {
        autopilot.start(idle)

        autopilot.start(idle)

        assertEquals(1, idle.walksEnded)
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
        assertEquals(listOf(StepWords.CANNOT_RESUME), broken.told)
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

        autopilot.onLogin(FakeAutopilotPlayer("zezima", IdleState(steps = listOf(LOOP), running = true)))

        assertEquals(2, scheduler.activeCount)
    }

    private companion object {
        val LOOP = StepSettings("loop")
        val BROKEN = StepSettings("broken")
    }

    @Test
    fun `the driver is built from the run as started, so it reads the start time`() {
        var seen = -1L
        val autopilot = Autopilot<FakeAutopilotPlayer>(scheduler, clock) { player -> seen = player.idleState.progress.startedAt; AutopilotDriver(activity, decisionDelayTicks = 1) }

        autopilot.start(idle)

        assertEquals(1_000L, seen)
    }

    @Test
    fun `a flow that does not resolve is left stopped`() {
        autopilot.start(broken)

        assertEquals(false to false, broken.idleState.running to autopilot.isRunning(broken))
    }

    @Test
    fun `a fresh run starts on the clock's time`() {
        autopilot.start(idle)

        assertEquals(1_000L, idle.idleState.progress.startedAt)
    }

    @Test
    fun `a resumed run keeps the time it started at`() {
        val resuming = FakeAutopilotPlayer("maxime", IdleState(steps = listOf(LOOP), running = true, runTile = Tile(3200, 3200), progress = RunProgress(startedAt = 5)))

        autopilot.start(resuming)

        assertEquals(5L, resuming.idleState.progress.startedAt)
    }
}
