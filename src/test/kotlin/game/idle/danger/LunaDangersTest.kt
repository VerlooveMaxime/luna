package game.idle.danger

import engine.spawn.NpcSpawn
import engine.spawn.NpcSpawnFileParser
import game.idle.flow.option.LunaGameNames
import game.idle.location.Area
import game.idle.location.Tile
import game.testworld.TestWorld
import io.luna.game.model.Direction
import io.luna.game.model.Position
import io.luna.game.model.mob.NpcAggressionProfile.NpcAggressionPolicy
import io.luna.game.model.mob.movement.wandering.WanderingFrequency
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class LunaDangersTest {

    private val darkWizard = 174
    private val darkWizardAt = Position(3084, 3236)
    private val dangers = LunaDangers(LunaGameNames)

    /** Luna's definitions come from the test world. */
    @BeforeEach
    fun loadDefinitions() {
        TestWorld.context
    }

    private fun spawn(id: Int, position: Position = darkWizardAt, wander: Int? = 5) = NpcSpawn(id, position, null, wander, WanderingFrequency.NORMAL, null)

    private fun threats(catalog: DangerCatalog, radius: Int = 0): List<Threat> = catalog.threats(Area(Tile.of(darkWizardAt), radius))

    @Test
    fun `an npc attacking by combat level attacks below twice its level plus one, as far as it wanders and looks`() {
        val catalog = dangers.catalog(listOf(spawn(darkWizard)))

        assertEquals(
            listOf(listOf(Threat("Dark wizard", 7, Aggression.BelowCombat(15))), emptyList()),
            listOf(catalog.threats(Area(Tile(3094, 3246), radius = 0)), catalog.threats(Area(Tile(3095, 3246), radius = 0))),
        )
    }

    @Test
    fun `an npc that stands still reaches as far as it looks`() {
        val catalog = dangers.catalog(listOf(spawn(darkWizard, wander = null)))

        assertEquals(listOf(1, 0), listOf(catalog.threats(Area(Tile(3089, 3236), radius = 0)).size, catalog.threats(Area(Tile(3090, 3236), radius = 0)).size))
    }

    @Test
    fun `an npc with no aggression is no danger`() {
        val man = 1

        assertEquals(emptyList<Threat>(), threats(dangers.catalog(listOf(spawn(man)))))
    }

    @Test
    fun `an npc with no combat definition is no danger`() {
        val runescapeGuide = 945

        assertEquals(emptyList<Threat>(), threats(dangers.catalog(listOf(spawn(runescapeGuide)))))
    }

    @Test
    fun `every policy of Luna's is worded, the god policies naming their god`() {
        assertEquals(
            listOf(Aggression.Always, Aggression.BelowCombat(15), Aggression.UnlessWorn("Saradomin"), Aggression.UnlessWorn("Guthix"), Aggression.UnlessWorn("Zamorak")),
            NpcAggressionPolicy.entries.map { LunaDangers.aggression(it, 7) },
        )
    }

    @Test
    fun `an npc looks five tiles for players, an always aggressive one ten and a boss the whole view`() {
        assertEquals(
            listOf(5, 10, 15),
            listOf(LunaDangers.looks(NpcAggressionPolicy.COMBAT_LEVEL, boss = false), LunaDangers.looks(NpcAggressionPolicy.ALWAYS, boss = false), LunaDangers.looks(NpcAggressionPolicy.ALWAYS, boss = true)),
        )
    }

    @Test
    fun `an npc that is always aggressive looks twice as far and attacks any level`() {
        val gunthor = 199
        val catalog = dangers.catalog(listOf(spawn(gunthor)))

        assertEquals(
            listOf(listOf(Threat("Gunthor the brave", 29, Aggression.Always)), emptyList()),
            listOf(catalog.threats(Area(Tile(3099, 3251), radius = 0)), catalog.threats(Area(Tile(3100, 3251), radius = 0))),
        )
    }

    @Test
    fun `an npc held off by a god's items names the god`() {
        val zamorakWizard = 1007
        val saradominWizard = 1264

        assertEquals(
            listOf(Aggression.UnlessWorn("Zamorak"), Aggression.UnlessWorn("Saradomin")),
            threats(dangers.catalog(listOf(spawn(zamorakWizard), spawn(saradominWizard)))).map { it.aggression },
        )
    }

    @Test
    fun `a boss attacks anyone in view`() {
        val catalog = LunaDangers(LunaGameNames) { it == darkWizard }.catalog(listOf(spawn(darkWizard)))

        assertEquals(listOf(1, 0), listOf(catalog.threats(Area(Tile(3104, 3236), radius = 0)).size, catalog.threats(Area(Tile(3105, 3236), radius = 0)).size))
    }

    @Test
    fun `Luna's spawn file has the two dark wizards by Draynor's willows`() {
        assertEquals(listOf("Dark wizard", "Black Knight"), dangers.catalog().threats(Area(Tile(3087, 3229), radius = 10)).map { it.name })
    }

    @Test
    fun `Luna's parser reads a row by npc name (the first of that name) or id, with its wander, respawn and facing or without`() {
        val firstDarkWizard = 172
        val named = NpcSpawn(firstDarkWizard, darkWizardAt, 9, 3, WanderingFrequency.NORMAL, Direction.NORTH)
        val still = NpcSpawn(945, Position(3200, 3200), null, null, null, null)

        assertEquals(listOf(named, named.copy(id = darkWizard, wanderRadius = 7, wanderFrequency = WanderingFrequency.SLOW), still), rows())
    }

    @Test
    fun `Luna's parser refuses a wander that is neither true nor an object`() {
        assertEquals("Invalid JSON wander value.", runCatching { rows("""[{ "id": 174, "position": { "x": 1, "y": 2, "z": 0 }, "wander": 5 }]""") }.exceptionOrNull()?.message)
    }

    @TempDir
    lateinit var dir: Path

    private fun rows(
        json: String = """[
          { "name": "Dark wizard", "position": { "x": 3084, "y": 3236, "z": 0 }, "respawn_ticks": 9, "wander": true, "default_direction": "NORTH" },
          { "id": 174, "position": { "x": 3084, "y": 3236, "z": 0 }, "respawn_ticks": 9, "wander": { "radius": 7, "frequency": "SLOW" }, "default_direction": "NORTH" },
          { "id": 945, "position": { "x": 3200, "y": 3200, "z": 0 } }
        ]""",
    ): List<NpcSpawn> = NpcSpawnFileParser.readSpawns(Files.writeString(dir.resolve("npc_spawns.json"), json))
}
