package game.skill.firemaking

import api.predef.*
import game.skill.Skills
import io.luna.game.model.mob.Player

/**
 * Holds constants and useful global functions related to Firemaking.
 *
 * @author lare96
 */
object Firemaking {

    /**
     * The tinderbox item ID.
     */
    const val TINDERBOX = 590

    /**
     * The fire object ID.
     */
    const val FIRE_OBJECT = 2732

    /**
     * The ashes item ID.
     */
    const val ASHES = 592

    /**
     * Jagex have stated that log type does not have an effect on burn times, so we use a random time between 45s
     * and 2m.
     */
    val BURN_TIME = 75..200

    /**
     * Ticks from the first strike of the tinderbox to the first chance of the fire catching, then between chances.
     * As LostCity scripts it: the 2004-2007 4-tick skilling cycle.
     */
    const val FIRST_ATTEMPT_TICKS = 3
    const val ATTEMPT_TICKS = 4

    /**
     * The chance of a fire catching, the same for every log: 65/256 at level 1, certain from level 43 (LostCity and
     * the OSRS wiki agree).
     */
    val LIGHT_CHANCE = 64 to 512

    /**
     * Whether one strike of the tinderbox lights the fire.
     */
    fun catches(plr: Player): Boolean = Skills.success(LIGHT_CHANCE, plr.firemaking.level)
}