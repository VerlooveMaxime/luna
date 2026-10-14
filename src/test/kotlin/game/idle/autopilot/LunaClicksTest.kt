package game.idle.autopilot

import engine.widget.skill.LevelUpData
import engine.widget.skill.LevelUpInterface
import game.idle.movement.approachTiles
import game.testworld.TestWorld
import io.luna.game.action.ActionType
import io.luna.game.event.impl.ButtonClickEvent
import io.luna.game.event.impl.ObjectClickEvent.ObjectFirstClickEvent
import io.luna.game.model.Direction
import io.luna.game.model.Position
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.Skill
import io.luna.game.model.mob.overlay.OverlayType
import io.luna.game.model.mob.overlay.StandardInterface
import io.luna.game.model.mob.overlay.WalkableInterface
import io.luna.game.model.`object`.ObjectDirection
import io.luna.game.model.`object`.ObjectType
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LunaClicksTest {

    private val boothId = 2213
    private val boothTile = Position(3200, 3200)
    private val nextToBooth = Position(3199, 3200)
    private val deadTreeId = 1286
    private val wallId = 1902
    private val walkTarget = Position(3210, 3200)
    private val button = 13717

    private fun player() = TestWorld.login("clicker", nextToBooth)

    private fun clickBooth(player: Player) {
        val booth = TestWorld.place(boothId, boothTile)
        LunaClicks.interact(player, ObjectFirstClickEvent(player, booth), booth, ObjectFirstClickEvent::class.java)
    }

    private fun recordBoothClicks(): MutableList<Int> {
        val clicked = mutableListOf<Int>()
        TestWorld.listen(ObjectFirstClickEvent::class.java) { clicked += it.gameObject.id }
        return clicked
    }

    private fun recordButtons(): MutableList<Int> {
        val pressed = mutableListOf<Int>()
        TestWorld.listen(ButtonClickEvent::class.java) { pressed += it.id }
        return pressed
    }

    private fun openWindow(player: Player) = player.overlays.open(StandardInterface(5292))

    /** Dead trees on [tile] and every tile around it. */
    private fun boxIn(tile: Position) =
        (approachTiles(tile, width = 1, height = 1, from = nextToBooth) + tile).forEach { TestWorld.place(deadTreeId, it) }

    private fun windowOpen(player: Player): Boolean = OverlayType.WIDGET_STANDARD in player.overlays.overlayMap

    @AfterEach
    fun resetWorld() = TestWorld.reset()

    @Test
    fun `a player standing still with nothing to do is not acting`() {
        assertFalse(LunaClicks.isActing(player()))
    }

    @Test
    fun `a walking player is acting`() {
        val player = player()
        player.walking.addStep(Direction.WEST)

        assertTrue(LunaClicks.isActing(player))
    }

    @Test
    fun `a player running a foreground action is acting`() {
        val player = player()
        player.submitAction(EndlessAction(player))

        assertTrue(LunaClicks.isActing(player))
    }

    @Test
    fun `a soft action alone does not keep a player acting`() {
        val player = player()
        player.submitAction(EndlessAction(player, ActionType.SOFT))

        assertFalse(LunaClicks.isActing(player))
    }

    @Test
    fun `an unlocked player whose controller allows the event may act`() {
        val player = player()
        val booth = TestWorld.place(boothId, boothTile)

        assertTrue(LunaClicks.mayAct(player, ObjectFirstClickEvent(player, booth)))
    }

    @Test
    fun `a locked player may not act`() {
        val player = player()
        val booth = TestWorld.place(boothId, boothTile)
        player.lock()

        assertFalse(LunaClicks.mayAct(player, ObjectFirstClickEvent(player, booth)))
    }

    @Test
    fun `a player whose controller refuses the event may not act`() {
        val player = player()
        val booth = TestWorld.place(boothId, boothTile)
        player.controllers.register(RefusingController(player))

        assertFalse(LunaClicks.mayAct(player, ObjectFirstClickEvent(player, booth)))
    }

    @Test
    fun `a click reaches the object's listeners on the next tick`() {
        val player = player()
        val clicked = recordBoothClicks()

        clickBooth(player)
        TestWorld.tick()

        assertEquals(listOf(boothId), clicked)
    }

    @Test
    fun `a click closes the open window first`() {
        val player = player()
        player.overlays.open(StandardInterface(5292))

        clickBooth(player)

        assertFalse(OverlayType.WIDGET_STANDARD in player.overlays.overlayMap)
    }

    @Test
    fun `interrupting breaks off the action in progress`() {
        val player = player()
        player.submitAction(EndlessAction(player))
        TestWorld.tick()

        LunaClicks.interrupt(player)
        TestWorld.tick()

        assertFalse(LunaClicks.isActing(player))
    }

    @Test
    fun `interrupting ends the walk the autopilot started`() {
        val player = player()
        player.navigator.navigate(Position(3220, 3200), false)
        TestWorld.tick()
        val stoppedAt = player.position

        LunaClicks.interrupt(player)
        TestWorld.tick(times = 3)

        assertEquals(stoppedAt, player.position)
    }

    @Test
    fun `a player who may not act does not click`() {
        val player = player()
        val clicked = recordBoothClicks()
        player.lock()

        clickBooth(player)
        TestWorld.tick()

        assertEquals(emptyList<Int>(), clicked)
    }

    @Test
    fun `an idle player with no window open is not busy`() {
        assertFalse(LunaClicks.busy(player()))
    }

    @Test
    fun `a walking player is busy`() {
        val player = player()
        player.walking.addStep(Direction.WEST)

        assertTrue(LunaClicks.busy(player))
    }

    @Test
    fun `an open window keeps the player busy`() {
        val player = player()
        openWindow(player)

        assertTrue(LunaClicks.busy(player))
    }

    @Test
    fun `a level-up dialogue does not keep the player busy`() {
        val player = player()
        player.overlays.open(LevelUpInterface(Skill.WOODCUTTING, 2, LevelUpData(4273, 4274, 4272)))

        assertFalse(LunaClicks.busy(player))
    }

    @Test
    fun `an overlay that is not a window does not keep the player busy`() {
        val player = player()
        player.overlays.open(WalkableInterface(197))

        assertFalse(LunaClicks.busy(player))
    }

    @Test
    fun `an idle player with no window open is not busy besides the kind's own`() {
        assertFalse(LunaClicks.busyBesides(player(), ownWindowOpen = false))
    }

    @Test
    fun `the kind's own window does not keep the player busy`() {
        val player = player()
        openWindow(player)

        assertFalse(LunaClicks.busyBesides(player, ownWindowOpen = true))
    }

    @Test
    fun `another window keeps the player busy whatever the kind's own`() {
        val player = player()
        openWindow(player)

        assertTrue(LunaClicks.busyBesides(player, ownWindowOpen = false))
    }

    @Test
    fun `acting keeps the player busy with their own window open`() {
        val player = player()
        player.submitAction(EndlessAction(player))

        assertTrue(LunaClicks.busyBesides(player, ownWindowOpen = true))
    }

    @Test
    fun `an idle player is not interacting`() {
        assertFalse(LunaClicks.interacting(player()))
    }

    @Test
    fun `a walking player is interacting`() {
        val player = player()
        player.walking.addStep(Direction.WEST)

        assertTrue(LunaClicks.interacting(player))
    }

    @Test
    fun `a click on its way to its target keeps the player interacting`() {
        val player = TestWorld.login("clicker", Position(3195, 3200))
        recordBoothClicks()

        clickBooth(player)

        assertTrue(LunaClicks.interacting(player))
    }

    @Test
    fun `an action that is no click does not keep the player interacting`() {
        val player = player()
        player.submitAction(EndlessAction(player))

        assertFalse(LunaClicks.interacting(player))
    }

    @Test
    fun `walking heads for a free tile`() {
        val player = player()

        LunaClicks.walk(player, walkTarget)

        assertEquals(walkTarget, player.navigator.currentTarget)
    }

    @Test
    fun `walking closes the open window like a click`() {
        val player = player()
        openWindow(player)

        LunaClicks.walk(player, walkTarget)

        assertFalse(windowOpen(player))
    }

    @Test
    fun `walking leaves the action in progress alone`() {
        val player = player()
        player.submitAction(EndlessAction(player))
        TestWorld.tick()

        LunaClicks.walk(player, walkTarget)

        assertTrue(player.actions.size(ActionType.WEAK) > 0)
    }

    @Test
    fun `a tile with a wall along one side is walked to itself`() {
        val player = player()
        TestWorld.place(wallId, walkTarget, ObjectType.STRAIGHT_WALL, ObjectDirection.NORTH)

        LunaClicks.walk(player, walkTarget)

        assertEquals(walkTarget, player.navigator.currentTarget)
    }

    @Test
    fun `walking to a taken tile heads for the nearest free tile next to it`() {
        val player = player()
        TestWorld.place(deadTreeId, walkTarget)

        LunaClicks.walk(player, walkTarget)

        assertEquals(Position(3209, 3200), player.navigator.currentTarget)
    }

    @Test
    fun `walking to a taken tile with no free tile next to it heads for the tile itself`() {
        val player = player()
        boxIn(walkTarget)

        LunaClicks.walk(player, walkTarget)

        assertEquals(walkTarget, player.navigator.currentTarget)
    }

    @Test
    fun `a step onto open ground is allowed`() {
        assertTrue(LunaClicks.canStep(TestWorld.world, Position(3210, 3210), Direction.EAST))
    }

    @Test
    fun `a step onto a tile something stands on is not`() {
        TestWorld.place(deadTreeId, Position(3211, 3210))

        assertFalse(LunaClicks.canStep(TestWorld.world, Position(3210, 3210), Direction.EAST))
    }

    @Test
    fun `a step through a wall is refused`() {
        TestWorld.place(wallId, Position(3211, 3210), ObjectType.STRAIGHT_WALL, ObjectDirection.WEST)

        assertFalse(LunaClicks.canStep(TestWorld.world, Position(3210, 3210), Direction.EAST))
    }

    @Test
    fun `posting reaches the event's listeners`() {
        val player = player()
        val pressed = recordButtons()

        LunaClicks.post(player, ButtonClickEvent(player, button))

        assertEquals(listOf(button), pressed)
    }

    @Test
    fun `posting leaves the open window alone`() {
        val player = player()
        openWindow(player)

        LunaClicks.post(player, ButtonClickEvent(player, button))

        assertTrue(windowOpen(player))
    }

    @Test
    fun `a player who may not act posts nothing`() {
        val player = player()
        val pressed = recordButtons()
        player.lock()

        LunaClicks.post(player, ButtonClickEvent(player, button))

        assertEquals(emptyList<Int>(), pressed)
    }

    @Test
    fun `posting with windows closed first closes the open window and reaches the listeners`() {
        val player = player()
        openWindow(player)
        val pressed = recordButtons()

        LunaClicks.postClosing(player, ButtonClickEvent(player, button))

        assertEquals(false to listOf(button), windowOpen(player) to pressed)
    }

    @Test
    fun `a player who may not act closes nothing and posts nothing`() {
        val player = player()
        openWindow(player)
        val pressed = recordButtons()
        player.controllers.register(RefusingController(player))

        LunaClicks.postClosing(player, ButtonClickEvent(player, button))

        assertEquals(true to emptyList<Int>(), windowOpen(player) to pressed)
    }
}
