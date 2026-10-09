package game.skill.crafting.glassMaking

import api.predef.*
import io.luna.game.model.item.Item

// Use oil lamp on empty frame.
useItem(OilLantern.LAMP).onItem(OilLantern.FRAME) {
    if (plr.crafting.level < OilLantern.LEVEL) {
        plr.sendMessage("You need a Crafting level of ${OilLantern.LEVEL} to combine these parts.")
    } else {
        val items = listOf(Item(OilLantern.LAMP), Item(OilLantern.FRAME))
        if (plr.inventory.removeAll(items)) {
            plr.sendMessage("You combine the lamp and frame to make a lantern.")
            plr.crafting.addExperience(OilLantern.EXP)
            plr.inventory.add(Item(OilLantern.LANTERN))
        }
    }
}