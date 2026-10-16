package game.combat.specialAttacks

import api.combat.specialAttack.SpecialAttackHandler.attack
import io.luna.game.model.LocalProjectile
import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.block.Graphic
import io.luna.game.model.mob.combat.SpecialAttackType.MAGIC_LONGBOW
import io.luna.game.model.mob.combat.damage.CombatDamageType

/**
 * The graphic displayed on the attacker when the magic longbow's Powershot is launched.
 */
val GRAPHIC = Graphic(250, 96, 0)

/**
 * Creates the Powershot projectile: the special arrow, flying as a normal arrow does.
 */
val PROJECTILE: (Mob, Mob) -> LocalProjectile = { mob, other ->
    LocalProjectile.followEntity(mob.context)
        .setSourceEntity(mob)
        .setTargetEntity(other)
        .setId(249)
        .setTicksToStart(44)
        .setTicksToEnd(12)
        .setStartHeight(40)
        .setEndHeight(36)
        .setInitialSlope(15)
        .build()
}

// Powershot: one arrow that cannot miss. A full flat accuracy bonus makes every hit chance one.
attack(type = MAGIC_LONGBOW,
       drain = 35,
       damageType = CombatDamageType.RANGED,
       attackBonus = 1.0) {

    attack { ranged(start = GRAPHIC, projectileFunction = PROJECTILE) }
}
