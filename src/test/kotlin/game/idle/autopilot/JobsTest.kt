package game.idle.autopilot

import game.idle.AutopilotJob
import game.idle.autopilot.woodcutting.ChopAction
import game.idle.location.LocationCatalog
import game.idle.location.catalogJson
import game.idle.location.locationJson
import game.skill.woodcutting.cutTree.Tree
import io.luna.game.model.Position
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class JobsTest {

    private val varrock = locationJson("id" to "varrock_west", "name" to "West of Varrock", "trees" to listOf("normal"))
    private val draynor = locationJson(
        "id" to "draynor",
        "name" to "Draynor village",
        "anchor" to mapOf("x" to 3087, "y" to 3235),
        "trees" to listOf("normal", "willow"),
    )
    private val jobs = Jobs(LocationCatalog.parse(catalogJson(varrock, draynor)))

    private val nearVarrock = Position(3182, 3440, 0)
    private val nearDraynor = Position(3090, 3240, 0)

    @Test
    fun `every location becomes a spot`() {
        assertEquals(2, jobs.size)
    }

    @Test
    fun `no arguments picks the nearest location and every tree there`() {
        val request = jobs.fromCommand(emptyList(), nearDraynor)

        assertEquals(AutopilotJob("draynor", listOf("normal", "willow")), request.job)
        assertEquals("Autopilot: chopping normal, willow trees at Draynor village.", request.message)
    }

    @Test
    fun `no locations at all is rejected`() {
        val empty = Jobs(LocationCatalog.parse("{}"))

        val request = empty.fromCommand(emptyList(), nearVarrock)

        assertEquals(JobRequest.rejected("Autopilot: no locations are defined."), request)
    }

    @Test
    fun `a location id is matched ignoring case`() {
        val request = jobs.fromCommand(listOf("Varrock_West"), nearDraynor)

        assertEquals("varrock_west", request.job?.locationId)
    }

    @Test
    fun `an unknown location lists the known ones`() {
        val request = jobs.fromCommand(listOf("atlantis"), nearVarrock)

        assertEquals(
            JobRequest.rejected("Autopilot: unknown location 'atlantis'. Locations: draynor, varrock_west"),
            request,
        )
    }

    @Test
    fun `tree names restrict the action`() {
        val request = jobs.fromCommand(listOf("draynor", "Willow"), nearVarrock)

        assertEquals(AutopilotJob("draynor", listOf("willow")), request.job)
        assertEquals("Autopilot: chopping willow trees at Draynor village.", request.message)
    }

    @Test
    fun `a word that is not a tree is rejected`() {
        val request = jobs.fromCommand(listOf("draynor", "palm"), nearVarrock)

        assertEquals(JobRequest.rejected("Autopilot: 'palm' is not a kind of tree."), request)
    }

    @Test
    fun `a tree that does not grow at the location is rejected`() {
        val request = jobs.fromCommand(listOf("draynor", "oak"), nearVarrock)

        assertEquals(
            JobRequest.rejected("Autopilot: no oak trees at Draynor village, only normal, willow."),
            request,
        )
    }

    @Test
    fun `a saved job resolves to its spot and action`() {
        val resolved = jobs.resolve(AutopilotJob("draynor", listOf("willow")))

        assertEquals("draynor", resolved?.spot?.location?.id)
        assertEquals(ChopAction(setOf(Tree.WILLOW)), resolved?.action)
    }

    @Test
    fun `a saved job at a removed location does not resolve`() {
        assertNull(jobs.resolve(AutopilotJob("atlantis", listOf("normal"))))
    }

    @Test
    fun `a saved job naming an unknown tree does not resolve`() {
        assertNull(jobs.resolve(AutopilotJob("draynor", listOf("palm"))))
    }

    @Test
    fun `a saved job naming a tree that no longer grows there does not resolve`() {
        assertNull(jobs.resolve(AutopilotJob("varrock_west", listOf("willow"))))
    }

    @Test
    fun `a saved job without trees does not resolve`() {
        assertNull(jobs.resolve(AutopilotJob("draynor", emptyList())))
    }

    @Test
    fun `locations at the same distance are picked by id`() {
        val twins = Jobs(LocationCatalog.parse(catalogJson(locationJson("id" to "b"), locationJson("id" to "a"))))

        val request = twins.fromCommand(emptyList(), nearVarrock)

        assertEquals("a", request.job?.locationId)
    }
}
