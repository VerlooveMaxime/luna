package game.idle.flow

import game.idle.IdleState
import game.idle.autopilot.Autopilot
import game.idle.autopilot.AutopilotDriver
import game.idle.autopilot.FakeActivity
import game.idle.autopilot.FakeAutopilotPlayer
import game.idle.autopilot.FakeTickScheduler
import game.idle.location.LocationCatalog
import game.idle.location.catalogJson
import game.idle.location.locationJson
import io.luna.game.model.Position
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FlowCommandTest {

    private val scheduler = FakeTickScheduler()
    private val autopilot = Autopilot<FakeAutopilotPlayer>(scheduler) { AutopilotDriver(FakeActivity(), decisionDelayTicks = 1) }
    private val resolver = FlowResolver(
        LocationCatalog.parse(catalogJson(locationJson("bank" to mapOf("x" to 3186, "y" to 3440)))),
    )
    private val command = FlowCommand(autopilot, resolver)

    private val player = FakeAutopilotPlayer("maxime")
    private val here = Position(3182, 3440, 0)

    private fun flow(vararg args: String) = command.flow(player, args.toList())

    @Test
    fun `add appends a valid step`() {
        flow("add", "chop", "normal", "@varrock_west", "until", "inventory", "full")

        assertEquals(listOf("chop normal @varrock_west until inventory full"), player.idleState.flow)
        assertEquals(listOf("Autopilot: step 1: chop normal @varrock_west until inventory full"), player.told)
    }

    @Test
    fun `add rejects a step that does not fit the flow`() {
        flow("add", "bank", "deposit", "all")

        assertEquals(emptyList<String>(), player.idleState.flow)
        assertEquals(listOf("Autopilot: Step 1: bank comes after a chop step, so the flow knows which bank to use"), player.told)
    }

    @Test
    fun `add while running is refused`() {
        flow("add", "chop", "normal", "@varrock_west")
        flow("run")

        flow("add", "loop")

        assertEquals(listOf("chop normal @varrock_west"), player.idleState.flow)
        assertEquals("Autopilot: stop the flow first (::flow stop).", player.told.last())
    }

    @Test
    fun `list shows an empty flow`() {
        flow("list")

        assertEquals(listOf("Autopilot: the flow is empty. ::flow add <step>"), player.told)
    }

    @Test
    fun `list numbers the steps and shows where it is`() {
        player.idleState = IdleState(flow = listOf("chop normal @varrock_west", "loop"), stepIndex = 1)

        flow("list")

        assertEquals(listOf("Autopilot: flow (stopped, at step 2):", "1. chop normal @varrock_west", "2. loop"), player.told)
    }

    @Test
    fun `list says when it is running`() {
        flow("add", "chop", "normal", "@varrock_west")
        flow("run")

        flow("list")

        assertEquals("Autopilot: flow (running, at step 1):", player.told[2])
    }

    @Test
    fun `clear stops and empties the flow`() {
        flow("add", "chop", "normal", "@varrock_west")
        flow("run")

        flow("clear")

        assertFalse(autopilot.isRunning(player))
        assertEquals(emptyList<String>(), player.idleState.flow)
        assertEquals("Autopilot: flow cleared.", player.told.last())
    }

    @Test
    fun `run starts from the first step`() {
        player.idleState = IdleState(flow = listOf("chop normal @varrock_west", "loop"), stepIndex = 1)

        flow("run")

        assertTrue(autopilot.isRunning(player))
        assertEquals(0, player.idleState.stepIndex)
        assertEquals(listOf("Autopilot: running step 1: chop normal @varrock_west"), player.told)
    }

    @Test
    fun `resume continues from the saved step`() {
        player.idleState = IdleState(flow = listOf("chop normal @varrock_west", "loop"), stepIndex = 1)

        flow("resume")

        assertEquals(1, player.idleState.stepIndex)
        assertEquals(listOf("Autopilot: running step 2: loop"), player.told)
    }

    @Test
    fun `run with an empty flow explains`() {
        flow("run")

        assertFalse(autopilot.isRunning(player))
        assertEquals(listOf("Autopilot: the flow is empty. ::flow add <step>"), player.told)
    }

    @Test
    fun `run with a flow that no longer resolves explains`() {
        player.idleState = IdleState(flow = listOf("chop normal @atlantis"))

        flow("run")

        assertFalse(autopilot.isRunning(player))
        assertEquals(listOf("Autopilot: Step 1: Unknown location 'atlantis'. Locations: varrock_west"), player.told)
    }

    @Test
    fun `stop stops`() {
        flow("add", "chop", "normal", "@varrock_west")
        flow("run")

        flow("stop")

        assertFalse(autopilot.isRunning(player))
        assertEquals("Autopilot: stopped.", player.told.last())
    }

    @Test
    fun `an unknown verb prints the usage`() {
        flow("dance")

        assertEquals(listOf("::flow add <step> | list | clear | run | resume | stop. Steps: ${FlowParser.HELP}"), player.told)
    }

    @Test
    fun `no verb prints the usage`() {
        flow()

        assertEquals(1, player.told.size)
    }

    @Test
    fun `idle alone replaces the flow with power chopping at the nearest location`() {
        command.idle(player, emptyList(), here)

        assertTrue(autopilot.isRunning(player))
        assertEquals(listOf("chop normal @varrock_west drop"), player.idleState.flow)
        assertEquals(listOf("Autopilot: running step 1: chop normal @varrock_west drop"), player.told)
    }

    @Test
    fun `idle alone while running stops`() {
        command.idle(player, emptyList(), here)

        command.idle(player, emptyList(), here)

        assertFalse(autopilot.isRunning(player))
        assertEquals("Autopilot: stopped.", player.told.last())
    }

    @Test
    fun `idle with a location and tree`() {
        command.idle(player, listOf("varrock_west", "normal"), here)

        assertEquals(listOf("chop normal @varrock_west drop"), player.idleState.flow)
    }

    @Test
    fun `idle with a bad location explains and changes nothing`() {
        command.idle(player, listOf("atlantis"), here)

        assertFalse(autopilot.isRunning(player))
        assertEquals(listOf("Autopilot: Unknown location 'atlantis'. Locations: varrock_west"), player.told)
    }

    @Test
    fun `idle with no locations explains`() {
        val empty = FlowCommand(autopilot, FlowResolver(LocationCatalog.parse("{}")))

        empty.idle(player, emptyList(), here)

        assertEquals(listOf("Autopilot: no locations are defined."), player.told)
    }
}
