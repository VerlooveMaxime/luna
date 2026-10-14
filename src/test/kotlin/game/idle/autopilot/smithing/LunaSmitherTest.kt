package game.idle.autopilot.smithing

import game.idle.autopilot.Candidate
import game.idle.autopilot.EndlessAction
import game.idle.autopilot.LunaCarried
import game.idle.autopilot.LunaClicks
import game.idle.flow.Preconditions
import game.idle.flow.WorkSpot
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
import io.luna.game.model.mob.Skill
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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
    private val anvilHere = Candidate(anvilId, anvilTile, 0, usableFromHere = true, besideAnvil)

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
    fun `a walking player is busy`() {
        val player = login()
        player.walking.addStep(Direction.EAST)

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
    fun `fewer bars than the item takes are nothing to do, neither a lack nor a bar to use`() {
        val player = login()
        player.skill(Skill.SMITHING).level = 18
        player.inventory.add(Item(hammer))
        player.inventory.add(Item(bronzeBar, 4))
        val step = SmithStep(BarType.BRONZE, SmithingTable.PLATEBODY, 10, WorkSpot.At(area.anchor))

        val needed = Preconditions.check(step.needs(), LunaCarried(player))

        assertEquals(null to null, needed to smither(player, SmithingTable.PLATEBODY).look().barSlot)
    }

    @Test
    fun `an anvil next to the player is used from where they stand`() {
        TestWorld.place(anvilId, anvilTile)

        assertEquals(listOf(anvilHere), smither(login(besideAnvil)).look().anvils)
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
    fun `walking to an anvil heads for its approach tile`() {
        val player = login()

        smither(player).walkTo(anvilHere)

        assertEquals(besideAnvil, player.navigator.currentTarget)
    }

    @Test
    fun `a walk back goes to the area's anchor`() {
        assertEquals(Tile(3200, 3200), smither(login(Position(3220, 3200))).anchor())
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
    fun `interrupting breaks off the smithing in progress`() {
        val player = login()
        player.submitAction(EndlessAction(player))
        TestWorld.tick()

        smither(player).interrupt()
        TestWorld.tick()

        assertFalse(LunaClicks.isActing(player))
    }
}
