package game.idle.movement

import api.bot.webwalk.data.WebWalkLoader
import api.bot.webwalk.model.EdgeType
import api.bot.webwalk.plan.CapabilitySnapshot
import game.testworld.TestWorld
import io.luna.game.model.Position
import io.luna.game.model.`object`.ObjectType
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class TravelWebsTest {

    @AfterEach
    fun resetWorld() = TestWorld.reset()

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

    /** The walk graph with a door, 1530 on 3220 3200, between two tiles of TestWorld's open map. */
    private fun withDoor(directory: Path): Path {
        Files.writeString(
            withWalkGraph(directory).resolve(WebWalkLoader.OBSTACLES),
            """[ { "type": "DOOR", "object": 1530, "pos": [3220, 3200, 0], "from": [3220, 3200, 0], "to": [3221, 3200, 0] } ]""",
        )
        Files.writeString(directory.resolve("doors.json"), """[ { "closed": 1530, "open": 1531 } ]""")
        return directory
    }

    private val door = Doorway(1530, Position(3220, 3200), EdgeType.DOOR)

    @Test
    fun `a door the world no longer has is removed`(@TempDir directory: Path) {
        val web = TravelWebs(withDoor(directory), doorDirectory = directory).of(TestWorld.world)
        assertEquals(setOf(door), web?.removedDoors)
    }

    @Test
    fun `a door the world has open, by the door files' open leaf, is not removed`(@TempDir directory: Path) {
        TestWorld.place(1531, Position(3220, 3201), ObjectType.STRAIGHT_WALL)
        val web = TravelWebs(withDoor(directory), doorDirectory = directory).of(TestWorld.world)
        assertEquals(emptySet<Doorway>(), web?.removedDoors)
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
