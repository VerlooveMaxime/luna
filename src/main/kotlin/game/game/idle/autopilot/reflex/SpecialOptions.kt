package game.idle.autopilot.reflex

import api.combat.specialAttack.SpecialAttackHandler
import io.luna.game.model.mob.combat.SpecialAttackType

/**
 * The weapons whose special attack Luna's scripts register, each with what one use drains of the bar (S13 part 4). Read
 * when asked, not at boot: the weapons' scripts register at their own load, which may come after the builder's.
 */
object SpecialOptions {

    fun drains(): Map<Int, Int> =
        SpecialAttackType.entries.flatMap { type ->
            SpecialAttackHandler.of(type)?.let { special -> type.ids.map { it to special.drain } }.orEmpty()
        }.toMap()
}
