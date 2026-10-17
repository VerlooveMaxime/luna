package game.idle.autopilot.smelting

import game.idle.autopilot.Candidate
import game.idle.autopilot.EndlessAction
import game.idle.autopilot.LunaCarried
import game.idle.autopilot.LunaClicks
import game.idle.flow.Preconditions
import game.idle.flow.WorkSpot
import game.idle.location.Area
import game.idle.location.Tile
import game.idle.movement.Doorways
import game.skill.smithing.BarType
import game.testworld.TestWorld
import io.luna.game.event.impl.UseItemEvent.ItemOnObjectEvent
import io.luna.game.model.Direction
import io.luna.game.model.Position
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LunaSmelterTest {

    private val anchor = Position(3200, 3200)
    private val islandFurnace = 3044
    private val furnaceTile = Position(3204, 3200)
    /** The furnace opens to one side only; placed facing north, that is its south side. */
    private val besideFurnace = Position(3205, 3199)
    private val copperOre = 436
    private val tinOre = 438
    private val ironOre = 440
    private val coal = 453
    private val area = Area(Tile(anchor.x, anchor.y), radius = 10)

    private fun login(position: Position = anchor): Player = TestWorld.login("smelter", position)

    private fun smelter(player: Player, bar: BarType = BarType.BRONZE) = LunaSmelter(player, bar, area, Doorways.NONE)

    @AfterEach
    fun resetWorld() = TestWorld.reset()

    @Test
    fun `a walking player is busy`() {
        val player = login()
        player.walking.addStep(Direction.EAST)

        assertTrue(smelter(player).isBusy())
    }

    @Test
    fun `with both ores for a bar the view holds the ore to use`() {
        val player = login()
        player.inventory.add(Item(tinOre))
        player.inventory.add(Item(copperOre))

        assertEquals(1, smelter(player).look().oreSlot)
    }

    @Test
    fun `with one of the two ores missing there is no ore to use`() {
        val player = login()
        player.inventory.add(Item(copperOre))

        assertNull(smelter(player).look().oreSlot)
    }

    @Test
    fun `steel is smelted from its coal, since iron ore names iron`() {
        val player = login()
        player.inventory.add(Item(ironOre))
        player.inventory.add(Item(coal, 2))

        assertEquals(1, smelter(player, BarType.STEEL).look().oreSlot)
    }

    @Test
    fun `steel needs two coal with its iron ore`() {
        val player = login()
        player.inventory.add(Item(ironOre))
        player.inventory.add(Item(coal))

        assertNull(smelter(player, BarType.STEEL).look().oreSlot)
    }

    @Test
    fun `steel with iron ore and no coal is nothing to do, neither a lack nor an ore to use`() {
        val player = login()
        player.inventory.add(Item(ironOre))
        val step = SmeltStep(BarType.STEEL, 10, WorkSpot.At(area.anchor))

        val needed = Preconditions.check(step.needs(), LunaCarried(player))

        assertEquals(null to null, needed to smelter(player, BarType.STEEL).look().oreSlot)
    }

    @Test
    fun `Tutorial Island's furnace is found, its whole footprint in the way`() {
        TestWorld.place(islandFurnace, furnaceTile)

        val furnaces = smelter(login()).look().furnaces

        assertEquals(listOf(islandFurnace), furnaces.map { it.id })
        assertFalse(furnaces.single().usableFromHere)
    }

    @Test
    fun `smelting uses the ore on the furnace`() {
        val player = login(besideFurnace)
        player.inventory.add(Item(copperOre))
        player.inventory.add(Item(tinOre))
        TestWorld.place(islandFurnace, furnaceTile)
        val used = mutableListOf<Int>()
        TestWorld.listen(ItemOnObjectEvent::class.java) { used += it.usedItemId }
        val furnace = smelter(player).look().furnaces.single()

        smelter(player).smelt(furnace, slot = 0)
        TestWorld.tick(10)

        assertEquals(listOf(copperOre), used)
    }

    @Test
    fun `a furnace gone since the look is not used`() {
        val player = login()
        player.inventory.add(Item(copperOre))
        val used = mutableListOf<Int>()
        TestWorld.listen(ItemOnObjectEvent::class.java) { used += it.usedItemId }

        smelter(player).smelt(Candidate(islandFurnace, furnaceTile, distance = 0, usableFromHere = true, approach = anchor), slot = 0)
        TestWorld.tick(10)

        assertEquals(emptyList<Int>(), used)
    }

    @Test
    fun `an empty slot is not used`() {
        val player = login()
        TestWorld.place(islandFurnace, furnaceTile)
        val used = mutableListOf<Int>()
        TestWorld.listen(ItemOnObjectEvent::class.java) { used += it.usedItemId }
        val furnace = smelter(player).look().furnaces.single()

        smelter(player).smelt(furnace, slot = 0)
        TestWorld.tick(10)

        assertEquals(emptyList<Int>(), used)
    }

    @Test
    fun `a walk back goes to the area's anchor`() {
        assertEquals(Tile(3200, 3200), smelter(login(Position(3220, 3200))).anchor())
    }

    @Test
    fun `the bars counted are the step's bar`() {
        val player = login()
        player.inventory.add(Item(2349, 2))
        player.inventory.add(Item(2351))

        assertEquals(2, smelter(player).bars())
    }

    @Test
    fun `interrupting breaks off the smelting in progress`() {
        val player = login()
        player.submitAction(EndlessAction(player))
        TestWorld.tick()

        smelter(player).interrupt()
        TestWorld.tick()

        assertFalse(LunaClicks.isActing(player))
    }
}
