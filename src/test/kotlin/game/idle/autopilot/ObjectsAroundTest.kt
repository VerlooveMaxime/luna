package game.idle.autopilot

import game.idle.location.Area
import game.idle.location.Tile
import game.idle.movement.Doorways
import game.testworld.TestWorld
import io.luna.game.model.Position
import io.luna.game.model.mob.Player
import io.luna.game.model.`object`.ObjectDirection
import io.luna.game.model.`object`.ObjectType
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ObjectsAroundTest {

    private val anchor = Position(3200, 3200)
    private val treeTile = Position(3203, 3200)
    private val besideTree = Position(3202, 3200)
    private val normalTree = 1276
    private val normalStump = 1342
    private val oakTree = 1281
    private val range = 3039
    private val wall = 1902
    private val area = Area(Tile.of(anchor), radius = 10)

    @AfterEach
    fun resetWorld() = TestWorld.reset()

    private fun login(position: Position = anchor): Player = TestWorld.login("looker", position)

    private fun trees(player: Player, doorways: Doorways = Doorways.NONE) = ObjectsAround(player, area, setOf(normalTree), doorways)

    @Test
    fun `a player inside the area is at the location`() {
        assertTrue(trees(login()).atLocation())
    }

    @Test
    fun `a player outside the area is not at the location`() {
        assertFalse(trees(login(Position(3220, 3200))).atLocation())
    }

    @Test
    fun `an object next to the player is usable from where they stand`() {
        TestWorld.place(normalTree, treeTile)

        val found = trees(login(besideTree)).candidates()

        assertEquals(listOf(Candidate(normalTree, treeTile, distance = 0, usableFromHere = true, approach = besideTree)), found)
    }

    @Test
    fun `an object out of reach is approached from the nearest tile it is used from`() {
        TestWorld.place(normalTree, treeTile)

        val found = trees(login()).candidates()

        assertEquals(listOf(Candidate(normalTree, treeTile, distance = 2, usableFromHere = false, approach = besideTree)), found)
    }

    @Test
    fun `an object diagonal to the player is walked to, since the click wants a side`() {
        TestWorld.place(normalTree, Position(3201, 3201))

        val found = trees(login()).candidates().single()

        assertEquals(false to 1, found.usableFromHere to found.distance)
    }

    /** Tutorial Island's kitchen: the range lies across the back of an alcove whose side walls flank the front tiles. */
    @Test
    fun `a tree behind a shut door of the web is walked to through it, six steps dearer, the door named`() {
        val doorways = WalledDoor.build()
        TestWorld.place(normalTree, Position(3207, 3200))

        val found = trees(login(), doorways).candidates()

        assertEquals(listOf(Candidate(normalTree, Position(3207, 3200), 12, false, Position(3206, 3200), crossings = listOf(WalledDoor.shut))), found)
    }

    @Test
    fun `a tree behind a shut door the web does not know is out of reach`() {
        WalledDoor.build()
        TestWorld.place(normalTree, Position(3207, 3200))

        assertEquals(emptyList<Candidate>(), trees(login()).candidates())
    }

    @Test
    fun `a long object at the back of an alcove is walked up to between the walls`() {
        TestWorld.place(range, Position(3205, 3200), direction = ObjectDirection.SOUTH)
        TestWorld.place(wall, Position(3205, 3201), ObjectType.STRAIGHT_WALL, ObjectDirection.WEST)
        TestWorld.place(wall, Position(3206, 3201), ObjectType.STRAIGHT_WALL, ObjectDirection.EAST)

        val found = ObjectsAround(login(Position(3205, 3205)), area, setOf(range), Doorways.NONE).candidates().single()

        assertTrue(found.approach in listOf(Position(3205, 3201), Position(3206, 3201)))
    }

    @Test
    fun `objects of other ids are left out`() {
        TestWorld.place(oakTree, treeTile)

        assertEquals(emptyList<Candidate>(), trees(login()).candidates())
    }

    @Test
    fun `an object only another player sees is left out`() {
        val player = login()
        TestWorld.placeFor(TestWorld.login("other", anchor), normalTree, treeTile)

        assertEquals(emptyList<Candidate>(), trees(player).candidates())
    }

    @Test
    fun `too far from the area nothing is found`() {
        TestWorld.place(normalTree, treeTile)

        assertEquals(emptyList<Candidate>(), trees(login(Position(3250, 3200))).candidates())
    }

    @Test
    fun `the object the look saw is found again`() {
        val tree = TestWorld.place(normalTree, treeTile)
        val around = trees(login())

        assertSame(tree, around.find(around.candidates().single()))
    }

    @Test
    fun `an object gone since the look is not found`() {
        val tree = TestWorld.place(normalTree, treeTile)
        val around = trees(login())
        val seen = around.candidates().single()
        TestWorld.world.objects.unregister(tree)

        assertNull(around.find(seen))
    }

    @Test
    fun `another object standing where it stood is not found`() {
        val around = trees(login())
        TestWorld.place(normalStump, treeTile)

        assertNull(around.find(Candidate(normalTree, treeTile, distance = 2, usableFromHere = false, approach = besideTree)))
    }

    @Test
    fun `an object only another player sees is not found`() {
        val player = login()
        TestWorld.placeFor(TestWorld.login("other", anchor), normalTree, treeTile)

        assertNull(trees(player).find(Candidate(normalTree, treeTile, distance = 2, usableFromHere = false, approach = besideTree)))
    }
}
