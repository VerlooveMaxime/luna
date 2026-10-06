package game.idle.autopilot.fishing

import game.idle.autopilot.EndlessAction
import game.idle.autopilot.LunaClicks
import game.idle.location.Area
import game.idle.location.Tile
import game.testworld.TestWorld
import io.luna.game.event.impl.NpcClickEvent.NpcFirstClickEvent
import io.luna.game.model.Direction
import io.luna.game.model.Position
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.overlay.StandardInterface
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LunaFisherTest {

    private val anchor = Position(3200, 3200)
    private val area = Area(Tile(3200, 3200), radius = 10)
    private val smallNet = 303
    private val rawShrimps = 317
    private val rawAnchovies = 321
    private val netSpot = 316
    private val rodSpot = 309

    @AfterEach
    fun resetWorld() = TestWorld.reset()

    private fun login(position: Position = anchor): Player = TestWorld.login("fisher", position)

    private fun fisher(player: Player) = LunaFisher(player, FishingMethod.SHRIMP, area)

    @Test
    fun `an idle player is not busy`() {
        assertFalse(fisher(login()).isBusy())
    }

    @Test
    fun `a walking player is busy`() {
        val player = login()
        player.walking.addStep(Direction.EAST)

        assertTrue(fisher(player).isBusy())
    }

    @Test
    fun `an open window keeps the player busy`() {
        val player = login()
        player.overlays.open(StandardInterface(5292))

        assertTrue(fisher(player).isBusy())
    }

    @Test
    fun `the view sees the net, the level, the room and the spots of the method`() {
        val player = login()
        player.inventory.add(Item(smallNet))
        val spot = TestWorld.spawnNpc(netSpot, Position(3201, 3200))
        TestWorld.spawnNpc(rodSpot, Position(3203, 3200))

        val view = fisher(player).look()

        assertEquals(
            FishingView(hasTool = true, inventoryFull = false, atLocation = true, spots = listOf(
                SpotCandidate(spot.index, Position(3201, 3200), distance = 0, usableFromHere = true, approach = anchor),
            )),
            view,
        )
    }

    @Test
    fun `a spot out of reach is walked up to`() {
        val player = login()
        TestWorld.spawnNpc(netSpot, Position(3205, 3200))

        val spot = fisher(player).look().spots.single()

        assertEquals(listOf(4, false, Position(3204, 3200)), listOf(spot.distance, spot.usableFromHere, spot.approach))
    }

    @Test
    fun `a spot diagonal to the player is walked up to, as the click needs it faced`() {
        val player = login()
        TestWorld.spawnNpc(netSpot, Position(3201, 3201))

        val spot = fisher(player).look().spots.single()

        assertEquals(listOf(1, false), listOf(spot.distance, spot.usableFromHere))
    }

    @Test
    fun `without a net the view says so`() {
        assertFalse(fisher(login()).look().hasTool)
    }

    @Test
    fun `fishing clicks the spot like the client`() {
        val player = login()
        val spot = TestWorld.spawnNpc(netSpot, Position(3201, 3200))
        val clicked = mutableListOf<Int>()
        TestWorld.listen(NpcFirstClickEvent::class.java) { clicked += it.targetNpc.id }

        fisher(player).fish(fisher(player).look().spots.single())
        TestWorld.tick()

        assertEquals(listOf(spot.id), clicked)
    }

    @Test
    fun `a spot that moved since the look is not clicked`() {
        val player = login()
        TestWorld.spawnNpc(netSpot, Position(3201, 3200))
        val seenElsewhere = fisher(player).look().spots.single().copy(position = Position(3201, 3199))
        val clicked = mutableListOf<Int>()
        TestWorld.listen(NpcFirstClickEvent::class.java) { clicked += it.targetNpc.id }

        fisher(player).fish(seenElsewhere)
        TestWorld.tick()

        assertEquals(emptyList<Int>(), clicked)
    }

    @Test
    fun `another npc where the spot was is not clicked`() {
        val player = login()
        val man = TestWorld.spawnNpc(1, Position(3201, 3200))
        val clicked = mutableListOf<Int>()
        TestWorld.listen(NpcFirstClickEvent::class.java) { clicked += it.targetNpc.id }

        fisher(player).fish(SpotCandidate(man.index, man.position, distance = 0, usableFromHere = true, approach = anchor))
        TestWorld.tick()

        assertEquals(emptyList<Int>(), clicked)
    }

    @Test
    fun `a spot gone since the look is not clicked`() {
        val player = login()
        val spot = TestWorld.spawnNpc(netSpot, Position(3201, 3200))
        val seen = fisher(player).look().spots.single()
        val clicked = mutableListOf<Int>()
        TestWorld.listen(NpcFirstClickEvent::class.java) { clicked += it.targetNpc.id }
        TestWorld.world.npcs.remove(spot)

        fisher(player).fish(seen)
        TestWorld.tick()

        assertEquals(emptyList<Int>(), clicked)
    }

    @Test
    fun `walking to a spot heads for its approach tile`() {
        val player = login()
        TestWorld.spawnNpc(netSpot, Position(3205, 3200))

        fisher(player).walkTo(fisher(player).look().spots.single())

        assertEquals(Position(3204, 3200), player.navigator.currentTarget)
    }

    @Test
    fun `walking back heads for the anchor`() {
        val player = login(Position(3220, 3200))

        fisher(player).walkToLocation()

        assertEquals(anchor, player.navigator.currentTarget)
    }

    @Test
    fun `catches count every fish the method catches`() {
        val player = login()
        player.inventory.add(Item(rawShrimps, 2))
        player.inventory.add(Item(rawAnchovies))
        player.inventory.add(Item(1511))

        assertEquals(3, fisher(player).catches())
    }

    @Test
    fun `stopping ends the fishing in progress`() {
        val player = login()
        player.submitAction(EndlessAction(player))
        TestWorld.tick()

        fisher(player).stop()
        TestWorld.tick()

        assertFalse(LunaClicks.isActing(player))
    }

    @Test
    fun `telling the player sends a chat box line`() {
        val player = login()

        fisher(player).tell("No net.")

        assertEquals(listOf("No net."), TestWorld.chatbox(player))
    }
}
