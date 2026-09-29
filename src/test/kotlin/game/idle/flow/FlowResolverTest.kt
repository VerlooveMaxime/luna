package game.idle.flow

import game.idle.autopilot.woodcutting.ChopAction
import game.idle.location.LocationCatalog
import game.idle.location.Tile
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
        "anchor" to mapOf("x" to 3087, "y" to 3235),
        "trees" to listOf("normal", "willow"),
    )
    private val resolver = FlowResolver(LocationCatalog.parse(catalogJson(varrock, draynor)))

    private val nearVarrock = Position(3182, 3440, 0)
    private val nearDraynor = Position(3090, 3240, 0)

    @Test
    fun `every location becomes a spot`() {
        assertEquals(2, resolver.size)
        assertEquals(listOf("draynor", "varrock_west"), resolver.locationIds)
    }

    @Test
    fun `a chop line resolves its spot, trees and condition`() {
        val steps = resolver.resolve(listOf("chop Willow @Draynor until 50 logs"))

        val chop = steps.single() as ResolvedStep.Chop
        assertEquals("draynor", chop.spot.location.id)
        assertEquals(ChopAction(setOf(Tree.WILLOW), dropWhenFull = false), chop.action)
        assertEquals(Until.Logs(50), chop.until)
    }

    @Test
    fun `a bank line uses the bank of the last chop location`() {
        val steps = resolver.resolve(listOf("chop normal @varrock_west", "bank deposit all", "loop"))

        assertEquals(ResolvedStep.Bank(steps.chop(0).spot.location, Tile(3186, 3440)), steps[1])
        assertEquals(ResolvedStep.Loop, steps[2])
    }

    @Test
    fun `a bank line before any chop is rejected`() {
        assertRejected("Step 1: bank comes after a chop step, so the flow knows which bank to use", listOf("bank deposit all"))
    }

    @Test
    fun `a bank line at a location without a bank is rejected`() {
        assertRejected("Step 2: Draynor village has no bank", listOf("chop normal @draynor", "bank deposit all"))
    }

    @Test
    fun `a syntax error names its step`() {
        assertRejected("Step 2: loop takes nothing after it", listOf("chop normal @draynor", "loop twice"))
    }

    @Test
    fun `an unknown location lists the known ones`() {
        assertRejected("Step 1: Unknown location 'atlantis'. Locations: draynor, varrock_west", listOf("chop normal @atlantis"))
    }

    @Test
    fun `a word that is not a tree is rejected`() {
        assertRejected("Step 1: 'palm' is not a kind of tree", listOf("chop palm @draynor"))
    }

    @Test
    fun `a tree that does not grow at the location is rejected`() {
        assertRejected("Step 1: No oak trees at Draynor village, only normal, willow", listOf("chop oak @draynor"))
    }

    @Test
    fun `the idle shorthand picks the nearest location and every tree there`() {
        assertEquals("chop normal,willow @draynor drop", resolver.nearestChopLine(nearDraynor))
        assertEquals("chop normal @varrock_west drop", resolver.nearestChopLine(nearVarrock))
    }

    @Test
    fun `the idle shorthand with no locations gives nothing`() {
        assertNull(FlowResolver(LocationCatalog.parse("{}")).nearestChopLine(nearVarrock))
    }

    @Test
    fun `locations at the same distance are picked by id`() {
        val twins = FlowResolver(LocationCatalog.parse(catalogJson(locationJson("id" to "b"), locationJson("id" to "a"))))

        assertEquals("chop normal @a drop", twins.nearestChopLine(nearVarrock))
    }

    @Test
    fun `the idle shorthand with a location and trees`() {
        assertEquals("chop willow @draynor drop", resolver.chopLine("Draynor", listOf("Willow")))
        assertEquals("chop normal,willow @draynor drop", resolver.chopLine("draynor", emptyList()))
    }

    @Test
    fun `the idle shorthand rejects a bad location or tree`() {
        assertEquals("Unknown location 'atlantis'. Locations: draynor, varrock_west", assertThrows<FlowError> { resolver.chopLine("atlantis", emptyList()) }.message)
        assertEquals("No oak trees at Draynor village, only normal, willow", assertThrows<FlowError> { resolver.chopLine("draynor", listOf("oak")) }.message)
    }

    private fun List<ResolvedStep>.chop(index: Int): ResolvedStep.Chop = this[index] as ResolvedStep.Chop

    private fun assertRejected(message: String, lines: List<String>) {
        val error = assertThrows<FlowError> { resolver.resolve(lines) }

        assertEquals(message, error.message)
    }
}
