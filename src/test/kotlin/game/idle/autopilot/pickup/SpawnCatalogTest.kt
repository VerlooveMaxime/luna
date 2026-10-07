package game.idle.autopilot.pickup

import engine.spawn.ItemSpawn
import engine.spawn.ItemSpawnFileParser
import game.idle.location.Area
import game.idle.location.Tile
import game.testworld.TestWorld
import io.luna.game.model.Position
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class SpawnCatalogTest {

    private val egg = ItemSpawn(1944, 1, Position(3255, 3270), 100)
    private val upstairs = ItemSpawn(1925, 1, Position(3255, 3270, 1), 100)

    @Test
    fun `the items spawning in an area are listed once each, other floors left out`() {
        val catalog = SpawnCatalog(listOf(egg, egg.copy(position = Position(3256, 3270)), upstairs))

        assertEquals(setOf(1944), catalog.itemsIn(Area(Tile(3253, 3270), radius = 5)))
    }

    @Test
    fun `Luna's spawn file holds no spawns yet`() {
        TestWorld.context

        assertEquals(emptySet<Int>(), SpawnCatalog.load().itemsIn(Area(Tile(3222, 3218), radius = Area.MAX_RADIUS)))
    }

    @Test
    fun `Luna's parser reads a row by item name or id, with its respawn time or the default`(@TempDir dir: Path) {
        TestWorld.context
        val file = Files.writeString(
            dir.resolve("item_spawns.json"),
            """[
              { "name": "Egg", "amount": 1, "position": { "x": 3255, "y": 3270, "z": 0 } },
              { "id": 1925, "amount": 2, "position": { "x": 3200, "y": 3200, "z": 0 }, "respawn_ticks": 50 }
            ]""",
        )

        val spawns = ItemSpawnFileParser.readSpawns(file)

        assertEquals(listOf(egg, ItemSpawn(1925, 2, Position(3200, 3200), 50)), spawns)
    }
}
