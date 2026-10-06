package game.idle.autopilot.smithing

import game.idle.flow.FlowContext
import game.idle.flow.FlowError
import game.idle.flow.WorkSpot
import game.idle.location.Tile
import game.skill.smithing.BarType
import game.skill.smithing.smithBar.SmithingTable
import game.testworld.TestWorld
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class SmithStepTypeTest {

    private val walkedTo = WorkSpot.At(Tile(3188, 3426))

    private fun choices(index: Int, values: List<String> = listOf("", "", "", "")) = SmithStepType.fields[index].choices(values)

    /** Luna's smithing items name themselves from the item definitions, which need the cache. */
    @BeforeEach
    fun `item definitions are loaded`() {
        TestWorld.world
    }

    @Test
    fun `a smith line names its metal and item, as many as the bars make, within the default radius`() {
        assertEquals(listOf("bronze", "dagger", "all", "10"), SmithStepType.parse(listOf("bronze", "dagger")))
    }

    @Test
    fun `a smith line may start with a count and end with a radius`() {
        assertEquals(listOf("iron", "platebody", "1", "5"), SmithStepType.parse(listOf("1", "iron", "platebody", "within", "5")))
    }

    @Test
    fun `defaults are left out of the line, anything else is written`() {
        assertEquals("smith bronze dagger", SmithStepType.line(listOf("bronze", "dagger", "all", "10")))
        assertEquals("smith 1 bronze dagger within 5", SmithStepType.line(listOf("bronze", "dagger", "1", "5")))
    }

    @Test
    fun `smith without an item`() {
        val error = assertThrows<FlowError> { SmithStepType.parse(listOf("5", "bronze")) }

        assertEquals("smith needs a metal and an item: smith [<n>] <metal> <item> [within <r>]", error.message)
    }

    @Test
    fun `a smith step works around the work spot the steps before it set, whatever the case`() {
        val step = SmithStepType.resolve(listOf("Iron", "Platebody", "5", "15"), FlowContext(workSpot = walkedTo))

        assertEquals(SmithStep(BarType.IRON, SmithingTable.PLATEBODY, 15, walkedTo, amount = 5), step)
    }

    @Test
    fun `a smith step without an amount makes as many as the bars allow`() {
        val step = SmithStepType.resolve(listOf("bronze", "dagger", "all", "10"), FlowContext())

        assertEquals(SmithStep(BarType.BRONZE, SmithingTable.DAGGER, 10, WorkSpot.RunTile, amount = null), step)
    }

    @Test
    fun `a metal that makes no items is rejected`() {
        val error = assertThrows<FlowError> { SmithStepType.resolve(listOf("gold", "dagger", "all", "10"), FlowContext()) }

        assertEquals("'gold' is not a metal to smith", error.message)
    }

    @Test
    fun `an item the metal does not make is rejected`() {
        val error = assertThrows<FlowError> { SmithStepType.resolve(listOf("bronze", "studs", "all", "10"), FlowContext()) }

        assertEquals("There is no bronze studs to smith", error.message)
    }

    @Test
    fun `the builder offers the metals there are items for, easiest first`() {
        assertEquals(listOf("bronze", "iron", "steel", "mithril", "adamant", "rune"), choices(0))
    }

    @Test
    fun `the builder offers the items of the chosen metal, easiest first`() {
        assertEquals(listOf("dagger", "axe", "mace"), choices(1, listOf("bronze", "", "", "")).take(3))
    }

    @Test
    fun `items only one metal makes are offered for that metal`() {
        assertTrue("studs" in choices(1, listOf("steel", "", "", "")))
    }

    @Test
    fun `before a metal is chosen the builder offers the easiest metal's items`() {
        assertEquals(choices(1, listOf("bronze", "", "", "")), choices(1))
    }

    @Test
    fun `the builder offers amounts and a few radii`() {
        assertEquals(listOf("all", "1", "5", "10"), choices(2))
        assertEquals(listOf("5", "10", "15", "20", "30"), choices(3))
    }

    @Test
    fun `the steps after a smith step know it makes its item`() {
        val after = SmithStep(BarType.BRONZE, SmithingTable.DAGGER, 10, walkedTo).after(FlowContext(walkedTo, setOf(2349)))

        assertEquals(FlowContext(walkedTo, setOf(2349, 1205)), after)
    }
}
