package game.item.degradable.jewellery

import api.predef.*
import api.predef.ext.*
import game.item.degradable.jewellery.TeleportJewellery.JewelleryDestination
import game.skill.magic.Magic.findLandingTile
import game.skill.magic.Magic.teleport
import game.skill.magic.teleportSpells.TeleportStyle
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.DialogueQueueBuilder.DialogueOption

/**
 * Teleporting with [TeleportJewellery] held in the inventory: rubbing opens the destination menu, picking a destination
 * teleports and uses a charge. [slot] is the inventory slot the jewellery sits in and [index] its place in
 * [TeleportJewellery.items], from the most charges to the last.
 *
 * @author hydrozoa
 */
object JewelleryTeleports {

    /**
     * Invoked when the player initially rubs the jewellery. Opens [openDialogue] once the jewellery's menu delay passes.
     */
    fun rub(plr: Player, slot: Int, index: Int, jewellery: TeleportJewellery) {
        plr.sendMessage(jewellery.rub)
        if (jewellery.menuDelay == 0) {
            openDialogue(plr, slot, index, jewellery)
            return
        }
        plr.lock()
        world.scheduleOnce(jewellery.menuDelay) {
            plr.unlock()
            openDialogue(plr, slot, index, jewellery)
        }
    }

    /**
     * Opens the destination menu once the jewellery is rubbed. Potentially forwards to [teleport].
     */
    fun openDialogue(plr: Player, slot: Int, index: Int, jewellery: TeleportJewellery) {
        val options = ArrayList<DialogueOption>(jewellery.destinations.size + 1)
        for (dest in jewellery.destinations) {
            options += DialogueOption(dest.option) { teleport(plr, dest, slot, index, jewellery) }
        }
        options += DialogueOption("Nowhere.") {
            plr.resetDialogues()
            jewellery.nowhere?.let { plr.sendMessage(it) }
        }

        plr.newDialogue()
            .options(options)
            .title(jewellery.title)
            .open()
    }

    /**
     * Teleports the player once a destination is picked. The charge is only used once the player lands. [onTeleport] is
     * invoked once the teleport has passed every check, before its message.
     */
    fun teleport(plr: Player, destination: JewelleryDestination, slot: Int, index: Int, jewellery: TeleportJewellery,
                 onTeleport: () -> Unit = {}) {
        if (!holdsRubbedItem(plr, slot, index, jewellery)) {
            return
        }
        val chargesLeft = jewellery.charges(jewellery.items[index]) - 1
        val landing = findLandingTile(destination.centre, destination.radius)
        plr.teleport(landing, TeleportStyle.REGULAR, jewellery.maxWildernessLevel, onLand = {
            useCharge(plr, slot, index, jewellery)
            jewellery.landMessage(chargesLeft)?.let { plr.sendMessage(it) }
        }) {
            onTeleport()
            jewellery.castMessage(chargesLeft)?.let { plr.sendMessage(it) }
        }
    }

    /**
     * Checks that the inventory slot that was rubbed still holds the same jewellery.
     */
    fun holdsRubbedItem(plr: Player, slot: Int, index: Int, jewellery: TeleportJewellery) =
        plr.inventory[slot]?.id == jewellery.items[index]

    /**
     * Replaces the rubbed jewellery with its next charge, its empty form, or nothing when it crumbles.
     */
    fun useCharge(plr: Player, slot: Int, index: Int, jewellery: TeleportJewellery) {
        if (!holdsRubbedItem(plr, slot, index, jewellery)) {
            return
        }
        val nextId = jewellery.items.getOrNull(index + 1) ?: jewellery.emptyId
        plr.inventory[slot] = if (nextId == null) null else Item(nextId)
    }
}
