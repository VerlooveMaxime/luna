package game.idle.autopilot.reflex

import api.combat.specialAttack.SpecialAttackHandler
import io.luna.game.model.mob.combat.SpecialAttackType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class SpecialOptionsTest {

    @Test
    fun `a registered special gives every id of its weapon its drain`() {
        SpecialAttackHandler.attack(SpecialAttackType.DRAGON_DAGGER, drain = 25) {}

        val drains = SpecialOptions.drains()

        assertEquals(listOf(25, 25, 25, 25), SpecialAttackType.DRAGON_DAGGER.ids.map { drains[it] })
    }

    @Test
    fun `a weapon whose special is not registered is absent`() {
        assertFalse(SpecialOptions.drains().containsKey(UNREGISTERED_HALBERD))
    }

    private companion object {
        /** The dragon halberd: no test registers its special, as `TestWorld` loads no plugin. */
        const val UNREGISTERED_HALBERD = 3204
    }
}
