package game.idle.autopilot.woodcutting

import game.idle.flow.FlowContext
import game.idle.flow.FlowError
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

class ChopStepTypeTest {

    private val varrock = locationJson("id" to "varrock_west", "name" to "West of Varrock", "bank" to mapOf("x" to 3186, "y" to 3440))
    private val draynor = locationJson(
        "id" to "draynor",
        "name" to "Draynor village",
        "trees" to mapOf("willow" to area(3087, 3235, 8), "normal" to area(3115, 3243, 20)),
    )
    private val chop = ChopStepType(LocationCatalog.parse(catalogJson(varrock, draynor)))

    private val nearVarrock = Position(3182, 3440, 0)
    private val nearWillows = Position(3090, 3240, 0)

    private fun resolve(vararg values: String) = chop.resolve(values.toList(), FlowContext()) as ChopStep

    @Test
    fun `a chop line names its tree and location`() {
        assertEquals(listOf("oak", "varrock_west"), chop.parse(listOf("oak", "@varrock_west")))
    }

    @Test
    fun `values read back as the line`() {
        assertEquals("chop oak @varrock_west", chop.line(listOf("oak", "varrock_west")))
    }

    @Test
    fun `chop without a tree`() {
        assertRejected("chop needs a tree: chop <tree> @<location>") { chop.parse(emptyList()) }
    }

    @Test
    fun `chop with several trees`() {
        assertRejected("One kind of tree per chop step: chop <tree> @<location>") { chop.parse(listOf("normal,oak", "@x")) }
    }

    @Test
    fun `chop without a location`() {
        assertRejected("chop needs a location: chop normal @<location>") { chop.parse(listOf("normal")) }
    }

    @Test
    fun `chop with a location missing its at sign`() {
        assertRejected("chop needs a location: chop normal @<location>") { chop.parse(listOf("normal", "varrock_west")) }
    }

    @Test
    fun `chop with an empty location`() {
        assertRejected("chop needs a location: chop normal @<location>") { chop.parse(listOf("normal", "@")) }
    }

    @Test
    fun `the old clauses on a chop step point at what replaced them`() {
        assertRejected(
            "Unexpected 'until inventory full' after the location. A chop step ends when the inventory is full; " +
                "'drop' and 'bank deposit all' are steps of their own, and the flow repeats by itself.",
        ) { chop.parse(listOf("normal", "@x", "until", "inventory", "full")) }
    }

    @Test
    fun `a chop step resolves its spot and tree, whatever the case`() {
        val step = resolve("Willow", "Draynor")

        assertEquals("draynor", step.spot.location.id)
        assertEquals(Tree.WILLOW, step.spot.tree)
        assertEquals(Tile(3087, 3235), step.spot.area.anchor)
        assertEquals(ChopAction(setOf(Tree.WILLOW)), step.action)
    }

    @Test
    fun `an unknown location lists the known ones`() {
        assertRejected("Unknown location 'atlantis'. Locations: draynor, varrock_west") { resolve("normal", "atlantis") }
    }

    @Test
    fun `an unknown tree is rejected`() {
        assertRejected("'palm' is not a kind of tree") { resolve("palm", "draynor") }
    }

    @Test
    fun `a tree that does not grow at the location is rejected`() {
        assertRejected("No oak trees at Draynor village, only normal, willow") { resolve("oak", "draynor") }
    }

    @Test
    fun `the tree field offers the trees of the chosen location, easiest first`() {
        assertEquals(listOf("normal", "willow"), chop.fields[0].choices(listOf("", "DRAYNOR")))
    }

    @Test
    fun `the tree field offers nothing at an unknown location`() {
        assertEquals(emptyList<String>(), chop.fields[0].choices(listOf("", "nowhere")))
    }

    @Test
    fun `the location field offers every location by id`() {
        assertEquals(listOf("draynor", "varrock_west"), chop.fields[1].choices(listOf("", "")))
    }

    @Test
    fun `the idle shorthand picks the spot nearest to the player`() {
        assertEquals("chop willow @draynor", chop.nearestLine(nearWillows))
        assertEquals("chop normal @varrock_west", chop.nearestLine(nearVarrock))
    }

    @Test
    fun `the idle shorthand with no locations gives nothing`() {
        assertNull(ChopStepType(LocationCatalog.parse("{}")).nearestLine(nearVarrock))
    }

    @Test
    fun `spots at the same distance are picked by location id, then by the easiest tree`() {
        val twins = ChopStepType(
            LocationCatalog.parse(catalogJson(locationJson("id" to "b"), locationJson("id" to "a", "trees" to mapOf("oak" to area(3165, 3445, 15), "normal" to area(3165, 3445, 15))))),
        )

        assertEquals("chop normal @a", twins.nearestLine(nearVarrock))
    }

    @Test
    fun `the idle shorthand with a location and a tree`() {
        assertEquals("chop willow @draynor", chop.lineAt("Draynor", "Willow"))
    }

    @Test
    fun `the idle shorthand with a location alone takes its easiest tree`() {
        assertEquals("chop normal @draynor", chop.lineAt("draynor", null))
    }

    @Test
    fun `the idle shorthand rejects a bad location or tree`() {
        assertRejected("Unknown location 'atlantis'. Locations: draynor, varrock_west") { chop.lineAt("atlantis", null) }
        assertRejected("No oak trees at Draynor village, only normal, willow") { chop.lineAt("draynor", "oak") }
    }

    @Test
    fun `the steps after a chop step know its location and its logs`() {
        val step = resolve("willow", "draynor")

        val after = step.after(FlowContext(gathered = setOf(Tree.NORMAL.logId)))

        assertEquals(FlowContext(step.spot.location, setOf(Tree.NORMAL.logId, Tree.WILLOW.logId)), after)
    }

    private fun assertRejected(message: String, action: () -> Unit) {
        val error = assertThrows<FlowError> { action() }

        assertEquals(message, error.message)
    }
}
