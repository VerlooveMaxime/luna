package game.combat.specialAttacks

import api.combat.specialAttack.SpecialAttackHandler.attack
import io.luna.game.model.LocalProjectile
import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.block.Graphic
import io.luna.game.model.mob.combat.SpecialAttackType.MAGIC_SHORTBOW
import io.luna.game.model.mob.combat.damage.CombatDamageAction
import io.luna.game.model.mob.combat.damage.CombatDamageRequest
import io.luna.game.model.mob.combat.damage.CombatDamageType

/**
 * The magic shortbow special attack animation id.
 */
val ANIMATION = 1074

/**
 * The graphic displayed on the attacker when the magic shortbow's Snapshot is launched. Not 249: that is the special
 * arrow's projectile, a spot animation without an animation, which the 377 client cannot draw on a player.
 */
val GRAPHIC = Graphic(256, 96, 0)

/**
 * How many client ticks after the first arrow the second one leaves the bow.
 */
val SECOND_ARROW_START = 30

/**
 * The game ticks after the launch at which the second arrow lands: one after the first, which the projectile takes two.
 */
val SECOND_ARROW_DELAY = 3

/**
 * Creates a Snapshot projectile: the special arrow, flying as a normal arrow does, [behind] client ticks after it.
 */
fun arrow(mob: Mob, other: Mob, behind: Int): LocalProjectile =
    LocalProjectile.followEntity(mob.context)
        .setSourceEntity(mob)
        .setTargetEntity(other)
        .setId(249)
        .setTicksToStart(44 + behind)
        .setTicksToEnd(12 + behind / 6)
        .setStartHeight(40)
        .setEndHeight(36)
        .setInitialSlope(15)
        .build()

/**
 * Creates the first Snapshot projectile.
 */
val PROJECTILE: (Mob, Mob) -> LocalProjectile = { mob, other -> arrow(mob, other, 0) }

// Snapshot: two arrows in quick succession at reduced accuracy, the second taking its own arrow from the quiver.
attack(type = MAGIC_SHORTBOW,
       drain = 55,
       damageType = CombatDamageType.RANGED,
       attackBonus = -0.10) {

    attack { ranged(animationId = ANIMATION, start = GRAPHIC, projectileFunction = PROJECTILE) }

    launched {
        if (attacker.combat.ranged.removeAmmo()) {
            arrow(attacker, victim, SECOND_ARROW_START).display()
            val secondHit = CombatDamageRequest.builder(attacker, victim, CombatDamageType.RANGED)
                .setFlatBonusAccuracy(attackBonus)
                .build()
                .resolve()
            victim.submitAction(CombatDamageAction(secondHit, attack, false, SECOND_ARROW_DELAY))
        }
        damage
    }
}
