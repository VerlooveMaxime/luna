package game.idle.autopilot

import game.idle.AutopilotJob
import game.idle.IdleState
import game.idle.location.LocationCatalog
import game.idle.location.catalogJson
import game.idle.location.locationJson
import io.luna.game.model.Position
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class IdleCommandTest {

    private val scheduler = FakeTickScheduler()
    private val autopilot = Autopilot<FakeAutopilotPlayer>(scheduler) { _, _ ->
        AutopilotDriver(FakeActivity(), decisionDelayTicks = 1)
    }
    private val jobs = Jobs(LocationCatalog.parse(catalogJson(locationJson())))
    private val command = IdleCommand(autopilot, jobs)

    private val player = FakeAutopilotPlayer("maxime")
    private val here = Position(3182, 3440, 0)

    @Test
    fun `idle alone starts at the nearest location`() {
        command.run(player, emptyList(), here)

        assertTrue(autopilot.isRunning(player))
        assertEquals(AutopilotJob("varrock_west", listOf("normal")), player.idleState.job)
        assertEquals(listOf("Autopilot: chopping normal trees at West of Varrock."), player.told)
    }

    @Test
    fun `idle alone while running stops`() {
        command.run(player, emptyList(), here)

        command.run(player, emptyList(), here)

        assertFalse(autopilot.isRunning(player))
        assertEquals("Autopilot disabled.", player.told.last())
    }

    @Test
    fun `idle with a location while running switches to it`() {
        command.run(player, emptyList(), here)

        command.run(player, listOf("varrock_west", "normal"), here)

        assertTrue(autopilot.isRunning(player))
        assertEquals(1, scheduler.activeCount)
    }

    @Test
    fun `a rejected request tells the player and changes nothing`() {
        val chopping = FakeAutopilotPlayer("maxime", IdleState(job = AutopilotJob("varrock_west", listOf("normal"))))

        command.run(chopping, listOf("atlantis"), here)

        assertFalse(autopilot.isRunning(chopping))
        assertEquals(listOf("Autopilot: unknown location 'atlantis'. Locations: varrock_west"), chopping.told)
    }
}
