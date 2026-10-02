package game.skill.firemaking

import game.skill.Skills
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The chance of a fire catching, as LostCity and the OSRS wiki give it. */
class FiremakingTest {

    private fun chanceAt(level: Int) = Skills.successRate(Firemaking.LIGHT_CHANCE.first, Firemaking.LIGHT_CHANCE.second, level)

    @Test
    fun `a fire catches 65 times in 256 at level 1`() {
        assertEquals(65.0 / 256, chanceAt(1))
    }

    @Test
    fun `a fire always catches from level 43`() {
        assertTrue(chanceAt(43) >= 1.0)
    }

    @Test
    fun `a fire can still fail at level 42`() {
        assertTrue(chanceAt(42) < 1.0)
    }
}
