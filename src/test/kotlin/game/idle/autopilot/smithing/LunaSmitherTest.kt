package game.idle.autopilot.smithing

import game.idle.autopilot.EndlessAction
import game.idle.autopilot.LunaClicks
import game.idle.autopilot.PlaceCandidate
import game.idle.autopilot.RefusingController
import game.idle.location.Area
import game.idle.location.Tile
import game.skill.smithing.BarType
import game.skill.smithing.smithBar.SmithingInterface
import game.skill.smithing.smithBar.SmithingTable
import game.testworld.TestWorld
import io.luna.game.event.impl.UseItemEvent.ItemOnObjectEvent
import io.luna.game.event.impl.WidgetItemClickEvent
import io.luna.game.event.impl.WidgetItemClickEvent.WidgetItemFirstClickEvent
import io.luna.game.event.impl.WidgetItemClickEvent.WidgetItemSecondClickEvent
import io.luna.game.event.impl.WidgetItemClickEvent.WidgetItemThirdClickEvent
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

class LunaSmitherTest {

    private val anchor = Position(3200, 3200)
    private val anvilId = 2783
    private val anvilTile = Position(3203, 3200)
    private val besideAnvil = Position(3202, 3200)
    private val bronzeBar = 2349
    private val bronzeDagger = 1205
    private val hammer = 2347
    private val area = Area(Tile(anchor.x, anchor.y), radius = 10)
    private val anvilHere = PlaceCandidate(anvilId, anvilTile, 0, usableFromHere = true, besideAnvil)

    private fun login(position: Position = anchor): Player = TestWorld.login("smith", position)

    private fun smither(player: Player, table: SmithingTable = SmithingTable.DAGGER) =
        LunaSmither(player, BarType.BRONZE, table, area)

    private fun recordUses(): MutableList<Int> {
        val used = mutableListOf<Int>()
        TestWorld.listen(ItemOnObjectEvent::class.java) { used += it.usedItemId }
        return used
    }

    private fun <E : WidgetItemClickEvent> recordChoices(type: Class<E>): MutableList<Int> {
        val chosen = mutableListOf<Int>()
        TestWorld.listen(type) { chosen += it.itemId }
        return chosen
    }

    @AfterEach
    fun resetWorld() = TestWorld.reset()

    @Test
    fun `an idle player is not busy`() {
        assertFalse(smither(login()).isBusy())
    }

    @Test
    fun `a walking player is busy`() {
        val player = login()
        player.walking.addStep(Direction.EAST)

        assertTrue(smither(player).isBusy())
    }

    @Test
    fun `an open window keeps the player busy`() {
        val player = login()
        player.overlays.open(StandardInterface(5292))

        assertTrue(smither(player).isBusy())
    }

    @Test
    fun `the smithing window does not keep the player busy, it is what the step answers`() {
        val player = login()
        player.overlays.open(SmithingInterface(BarType.BRONZE))

        assertFalse(smither(player).isBusy())
        assertTrue(smither(player).look().windowOpen)
    }

    @Test
    fun `with a bar the view holds its slot`() {
        val player = login()
        player.inventory.add(Item(hammer))
        player.inventory.add(Item(bronzeBar))

        assertEquals(1, smither(player).look().barSlot)
    }

    @Test
    fun `with fewer bars than the item takes there is no bar to use`() {
        val player = login()
        player.inventory.add(Item(bronzeBar, 4))

        assertNull(smither(player, SmithingTable.PLATEBODY).look().barSlot)
    }

    @Test
    fun `a hammer in the inventory shows in the view`() {
        val player = login()
        player.inventory.add(Item(hammer))

        assertTrue(smither(player).look().hasHammer)
    }

    @Test
    fun `without a hammer the view has none`() {
        assertFalse(smither(login()).look().hasHammer)
    }

    @Test
    fun `the view carries the smithing level and whether the player is at the work spot`() {
        val view = smither(login(Position(3220, 3200))).look()

        assertEquals(listOf(1, false), listOf(view.smithingLevel, view.atLocation))
    }

    @Test
    fun `an anvil next to the player is used from where they stand`() {
        TestWorld.place(anvilId, anvilTile)

        assertEquals(listOf(anvilHere), smither(login(besideAnvil)).look().anvils)
    }

    @Test
    fun `objects that are not anvils are left out`() {
        TestWorld.place(3044, anvilTile)

        assertEquals(emptyList<PlaceCandidate>(), smither(login()).look().anvils)
    }

