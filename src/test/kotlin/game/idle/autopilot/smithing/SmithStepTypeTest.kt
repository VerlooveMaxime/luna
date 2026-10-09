package game.idle.autopilot.smithing

import game.idle.flow.FlowContext
import game.idle.flow.FlowError
import game.idle.flow.StepField
import game.idle.flow.StepIcon
import game.idle.flow.StepSettings
import game.idle.flow.WorkSpot
import game.idle.location.Tile
import game.skill.smithing.BarType
import game.skill.smithing.smithBar.SmithingTable
import game.testworld.TestWorld
import io.luna.game.model.mob.Skill
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class SmithStepTypeTest {

    private val walkedTo = WorkSpot.At(Tile(3188, 3426))

    private fun smith(vararg values: Pair<String, String>) = StepSettings("smith", mapOf(*values))

    private fun choices(index: Int, settings: StepSettings = smith()) = (SmithStepType.fields[index] as StepField.Choice).choices(settings)

    /** Luna's smithing items name themselves from the item definitions, which need the cache. */
    @BeforeEach
    fun `item definitions are loaded`() {
        TestWorld.world
    }

    @Test
    fun `a smith step reads as its metal and item, its defaults left out, anything else written`() {
        assertEquals("smith bronze dagger", SmithStepType.summary(smith("metal" to "bronze", "item" to "dagger", "within" to "10")))
        assertEquals(
            "smith 1 bronze dagger within 5",
            SmithStepType.summary(smith("metal" to "bronze", "item" to "dagger", "amount" to "1", "within" to "5")),
        )
    }

    @Test
    fun `a smith step missing its metal or item reads with question marks`() {
        assertEquals("smith ? ?", SmithStepType.summary(smith()))
    }

    @Test
    fun `a smith step without a metal or an item is rejected`() {
        assertRejected("smith needs a metal") { SmithStepType.resolve(smith("item" to "dagger"), FlowContext()) }
        assertRejected("smith needs an item") { SmithStepType.resolve(smith("metal" to "bronze"), FlowContext()) }
    }

    @Test
    fun `a smith step works around the work spot the steps before it set, whatever the case`() {
        val step = SmithStepType.resolve(
            smith("metal" to "Iron", "item" to "Platebody", "amount" to "5", "within" to "15"),
            FlowContext(workSpot = walkedTo),
        )

        assertEquals(SmithStep(BarType.IRON, SmithingTable.PLATEBODY, 15, walkedTo, amount = 5), step)
    }

    @Test
    fun `a smith step without an amount makes as many as the bars allow`() {
        val step = SmithStepType.resolve(smith("metal" to "bronze", "item" to "dagger"), FlowContext())

        assertEquals(SmithStep(BarType.BRONZE, SmithingTable.DAGGER, 10, WorkSpot.RunTile, amount = null), step)
    }

    @Test
    fun `a metal that makes no items is rejected`() {
        assertRejected("'gold' is not a metal to smith") { SmithStepType.resolve(smith("metal" to "gold", "item" to "dagger"), FlowContext()) }
    }

    @Test
    fun `an item the metal does not make is rejected`() {
        assertRejected("There is no bronze studs to smith") { SmithStepType.resolve(smith("metal" to "bronze", "item" to "studs"), FlowContext()) }
    }

    @Test
    fun `the builder offers the metals there are items for, easiest first`() {
        assertEquals(listOf("bronze", "iron", "steel", "mithril", "adamant", "rune"), choices(0))
    }

    @Test
    fun `the builder offers the items of the chosen metal, easiest first`() {
        assertEquals(listOf("dagger", "axe", "mace"), choices(1, smith("metal" to "bronze")).take(3))
    }

    @Test
    fun `items only one metal makes are offered for that metal`() {
        assertTrue("studs" in choices(1, smith("metal" to "steel")))
    }

    @Test
    fun `before a metal is chosen the builder offers the easiest metal's items`() {
        assertEquals(choices(1, smith("metal" to "bronze")), choices(1))
    }

    @Test
    fun `a metal that makes no items offers the easiest metal's items`() {
        assertEquals(choices(1, smith("metal" to "bronze")), choices(1, smith("metal" to "gold")))
    }

    @Test
    fun `the builder offers amounts and a few radii`() {
        assertEquals(listOf("", "1", "5", "10"), choices(2))
        assertEquals(listOf("5", "10", "15", "20", "30"), choices(3))
    }

    @Test
    fun `the steps after a smith step know it makes its item`() {
        val after = SmithStep(BarType.BRONZE, SmithingTable.DAGGER, 10, walkedTo).after(FlowContext(walkedTo, setOf(2349)))

        assertEquals(FlowContext(walkedTo, setOf(2349, 1205)), after)
    }

    private fun assertRejected(message: String, action: () -> Unit) {
        assertEquals(message, assertThrows<FlowError> { action() }.message)
    }

    @Test
    fun `a smith step shows the Smithing icon`() {
        assertEquals(StepIcon.Skill(Skill.SMITHING), SmithStepType.icon(StepSettings("smith")))
    }
}
