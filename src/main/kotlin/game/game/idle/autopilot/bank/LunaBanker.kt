package game.idle.autopilot.bank

import engine.bank.Banking
import game.idle.autopilot.LunaClicks
import game.idle.movement.navigateToReach
import io.luna.game.event.impl.ObjectClickEvent.ObjectSecondClickEvent
import io.luna.game.model.Position
import io.luna.game.model.World
import io.luna.game.model.def.ItemDefinition
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.interact.InteractionPolicy.STANDARD_SIZE
import io.luna.game.model.mob.varp.PersistentVarp
import io.luna.game.model.`object`.GameObject

/**
 * [Banker] for a logged-in player using the booth on [boothTile], none when null (no bank on the player's floor).
 * Opening goes through the booth's "Use-quickly" click like the client does; depositing and withdrawing call the bank
 * directly, which is what the bank window's clicks end up doing.
 */
class LunaBanker(private val player: Player, private val boothTile: Position?) : Banker {

    private val world get() = player.world

    /** The bank window is the point of this activity, so only walking and actions count as busy. */
    override fun isBusy(): Boolean = LunaClicks.isActing(player)

    override fun look(): BankView {
        val booth = booth()
        return BankView(
            boothFound = booth != null,
            boothUsableFromHere = booth != null && world.collisionManager.reached(player, booth, STANDARD_SIZE),
            bankOpen = player.bank.isOpen,
            bag = (0 until player.inventory.capacity()).map { slot -> player.inventory[slot]?.let { Held(it.id, it.amount) } },
            bank = player.bank.filterNotNull().groupingBy { it.id }.fold(0) { total, item -> total + item.amount },
        )
    }

    override fun stacks(id: Int): Boolean = ItemDefinition.ALL.retrieve(id).isStackable

    override fun walkToBooth() {
        val booth = booth() ?: return
        player.overlays.closeWindows(false)
        navigateToReach(player, booth)
    }

    override fun open() {
        val booth = booth() ?: return
        LunaClicks.interact(player, ObjectSecondClickEvent(player, booth), booth, ObjectSecondClickEvent::class.java)
    }

    /**
     * `Bank.deposit` removes its amount by item id from the first slots holding that id, so a call per slot would
     * empty other slots and then skip the given ones as empty. One call per id, for the total the given slots hold.
     */
    override fun deposit(slots: List<Int>): BankMove {
        val items = slots.mapNotNull { slot -> player.inventory[slot]?.let { slot to it } }
        val failed = items.groupBy { (_, item) -> item.id }.filter { (_, sameId) ->
            val (firstSlot, _) = sameId.first()
            !player.bank.deposit(firstSlot, sameId.sumOf { (_, item) -> item.amount })
        }.keys
        return BankMove(moved = failed.size < items.map { (_, item) -> item.id }.toSet().size, failed)
    }

    /** The player's "withdraw as note" setting is put back to items first, as opening the bank does: steps use items. */
    override fun withdraw(takes: List<Take>): BankMove {
        player.varpManager.setAndSendValue(PersistentVarp.WITHDRAW_AS_NOTE, 0)
        val failed = takes.filter { take ->
            val slot = player.bank.computeIndexForId(take.id)
            slot < 0 || !player.bank.withdraw(slot, take.amount)
        }.map { it.id }.toSet()
        return BankMove(moved = failed.size < takes.size, failed)
    }

    override fun close() {
        player.overlays.closeWindows(false)
    }

    override fun tell(message: String) {
        player.sendMessage(message)
    }

    private fun booth(): GameObject? = boothTile?.let { boothOn(world, it) }
}

// Objects found through their chunk are always ACTIVE, so only the id needs checking.
internal fun boothOn(world: World, tile: Position): GameObject? =
    world.locator.findObjectsOnTile(tile) { it.id in Banking.bankingObjects }.firstOrNull()
