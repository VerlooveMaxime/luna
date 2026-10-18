package game.idle.autopilot

import api.bot.webwalk.model.EdgeType
import engine.widget.skill.LevelUpData
import engine.widget.skill.LevelUpInterface
import game.idle.movement.DoorStep
import game.idle.movement.Doorway
import game.idle.movement.Doorways
import game.idle.movement.Step
import game.idle.movement.approachTiles
import game.testworld.TestWorld
import io.luna.game.action.ActionType
import io.luna.game.event.impl.ButtonClickEvent
import io.luna.game.event.impl.NpcClickEvent.NpcFirstClickEvent
import io.luna.game.event.impl.ObjectClickEvent
import io.luna.game.event.impl.ObjectClickEvent.ObjectFirstClickEvent
import io.luna.game.event.impl.ObjectClickEvent.ObjectSecondClickEvent
import io.luna.game.event.impl.ObjectClickEvent.ObjectThirdClickEvent
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
import org.junit.jupiter.api.Assertions.assertNull
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

    /** Ticks a walk of one tile up to an object takes to end, its click due in the tick after (measured on TestWorld). */
    private val arrivalTicks = 3

    /** Using objects: the player on [here], a door or a ladder [beside] them. */
    private val here = Position(3200, 3200)
    private val beside = Position(3201, 3200)
    private val door = 1530
    private val ladder = 1748

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

    private fun login(name: String = "traveller"): Player = TestWorld.login(name, here)

    private fun clicks(): MutableList<ObjectClickEvent> {
        val clicked = mutableListOf<ObjectClickEvent>()
        TestWorld.listen(ObjectFirstClickEvent::class.java) { clicked += it }
        TestWorld.listen(ObjectSecondClickEvent::class.java) { clicked += it }
        TestWorld.listen(ObjectThirdClickEvent::class.java) { clicked += it }
        return clicked
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
    fun `an idle player is clicking nothing`() {
        assertFalse(LunaClicks.clicking(player()))
    }

    @Test
    fun `a walk is no click`() {
        val player = player()
        player.walking.addStep(Direction.WEST)

        assertFalse(LunaClicks.clicking(player))
    }

    @Test
    fun `a click on its way to its target is a click under way`() {
        val player = TestWorld.login("clicker", Position(3195, 3200))
        recordBoothClicks()

        clickBooth(player)

        assertTrue(LunaClicks.clicking(player))
    }

    @Test
    fun `a click waiting for the walk up to its object is a click under way`() {
        val player = TestWorld.login("traveller", Position(3195, 3200))
        TestWorld.place(door, beside)

        LunaClicks.use(player, door, beside, option = null, from = here)

        assertTrue(LunaClicks.clicking(player))
    }

    @Test
    fun `a walk found on the game thread is under way from the next tick`() {
        val player = player()

        LunaClicks.walk(player, walkTarget, async = false)
        TestWorld.tick()

        assertFalse(player.walking.isEmpty)
    }

    @Test
    fun `a walk up found on the game thread is under way from the next tick`() {
        val player = TestWorld.login("traveller", Position(3195, 3200))
        TestWorld.place(door, beside)

        LunaClicks.use(player, door, beside, option = null, from = here, async = false)
        TestWorld.tick()

        assertFalse(player.walking.isEmpty)
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
    fun `the reach scans step onto open ground as collision lets them`() {
        assertEquals(Step.Plain, LunaClicks.reachSteps(TestWorld.world, Doorways.NONE).step(Position(3210, 3210), Direction.EAST))
    }

    @Test
    fun `the reach scans step through a shut door of the web`() {
        val doorTile = Position(3211, 3210)
        TestWorld.place(door, doorTile, ObjectType.STRAIGHT_WALL, ObjectDirection.WEST)
        val doorway = Doorway(door, doorTile, EdgeType.DOOR)
        val steps = LunaClicks.reachSteps(TestWorld.world, Doorways(mapOf((Position(3210, 3210) to doorTile) to doorway)))

        assertEquals(DoorStep(doorway, Position(3210, 3210), doorTile, shut = true), steps.step(Position(3210, 3210), Direction.EAST))
    }

    @Test
    fun `an object on its tile is found`() {
        val placed = TestWorld.place(door, beside)

        assertEquals(placed, LunaClicks.objectOn(login(), door, beside))
    }

    @Test
    fun `an object gone from its tile is not found`() {
        assertNull(LunaClicks.objectOn(login(), door, beside))
    }

    @Test
    fun `another object on the tile is not the one found`() {
        TestWorld.place(ladder, beside)
        assertNull(LunaClicks.objectOn(login(), door, beside))
    }

    @Test
    fun `an object only another player sees is not found`() {
        val player = login()
        TestWorld.placeFor(login("someone"), door, beside)
        assertNull(LunaClicks.objectOn(player, door, beside))
    }

    @Test
    fun `using an object that is gone clicks nothing`() {
        assertFalse(LunaClicks.use(login(), door, beside, option = null, from = here))
    }

    @Test
    fun `using an option the object lacks clicks nothing`() {
        TestWorld.place(door, beside)
        assertFalse(LunaClicks.use(login(), door, beside, "Climb-up", from = here))
    }

    @Test
    fun `using an object out of reach walks up to it and clicks on arrival, as the client does`() {
        val player = TestWorld.login("traveller", Position(3195, 3200))
        TestWorld.place(door, beside)
        val clicked = clicks()
        LunaClicks.use(player, door, beside, option = null, from = here)
        TestWorld.tick(12)
        assertEquals(here to listOf(ObjectFirstClickEvent::class.java), player.position to clicked.map { it.javaClass })
    }

    @Test
    fun `a walk up cut short drops its click and frees the player`() {
        val player = TestWorld.login("traveller", Position(3195, 3200))
        TestWorld.place(door, beside)
        val clicked = clicks()
        LunaClicks.use(player, door, beside, option = null, from = here)
        TestWorld.tick()
        LunaClicks.endWalk(player)
        TestWorld.tick(8)
        assertEquals(emptyList<ObjectClickEvent>() to false, clicked to LunaClicks.isActing(player))
    }

    @Test
    fun `the walk up goes to the side the click comes from`() {
        val player = TestWorld.login("traveller", Position(3195, 3200))
        TestWorld.place(door, beside)
        LunaClicks.use(player, door, beside, option = null, from = Position(3202, 3200))
        assertEquals(Position(3202, 3200), player.navigator.currentTarget)
    }

    @Test
    fun `an object no tile reaches is not used`() {
        val player = TestWorld.login("traveller", Position(3195, 3200))
        TestWorld.place(door, beside)
        listOf(-1, 0, 1).flatMap { dx -> listOf(-1, 0, 1).map { dy -> Position(beside.x + dx, beside.y + dy) } }
            .filter { it != beside }.forEach { TestWorld.place(door, it) }
        assertFalse(LunaClicks.use(player, door, beside, option = null, from = here))
    }

    @Test
    fun `an option is clicked by its place among the object's options`() {
        val player = login()
        TestWorld.place(ladder, beside)
        val clicked = clicks()
        LunaClicks.use(player, ladder, beside, "Climb-up", from = here)
        TestWorld.tick()
        assertEquals(listOf(ObjectSecondClickEvent::class.java), clicked.map { it.javaClass })
    }

    @Test
    fun `a third option is clicked as the third`() {
        val player = login()
        TestWorld.place(ladder, beside)
        val clicked = clicks()
        LunaClicks.use(player, ladder, beside, "Climb-down", from = here)
        TestWorld.tick()
        assertEquals(listOf(ObjectThirdClickEvent::class.java), clicked.map { it.javaClass })
    }

    @Test
    fun `using an object with no option named clicks its first option`() {
        val player = login()
        TestWorld.place(door, beside)
        val clicked = clicks()
        LunaClicks.use(player, door, beside, option = null, from = here)
        TestWorld.tick()
        assertEquals(listOf(ObjectFirstClickEvent::class.java), clicked.map { it.javaClass })
    }

    @Test
    fun `using a door from the side its own tile is on walks to that tile, not round to the tile across it`() {
        val player = TestWorld.login("traveller", Position(3205, 3200))
        TestWorld.place(door, beside, ObjectType.STRAIGHT_WALL, ObjectDirection.WEST)

        LunaClicks.use(player, door, beside, option = null, from = beside)

        assertEquals(beside, player.navigator.currentTarget)
    }

    @Test
    fun `an interrupt during the walk up drops its click`() {
        val player = TestWorld.login("traveller", Position(3195, 3200))
        TestWorld.place(door, beside)
        val clicked = clicks()
        LunaClicks.use(player, door, beside, option = null, from = here)
        TestWorld.tick()

        LunaClicks.interrupt(player)
        TestWorld.tick(8)

        assertEquals(emptyList<ObjectClickEvent>() to false, clicked to LunaClicks.isActing(player))
    }

    @Test
    fun `an interrupt once the walk up arrived still drops the click due the tick after`() {
        val player = TestWorld.login("traveller", Position(3199, 3200))
        TestWorld.place(door, beside)
        val clicked = clicks()
        LunaClicks.use(player, door, beside, option = null, from = here)
        TestWorld.tick(arrivalTicks)

        LunaClicks.interrupt(player)
        TestWorld.tick(2)

        assertEquals(emptyList<ObjectClickEvent>(), clicked)
    }

    @Test
    fun `a click due on arrival at an object gone since is not made`() {
        val player = TestWorld.login("traveller", Position(3199, 3200))
        val placed = TestWorld.place(door, beside)
        val clicked = clicks()
        LunaClicks.use(player, door, beside, option = null, from = here)
        TestWorld.tick(arrivalTicks)

        TestWorld.world.objects.unregister(placed)
        TestWorld.tick(2)

        assertEquals(emptyList<ObjectClickEvent>() to false, clicked to LunaClicks.isActing(player))
    }

    @Test
    fun `a click waiting for the walk up to its object keeps the player acting`() {
        val player = TestWorld.login("traveller", Position(3195, 3200))
        TestWorld.place(door, beside)

        LunaClicks.use(player, door, beside, option = null, from = here)

        assertTrue(LunaClicks.isActing(player))
    }

    @Test
    fun `a click waiting for the walk up to its object keeps the player interacting, before the walk shows`() {
        val player = TestWorld.login("traveller", Position(3195, 3200))
        TestWorld.place(door, beside)

        LunaClicks.use(player, door, beside, option = null, from = here)

        assertTrue(LunaClicks.interacting(player))
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

    private val sailor = 376

    private fun talks(): MutableList<Int> {
        val talked = mutableListOf<Int>()
        TestWorld.listen(NpcFirstClickEvent::class.java) { talked += it.targetNpc.id }
        return talked
    }

    @Test
    fun `talking to an npc in reach clicks its first option`() {
        val player = login()
        val npc = TestWorld.spawnNpc(sailor, beside)
        val talked = talks()

        LunaClicks.talkTo(player, npc)
        TestWorld.tick()

        assertEquals(listOf(sailor), talked)
    }

    @Test
    fun `talking to an npc out of reach walks up to it and talks on arrival, as the client does`() {
        val player = TestWorld.login("traveller", Position(3195, 3200))
        val npc = TestWorld.spawnNpc(sailor, beside)
        val talked = talks()

        LunaClicks.talkTo(player, npc)
        TestWorld.tick(12)

        assertEquals(listOf(sailor), talked)
    }

    @Test
    fun `a talk due on arrival at an npc gone since is not made`() {
        val player = TestWorld.login("traveller", Position(3198, 3200))
        val npc = TestWorld.spawnNpc(sailor, beside)
        val talked = talks()
        LunaClicks.talkTo(player, npc, async = false)
        TestWorld.tick(3)

        TestWorld.world.npcs.remove(npc)
        TestWorld.tick(2)

        assertEquals(emptyList<Int>() to false, talked to LunaClicks.isActing(player))
    }

    @Test
    fun `a walk up to an npc cut short drops the talk`() {
        val player = TestWorld.login("traveller", Position(3195, 3200))
        val npc = TestWorld.spawnNpc(sailor, beside)
        val talked = talks()
        LunaClicks.talkTo(player, npc)
        TestWorld.tick()

        LunaClicks.endWalk(player)
        TestWorld.tick(8)

        assertEquals(emptyList<Int>(), talked)
    }
}
