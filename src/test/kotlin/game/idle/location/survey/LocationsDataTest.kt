package game.idle.location.survey

import game.idle.location.LocationCatalog
import game.idle.location.Tile
import game.skill.woodcutting.cutTree.Tree
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/** Checks the tracked data against the real cache. Skipped, never green, where the cache is absent (it is gitignored). */
class LocationsDataTest {

    @BeforeEach
    fun `needs the cache`() {
        assumeTrue(CacheMap.isPresent, "No cache in luna/data/game/cache (revision 377, see README): skipped")
    }

    @Test
    fun `every tracked location has its trees and its booth in the cache`() {
        val audit = LocationAudit(CacheMap.map)

        assertEquals(emptyList<String>(), LocationCatalog.load(LocationCatalog.PATH).locations.flatMap(audit::problems))
    }

    @Test
    fun `the cache places the trees found live west of Varrock`() {
        val found = listOf(Tile(3171, 3444), Tile(3170, 3454), Tile(3168, 3437), Tile(3168, 3434))

        assertTrue(CacheMap.map.trees.map { it.tile }.containsAll(found))
    }

    @Test
    fun `the survey command writes a report per tree kind`(@TempDir dir: Path) {
        main(arrayOf(dir.toString()))

        assertEquals(Tree.entries.size.toLong(), Files.list(dir).use { it.count() })
    }
}
