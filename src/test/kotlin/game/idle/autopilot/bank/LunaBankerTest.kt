package game.idle.autopilot.bank

import game.testworld.TestWorld
import io.luna.game.event.impl.ObjectClickEvent.ObjectSecondClickEvent
import io.luna.game.model.Direction
import io.luna.game.model.Position
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.overlay.OverlayType
import io.luna.game.model.mob.overlay.StandardInterface
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

        assertEquals(BankView(boothFound = false, boothUsableFromHere = false, bankOpen = false, emptyList()), view)
    }

    @Test
    fun `a booth next to the player is usable from here`() {
        placeBooth()

        val view = LunaBanker(login(), boothTile).look()

        assertEquals(BankView(boothFound = true, boothUsableFromHere = true, bankOpen = false, emptyList()), view)
    }

    @Test
    fun `a booth farther away is found but not usable from here`() {
        placeBooth()

        val view = LunaBanker(login(awayFromBooth), boothTile).look()

        assertEquals(BankView(boothFound = true, boothUsableFromHere = false, bankOpen = false, emptyList()), view)
    }

    @Test
    fun `an open bank shows in the view`() {
        val player = login()
        player.bank.open()

        assertTrue(LunaBanker(player, boothTile).look().bankOpen)
    }

    @Test
    fun `every slot but the axe's is depositable`() {
        val player = login()
        holdAxeLogsAndCoins(player)

        assertEquals(listOf(1, 2, 3), LunaBanker(player, boothTile).look().depositableSlots)
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

        LunaBanker(player, boothTile).deposit(listOf(1, 2, 3))

        assertEquals(listOf(2, 100), listOf(player.bank.computeAmountForId(logs), player.bank.computeAmountForId(coins)))
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

    @Test
    fun `telling the player sends a chat box line`() {
        val player = login()

        LunaBanker(player, boothTile).tell("Your inventory is empty.")

        assertEquals(listOf("Your inventory is empty."), TestWorld.chatbox(player))
    }
}
