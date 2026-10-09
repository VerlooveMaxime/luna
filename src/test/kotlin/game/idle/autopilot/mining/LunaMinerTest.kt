package game.idle.autopilot.mining

import api.predef.mining
import game.idle.autopilot.EndlessAction
import game.idle.autopilot.LunaClicks
import game.idle.location.Area
import game.idle.location.Tile
import game.skill.mining.Ore
import game.testworld.TestWorld
import io.luna.game.event.impl.ObjectClickEvent.ObjectFirstClickEvent
import io.luna.game.model.Direction
import io.luna.game.model.Position
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.overlay.OverlayType
import io.luna.game.model.mob.overlay.StandardInterface
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LunaMinerTest {

    private val anchor = Position(3200, 3200)
    private val rockTile = Position(3203, 3200)
    private val besideRock = Position(3202, 3200)
    private val copperRock = 2090
    private val islandCopperRock = 3042
    private val tinRock = 2094
    private val emptyRock = 450
    private val bronzePickaxe = 1265
    private val copperOre = 436
    private val area = Area(Tile(anchor.x, anchor.y), radius = 10)
    private val rockHere = RockCandidate(copperRock, rockTile, 0, usableFromHere = true, besideRock)

    private fun login(position: Position = anchor): Player = TestWorld.login("miner", position)

    private fun miner(player: Player) = LunaMiner(player, Ore.COPPER, area)

    private fun recordClicks(): MutableList<Int> {
        val clicked = mutableListOf<Int>()
        TestWorld.listen(ObjectFirstClickEvent::class.java) { clicked += it.gameObject.id }
        return clicked
    }

    @AfterEach
    fun resetWorld() = TestWorld.reset()

    @Test
    fun `an idle player with no window open is not busy`() {
        assertFalse(miner(login()).isBusy())
    }

    @Test
    fun `a walking player is busy`() {
        val player = login()
        player.walking.addStep(Direction.EAST)

        assertTrue(miner(player).isBusy())
    }

    @Test
    fun `an open window keeps the player busy`() {
        val player = login()
        player.overlays.open(StandardInterface(5292))

        assertTrue(miner(player).isBusy())
    }

    @Test
    fun `the view carries the player's mining level`() {
        val player = login()
        player.mining.level = 15

        assertEquals(15, miner(player).look().miningLevel)
    }

    @Test
    fun `a player without a pickaxe has no usable pickaxe`() {
        assertFalse(miner(login()).look().hasUsablePickaxe)
    }

    @Test
    fun `a pickaxe in the inventory is a usable pickaxe`() {
        val player = login()
        player.inventory.add(Item(bronzePickaxe))

        assertTrue(miner(player).look().hasUsablePickaxe)
    }

    @Test
    fun `a full inventory shows in the view`() {
        val player = login()
        player.inventory.add(Item(copperOre, 28))

        assertTrue(miner(player).look().inventoryFull)
    }

    @Test
    fun `a player inside the work area is at the location`() {
        assertTrue(miner(login()).look().atLocation)
    }

    @Test
    fun `a player outside the work area is not at the location`() {
        assertFalse(miner(login(Position(3220, 3200))).look().atLocation)
    }

    @Test
    fun `a rock out of reach is approached from the nearest tile it can be mined from`() {
        TestWorld.place(copperRock, rockTile)

        val rocks = miner(login()).look().rocks

        assertEquals(listOf(RockCandidate(copperRock, rockTile, 2, usableFromHere = false, besideRock)), rocks)
    }

    @Test
    fun `a rock next to the player is mined from where they stand`() {
        TestWorld.place(copperRock, rockTile)

        assertEquals(listOf(rockHere), miner(login(besideRock)).look().rocks)
    }

    @Test
    fun `Tutorial Island's copper rocks count as copper rocks`() {
        TestWorld.place(islandCopperRock, rockTile)

        assertEquals(listOf(islandCopperRock), miner(login(besideRock)).look().rocks.map { it.objectId })
    }

    @Test
    fun `rocks of other ores are left out`() {
        TestWorld.place(tinRock, rockTile)

        assertEquals(emptyList<RockCandidate>(), miner(login()).look().rocks)
    }

    @Test
    fun `a rock only another player sees is left out`() {
        val player = login()
        TestWorld.placeFor(TestWorld.login("other", anchor), copperRock, rockTile)

        assertEquals(emptyList<RockCandidate>(), miner(player).look().rocks)
    }

    @Test
    fun `mining clicks the rock the candidate names`() {
        val player = login(besideRock)
        TestWorld.place(copperRock, rockTile)
        val clicked = recordClicks()

        miner(player).mine(rockHere)
        TestWorld.tick()

        assertEquals(listOf(copperRock), clicked)
    }

    @Test
    fun `a rock emptied since the look is not clicked`() {
        val player = login(besideRock)
        TestWorld.place(emptyRock, rockTile)
        val clicked = recordClicks()

        miner(player).mine(rockHere)
        TestWorld.tick()

        assertEquals(emptyList<Int>(), clicked)
    }

    @Test
    fun `a rock only another player sees is not clicked`() {
        val player = login(besideRock)
        TestWorld.placeFor(TestWorld.login("other", anchor), copperRock, rockTile)
        val clicked = recordClicks()

        miner(player).mine(rockHere)
        TestWorld.tick()

        assertEquals(emptyList<Int>(), clicked)
    }

    @Test
    fun `walking to a rock heads for its approach tile`() {
        val player = login()

        miner(player).walkTo(rockHere)

        assertEquals(besideRock, player.navigator.currentTarget)
    }

    @Test
    fun `walking to a rock closes the open window`() {
        val player = login()
        player.overlays.open(StandardInterface(5292))

        miner(player).walkTo(rockHere)

        assertFalse(OverlayType.WIDGET_STANDARD in player.overlays.overlayMap)
    }

    @Test
    fun `walking back heads for the work spot`() {
        val player = login(Position(3220, 3200))

        miner(player).walkToLocation()

        assertEquals(anchor, player.navigator.currentTarget)
    }

    @Test
    fun `the ores counted are the step's kind only`() {
        val player = login()
        player.inventory.add(Item(copperOre, 3))
        player.inventory.add(Item(438))

        assertEquals(3, miner(player).ores())
    }

    @Test
    fun `stopping ends the mining in progress`() {
        val player = login()
        player.submitAction(EndlessAction(player))
        TestWorld.tick()

        miner(player).stop()
        TestWorld.tick()

        assertFalse(LunaClicks.isActing(player))
    }
}
