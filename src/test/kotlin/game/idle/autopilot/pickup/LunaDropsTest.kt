package game.idle.autopilot.pickup

import api.drops.DropTableHandler
import api.drops.GenericDropTables
import game.testworld.TestWorld
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Luna's drop registry is a global map with no way to remove a table, so each test registers its own npc id, one no
 * plugin uses (the test world loads no drop tables).
 */
class LunaDropsTest {

    private val uncutSapphire = 1623

    @BeforeEach
    fun cache() {
        TestWorld.context
    }

    @Test
    fun `an npc without a table drops nothing`() {
        assertEquals(emptySet<Int>(), LunaDrops.itemsOf(3800))
    }

    @Test
    fun `an npc's own table lists its items`() {
        DropTableHandler.createNpc(3801) { table { 526.x(1..1).chance(1.0) } }

        assertEquals(setOf(526), LunaDrops.itemsOf(3801))
    }

    @Test
    fun `a shared table the npc rolls is listed whole, without its empty slots`() {
        DropTableHandler.createNpc(3802) { tables += GenericDropTables.gemDropTable() }

        val items = LunaDrops.itemsOf(3802)

        assertEquals(listOf(true, false), listOf(uncutSapphire in items, -1 in items))
    }
}
