package game.skill.crafting.armorCrafting

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import api.predef.*
import io.luna.game.action.impl.ItemContainerAction.InventoryAction
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation

/**
 * An [InventoryAction] used for crafting armor from tanned hides.
 *
 * @author lare96
 */
class CraftArmorActionItem(private val plr: Player,
                           private val armor: HideArmor,
                           amount: Int) : InventoryAction(plr, true, 4, amount) {

    companion object {

        /**
         * The crafting animation.
         */
        val ANIM = Animation(1249)

        /**
         * The needle identifier.
         */
        val NEEDLE_ID = 1733

        /**
         * The thread identifier.
         */
        val THREAD_ID = 1734

        /**
         * How many items a reel of thread makes before it is used up.
         */
        const val THREAD_USES = 5

        /**
         * How many items the reel of thread in use has made, saved like the game's own counter.
         */
        var Player.threadUsed by Attr.int().persist("thread_used")
    }

    override fun executeIf(start: Boolean) =
        when {
            plr.crafting.level < armor.level -> {
                plr.sendMessage("You need a Crafting level of ${armor.level} to make this.")
                false
            }

            !plr.inventory.containsAll(NEEDLE_ID, THREAD_ID) -> {
                plr.sendMessage("You need a needle and thread in order to craft armor.")
                false
            }

            else -> true
        }

    override fun execute() {
        mob.animation(ANIM)
        mob.sendMessage("You make ${articleItemName(armor.armorItem.id)}.")
        mob.crafting.addExperience(armor.exp)
        // Counted once the item is made, so a batch that runs out of hides uses no thread.
        mob.threadUsed = (mob.threadUsed + 1) % THREAD_USES
        if (mob.threadUsed == 0) {
            plr.sendMessage("You use up one of your reels of thread.")
        }
    }

    override fun add(): List<Item> = listOf(armor.armorItem)

    override fun remove(): List<Item> {
        val rem = armor.hidesItem!!
        // A reel lasts THREAD_USES items and is used up with the last of them.
        return if (mob.threadUsed + 1 >= THREAD_USES) listOf(rem, Item(THREAD_ID)) else listOf(rem)
    }
}
