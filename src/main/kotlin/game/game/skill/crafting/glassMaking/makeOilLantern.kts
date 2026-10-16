package game.skill.crafting.glassMaking

import api.predef.*
import io.luna.game.model.item.Item

/**
 * Assembles an oil lamp and oil-lantern frame into one oil lantern at Crafting level 26 for 50 XP.
 * Both components must be present before either is removed. ItemContainer.removeAll reports whether
 * any removal succeeded, so it cannot by itself validate the complete ingredient pair.
 * The conversion reduces occupied slots and uses the normal player item-on-item interaction.
 *
 * @author lare96
 */
useItem(OilLantern.LAMP).onItem(OilLantern.FRAME) {
    if (plr.crafting.level < OilLantern.LEVEL) {
        plr.sendMessage("You need a Crafting level of ${OilLantern.LEVEL} to combine these parts.")
    } else {
        val items = listOf(Item(OilLantern.LAMP), Item(OilLantern.FRAME))
        if (plr.inventory.containsAll(items) && plr.inventory.removeAll(items)) {
            plr.sendMessage("You combine the lamp and frame to make a lantern.")
            plr.crafting.addExperience(OilLantern.EXP)
            plr.inventory.add(Item(OilLantern.LANTERN))
        }
    }
}