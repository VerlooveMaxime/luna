package game.idle.autopilot.firemaking

import game.idle.autopilot.RefusingController
import game.testworld.TestWorld
import io.luna.game.event.impl.UseItemEvent.ItemOnItemEvent
import io.luna.game.model.Direction
import io.luna.game.model.Position
import io.luna.game.model.`object`.ObjectDirection
import io.luna.game.model.`object`.ObjectType
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.overlay.StandardInterface
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LunaLighterTest {

    private val here = Position(3200, 3200)
    private val tinderbox = 590
    private val logs = 1511
    private val oakLogs = 1521
    private val fire = 2732
    private val deadTree = 1286

    @AfterEach
    fun resetWorld() = TestWorld.reset()

    private fun login(): Player = TestWorld.login("lighter", here)

    private fun lighter(player: Player, logIds: Set<Int> = setOf(logs, oakLogs)) = LunaLighter(player, logIds)

    @Test
    fun `an idle player is not busy`() {
        assertFalse(lighter(login()).isBusy())
    }

    @Test
    fun `a walking player is busy`() {
        val player = login()
        player.walking.addStep(Direction.EAST)

        assertTrue(lighter(player).isBusy())
    }

    @Test
    fun `an open window keeps the player busy`() {
        val player = login()
        player.overlays.open(StandardInterface(5292))

        assertTrue(lighter(player).isBusy())
    }

    @Test
    fun `the view counts the step's logs and finds one the player can light`() {
        val player = login()
        player.inventory.add(Item(tinderbox))
        player.inventory.add(Item(oakLogs))
        player.inventory.add(Item(logs, 2))

        assertEquals(LightView(hasTinderbox = true, logs = 3, lightable = 2, tileFree = true), lighter(player).look())
    }

    @Test
    fun `logs the step does not light are not offered`() {
        val player = login()
        player.inventory.add(Item(logs))

        assertNull(lighter(player, logIds = setOf(oakLogs)).look().lightable)
    }

    @Test
    fun `logs above the player's level are not offered`() {
        val player = login()
        player.inventory.add(Item(oakLogs))

        assertEquals(LightView(hasTinderbox = false, logs = 1, lightable = null, tileFree = true), lighter(player).look())
    }

    @Test
    fun `a fire on the player's tile takes it`() {
        val player = login()
        TestWorld.place(fire, here)

        assertFalse(lighter(player).look().tileFree)
    }

    @Test
    fun `lighting uses the tinderbox on the log like the client`() {
        val player = login()
        player.inventory.add(Item(tinderbox))
        player.inventory.add(Item(logs))
        val used = mutableListOf<String>()
        TestWorld.listen(ItemOnItemEvent::class.java) { used += "${it.usedItemId} ${it.usedItemIndex} on ${it.targetItemId} ${it.targetItemIndex}" }

        lighter(player).light(1)

        assertEquals(listOf("590 0 on 1511 1"), used)
    }

    @Test
    fun `a click the controller refuses lights nothing`() {
        val player = login()
        player.inventory.add(Item(tinderbox))
        player.inventory.add(Item(logs))
        player.controllers.register(RefusingController(player))
        val used = mutableListOf<Int>()
        TestWorld.listen(ItemOnItemEvent::class.java) { used += it.targetItemId }

        lighter(player).light(1)

        assertEquals(emptyList<Int>(), used)
    }

    @Test
    fun `an empty slot is not lit`() {
        val player = login()
        val used = mutableListOf<Int>()
        TestWorld.listen(ItemOnItemEvent::class.java) { used += it.targetItemId }

        lighter(player).light(5)

        assertEquals(emptyList<Int>(), used)
    }

    private val around = Direction.values().filter { it != Direction.NONE }.map { here.translate(1, it) }

    @Test
    fun `a player whose tile is taken walks to a free tile next to it`() {
        val player = login()
        TestWorld.place(fire, here)

        assertTrue(lighter(player).moveToFreeTile())

        assertEquals(1, player.navigator.currentTarget?.let { maxOf(Math.abs(it.x - here.x), Math.abs(it.y - here.y)) })
    }

    @Test
    fun `a player boxed in by fires walks past them to the nearest free tile`() {
        val player = login()
        (around + here).forEach { TestWorld.place(fire, it) }

        lighter(player).moveToFreeTile()

        assertEquals(2, player.navigator.currentTarget?.let { maxOf(Math.abs(it.x - here.x), Math.abs(it.y - here.y)) })
    }

    @Test
    fun `the free tile is the one nearest where the step started, not where the player stands`() {
        val player = login()
        val lighter = lighter(player)
        player.move(Position(3205, 3200))
        TestWorld.place(fire, Position(3205, 3200))

        lighter.moveToFreeTile()

        assertEquals(here, player.navigator.currentTarget)
    }

    @Test
    fun `a tile scenery stands on is passed over`() {
        val player = login()
        TestWorld.place(fire, here)
        around.filter { it != Position(3200, 3201) }.forEach { TestWorld.place(deadTree, it) }

        lighter(player).moveToFreeTile()

        assertEquals(Position(3200, 3201), player.navigator.currentTarget)
    }

    @Test
    fun `a free tile a walk cannot reach is passed over`() {
        val player = login()
        TestWorld.place(fire, here)
        around.filter { it != Position(3201, 3201) }.forEach { TestWorld.place(deadTree, it) }

        assertFalse(lighter(player).moveToFreeTile())
    }

    @Test
    fun `with no free tile a walk reaches near the start there is none to go to`() {
        val player = login()
        TestWorld.place(fire, here)
        around.forEach { TestWorld.place(deadTree, it) }

        assertFalse(lighter(player).moveToFreeTile())
    }
}
