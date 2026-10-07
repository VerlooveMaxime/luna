package io.luna.game.model.mob.combat.state

import game.testworld.TestWorld
import io.luna.game.model.Direction
import io.luna.game.model.Position
import io.luna.game.model.mob.combat.attack.PlayerMeleeCombatAttack
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlayerCombatContextTest {

    private val giantRat = 950
    private val tile = Position(3230, 3230)

    @AfterEach
    fun resetWorld() = TestWorld.reset()

    @Test
    fun `an attack prepared against one npc is not used against another`() {
        val player = TestWorld.login("fighter", tile)
        val first = TestWorld.spawnNpc(giantRat, tile.translate(1, 0))
        val second = TestWorld.spawnNpc(giantRat, tile.translate(0, 1))
        player.combat.firstAttack = PlayerMeleeCombatAttack(player, first, player.combat.styleDef)

        assertSame(second, player.combat.getNextAttack(second).victim)
    }

    @Test
    fun `an attack prepared against another npc is dropped once looked at`() {
        val player = TestWorld.login("fighter", tile)
        val first = TestWorld.spawnNpc(giantRat, tile.translate(1, 0))
        player.combat.firstAttack = PlayerMeleeCombatAttack(player, first, player.combat.styleDef)

        player.combat.getNextAttack(TestWorld.spawnNpc(giantRat, tile.translate(0, 1)))

        assertNull(player.combat.firstAttack)
    }

    @Test
    fun `preparing a first attack replaces one left by a click that never reached its target`() {
        val player = TestWorld.login("fighter", tile)
        val rat = TestWorld.spawnNpc(giantRat, tile.translate(1, 0))
        val stale = PlayerMeleeCombatAttack(player, rat, player.combat.styleDef)
        player.combat.firstAttack = stale

        assertNotSame(stale, player.combat.prepareFirstAttack(rat))
    }

    @Test
    fun `the prepared first attack is the next attack against its victim`() {
        val player = TestWorld.login("fighter", tile)
        val rat = TestWorld.spawnNpc(giantRat, tile.translate(1, 0))
        val prepared = player.combat.prepareFirstAttack(rat)

        assertEquals(prepared, player.combat.getNextAttack(rat))
    }

    @Test
    fun `a player standing still fights back`() {
        assertTrue(TestWorld.login("fighter", tile).combat.isAutoRetaliate)
    }

    @Test
    fun `a walking player does not turn to fight back`() {
        val player = TestWorld.login("fighter", tile)
        player.walking.addStep(Direction.EAST)

        assertFalse(player.combat.isAutoRetaliate)
    }
}
