package game.idle.autopilot.woodcutting

import game.idle.flow.FlowContext
import game.idle.flow.FlowError
import game.idle.flow.WorkSpot
import game.idle.location.Tile
import game.skill.woodcutting.cutTree.Tree
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ChopStepTypeTest {

    private val walkedTo = WorkSpot.At(Tile(3086, 3233))

    private fun choices(index: Int) = ChopStepType.fields[index].choices(listOf("", "", ""))

    @Test
    fun `a chop line names its tree, until the inventory is full, within the default radius`() {
        assertEquals(listOf("oak", "full", "10"), ChopStepType.parse(listOf("oak")))
    }

    @Test
    fun `a chop line may start with a count and end with a radius`() {
        assertEquals(listOf("oak", "5", "25"), ChopStepType.parse(listOf("5", "oak", "within", "25")))
    }

    @Test
    fun `defaults are left out of the line, anything else is written`() {
        assertEquals("chop oak", ChopStepType.line(listOf("oak", "full", "10")))
        assertEquals("chop 1 oak within 5", ChopStepType.line(listOf("oak", "1", "5")))
    }

    @Test
    fun `chop without a tree`() {
        assertRejected("chop needs a tree: chop [<n>] <tree> [within <r>]") { ChopStepType.parse(emptyList()) }
        assertRejected("chop needs a tree: chop [<n>] <tree> [within <r>]") { ChopStepType.parse(listOf("5")) }
    }

    @Test
    fun `chop with several trees`() {
        assertRejected("One kind of tree per chop step: chop [<n>] <tree> [within <r>]") { ChopStepType.parse(listOf("normal,oak")) }
    }

    @Test
    fun `a location from the old grammar points at the walk step`() {
        assertRejected(
            "chop no longer takes a location: put a 'walk' step before it, or leave it out to chop around where you press Run",
        ) { ChopStepType.parse(listOf("willow", "@draynor")) }
    }

    @Test
    fun `anything else after the tree is rejected`() {
        assertRejected("Unexpected 'until inventory full' after the tree: chop [<n>] <tree> [within <r>]") {
            ChopStepType.parse(listOf("normal", "until", "inventory", "full"))
        }
    }

    @Test
    fun `a chop step works around the work spot the steps before it set, whatever the case of the tree`() {
        val step = ChopStepType.resolve(listOf("Willow", "5", "15"), FlowContext(workSpot = walkedTo))

        assertEquals(ChopStep(Tree.WILLOW, 15, walkedTo, amount = 5), step)
    }

    @Test
    fun `a chop step with no walk before it works around the run tile, until the inventory is full`() {
        assertEquals(ChopStep(Tree.OAK, 10, WorkSpot.RunTile, amount = null), ChopStepType.resolve(listOf("oak", "full", "10"), FlowContext()))
    }

    @Test
    fun `an unknown tree is rejected`() {
        assertRejected("'palm' is not a kind of tree") { ChopStepType.resolve(listOf("palm", "full", "10"), FlowContext()) }
    }

    @Test
    fun `a tree Luna has no standing objects for is rejected`() {
        assertRejected("There are no teak trees to cut in this world yet") { ChopStepType.resolve(listOf("teak", "full", "10"), FlowContext()) }
    }

    @Test
    fun `the builder offers the trees there are, easiest first, amounts and a few radii`() {
        assertEquals(listOf("normal", "oak", "willow", "maple", "yew", "magic"), choices(0))
        assertEquals(listOf("full", "1", "5", "10"), choices(1))
        assertEquals(listOf("5", "10", "15", "20", "30"), choices(2))
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
}
