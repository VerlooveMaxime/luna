package game.idle.autopilot.bank

import engine.bank.Banking
import game.idle.autopilot.LunaClicks
import game.idle.movement.navigateToReach
import game.skill.woodcutting.cutTree.Axe
import io.luna.game.event.impl.ObjectClickEvent.ObjectSecondClickEvent
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.interact.InteractionPolicy.STANDARD_SIZE
import io.luna.game.model.`object`.GameObject

/**
 * [Banker] for a logged-in player using the booth on [boothTile]. Opening goes through the booth's "Use-quickly"
 * click like the client does; depositing calls the bank directly, slot by slot, which is what the deposit widget
 * click ends up doing. Axes stay in the inventory so the next chop step can start.
 */
class LunaBanker(private val player: Player, private val boothTile: Position) : Banker {

    private val world get() = player.world

    /** The bank window is the point of this activity, so only walking and actions count as busy. */
    override fun isBusy(): Boolean = LunaClicks.isActing(player)

    override fun look(): BankView {
        val booth = booth()
        return BankView(
            boothFound = booth != null,
            boothUsableFromHere = booth != null && world.collisionManager.reached(player, booth, STANDARD_SIZE),
            bankOpen = player.bank.isOpen,
            depositableSlots = depositableSlots(),
        )
    }

    override fun walkToBooth() {
        val booth = booth() ?: return
        player.overlays.closeWindows(false)
        navigateToReach(player, booth)
    }

    override fun open() {
        val booth = booth() ?: return
        LunaClicks.clickObject(player, ObjectSecondClickEvent(player, booth), booth, ObjectSecondClickEvent::class.java)
    }

    override fun deposit(slots: List<Int>) {
        for (slot in slots.sortedDescending()) {
            val item = player.inventory[slot] ?: continue
            player.bank.deposit(slot, item.amount)
        }
    }

    override fun close() {
        player.overlays.closeWindows(false)
    }

    override fun tell(message: String) {
        player.sendMessage(message)
    }

    private fun booth(): GameObject? =
        world.locator.findObjectsOnTile(boothTile) { it.id in Banking.bankingObjects && it.state == EntityState.ACTIVE }
            .firstOrNull()

    private fun depositableSlots(): List<Int> =
        (0 until player.inventory.capacity()).filter { slot ->
            val id = player.inventory[slot]?.id
            id != null && id !in AXE_IDS
        }

    private companion object {
        val AXE_IDS: Set<Int> = Axe.entries.mapTo(mutableSetOf()) { it.id }
    }
}
