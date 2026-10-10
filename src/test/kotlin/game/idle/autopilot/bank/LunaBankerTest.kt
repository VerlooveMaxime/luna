package game.idle.autopilot.bank

import game.testworld.TestWorld
import io.luna.game.event.impl.ObjectClickEvent.ObjectSecondClickEvent
import io.luna.game.model.Direction
import io.luna.game.model.Position
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.overlay.OverlayType
import io.luna.game.model.mob.overlay.StandardInterface
import io.luna.game.model.mob.varp.PersistentVarp
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LunaBankerTest {

    private val boothId = 2213
    private val boothTile = Position(3200, 3200)
    private val besideBooth = Position(3199, 3200)
    private val awayFromBooth = Position(3195, 3200)
    private val bronzeAxe = 1351
    private val logs = 1511
    private val coins = 995

    private fun login(position: Position = besideBooth): Player = TestWorld.login("banker", position)

    private fun placeBooth() = TestWorld.place(boothId, boothTile)

    private fun holdAxeLogsAndCoins(player: Player) {
        player.inventory.add(Item(bronzeAxe))
        player.inventory.add(Item(logs, 2))
        player.inventory.add(Item(coins, 100))
    }

    private fun recordBoothClicks(): MutableList<Int> {
        val clicked = mutableListOf<Int>()
        TestWorld.listen(ObjectSecondClickEvent::class.java) { clicked += it.gameObject.id }
        return clicked
    }

    @AfterEach
    fun resetWorld() = TestWorld.reset()

    @Test
    fun `an open bank window does not keep the banker busy`() {
        val player = login()
        player.bank.open()

        assertFalse(LunaBanker(player, boothTile).isBusy())
    }

    @Test
    fun `a walking player is busy`() {
        val player = login()
        player.walking.addStep(Direction.WEST)

        assertTrue(LunaBanker(player, boothTile).isBusy())
    }

    @Test
    fun `without a booth on the tile the view has none to use`() {
        val view = LunaBanker(login(), boothTile).look()

        assertEquals(listOf(false, false, false), listOf(view.boothFound, view.boothUsableFromHere, view.bankOpen))
    }

    @Test
    fun `without a booth tile, as when no bank is on the player's floor, the view has none to use`() {
        placeBooth()

        val view = LunaBanker(login(), boothTile = null).look()

        assertEquals(listOf(false, false, false), listOf(view.boothFound, view.boothUsableFromHere, view.bankOpen))
    }

    @Test
    fun `a booth next to the player is usable from here`() {
        placeBooth()

        val view = LunaBanker(login(), boothTile).look()

        assertEquals(listOf(true, true, false), listOf(view.boothFound, view.boothUsableFromHere, view.bankOpen))
    }

    @Test
    fun `a booth farther away is found but not usable from here`() {
        placeBooth()

        val view = LunaBanker(login(awayFromBooth), boothTile).look()

        assertEquals(listOf(true, false, false), listOf(view.boothFound, view.boothUsableFromHere, view.bankOpen))
    }

    @Test
    fun `an open bank shows in the view`() {
        val player = login()
        player.bank.open()

        assertTrue(LunaBanker(player, boothTile).look().bankOpen)
    }

    @Test
    fun `the view holds the bag's slots, empty ones as none`() {
        val player = login()
        holdAxeLogsAndCoins(player)

        assertEquals(listOf(Held(bronzeAxe, 1), Held(logs, 1), Held(logs, 1), Held(coins, 100), null), LunaBanker(player, boothTile).look().bag.take(5))
    }

    @Test
    fun `the view counts what the bank holds by item`() {
        val player = login()
        player.bank.add(Item(logs, 40))
        player.bank.add(Item(coins, 7))

        assertEquals(mapOf(logs to 40, coins to 7), LunaBanker(player, boothTile).look().bank)
    }

    @Test
    fun `coins stack and logs do not`() {
        val banker = LunaBanker(login(), boothTile)

        assertEquals(listOf(true, false), listOf(banker.stacks(coins), banker.stacks(logs)))
    }

    @Test
    fun `walking to the booth heads for the nearest tile it is used from`() {
        val player = login(awayFromBooth)
        placeBooth()

        LunaBanker(player, boothTile).walkToBooth()

        assertEquals(besideBooth, player.navigator.currentTarget)
    }

    @Test
    fun `walking to the booth closes the open window`() {
        val player = login(awayFromBooth)
        placeBooth()
        player.overlays.open(StandardInterface(5292))

        LunaBanker(player, boothTile).walkToBooth()

        assertFalse(OverlayType.WIDGET_STANDARD in player.overlays.overlayMap)
    }

    @Test
    fun `without a booth the player does not walk`() {
        val player = login(awayFromBooth)

        LunaBanker(player, boothTile).walkToBooth()

        assertNull(player.navigator.currentTarget)
    }

    @Test
    fun `opening the bank uses the booth's second option`() {
        val player = login()
        placeBooth()
        val clicked = recordBoothClicks()

        LunaBanker(player, boothTile).open()
        TestWorld.tick()

        assertEquals(listOf(boothId), clicked)
    }

    @Test
    fun `without a booth nothing is clicked`() {
        val player = login()
        val clicked = recordBoothClicks()

        LunaBanker(player, boothTile).open()
        TestWorld.tick()

        assertEquals(emptyList<Int>(), clicked)
    }

    @Test
    fun `depositing moves each slot's whole stack into the bank`() {
        val player = login()
        holdAxeLogsAndCoins(player)

        val move = LunaBanker(player, boothTile).deposit(listOf(1, 2, 3))

        assertEquals(listOf(2, 100), listOf(player.bank.computeAmountForId(logs), player.bank.computeAmountForId(coins)))
        assertEquals(BankMove(moved = true, failed = emptySet()), move)
    }

    @Test
    fun `a deposit the bank has no room for fails`() {
        val player = login()
        holdAxeLogsAndCoins(player)
        (0 until player.bank.capacity()).forEach { player.bank.add(Item(4000 + it)) }

        val move = LunaBanker(player, boothTile).deposit(listOf(1, 3))

        assertEquals(BankMove(moved = false, failed = setOf(logs, coins)), move)
    }

    @Test
    fun `withdrawing takes each amount from the bank into the bag`() {
        val player = login()
        player.bank.add(Item(logs, 40))
        player.bank.add(Item(coins, 500))

        val move = LunaBanker(player, boothTile).withdraw(listOf(Take(logs, 5), Take(coins, 200)))

        assertEquals(listOf(5, 200, 35), listOf(player.inventory.computeAmountForId(logs), player.inventory.computeAmountForId(coins), player.bank.computeAmountForId(logs)))
        assertEquals(BankMove(moved = true, failed = emptySet()), move)
    }

    @Test
    fun `withdrawing hands out items, not notes, whatever the player's bank setting`() {
        val player = login()
        player.bank.add(Item(logs, 40))
        player.varpManager.setValue(PersistentVarp.WITHDRAW_AS_NOTE, 1)

        LunaBanker(player, boothTile).withdraw(listOf(Take(logs, 5)))

        assertEquals(5, player.inventory.computeAmountForId(logs))
    }

    @Test
    fun `a withdrawal the bank holds none of fails`() {
        val player = login()
        player.bank.add(Item(logs, 40))

        val move = LunaBanker(player, boothTile).withdraw(listOf(Take(coins, 5), Take(logs, 5)))

        assertEquals(BankMove(moved = true, failed = setOf(coins)), move)
    }

    @Test
    fun `a withdrawal into a full bag fails`() {
        val player = login()
        player.bank.add(Item(logs, 40))
        (0 until player.inventory.capacity()).forEach { player.inventory.add(Item(bronzeAxe)) }

        val move = LunaBanker(player, boothTile).withdraw(listOf(Take(logs, 5)))

        assertEquals(BankMove(moved = false, failed = setOf(logs)), move)
    }

    @Test
    fun `the banker tells the player in the chat box`() {
        val player = login()

        LunaBanker(player, boothTile).tell("Autopilot: hello.")

        assertEquals(listOf("Autopilot: hello."), TestWorld.chatbox(player))
    }

    @Test
    fun `depositing leaves the slots it is not given`() {
        val player = login()
        holdAxeLogsAndCoins(player)

        LunaBanker(player, boothTile).deposit(listOf(1, 2, 3))

        assertEquals(listOf(bronzeAxe), player.inventory.filterNotNull().map { it.id })
    }

    @Test
    fun `depositing some of the slots holding an item moves that many`() {
        val player = login()
        holdAxeLogsAndCoins(player)

        LunaBanker(player, boothTile).deposit(listOf(2))

        assertEquals(listOf(1, 1), listOf(player.bank.computeAmountForId(logs), player.inventory.computeAmountForId(logs)))
    }

    @Test
    fun `an empty slot in the list is skipped`() {
        val player = login()
        holdAxeLogsAndCoins(player)

        LunaBanker(player, boothTile).deposit(listOf(3, 10))

        assertEquals(100, player.bank.computeAmountForId(coins))
    }

    @Test
    fun `closing shuts the bank window`() {
        val player = login()
        player.bank.open()

        LunaBanker(player, boothTile).close()

        assertFalse(player.bank.isOpen)
    }
}
