package game.idle.autopilot.smithing

import game.idle.flow.FlowContext
import game.idle.flow.FlowError
import game.idle.flow.StepIcon
import game.idle.flow.StepSettings
import game.idle.flow.WorkSpot
import game.idle.flow.described
import game.idle.flow.option.FakeNames
import game.idle.location.Tile
import game.skill.smithing.BarType
import game.skill.smithing.smithBar.SmithingTable
import game.testworld.TestWorld
import io.luna.game.model.mob.Skill
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class SmithStepTypeTest {

    private val walkedTo = WorkSpot.At(Tile(3188, 3426))

    private fun smith(vararg values: Pair<String, String>) = StepSettings("smith", mapOf(*values))

    /** Luna's smithing items name themselves from the item definitions, which need the cache. */
    @BeforeEach
    fun `item definitions are loaded`() {
        TestWorld.world
    }

    @Test
    fun `a smith step reads as its item, its defaults left out, anything else written`() {
        assertEquals("smith bronze dagger", SmithStepType.summary(smith("item" to "$BRONZE_DAGGER", "within" to "10")))
        assertEquals("smith 1 bronze dagger within 5", SmithStepType.summary(smith("item" to "$BRONZE_DAGGER", "amount" to "1", "within" to "5")))
    }

    @Test
    fun `a smith step without an item reads with a question mark`() {
        assertEquals("smith ?", SmithStepType.summary(smith()))
    }

    @Test
    fun `a smith step whose item is no smithing item reads as it is kept`() {
        assertEquals(listOf("smith dagger", "smith 4"), listOf(SmithStepType.summary(smith("item" to "dagger")), SmithStepType.summary(smith("item" to "4"))))
    }

    @Test
    fun `a smith step without an item is rejected`() {
        assertRejected("smith needs an item") { SmithStepType.resolve(smith(), FlowContext()) }
    }

    @Test
    fun `a smith step works around the work spot the steps before it set, its metal and table row following from the item`() {
        val step = SmithStepType.resolve(smith("item" to "$IRON_PLATEBODY", "amount" to "5", "within" to "15"), FlowContext(workSpot = walkedTo))

        assertEquals(SmithStep(BarType.IRON, SmithingTable.PLATEBODY, 15, walkedTo, amount = 5), step)
    }

    @Test
    fun `a smith step without an amount makes as many as the bars allow`() {
        val step = SmithStepType.resolve(smith("item" to "$BRONZE_DAGGER"), FlowContext())

        assertEquals(SmithStep(BarType.BRONZE, SmithingTable.DAGGER, 10, WorkSpot.RunTile, amount = null), step)
    }

    @Test
    fun `an item no table row smiths is rejected`() {
        assertRejected("'4' is not an item to smith") { SmithStepType.resolve(smith("item" to "4"), FlowContext()) }
        assertRejected("'dagger' is not an item to smith") { SmithStepType.resolve(smith("item" to "dagger"), FlowContext()) }
    }

    @Test
    fun `the configure screen searches the item, types the amount and the radius`() {
        assertEquals(
            listOf(
                "Item (left): search item, 'What would you like to smith?'",
                "Amount (left): typed amount 1..1000, button 'All'",
                "Within (right): typed within 1..32",
            ),
            described(SmithStepType.fields(FakeNames())),
        )
    }

    @Test
    fun `the steps after a smith step know it makes its item`() {
        val after = SmithStep(BarType.BRONZE, SmithingTable.DAGGER, 10, walkedTo).after(FlowContext(walkedTo, setOf(2349)))

        assertEquals(FlowContext(walkedTo, setOf(2349, BRONZE_DAGGER)), after)
    }

    @Test
    fun `a smith step shows the Smithing icon and level`() {
        assertEquals(StepIcon.Skill(Skill.SMITHING), SmithStepType.icon(smith()))
        assertEquals(Skill.SMITHING, SmithStepType.skill(smith()))
    }

    @Test
    fun `a smith step's slot says how many`() {
        assertEquals(listOf("all of them"), SmithStepType.details(smith("item" to "$BRONZE_DAGGER"), FlowContext()))
    }

    @Test
    fun `a smith step's target is the item, by id`() {
        val target = SmithStepType.target(FakeNames(items = mapOf(BRONZE_DAGGER to "Bronze dagger")))

        assertEquals("Bronze dagger", target.picked(smith("item" to "$BRONZE_DAGGER"))?.label)
    }

    private fun assertRejected(message: String, action: () -> Unit) {
        assertEquals(message, assertThrows<FlowError> { action() }.message)
    }

    private companion object {
        const val BRONZE_DAGGER = 1205
        const val IRON_PLATEBODY = 1115
    }
}
