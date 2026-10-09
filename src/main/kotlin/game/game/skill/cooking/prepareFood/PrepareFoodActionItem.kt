package game.skill.cooking.prepareFood

import api.predef.*
import game.obj.resource.fillable.WaterResource
import io.luna.game.action.impl.ItemContainerAction.InventoryAction
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player

/**
 * An [InventoryAction] that prepares an [IncompleteFood] type.
 *
 * @author lare96
 */
class PrepareFoodActionItem(plr: Player,
                            val food: IncompleteFood,
                            private val removeIds: MutableSet<Int>,
                            amount: Int) :
    InventoryAction(plr, true, 1, amount) {

    companion object {

        /**
         * The curry leaf, of which uncooked curry takes [CURRY_LEAVES].
         */
        const val CURRY_LEAF = 5970

        /**
         * How many curry leaves an uncooked curry takes.
         */
        const val CURRY_LEAVES = 3
    }

    override fun executeIf(start: Boolean): Boolean =
        when {
            mob.cooking.level < food.lvl -> {
                mob.sendMessage("You need a Cooking level of ${food.lvl} to make this.")
                false
            }

            else -> true
        }

    override fun execute() {
        if (food.exp > 0.0) {
            mob.cooking.addExperience(food.exp)
        }
        val name = food.name
        val str =
            if (name.endsWith("DOUGH")) "You make some ${itemName(food.id)}."
            else if (food != IncompleteFood.PINEAPPLE_RING) "You make ${articleItemName(food.id)}."
            else "You make some ${itemName(food.id)}s."
        mob.sendMessage(str)
    }

    override fun add(): List<Item> {
        val addItems = ArrayList<Item>()
        addItems += when (food) {
            // Using knife with pineapple gives 4 rings.
            IncompleteFood.PINEAPPLE_RING -> Item(food.id, 4)
            // All other food preparation.
            else -> Item(food.id)
        }
        if (currentRemove != null) {
            // Replace items with empty counterparts. Empty buckets, jugs, pots, etc.
            for (item in currentRemove) {
                if (food.keepsContainer && item.id == food.baseIngredient) {
                    continue
                }
                val replaceId = computeReplacedItems(item.id)
                if (replaceId != null) {
                    addItems += Item(replaceId, item.amount)
                }
            }
        }
        return addItems
    }

    override fun remove() = when {
        // Uncooked cake requires all ingredients at once.
        food == IncompleteFood.UNCOOKED_CAKE -> listOf(*food.otherIngredients.map { Item(it) }.toTypedArray())
        // Making uncooked curry requires 3 leaves.
        food == IncompleteFood.UNCOOKED_CURRY -> {
            removeIds.map {
                if (it == CURRY_LEAF) {
                    Item(it, CURRY_LEAVES)
                } else {
                    Item(it)
                }
            }
        }
        // Don't remove knife when cutting pineapple.
        food == IncompleteFood.PINEAPPLE_RING -> listOf(Item(2114))

        else -> removeIds.map { Item(it) }
    }

    /**
     * Determines if and what a removed item will be replaced by. Used to empty containers.
     */
    private fun computeReplacedItems(id: Int): Int? {
        // Handle all water containers dynamically.
        val inverseFillables = WaterResource.FILLABLES.inverse()
        val emptyId = inverseFillables[id]
        if (emptyId != null) {
            return emptyId
        }
        return when (id) {
            1933 -> 1931 // Pot of flour
            1927 -> 1925 // Bucket of milk
            4239 -> 1923 // Nettle tea
            else -> null
        }
    }
}