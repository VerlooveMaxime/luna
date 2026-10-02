package game.idle.flow

import game.idle.autopilot.woodcutting.ChopAction
import game.idle.location.LocationCatalog
import game.idle.location.Tile
import game.idle.location.area
import game.idle.location.catalogJson
import game.idle.location.locationJson
import game.skill.woodcutting.cutTree.Tree
import io.luna.game.model.Position
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class FlowResolverTest {

    private val varrock = locationJson("id" to "varrock_west", "name" to "West of Varrock", "bank" to mapOf("x" to 3186, "y" to 3440))
    private val draynor = locationJson(
        "id" to "draynor",
        "name" to "Draynor village",
        "trees" to mapOf("normal" to area(3115, 3243, 20), "willow" to area(3087, 3235, 8)),
    )
    private val resolver = FlowResolver(LocationCatalog.parse(catalogJson(varrock, draynor)))

    private val nearVarrock = Position(3182, 3440, 0)
    private val nearWillows = Position(3090, 3240, 0)

    private fun List<ResolvedStep>.chop(index: Int) = this[index] as ResolvedStep.Chop

    @Test
    fun `every location becomes a set of spots`() {
        assertEquals(2, resolver.size)
        assertEquals(listOf("draynor", "varrock_west"), resolver.locationIds)
    }

    @Test
    fun `a chop line resolves its spot and tree`() {
        val chop = resolver.resolve(listOf("chop Willow @Draynor")).chop(0)

        assertEquals("draynor", chop.spot.location.id)
        assertEquals(Tree.WILLOW, chop.spot.tree)
        assertEquals(Tile(3087, 3235), chop.spot.area.anchor)
        assertEquals(ChopAction(setOf(Tree.WILLOW)), chop.action)
    }

    @Test
    fun `a drop line drops the logs of the chop steps before it`() {
        val steps = resolver.resolve(listOf("chop willow @draynor", "chop normal @draynor", "drop"))

        assertEquals(ResolvedStep.Drop(setOf(Tree.WILLOW.logId, Tree.NORMAL.logId)), steps[2])
    }

    @Test
    fun `a drop line before any chop is rejected`() {
        assertRejected("Step 1: drop comes after a chop step, so the flow knows what to drop", listOf("drop"))
    }

    @Test
    fun `a bank line uses the bank of the last chop location`() {
        val steps = resolver.resolve(listOf("chop normal @varrock_west", "bank deposit all"))

        assertEquals(ResolvedStep.Bank(steps.chop(0).spot.location, Tile(3186, 3440)), steps[1])
    }

    @Test
    fun `a bank line before any chop is rejected`() {
        assertRejected("Step 1: bank comes after a chop step, so the flow knows which bank to use", listOf("bank deposit all"))
    }

    @Test
    fun `a bank line at a location without a bank is rejected`() {
        assertRejected("Step 2: Draynor village has no bank", listOf("chop willow @draynor", "bank deposit all"))
    }

    @Test
    fun `a syntax error names its step`() {
        assertRejected("Step 2: drop takes nothing after it: it drops what the chop steps before it gathered", listOf("chop normal @varrock_west", "drop logs"))
    }

    @Test
    fun `an unknown location lists the known ones`() {
        assertRejected("Step 1: Unknown location 'atlantis'. Locations: draynor, varrock_west", listOf("chop normal @atlantis"))
    }

    @Test
    fun `an unknown tree is rejected`() {
        assertRejected("Step 1: 'palm' is not a kind of tree", listOf("chop palm @draynor"))
    }

    @Test
    fun `a tree that does not grow at the location is rejected`() {
        assertRejected("Step 1: No oak trees at Draynor village, only normal, willow", listOf("chop oak @draynor"))
    }

    @Test
    fun `the idle shorthand picks the spot nearest to the player`() {
        assertEquals("chop willow @draynor", resolver.nearestChopLine(nearWillows))
        assertEquals("chop normal @varrock_west", resolver.nearestChopLine(nearVarrock))
    }

    @Test
    fun `the idle shorthand with no locations gives nothing`() {
        assertNull(FlowResolver(LocationCatalog.parse("{}")).nearestChopLine(nearVarrock))
    }

    @Test
    fun `spots at the same distance are picked by location id, then by the easiest tree`() {
        val twins = FlowResolver(
            LocationCatalog.parse(catalogJson(locationJson("id" to "b"), locationJson("id" to "a", "trees" to mapOf("oak" to area(3165, 3445, 15), "normal" to area(3165, 3445, 15))))),
        )

        assertEquals("chop normal @a", twins.nearestChopLine(nearVarrock))
    }

    @Test
    fun `the idle shorthand with a location and a tree`() {
        assertEquals("chop willow @draynor", resolver.chopLine("Draynor", "Willow"))
    }

    @Test
    fun `the idle shorthand with a location alone takes its easiest tree`() {
        assertEquals("chop normal @draynor", resolver.chopLine("draynor", null))
    }

    @Test
    fun `the idle shorthand rejects a bad location or tree`() {
        assertEquals("Unknown location 'atlantis'. Locations: draynor, varrock_west", assertThrows<FlowError> { resolver.chopLine("atlantis", null) }.message)
        assertEquals("No oak trees at Draynor village, only normal, willow", assertThrows<FlowError> { resolver.chopLine("draynor", "oak") }.message)
    }

    @Test
    fun `the trees at a location come easiest first, whatever the case of the id`() {
        assertEquals(listOf("normal", "willow"), resolver.treesAt("DRAYNOR"))
    }

    @Test
    fun `an unknown location has no trees`() {
        assertEquals(emptyList<String>(), resolver.treesAt("nowhere"))
    }

    private fun assertRejected(message: String, lines: List<String>) {
        val error = assertThrows<FlowError> { resolver.resolve(lines) }

        assertEquals(message, error.message)
    }
}
