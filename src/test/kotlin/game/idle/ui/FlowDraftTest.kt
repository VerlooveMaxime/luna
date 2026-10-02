package game.idle.ui

import game.idle.flow.FlowStep
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FlowDraftTest {

    private val trees = mapOf("varrock_west" to listOf("normal"), "draynor" to listOf("normal", "willow"))
    private val locations = trees.keys.sorted()
    private val chop = FlowDraft(resource = "willow", locationId = "draynor")

    @Test
    fun `the first draft chops the first tree of the first location`() {
        assertEquals(FlowDraft(resource = "normal", locationId = "draynor"), FlowDraft.first(locations) { trees.getValue(it) })
    }

    @Test
    fun `the first draft without locations stays blank`() {
        assertEquals(FlowDraft(), FlowDraft.first(emptyList()) { emptyList() })
    }

    @Test
    fun `a chop line names the tree and the location`() {
        assertEquals("chop willow @draynor", chop.line())
    }

    @Test
    fun `drop and bank lines ignore the other fields`() {
        assertEquals("drop", chop.copy(kind = StepKind.DROP).line())
        assertEquals("bank deposit all", chop.copy(kind = StepKind.BANK).line())
    }

    @Test
    fun `kinds cycle and wrap`() {
        assertEquals(StepKind.DROP, chop.nextKind().kind)
        assertEquals(StepKind.CHOP, chop.copy(kind = StepKind.BANK).nextKind().kind)
    }

    @Test
    fun `the next resource wraps around the location's trees`() {
        assertEquals("normal", chop.nextResource(listOf("normal", "willow")).resource)
        assertEquals("willow", chop.copy(resource = "normal").nextResource(listOf("normal", "willow")).resource)
    }

    @Test
    fun `a resource not in the list gives the first, no resources keep it`() {
        assertEquals("oak", chop.nextResource(listOf("oak", "yew")).resource)
        assertEquals("willow", chop.nextResource(emptyList()).resource)
    }

    @Test
    fun `the next location takes its first resource`() {
        val draft = chop.nextLocation(locations) { trees.getValue(it) }

        assertEquals(FlowDraft(resource = "normal", locationId = "varrock_west"), draft)
    }

    @Test
    fun `a location without resources leaves the resource blank`() {
        assertEquals("", chop.withLocation("desert", emptyList()).resource)
    }

    @Test
    fun `editing a chop step loads its tree and location`() {
        assertEquals(FlowDraft(resource = "oak", locationId = "varrock_west"), chop.editing(FlowStep.Chop("oak", "varrock_west")))
    }

    @Test
    fun `editing a drop or bank step keeps the chop fields for later`() {
        assertEquals(chop.copy(kind = StepKind.DROP), chop.editing(FlowStep.Drop))
        assertEquals(chop.copy(kind = StepKind.BANK), chop.editing(FlowStep.BankDepositAll))
    }
}
