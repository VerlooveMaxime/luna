package game.idle.autopilot.fishing

import game.idle.autopilot.Candidate
import game.idle.autopilot.EndlessAction
import game.idle.autopilot.LunaClicks
import game.idle.location.Area
import game.idle.location.Tile
import game.idle.movement.Doorways
import game.skill.fishing.catchFish.Fish
import game.skill.fishing.catchFish.Tool
import game.testworld.TestWorld
import io.luna.game.event.impl.NpcClickEvent.NpcFirstClickEvent
import io.luna.game.event.impl.NpcClickEvent.NpcSecondClickEvent
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
    private val rawShrimps = 317
    private val rawAnchovies = 321
    private val netSpot = 316
    private val rodSpot = 309

    @AfterEach
    fun resetWorld() = TestWorld.reset()

    private fun login(position: Position = anchor): Player = TestWorld.login("fisher", position)

    private val fishingBait = 313

    private fun fisher(player: Player, tool: Tool = Tool.SMALL_NET, fish: Fish = Fish.SHRIMP) = LunaFisher(player, FishingCatch(fish, FishingMethod(tool)), area, Doorways.NONE)

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
    fun `the view sees the player in the area and the spots of the method`() {
        val player = login()
        val spot = TestWorld.spawnNpc(netSpot, Position(3201, 3200))
        TestWorld.spawnNpc(rodSpot, Position(3203, 3200))

        val view = fisher(player).look()

        assertEquals(
            FishingView(atLocation = true, spots = listOf(
                Candidate(spot.index, Position(3201, 3200), distance = 0, usableFromHere = true, approach = anchor),
            )),
            view,
        )
    }

    @Test
    fun `out of bait, an open window does not keep the player busy, so the step can say why it stopped`() {
        val player = login()
        player.inventory.add(Item(Tool.FISHING_ROD.id))
        player.overlays.open(StandardInterface(5292))

        assertFalse(fisher(player, Tool.FISHING_ROD).isBusy())
    }

    @Test
    fun `with the rod and bait, an open window keeps a rod fisher busy`() {
        val player = login()
        player.inventory.add(Item(Tool.FISHING_ROD.id))
        player.inventory.add(Item(fishingBait, 5))
        player.overlays.open(StandardInterface(5292))

        assertTrue(fisher(player, Tool.FISHING_ROD).isBusy())
    }

    @Test
    fun `without the tool, an open window does not keep the player busy, so the step can say why it stopped`() {
        val player = login()
        player.overlays.open(StandardInterface(5292))

        assertFalse(fisher(player).isBusy())
    }

    @Test
    fun `with a full bag, Luna's dialogue saying so does not keep the player busy, so the step can end`() {
        val player = login()
        player.inventory.add(Item(Tool.SMALL_NET.id))
        player.inventory.add(Item(rawShrimps, 27))
        player.overlays.open(StandardInterface(5292))

        assertFalse(fisher(player).isBusy())
    }

    @Test
    fun `a player still acting with a full bag stays busy, so a last cast lands first`() {
        val player = login()
        player.inventory.add(Item(Tool.SMALL_NET.id))
        player.inventory.add(Item(rawShrimps, 27))
        player.walking.addStep(Direction.EAST)

        assertTrue(fisher(player).isBusy())
    }

    @Test
    fun `a rod fishes a sea spot through its second option`() {
        val player = login()
        TestWorld.spawnNpc(netSpot, Position(3201, 3200))
        val clicked = mutableListOf<String>()
        TestWorld.listen(NpcFirstClickEvent::class.java) { clicked += "first ${it.targetNpc.id}" }
        TestWorld.listen(NpcSecondClickEvent::class.java) { clicked += "second ${it.targetNpc.id}" }

        val rod = fisher(player, Tool.FISHING_ROD)
        rod.fish(rod.look().spots.single())
        TestWorld.tick()

        assertEquals(listOf("second $netSpot"), clicked)
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

        fisher(player).fish(Candidate(man.index, man.position, distance = 0, usableFromHere = true, approach = anchor))
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
    fun `a walk back goes to the area's anchor`() {
        assertEquals(Tile(3200, 3200), fisher(login(Position(3220, 3200))).anchor())
    }

    @Test
    fun `catches count the fish the step is for, not what else the net pulls up`() {
        val player = login()
        player.inventory.add(Item(rawShrimps, 2))
        player.inventory.add(Item(rawAnchovies))
        player.inventory.add(Item(1511))

        assertEquals(2, fisher(player).catches())
    }

    @Test
    fun `a step for the method's other fish counts that one`() {
        val player = login()
        player.inventory.add(Item(rawShrimps, 2))
        player.inventory.add(Item(rawAnchovies))

        assertEquals(1, fisher(player, fish = Fish.ANCHOVY).catches())
    }

    @Test
    fun `interrupting breaks off the fishing in progress`() {
        val player = login()
        player.submitAction(EndlessAction(player))
        TestWorld.tick()

        fisher(player).interrupt()
        TestWorld.tick()

        assertFalse(LunaClicks.isActing(player))
    }

    @Test
    fun `everything the method pulls up counts as pulled up`() {
        val player = login()
        player.inventory.add(Item(rawShrimps, 2))
        player.inventory.add(Item(rawAnchovies))
        player.inventory.add(Item(1511))

        assertEquals(3, fisher(player).pulledUp())
    }
}
