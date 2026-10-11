package game.idle.autopilot

import game.testworld.TestWorld
import io.luna.game.model.Position
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.Skill
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LunaCarriedTest {

    private val bronzeAxe = 1351
    private val logs = 1511

    @AfterEach
    fun resetWorld() = TestWorld.reset()

    private fun login(): Player = TestWorld.login("carrier", Position(3200, 3200))

    @Test
    fun `the level counts a boost, as Luna's own checks do`() {
        val player = login()
        player.skill(Skill.WOODCUTTING).level = 4

        assertEquals(4, LunaCarried(player).level(Skill.WOODCUTTING))
    }

    @Test
    fun `an item in the bag is held`() {
        val player = login()
        player.inventory.add(Item(logs))

        assertTrue(LunaCarried(player).holds(logs))
    }

    @Test
    fun `an item not in the bag is not held`() {
        assertFalse(LunaCarried(login()).holds(logs))
    }

    @Test
    fun `an item equipped is worn`() {
        val player = login()
        player.equipment.add(Item(bronzeAxe))

        assertTrue(LunaCarried(player).wears(bronzeAxe))
    }

    @Test
    fun `an item in the bag is not worn`() {
        val player = login()
        player.inventory.add(Item(bronzeAxe))

        assertFalse(LunaCarried(player).wears(bronzeAxe))
    }

    @Test
    fun `a bag with room is not full`() {
        assertFalse(LunaCarried(login()).bagFull())
    }

    @Test
    fun `a bag of 28 is full`() {
        val player = login()
        player.inventory.add(Item(logs, 28))

        assertTrue(LunaCarried(player).bagFull())
    }
}
