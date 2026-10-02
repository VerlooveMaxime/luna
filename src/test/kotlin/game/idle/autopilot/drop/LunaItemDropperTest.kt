package game.idle.autopilot.drop

import game.testworld.TestWorld
import io.luna.game.event.impl.DropItemEvent
import io.luna.game.model.Direction
import io.luna.game.model.Position
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LunaItemDropperTest {

    private val bronzeAxe = 1351
    private val logs = 1511
    private val oakLogs = 1521

    @AfterEach
    fun resetWorld() = TestWorld.reset()

    private fun login(): Player = TestWorld.login("dropper", Position(3200, 3200))

    private fun dropper(player: Player) = LunaItemDropper(player, setOf(logs))

    private fun recordDrops(): MutableList<Int> {
        val droppedSlots = mutableListOf<Int>()
        TestWorld.listen(DropItemEvent::class.java) { droppedSlots += it.index }
        return droppedSlots
    }

    private fun holdAxeAndLogs(player: Player) {
        player.inventory.add(Item(bronzeAxe))
        player.inventory.add(Item(logs, 2))
        player.inventory.add(Item(oakLogs))
    }

    @Test
    fun `an idle player is not busy`() {
        assertFalse(dropper(login()).isBusy())
    }

    @Test
    fun `a walking player is busy`() {
        val player = login()
        player.walking.addStep(Direction.EAST)

        assertTrue(dropper(player).isBusy())
    }

    @Test
    fun `a player holding the items has items to drop`() {
        val player = login()
        holdAxeAndLogs(player)

        assertTrue(dropper(player).hasItems())
    }

    @Test
    fun `a player holding other things has nothing to drop`() {
        val player = login()
        player.inventory.add(Item(bronzeAxe))
        player.inventory.add(Item(oakLogs))

        assertFalse(dropper(player).hasItems())
    }

    @Test
    fun `only the step's items are dropped, through the drop event`() {
        val player = login()
        holdAxeAndLogs(player)
        val droppedSlots = recordDrops()

        dropper(player).dropItems()

        assertEquals(listOf(1, 2), droppedSlots)
    }

    @Test
    fun `a player who may not act drops nothing`() {
        val player = login()
        holdAxeAndLogs(player)
        val droppedSlots = recordDrops()
        player.lock()

        dropper(player).dropItems()

        assertEquals(emptyList<Int>(), droppedSlots)
    }
}