    @Test
    fun `an anvil only another player sees is left out`() {
        val player = login()
        TestWorld.placeFor(TestWorld.login("other", anchor), anvilId, anvilTile)

        assertEquals(emptyList<PlaceCandidate>(), smither(player).look().anvils)
    }

    @Test
    fun `using the bar on the anvil goes through the client's interaction`() {
        val player = login(besideAnvil)
        player.inventory.add(Item(bronzeBar))
        TestWorld.place(anvilId, anvilTile)
        val used = recordUses()

        smither(player).useOn(anvilHere, slot = 0)
        TestWorld.tick()

        assertEquals(listOf(bronzeBar), used)
    }

    @Test
    fun `an anvil gone since the look is not used`() {
        val player = login(besideAnvil)
        player.inventory.add(Item(bronzeBar))
        val used = recordUses()

        smither(player).useOn(anvilHere, slot = 0)
        TestWorld.tick()

        assertEquals(emptyList<Int>(), used)
    }

    @Test
    fun `another object standing where the anvil was is not used`() {
        val player = login(besideAnvil)
        player.inventory.add(Item(bronzeBar))
        TestWorld.place(1276, anvilTile)
        val used = recordUses()

        smither(player).useOn(anvilHere, slot = 0)
        TestWorld.tick()

        assertEquals(emptyList<Int>(), used)
    }

    @Test
    fun `an anvil only another player sees is not used`() {
        val player = login(besideAnvil)
        player.inventory.add(Item(bronzeBar))
        TestWorld.placeFor(TestWorld.login("other", anchor), anvilId, anvilTile)
        val used = recordUses()

        smither(player).useOn(anvilHere, slot = 0)
        TestWorld.tick()

        assertEquals(emptyList<Int>(), used)
    }

    @Test
    fun `choosing one clicks the item's first option`() {
        val player = login()
        val chosen = recordChoices(WidgetItemFirstClickEvent::class.java)

        smither(player).choose(1)

        assertEquals(listOf(bronzeDagger), chosen)
    }

    @Test
    fun `choosing five clicks the item's second option`() {
        val player = login()
        val chosen = recordChoices(WidgetItemSecondClickEvent::class.java)

        smither(player).choose(5)

        assertEquals(listOf(bronzeDagger), chosen)
    }

    @Test
    fun `choosing ten clicks the item's third option`() {
        val player = login()
        val chosen = recordChoices(WidgetItemThirdClickEvent::class.java)

        smither(player).choose(10)

        assertEquals(listOf(bronzeDagger), chosen)
    }

    @Test
    fun `a controller that refuses the click keeps the choice from going out`() {
        val player = login()
        player.controllers.register(RefusingController(player))
        val chosen = recordChoices(WidgetItemThirdClickEvent::class.java)

        smither(player).choose(10)

        assertEquals(emptyList<Int>(), chosen)
    }

    @Test
    fun `walking to an anvil heads for its approach tile`() {
        val player = login()

        smither(player).walkTo(anvilHere)

        assertEquals(besideAnvil, player.navigator.currentTarget)
    }

    @Test
    fun `walking to an anvil closes the open window`() {
        val player = login()
        player.overlays.open(StandardInterface(5292))

        smither(player).walkTo(anvilHere)

        assertFalse(OverlayType.WIDGET_STANDARD in player.overlays.overlayMap)
    }

    @Test
    fun `walking back heads for the work spot`() {
        val player = login(Position(3220, 3200))

        smither(player).walkToLocation()

        assertEquals(anchor, player.navigator.currentTarget)
    }

    @Test
    fun `what is counted is the step's item`() {
        val player = login()
        player.inventory.add(Item(bronzeDagger))
        player.inventory.add(Item(bronzeDagger))
        player.inventory.add(Item(1351))

        assertEquals(2, smither(player).made())
    }

    @Test
    fun `the item counted is the step's metal's`() {
        val player = login()
        player.inventory.add(Item(bronzeDagger))
        player.inventory.add(Item(1203))

        assertEquals(1, LunaSmither(player, BarType.IRON, SmithingTable.DAGGER, area).made())
    }

    @Test
    fun `stopping ends the smithing in progress`() {
        val player = login()
        player.submitAction(EndlessAction(player))
        TestWorld.tick()

        smither(player).stop()
        TestWorld.tick()

        assertFalse(LunaClicks.isActing(player))
    }

    @Test
    fun `telling the player sends a chat box line`() {
        val player = login()

        smither(player).tell("No hammer.")

        assertEquals(listOf("No hammer."), TestWorld.chatbox(player))
    }
}
