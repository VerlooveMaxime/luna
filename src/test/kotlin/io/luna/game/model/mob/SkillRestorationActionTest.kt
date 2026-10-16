package io.luna.game.model.mob

import game.testworld.TestWorld
import io.luna.game.model.Position
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SkillRestorationActionTest {

    @AfterEach
    fun resetWorld() = TestWorld.reset()

    @Test
    fun `a potion boost decays one level at a time instead of clamping to base`() {
        val player = TestWorld.login("boosted", Position(3200, 3200))
        val strength = player.skill(Skill.STRENGTH)
        strength.level = 4
        val restoration = SkillRestorationAction(player)

        assertFalse(restoration.run())
        assertEquals(3, strength.level)
        assertFalse(restoration.run())
        assertEquals(2, strength.level)
        assertFalse(restoration.run())
        assertEquals(1, strength.level)
        assertTrue(restoration.run())
    }

    @Test
    fun `brew hitpoints decay toward base by one`() {
        val player = TestWorld.login("brewed", Position(3200, 3200))
        player.health = 13

        SkillRestorationAction(player).run()

        assertEquals(12, player.health)
    }

    @Test
    fun `a depleted skill still recovers one level and prayer stays depleted`() {
        val player = TestWorld.login("drained", Position(3200, 3200))
        player.health = 7
        player.skill(Skill.PRAYER).level = 0

        SkillRestorationAction(player).run()

        assertEquals(8, player.health)
        assertEquals(0, player.skill(Skill.PRAYER).level)
    }
}
