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

    @Test
    fun `stepping aside goes west first`() {
        val player = login()

        assertTrue(lighter(player).stepAside())
        TestWorld.tick()

        assertEquals(Position(3199, 3200), player.position)
    }

    @Test
    fun `stepping aside passes over a tile something takes`() {
        val player = login()
        TestWorld.place(fire, Position(3199, 3200))

        lighter(player).stepAside()
        TestWorld.tick()

        assertEquals(Position(3201, 3200), player.position)
    }

    @Test
    fun `stepping aside passes over a tile a wall shuts off`() {
        val player = login()
        TestWorld.place(deadTree, Position(3199, 3200))
        TestWorld.place(deadTree, Position(3201, 3200))

        lighter(player).stepAside()
        TestWorld.tick()

        assertEquals(Position(3200, 3199), player.position)
    }

    @Test
    fun `stepping aside passes over a free tile behind a wall on the player's own tile`() {
        val player = login()
        TestWorld.place(1902, Position(3200, 3200), ObjectType.STRAIGHT_WALL, ObjectDirection.WEST)

        lighter(player).stepAside()
        TestWorld.tick()

        assertEquals(Position(3201, 3200), player.position)
    }

    @Test
    fun `with every side taken there is nowhere to step`() {
        val player = login()
        listOf(Position(3199, 3200), Position(3201, 3200), Position(3200, 3199), Position(3200, 3201)).forEach { TestWorld.place(fire, it) }

        assertFalse(lighter(player).stepAside())
    }

    @Test
    fun `telling the player sends a chat box line`() {
        val player = login()

        lighter(player).tell("No tinderbox.")

        assertEquals(listOf("No tinderbox."), TestWorld.chatbox(player))
    }
}
