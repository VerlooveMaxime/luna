package io.luna.game.model.mob.combat

import game.testworld.TestWorld
import io.luna.game.model.Position
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.Skill
import io.luna.game.model.mob.combat.CombatFormula.PhysicalType
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CombatFormulaTest {

    private val tile = Position(3230, 3230)

    @AfterEach
    fun resetWorld() = TestWorld.reset()

    /** Strength 92 unarmed: 92 effective strength hits up to 9, 95 up to 10, so the stance's +3 shows. */
    private fun unarmedFighter(stance: CombatStance): Player {
        val player = TestWorld.login("fighter", tile)
        player.skill(Skill.STRENGTH).level = 92
        player.combat.weapon.refreshWeapon(stance)
        return player
    }

    @Test
    fun `the aggressive stance raises the melee max hit`() {
        assertEquals(10, CombatFormula.calculatePhysicalMaxHit(unarmedFighter(CombatStance.AGGRESSIVE), PhysicalType.MELEE))
    }

    @Test
    fun `the accurate stance leaves the melee max hit alone`() {
        assertEquals(9, CombatFormula.calculatePhysicalMaxHit(unarmedFighter(CombatStance.ACCURATE), PhysicalType.MELEE))
    }
}
