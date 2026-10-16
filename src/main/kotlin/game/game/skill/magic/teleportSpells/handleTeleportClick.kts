package game.skill.magic.teleportSpells

import api.predef.*
import game.skill.magic.teleportSpells.TeleportAction.Companion.teleportDelay

/* Intercept all button clicks.*/
for (spell in TeleportSpell.VALUES) {
    button(spell.button) {
        if (plr.teleportDelay.ready(TeleportAction.CLICK_THROTTLE)) { // So player can't button spam.
            plr.submitAction(TeleportSpellAction(plr, spell))
        }
    }
}
