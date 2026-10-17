package game.idle.autopilot.mining

import game.idle.autopilot.Candidate
import game.idle.autopilot.EndlessAction
import game.idle.autopilot.LunaClicks
import game.idle.location.Area
import game.idle.location.Tile
import game.idle.movement.Doorways
import game.skill.mining.Ore
import game.testworld.TestWorld
import io.luna.game.event.impl.ObjectClickEvent.ObjectFirstClickEvent
import io.luna.game.model.Direction
import io.luna.game.model.Position
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
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
    private val copperOre = 436
    private val area = Area(Tile(anchor.x, anchor.y), radius = 10)
    private val rockHere = Candidate(copperRock, rockTile, 0, usableFromHere = true, besideRock)

    private fun login(position: Position = anchor): Player = TestWorld.login("miner", position)

    private fun miner(player: Player) = LunaMiner(player, Ore.COPPER, area, Doorways.NONE)

    private fun recordClicks(): MutableList<Int> {
        val clicked = mutableListOf<Int>()
        TestWorld.listen(ObjectFirstClickEvent::class.java) { clicked += it.gameObject.id }
        return clicked
    }

    @AfterEach
    fun resetWorld() = TestWorld.reset()

    @Test
    fun `a walking player is busy`() {
        val player = login()
        player.walking.addStep(Direction.EAST)

        assertTrue(miner(player).isBusy())
    }

    @Test
    fun `a rock next to the player is mined from where they stand`() {
        TestWorld.place(copperRock, rockTile)

        assertEquals(listOf(rockHere), miner(login(besideRock)).look().rocks)
    }

    @Test
    fun `Tutorial Island's copper rocks count as copper rocks`() {
        TestWorld.place(islandCopperRock, rockTile)

        assertEquals(listOf(islandCopperRock), miner(login(besideRock)).look().rocks.map { it.id })
    }

    @Test
    fun `rocks of other ores are left out`() {
        TestWorld.place(tinRock, rockTile)

        assertEquals(emptyList<Candidate>(), miner(login()).look().rocks)
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
    fun `a walk back goes to the area's anchor`() {
        assertEquals(Tile(3200, 3200), miner(login(Position(3220, 3200))).anchor())
    }

    @Test
    fun `the ores counted are the step's kind only`() {
        val player = login()
        player.inventory.add(Item(copperOre, 3))
        player.inventory.add(Item(438))

        assertEquals(3, miner(player).ores())
    }

    @Test
    fun `interrupting breaks off the mining in progress`() {
        val player = login()
        player.submitAction(EndlessAction(player))
        TestWorld.tick()

        miner(player).interrupt()
        TestWorld.tick()

        assertFalse(LunaClicks.isActing(player))
    }
}
