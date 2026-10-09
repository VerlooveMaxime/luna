package game.skill.crafting.armorCrafting

import api.predef.*

/**
 * The steel studs identifier.
 */
val steelStuds = CraftStuddedActionItem.STUDS

// Make studded body and chaps.
CraftStuddedActionItem.LEATHER_TO_STUDDED.forEach { (leather, studded) ->
    useItem(steelStuds)
        .onItem(leather.id) {
            plr.submitAction(CraftStuddedActionItem(plr, studded, leather.id))
        }
}
