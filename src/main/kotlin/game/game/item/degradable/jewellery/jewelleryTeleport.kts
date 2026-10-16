package game.item.degradable.jewellery

import api.predef.*

for (jewellery in TeleportJewellery.values()) {
    for (index in 0 until jewellery.items.size) {
        item4(jewellery.items[index]) { JewelleryTeleports.rub(plr, this.index, index, jewellery) }
    }
    val emptyId = jewellery.emptyId
    val empty = jewellery.empty
    if (emptyId != null && empty != null) {
        item4(emptyId) { plr.sendMessage(empty) }
    }
}
