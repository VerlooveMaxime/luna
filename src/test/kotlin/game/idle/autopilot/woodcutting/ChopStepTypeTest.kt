package game.idle.autopilot.woodcutting

import game.idle.flow.FlowContext
import game.idle.flow.FlowError
import game.idle.flow.StepField
import game.idle.flow.StepIcon
import game.idle.flow.StepSettings
import game.idle.flow.WorkSpot
import game.idle.flow.option.FakeNames
import game.idle.flow.option.OptionIcon
import game.idle.location.Tile
import game.skill.woodcutting.cutTree.Tree
import io.luna.game.model.mob.Skill
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ChopStepTypeTest {

    private val walkedTo = WorkSpot.At(Tile(3086, 3233))

    private fun chop(vararg values: Pair<String, String>) = StepSettings("chop", mapOf(*values))

    private fun choices(index: Int) = (ChopStepType.fields[index] as StepField.Choice).choices(chop())

    @Test
    fun `a chop step reads as its tree, its defaults left out`() {
        assertEquals("chop oak", ChopStepType.summary(chop("tree" to "oak", "within" to "10")))
    }

    @Test
    fun `a chop step's count and radius are written`() {
        assertEquals("chop 1 oak within 5", ChopStepType.summary(chop("tree" to "oak", "amount" to "1", "within" to "5")))
    }

    @Test
    fun `a chop step without a tree reads with a question mark and is rejected`() {
        assertEquals("chop ?", ChopStepType.summary(chop()))
        assertRejected("chop needs a tree") { ChopStepType.resolve(chop(), FlowContext()) }
    }

    @Test
    fun `a chop step works around the work spot the steps before it set, whatever the case of the tree`() {
        val step = ChopStepType.resolve(chop("tree" to "Willow", "amount" to "5", "within" to "15"), FlowContext(workSpot = walkedTo))

        assertEquals(ChopStep(Tree.WILLOW, 15, walkedTo, amount = 5), step)
    }

    @Test
    fun `a chop step with no walk before it works around the run tile, until the inventory is full`() {
        assertEquals(ChopStep(Tree.OAK, 10, WorkSpot.RunTile, amount = null), ChopStepType.resolve(chop("tree" to "oak"), FlowContext()))
    }

    @Test
    fun `an unknown tree is rejected`() {
        assertRejected("'palm' is not a kind of tree") { ChopStepType.resolve(chop("tree" to "palm"), FlowContext()) }
    }

    @Test
    fun `a tree Luna has no standing objects for is rejected`() {
        assertRejected("There are no teak trees to cut in this world yet") { ChopStepType.resolve(chop("tree" to "teak"), FlowContext()) }
    }

    @Test
    fun `the builder offers the trees there are, easiest first, amounts and a few radii`() {
        assertEquals(listOf("normal", "oak", "willow", "maple", "yew", "magic"), choices(0))
        assertEquals(listOf("", "1", "5", "10"), choices(1))
        assertEquals(listOf("5", "10", "15", "20", "30"), choices(2))
    }

    @Test
    fun `no amount shows as full in the builder`() {
        assertEquals("full", (ChopStepType.fields[1] as StepField.Choice).display(""))
    }

    @Test
    fun `the steps after a chop step know it gathers its logs`() {
        val after = ChopStep(Tree.WILLOW, 10, walkedTo).after(FlowContext(walkedTo, gathered = setOf(Tree.NORMAL.logId)))

        assertEquals(FlowContext(walkedTo, setOf(Tree.NORMAL.logId, Tree.WILLOW.logId)), after)
    }

    private fun assertRejected(message: String, action: () -> Unit) {
        val error = assertThrows<FlowError> { action() }

        assertEquals(message, error.message)
    }

    @Test
    fun `a chop step shows the Woodcutting icon`() {
        assertEquals(StepIcon.Skill(Skill.WOODCUTTING), ChopStepType.icon(StepSettings("chop")))
    }

    @Test
    fun `a chop step's target is its tree among the trees there are, shown with its logs`() {
        val target = ChopStepType.target(FakeNames())

        assertEquals(listOf<Any?>("tree", OptionIcon.Item(Tree.OAK.logId)), listOf(target.key, target.picked(chop("tree" to "oak"))?.icon))
    }

    @Test
    fun `a chop step's slot says how many logs a lap cuts`() {
        assertEquals(listOf("5 per lap"), ChopStepType.details(chop("tree" to "oak", "amount" to "5", "within" to "15"), FlowContext()))
    }

    @Test
    fun `a chop step without a count cuts until the bag is full, within the default radius`() {
        assertEquals(listOf("until the bag is full"), ChopStepType.details(chop(), FlowContext()))
    }
}
