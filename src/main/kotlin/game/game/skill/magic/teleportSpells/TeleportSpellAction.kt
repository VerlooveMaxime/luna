package game.skill.magic.teleportSpells

import io.luna.game.model.mob.Player
import io.luna.util.StringUtils

/**
 * The [TeleportAction] a [TeleportSpell] casts: the spell's level, experience, destination, style and runes, with the
 * message sent as the teleport starts. Submitted by the spellbook's button and by anything else that casts the spell
 * for the player.
 *
 * @author lare96
 */
open class TeleportSpellAction(plr: Player, val spell: TeleportSpell) : TeleportAction(plr,
                                                                                    spell.level,
                                                                                    spell.xp,
                                                                                    spell.destination,
                                                                                    spell.style,
                                                                                    spell.requirements) {

    override fun onTeleport() {
        mob.sendMessage("You teleport to ${StringUtils.capitalize(spell.displayName)}.")
    }
}
