package game.idle.movement

import api.bot.webwalk.data.WebWalkLoader
import api.bot.webwalk.model.EdgeType
import api.bot.webwalk.plan.CapabilitySnapshot
import game.testworld.TestWorld
import io.luna.game.model.Position
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class TravelWebsTest {

    /** Two waypoints on TestWorld's open map, a walk apart. */
    private fun withWalkGraph(directory: Path): Path {
        Files.writeString(
            directory.resolve(WebWalkLoader.WALK_GRAPH),
            """
            {
              "waypoints": [ { "id": "a", "pos": [3200, 3200, 0] }, { "id": "b", "pos": [3240, 3200, 0] } ],
              "edges": [ { "from": "a", "to": "b" } ]
            }
            """.trimIndent(),
        )
        return directory
    }

    @Test
    fun `there is no web while the walk graph is missing`(@TempDir directory: Path) {
        assertNull(TravelWebs(directory).of(TestWorld.world))
    }

    @Test
    fun `a world's web is read once`(@TempDir directory: Path) {
        val webs = TravelWebs(withWalkGraph(directory))
        assertSame(webs.of(TestWorld.world), webs.of(TestWorld.world))
    }

    @Test
    fun `the web read walks over the world's own terrain`(@TempDir directory: Path) {
        val web = TravelWebs(withWalkGraph(directory)).of(TestWorld.world)
        val trip = web?.plan(Position(3200, 3200), listOf(Position(3210, 3200)), CapabilitySnapshot())
        assertEquals(listOf(EdgeType.WALK), trip?.legs?.map { it.type })
    }

    @Test
    fun `there are no doorways while the walk graph is missing`(@TempDir directory: Path) {
        assertSame(Doorways.NONE, TravelWebs(directory).doorwaysOf(TestWorld.world))
    }

    @Test
    fun `the doorways are the web's`(@TempDir directory: Path) {
        val webs = TravelWebs(withWalkGraph(directory))
        assertSame(webs.of(TestWorld.world)?.doorways, webs.doorwaysOf(TestWorld.world))
    }
}
