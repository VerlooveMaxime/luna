package game.idle.autopilot

import game.idle.location.Area
import game.idle.location.Tile
import game.idle.movement.Doorways
import game.testworld.TestWorld
import io.luna.game.model.Position
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.interact.InteractionPolicy
import io.luna.game.model.mob.interact.InteractionPolicy.STANDARD_SIZE
import io.luna.game.model.mob.interact.InteractionType
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NpcsAroundTest {

    private val anchor = Position(3200, 3200)
    private val chicken = 41
    private val cow = 81
    private val area = Area(Tile.of(anchor), radius = 10)

    @AfterEach
    fun resetWorld() = TestWorld.reset()

    private fun login(position: Position = anchor): Player = TestWorld.login("looker", position)

    private fun chickens(player: Player, policy: InteractionPolicy = STANDARD_SIZE, doorways: Doorways = Doorways.NONE) =
        NpcsAround(player, area, accepts = { it.id == chicken }, policy = { policy }, doorways)

    private fun candidate(npc: Npc, distance: Int, usableFromHere: Boolean, approach: Position, attacking: Boolean = false) =
        Candidate(npc.index, npc.position, distance, usableFromHere, approach, attacking)

    @Test
    fun `a player inside the area is at the location`() {
        assertTrue(chickens(login()).atLocation())
    }

    @Test
    fun `a player outside the area is not at the location`() {
        assertFalse(chickens(login(Position(3220, 3200))).atLocation())
    }

    @Test
    fun `an npc next to the player is reached from where they stand`() {
        val npc = TestWorld.spawnNpc(chicken, Position(3201, 3200))

        assertEquals(listOf(candidate(npc, distance = 0, usableFromHere = true, approach = anchor)), chickens(login()).candidates())
    }

    @Test
    fun `an npc behind a shut door of the web is walked up to through it, the door named`() {
        val doorways = WalledDoor.build()
        val npc = TestWorld.spawnNpc(chicken, Position(3207, 3200))

        val found = chickens(login(), doorways = doorways).candidates()

        assertEquals(listOf(candidate(npc, distance = 12, usableFromHere = false, approach = Position(3206, 3200)).copy(doors = listOf(WalledDoor.shut))), found)
    }

    @Test
    fun `an npc out of reach is walked up to`() {
        val npc = TestWorld.spawnNpc(chicken, Position(3205, 3200))

        val found = chickens(login()).candidates()

        assertEquals(listOf(candidate(npc, distance = 4, usableFromHere = false, approach = Position(3204, 3200))), found)
    }

    @Test
    fun `an npc diagonal to the player is walked up to, as the click needs it faced`() {
        TestWorld.spawnNpc(chicken, Position(3201, 3201))

        val found = chickens(login()).candidates().single()

        assertEquals(false to 1, found.usableFromHere to found.distance)
    }

    @Test
    fun `under a line-of-sight policy an npc in range is reached from where the player stands`() {
        TestWorld.spawnNpc(chicken, Position(3205, 3200))

        val found = chickens(login(), InteractionPolicy(InteractionType.LINE_OF_SIGHT, 5)).candidates().single()

        assertTrue(found.usableFromHere)
    }

    @Test
    fun `an npc the step does not accept is left out`() {
        TestWorld.spawnNpc(cow, Position(3201, 3200))

        assertEquals(emptyList<Candidate>(), chickens(login()).candidates())
    }

    @Test
    fun `an npc fighting the player is marked as attacking`() {
        val player = login()
        TestWorld.spawnNpc(chicken, Position(3201, 3200)).combat.target = player

        assertTrue(chickens(player).candidates().single().attacking)
    }

    @Test
    fun `too far from the area nothing is found`() {
        TestWorld.spawnNpc(chicken, Position(3201, 3200))

        assertEquals(emptyList<Candidate>(), chickens(login(Position(3250, 3200))).candidates())
    }

    @Test
    fun `the npc the look saw is found again by its index`() {
        val npc = TestWorld.spawnNpc(chicken, Position(3201, 3200))
        val around = chickens(login())

        assertSame(npc, around.find(around.candidates().single()))
    }

    @Test
    fun `an npc gone since the look is not found`() {
        val npc = TestWorld.spawnNpc(chicken, Position(3201, 3200))
        val around = chickens(login())
        val seen = around.candidates().single()
        TestWorld.world.npcs.remove(npc)

        assertNull(around.find(seen))
    }

    @Test
    fun `whatever npc holds the index is found, for the step to judge`() {
        TestWorld.spawnNpc(chicken, Position(3201, 3200))
        val other = TestWorld.spawnNpc(cow, Position(3202, 3200))
        val around = chickens(login())

        val found = around.find(around.candidates().single().copy(id = other.index))

        assertSame(other, found)
    }
}
