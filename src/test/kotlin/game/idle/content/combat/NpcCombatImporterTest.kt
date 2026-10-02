package game.idle.content.combat

import api.bot.zone.Zone
import game.idle.location.survey.CacheMap
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate

class NpcCombatImporterTest {

    private val draynor = 12338
    private val tutorialMine = 12436

    private val npcs = mapOf(
        86 to CacheNpc(NpcIdentity(86, "Giant rat", 3), attackable = true),
        447 to CacheNpc(JAIL_GUARD, attackable = true),
        950 to CacheNpc(GIANT_RAT, attackable = true),
        953 to CacheNpc(NpcIdentity(953, "Banker", 0), attackable = false),
    )

    private fun spawn(npc: String, x: Int, y: Int) = """{ $npc, "position": { "x": $x, "y": $y, "z": 0 } }"""

    private fun spawns(vararg spawns: String) = spawns.joinToString(",", "[", "]")

    @Test
    fun `attackable npcs spawned in the regions are the targets`() {
        val json = spawns(spawn("\"id\": 950", 3104, 9518), spawn("\"id\": 447", 3110, 3240))

        assertEquals(listOf(GIANT_RAT), spawnedAttackable(json, npcs, setOf(tutorialMine)))
    }

    @Test
    fun `npcs that cannot be attacked are not targets`() {
        val json = spawns(spawn("\"id\": 953", 3104, 9518))

        assertEquals(emptyList<NpcIdentity>(), spawnedAttackable(json, npcs, setOf(tutorialMine)))
    }

    @Test
    fun `a spawn by name takes the lowest id of that name`() {
        val json = spawns(spawn("\"name\": \"GIANT RAT\"", 3104, 9518))

        assertEquals(listOf(86), spawnedAttackable(json, npcs, setOf(tutorialMine)).map { it.id })
    }

    @Test
    fun `each kind is a target once, lowest id first`() {
        val json = spawns(
            spawn("\"id\": 950", 3104, 9518),
            spawn("\"id\": 447", 3110, 3240),
            spawn("\"id\": 950", 3105, 9518),
        )

        assertEquals(listOf(447, 950), spawnedAttackable(json, npcs, setOf(tutorialMine, draynor)).map { it.id })
    }

    @Test
    fun `a number names a region`() {
        assertEquals(setOf(12336, 12436), regionsOf(listOf("12336", "12436"), Zone.entries))
    }

    @Test
    fun `a zone name in any case names the zone's regions`() {
        assertEquals(Zone.DRAYNOR.regions, regionsOf(listOf("Draynor"), Zone.entries))
    }

    @Test
    fun `an unknown area is refused with the zones to choose from`() {
        val error = assertThrows<IllegalArgumentException> { regionsOf(listOf("tutorial_island"), Zone.entries) }

        assertTrue(error.message.orEmpty().startsWith("'tutorial_island' is neither a region id nor a zone (draynor, "))
    }

    private fun sourcesDir(root: Path): Path {
        lostCityTree(
            root.resolve(NpcCombatImporter.LOSTCITY_377),
            npcPack = "447=jailguard\n950=newbiegiantrat\n",
            seqPack = "",
            npcFiles = mapOf("all.npc" to "[newbiegiantrat]\nname=Giant rat\nhitpoints=3\nrespawnrate=60\n"),
        )
        lostCityTree(root.resolve(NpcCombatImporter.LOSTCITY_289), npcPack = "", seqPack = "", npcFiles = emptyMap())
        val osrs = root.resolve(NpcCombatImporter.OSRS_MONSTERS)
        Files.createDirectories(osrs.parent)
        Files.writeString(osrs, "{}")
        return root
    }

    private fun dataDir(root: Path, combatText: String): Path {
        val combat = root.resolve(NpcCombatImporter.COMBAT_FILE)
        Files.createDirectories(combat.parent)
        Files.writeString(combat, combatText)
        val spawnsFile = root.resolve(NpcCombatImporter.SPAWNS_FILE)
        Files.createDirectories(spawnsFile.parent)
        Files.writeString(spawnsFile, spawns(spawn("\"id\": 950", 3104, 9518), spawn("\"id\": 447", 3110, 3240)))
        return root
    }

    private val ratFile =
        COMBAT_FILE_HEAD + MAN_BLOCK.replace("\"id\": 1, // Man", "\"id\": 950, // Giant rat") + "\n]\n"

    @Test
    fun `a run rewrites the rows of the npcs it imported`(@TempDir dir: Path) {
        val data = dataDir(dir.resolve("data"), ratFile)
        NpcCombatImporter(sourcesDir(dir.resolve("sources")), data, npcs, LocalDate.of(2026, 10, 2))
            .run(listOf("12436"), setOf(tutorialMine))

        val rat = NpcCombatFile.parse(Files.readString(data.resolve(NpcCombatImporter.COMBAT_FILE))).row(950)
        assertEquals(listOf(3, 60), listOf(rat?.hitpoints, rat?.respawnTicks))
    }

    @Test
    fun `a run returns its report`(@TempDir dir: Path) {
        val data = dataDir(dir.resolve("data"), ratFile)
        val report = NpcCombatImporter(sourcesDir(dir.resolve("sources")), data, npcs, LocalDate.of(2026, 10, 2))
            .run(listOf("12436"), setOf(tutorialMine))

        assertEquals(listOf("Npc combat import, 2026-10-02", "Areas: 12436"), report.lines().take(2))
    }

    @Test
    fun `the importer needs at least one area`() {
        assertThrows<IllegalArgumentException> { main(arrayOf("sources", "data", "report.txt")) }
    }

    @Test
    fun `the cache gives each npc its name, level and whether it can be attacked`() {
        assumeTrue(CacheMap.isPresent, "the 377 cache is not in data/game/cache")

        assertEquals(CacheNpc(GIANT_RAT, attackable = true), CacheNpcs.all[950])
    }

    @Test
    fun `main writes the report it is given a path for`(@TempDir dir: Path) {
        assumeTrue(CacheMap.isPresent, "the 377 cache is not in data/game/cache")
        val report = dir.resolve("report/combat.txt")

        val sources = sourcesDir(dir.resolve("sources")).toString()
        val data = dataDir(dir.resolve("data"), ratFile).toString()

        main(arrayOf(sources, data, report.toString(), "12436"))

        assertEquals("Areas: 12436", Files.readAllLines(report)[1])
    }
}
