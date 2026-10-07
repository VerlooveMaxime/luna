package game.idle.autopilot.bank

import engine.bank.Banking
import game.idle.autopilot.LunaClicks
import game.idle.movement.navigateToReach
import game.skill.firemaking.Firemaking
import game.skill.fishing.catchFish.Tool
import game.skill.woodcutting.cutTree.Axe
import io.luna.game.event.impl.ObjectClickEvent.ObjectSecondClickEvent
import io.luna.game.model.Position
import io.luna.game.model.World
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.interact.InteractionPolicy.STANDARD_SIZE
import io.luna.game.model.`object`.GameObject

/**
 * [Banker] for a logged-in player using the booth on [boothTile], none when null (no bank on the player's floor).
 * Opening goes through the booth's "Use-quickly" click like the client does; depositing calls the bank directly,
 * slot by slot, which is what the deposit widget click ends up doing. Tools (axes, the tinderbox, fishing tools)
 * stay in the inventory so the next steps can start.
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
        LunaClicks.interact(player, ObjectSecondClickEvent(player, booth), booth, ObjectSecondClickEvent::class.java)
    }

    /**
     * `Bank.deposit` removes its amount by item id from the first slots holding that id, so a call per slot would
     * empty other slots and then skip the given ones as empty. One call per id, for the total the given slots hold.
     */
    override fun deposit(slots: List<Int>) {
        val items = slots.mapNotNull { slot -> player.inventory[slot]?.let { slot to it } }
        for (sameId in items.groupBy { (_, item) -> item.id }.values) {
            val (firstSlot, _) = sameId.first()
            player.bank.deposit(firstSlot, sameId.sumOf { (_, item) -> item.amount })
        }
    }

    override fun close() {
        player.overlays.closeWindows(false)
    }

    override fun tell(message: String) {
        player.sendMessage(message)
    }

    private fun booth(): GameObject? = boothTile?.let { boothOn(world, it) }

    private fun depositableSlots(): List<Int> =
        (0 until player.inventory.capacity()).filter { slot ->
            val id = player.inventory[slot]?.id
            id != null && id !in TOOL_IDS
        }

    private companion object {
        val TOOL_IDS: Set<Int> = Axe.entries.map { it.id }.toSet() + Firemaking.TINDERBOX + Tool.entries.map { it.id }
    }
}

// Objects found through their chunk are always ACTIVE, so only the id needs checking.
internal fun boothOn(world: World, tile: Position): GameObject? =
    world.locator.findObjectsOnTile(tile) { it.id in Banking.bankingObjects }.firstOrNull()
