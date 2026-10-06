package io.luna.game.model.mob.combat.damage

import api.combat.death.DeathHookHandler
import game.testworld.TestWorld
import io.luna.game.model.Position
import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.combat.attack.PlayerMeleeCombatAttack
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import java.util.OptionalInt

class KillCreditTest {

    private val man = 1
    private val tile = Position(3230, 3230)
    private var killer: Mob? = null

    @AfterEach
    fun resetWorld() = TestWorld.reset()

    @Test
    fun `a kill whose killing hit is the first damaging one is credited to its attacker`() {
        DeathHookHandler.addNpcHook(man) { killer = source }
        val player = TestWorld.login("fighter", tile)
        val npc = TestWorld.spawnNpc(man, tile.translate(1, 0))
        npc.health = 1
        val attack = PlayerMeleeCombatAttack(player, npc, player.combat.styleDef)

        CombatDamage(player, npc, CombatDamageType.MELEE, OptionalInt.of(1)).apply(attack)
        TestWorld.tick(2)

        assertSame(player, killer)
    }
}
