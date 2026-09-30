package game.idle.autopilot

import game.testworld.TestWorld
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WorldTickSchedulerTest {

    @Test
    fun `the action runs once per tick`() {
        var runs = 0
        val scheduled = WorldTickScheduler(TestWorld.world).everyTick { runs++ }

        TestWorld.tick(times = 3)
        scheduled.cancel()

        assertEquals(3, runs)
    }

    @Test
    fun `a cancelled action no longer runs`() {
        var runs = 0
        val scheduled = WorldTickScheduler(TestWorld.world).everyTick { runs++ }

        scheduled.cancel()
        TestWorld.tick(times = 2)

        assertEquals(0, runs)
    }
}
