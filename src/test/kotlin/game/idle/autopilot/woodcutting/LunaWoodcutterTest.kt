package game.idle.autopilot.woodcutting

import game.idle.autopilot.Candidate
import game.idle.autopilot.EndlessAction
import game.idle.autopilot.LunaClicks
import game.idle.location.Area
import game.idle.location.Tile
import game.idle.movement.Doorways
import game.skill.woodcutting.cutTree.Tree
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

class LunaWoodcutterTest {

    private val anchor = Position(3200, 3200)
    private val treeTile = Position(3203, 3200)
    private val besideTree = Position(3202, 3200)
    private val normalTree = 1276
    private val normalStump = 1342
    private val oakTree = 1281
    private val logs = 1511
    private val area = Area(Tile(anchor.x, anchor.y), radius = 10)
    private val normalTreeHere = Candidate(normalTree, treeTile, 0, usableFromHere = true, besideTree)

    private fun login(position: Position = anchor): Player = TestWorld.login("lumberjack", position)

    private fun woodcutter(player: Player) = LunaWoodcutter(player, Tree.NORMAL, area, Doorways.NONE)

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

        assertTrue(woodcutter(player).isBusy())
    }

    @Test
    fun `a tree next to the player is cut from where they stand`() {
        TestWorld.place(normalTree, treeTile)

        val trees = woodcutter(login(besideTree)).look().trees

        assertEquals(listOf(normalTreeHere), trees)
    }

    @Test
    fun `a tree diagonal to the player is walked to, since the click wants a side`() {
        TestWorld.place(normalTree, Position(3201, 3201))

        val tree = woodcutter(login()).look().trees.single()

        assertEquals(false to 1, tree.usableFromHere to tree.distance)
    }

    @Test
    fun `trees of other kinds than the step's are left out`() {
        TestWorld.place(oakTree, treeTile)

        assertEquals(emptyList<Candidate>(), woodcutter(login()).look().trees)
    }

    @Test
    fun `chopping clicks the tree the candidate names`() {
        val player = login(besideTree)
        TestWorld.place(normalTree, treeTile)
        val clicked = recordClicks()

        woodcutter(player).chop(normalTreeHere)
        TestWorld.tick()

        assertEquals(listOf(normalTree), clicked)
    }

    @Test
    fun `a tree cut down since the look is not clicked`() {
        val player = login(besideTree)
        TestWorld.place(normalStump, treeTile)
        val clicked = recordClicks()

        woodcutter(player).chop(normalTreeHere)
        TestWorld.tick()

        assertEquals(emptyList<Int>(), clicked)
    }

    @Test
    fun `walking to a tree heads for its approach tile`() {
        val player = login()

        woodcutter(player).walkTo(normalTreeHere)

        assertEquals(besideTree, player.navigator.currentTarget)
    }

    @Test
    fun `a walk back goes to the area's anchor`() {
        assertEquals(Tile(3200, 3200), woodcutter(login(Position(3220, 3200))).anchor())
    }

    @Test
    fun `the logs counted are the step's kind only`() {
        val player = login()
        player.inventory.add(Item(logs, 3))
        player.inventory.add(Item(1521))

        assertEquals(3, woodcutter(player).logs())
    }

    @Test
    fun `interrupting breaks off the chop in progress`() {
        val player = login()
        player.submitAction(EndlessAction(player))
        TestWorld.tick()

        woodcutter(player).interrupt()
        TestWorld.tick()

        assertFalse(LunaClicks.isActing(player))
    }
}
