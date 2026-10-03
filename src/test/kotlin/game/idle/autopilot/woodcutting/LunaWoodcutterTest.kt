package game.idle.autopilot.woodcutting

import api.predef.woodcutting
import engine.widget.skill.LevelUpData
import engine.widget.skill.LevelUpInterface
import game.idle.location.Area
import game.idle.location.Tile
import game.skill.woodcutting.cutTree.Tree
import game.testworld.TestWorld
import io.luna.game.event.impl.ObjectClickEvent.ObjectFirstClickEvent
import io.luna.game.model.Direction
import io.luna.game.model.Position
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.Skill
import io.luna.game.model.mob.overlay.OverlayType
import io.luna.game.model.mob.overlay.StandardInterface
import io.luna.game.model.mob.overlay.WalkableInterface
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
    private val bronzeAxe = 1351
    private val logs = 1511
    private val area = Area(Tile(anchor.x, anchor.y), radius = 10)
    private val spot = WoodcuttingSpot(Tree.NORMAL, area)
    private val normalTreeHere = TreeCandidate(normalTree, treeTile, Tree.NORMAL, 0, usableFromHere = true, besideTree)

    private fun login(position: Position = anchor): Player = TestWorld.login("lumberjack", position)

    private fun recordClicks(): MutableList<Int> {
        val clicked = mutableListOf<Int>()
        TestWorld.listen(ObjectFirstClickEvent::class.java) { clicked += it.gameObject.id }
        return clicked
    }

    @AfterEach
    fun resetWorld() = TestWorld.reset()

    @Test
    fun `an idle player with no window open is not busy`() {
        assertFalse(LunaWoodcutter(login(), spot).isBusy())
    }

    @Test
    fun `a walking player is busy`() {
        val player = login()
        player.walking.addStep(Direction.EAST)

        assertTrue(LunaWoodcutter(player, spot).isBusy())
    }

    @Test
    fun `an open window keeps the player busy`() {
        val player = login()
        player.overlays.open(StandardInterface(5292))

        assertTrue(LunaWoodcutter(player, spot).isBusy())
    }

    @Test
    fun `a level-up dialogue does not keep the player busy`() {
        val player = login()
        player.overlays.open(LevelUpInterface(Skill.WOODCUTTING, 2, LevelUpData(4273, 4274, 4272)))

        assertFalse(LunaWoodcutter(player, spot).isBusy())
    }

    @Test
    fun `an overlay that is not a window does not keep the player busy`() {
        val player = login()
        player.overlays.open(WalkableInterface(197))

        assertFalse(LunaWoodcutter(player, spot).isBusy())
    }

    @Test
    fun `the view carries the player's woodcutting level`() {
        val player = login()
        player.woodcutting.level = 15

        assertEquals(15, LunaWoodcutter(player, spot).look().woodcuttingLevel)
    }

    @Test
    fun `a player without an axe has no usable axe`() {
        assertFalse(LunaWoodcutter(login(), spot).look().hasUsableAxe)
    }

    @Test
    fun `an axe in the inventory is a usable axe`() {
        val player = login()
        player.inventory.add(Item(bronzeAxe))

        assertTrue(LunaWoodcutter(player, spot).look().hasUsableAxe)
    }

    @Test
    fun `the view counts only the logs in the inventory`() {
        val player = login()
        player.inventory.add(Item(bronzeAxe))
        player.inventory.add(Item(logs, 2))

        assertEquals(2, LunaWoodcutter(player, spot).look().logsInInventory)
    }

    @Test
    fun `a full inventory shows in the view`() {
        val player = login()
        player.inventory.add(Item(logs, 28))

        assertTrue(LunaWoodcutter(player, spot).look().inventoryFull)
    }

    @Test
    fun `a player inside the location's area is at the location`() {
        assertTrue(LunaWoodcutter(login(), spot).look().atLocation)
    }

    @Test
    fun `a player outside the location's area is not at the location`() {
        assertFalse(LunaWoodcutter(login(Position(3220, 3200)), spot).look().atLocation)
    }

    @Test
    fun `a tree out of reach is approached from the nearest tile it can be cut from`() {
        TestWorld.place(normalTree, treeTile)

        val trees = LunaWoodcutter(login(), spot).look().trees

        assertEquals(listOf(TreeCandidate(normalTree, treeTile, Tree.NORMAL, 2, usableFromHere = false, besideTree)), trees)
    }

    @Test
    fun `a tree next to the player is cut from where they stand`() {
        TestWorld.place(normalTree, treeTile)

        val trees = LunaWoodcutter(login(besideTree), spot).look().trees

        assertEquals(listOf(normalTreeHere), trees)
    }

    @Test
    fun `trees of kinds the spot does not cut are left out`() {
        TestWorld.place(oakTree, treeTile)

        assertEquals(emptyList<TreeCandidate>(), LunaWoodcutter(login(), spot).look().trees)
    }

    @Test
    fun `a tree only another player sees is left out`() {
        val player = login()
        TestWorld.placeFor(TestWorld.login("other", anchor), normalTree, treeTile)

        assertEquals(emptyList<TreeCandidate>(), LunaWoodcutter(player, spot).look().trees)
    }

    @Test
    fun `chopping clicks the tree the candidate names`() {
        val player = login(besideTree)
        TestWorld.place(normalTree, treeTile)
        val clicked = recordClicks()

        LunaWoodcutter(player, spot).chop(normalTreeHere)
        TestWorld.tick()

        assertEquals(listOf(normalTree), clicked)
    }

    @Test
    fun `a tree cut down since the look is not clicked`() {
        val player = login(besideTree)
        TestWorld.place(normalStump, treeTile)
        val clicked = recordClicks()

        LunaWoodcutter(player, spot).chop(normalTreeHere)
        TestWorld.tick()

        assertEquals(emptyList<Int>(), clicked)
    }

    @Test
    fun `a tree only another player sees is not clicked`() {
        val player = login(besideTree)
        TestWorld.placeFor(TestWorld.login("other", anchor), normalTree, treeTile)
        val clicked = recordClicks()

        LunaWoodcutter(player, spot).chop(normalTreeHere)
        TestWorld.tick()

        assertEquals(emptyList<Int>(), clicked)
    }

    @Test
    fun `walking to a tree heads for its approach tile`() {
        val player = login()

        LunaWoodcutter(player, spot).walkTo(normalTreeHere)

        assertEquals(besideTree, player.navigator.currentTarget)
    }

    @Test
    fun `walking to a tree closes the open window`() {
        val player = login()
        player.overlays.open(StandardInterface(5292))

        LunaWoodcutter(player, spot).walkTo(normalTreeHere)

        assertFalse(OverlayType.WIDGET_STANDARD in player.overlays.overlayMap)
    }

    @Test
    fun `walking back heads for the location's anchor`() {
        val player = login(Position(3220, 3200))

        LunaWoodcutter(player, spot).walkToLocation()

        assertEquals(anchor, player.navigator.currentTarget)
    }

    @Test
    fun `telling the player sends a chat box line`() {
        val player = login()

        LunaWoodcutter(player, spot).tell("You need an axe.")

        assertEquals(listOf("You need an axe."), TestWorld.chatbox(player))
    }
}
