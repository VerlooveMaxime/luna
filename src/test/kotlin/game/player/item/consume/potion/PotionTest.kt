package game.player.item.consume.potion

import game.testworld.TestWorld
import io.luna.game.model.Position
import io.luna.game.model.mob.Player
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PotionTest {

    @AfterEach
    fun resetWorld() = TestWorld.reset()

    private fun player(health: Int): Player = TestWorld.login("brew", Position(3200, 3200)).also { it.health = health }

    @Test
    fun `a Saradomin brew heals its dose amount instead of dealing damage`() {
        val player = player(3)

        Potion.SARADOMIN_BREW.effect(player)

        assertEquals(6, player.health)
    }

    @Test
    fun `a brew can heal above base hitpoints`() {
        val player = player(10)

        Potion.SARADOMIN_BREW.effect(player)

        assertEquals(13, player.health)
    }

    @Test
    fun `a brew dose caps its healing at base plus the boost`() {
        val player = player(12)

        Potion.SARADOMIN_BREW.effect(player)

        assertEquals(13, player.health)
    }

    @Test
    fun `another brew at the cap heals no farther`() {
        val player = player(13)

        Potion.SARADOMIN_BREW.effect(player)

        assertEquals(13, player.health)
    }

    @Test
    fun `a brew preserves health already above its own cap`() {
        val player = player(14)

        Potion.SARADOMIN_BREW.effect(player)

        assertEquals(14, player.health)
    }
}
