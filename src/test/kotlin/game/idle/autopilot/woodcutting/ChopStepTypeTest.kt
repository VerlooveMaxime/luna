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

    private fun choices(index: Int) = ChopStepType.fields[index].choices(listOf("", ""))

    @Test
    fun `a chop line names its tree, within the default radius`() {
        assertEquals(listOf("oak", "10"), ChopStepType.parse(listOf("oak")))
    }

    @Test
    fun `a chop line may name its radius`() {
        assertEquals(listOf("oak", "25"), ChopStepType.parse(listOf("oak", "within", "25")))
    }

    @Test
    fun `the default radius is left out of the line, any other is written`() {
        assertEquals("chop oak", ChopStepType.line(listOf("oak", "10")))
        assertEquals("chop oak within 5", ChopStepType.line(listOf("oak", "5")))
    }

    @Test
    fun `chop without a tree`() {
        assertRejected("chop needs a tree: chop <tree> [within <n>]") { ChopStepType.parse(emptyList()) }
    }

    @Test
    fun `chop with several trees`() {
        assertRejected("One kind of tree per chop step: chop <tree> [within <n>]") { ChopStepType.parse(listOf("normal,oak")) }
    }

    @Test
    fun `a location from the old grammar points at the walk step`() {
        assertRejected(
            "chop no longer takes a location: put a 'walk' step before it, or leave it out to chop around where you press Run",
        ) { ChopStepType.parse(listOf("willow", "@draynor")) }
    }

    @Test
    fun `anything else after the tree is rejected`() {
        assertRejected("Unexpected 'until inventory full' after the tree: chop <tree> [within <n>]") {
            ChopStepType.parse(listOf("normal", "until", "inventory", "full"))
        }
        assertRejected("Unexpected 'within' after the tree: chop <tree> [within <n>]") { ChopStepType.parse(listOf("normal", "within")) }
        assertRejected("Unexpected 'near 5' after the tree: chop <tree> [within <n>]") { ChopStepType.parse(listOf("normal", "near", "5")) }
    }

    @Test
    fun `the radius is a number of tiles from 1 to 32`() {
        assertRejected("within takes a number of tiles from 1 to 32, not 'far'") { ChopStepType.parse(listOf("oak", "within", "far")) }
        assertRejected("within takes a number of tiles from 1 to 32, not '0'") { ChopStepType.parse(listOf("oak", "within", "0")) }
        assertRejected("within takes a number of tiles from 1 to 32, not '33'") { ChopStepType.parse(listOf("oak", "within", "33")) }
    }

    @Test
    fun `a chop step works around the work spot the steps before it set, whatever the case of the tree`() {
        val step = ChopStepType.resolve(listOf("Willow", "15"), FlowContext(workSpot = walkedTo))

        assertEquals(ChopStep(Tree.WILLOW, 15, walkedTo), step)
    }

    @Test
    fun `a chop step with no walk before it works around the run tile`() {
        assertEquals(ChopStep(Tree.OAK, 10, WorkSpot.RunTile), ChopStepType.resolve(listOf("oak", "10"), FlowContext()))
    }

    @Test
    fun `an unknown tree is rejected`() {
        assertRejected("'palm' is not a kind of tree") { ChopStepType.resolve(listOf("palm", "10"), FlowContext()) }
    }

    @Test
    fun `a tree Luna has no standing objects for is rejected`() {
        assertRejected("There are no teak trees to cut in this world yet") { ChopStepType.resolve(listOf("teak", "10"), FlowContext()) }
    }

    @Test
    fun `the builder offers the trees there are, easiest first, and a few radii`() {
        assertEquals(listOf("normal", "oak", "willow", "maple", "yew", "magic"), choices(0))
        assertEquals(listOf("5", "10", "15", "20", "30"), choices(1))
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
