package game.idle.content.combat

import api.bot.zone.Zone
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.luna.game.cache.Cache
import io.luna.game.cache.codec.NpcDefinitionDecoder
import io.luna.game.model.Position
import io.luna.game.model.def.NpcDefinition
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.LocalDate

/** A cache npc as target selection sees it. */
data class CacheNpc(val identity: NpcIdentity, val attackable: Boolean)

/** The npc definitions of the cache on disk, read once per JVM. Needs the `luna/` working directory. */
object CacheNpcs {

    val all: Map<Int, CacheNpc> by lazy { read() }

    // A JVM that already decoded the definitions (a test world) only logs that the repository is locked and keeps its
    // own, which are the same.
    private fun read(): Map<Int, CacheNpc> {
        Cache().also { it.open() }.use { cache ->
            cache.runDecoders(null, NpcDefinitionDecoder())
            cache.waitForDecoders()
        }
        // The repository is an array and yields null for the ids the cache leaves out.
        return NpcDefinition.ALL.filterNotNull().associate {
            it.id() to CacheNpc(NpcIdentity(it.id(), it.name, it.combatLevel), attackable = it.actions[1] == "Attack")
        }
    }
}

/**
 * The attackable npc kinds `npc_spawns.json` places in [regions], lowest id first. A spawn given by name takes the
 * first id of that name, as Luna's spawn loader does.
 */
fun spawnedAttackable(spawnsJson: String, npcs: Map<Int, CacheNpc>, regions: Set<Int>): List<NpcIdentity> {
    val firstIdByName = npcs.values.map { it.identity }.sortedBy { it.id }.distinctBy { it.name.lowercase() }
        .associate { it.name.lowercase() to it.id }
    return JsonParser.parseString(spawnsJson).asJsonArray
        .map { it.asJsonObject }
        .filter { regionOf(it["position"].asJsonObject) in regions }
        .map { spawn -> spawn["id"]?.asInt ?: firstIdByName.getValue(spawn["name"].asString.lowercase()) }
        .distinct()
        .sorted()
        .map(npcs::getValue)
        .filter { it.attackable }
        .map { it.identity }
}

private fun regionOf(position: JsonObject): Int = Position(position["x"].asInt, position["y"].asInt).regionId

/** Region ids given as numbers, and the regions of the bot zones given by name. */
fun regionsOf(areas: List<String>, zones: List<Zone>): Set<Int> =
    areas.flatMap { area ->
        area.toIntOrNull()?.let(::listOf)
            ?: zones.firstOrNull { it.name.equals(area, ignoreCase = true) }?.regions?.toList()
            ?: throw IllegalArgumentException(
                "'$area' is neither a region id nor a zone (${zones.joinToString { it.name.lowercase() }})",
            )
    }.toSet()

/** Imports the combat rows of the npcs spawned in some regions from the sources under [sourcesDir] into [dataDir]. */
class NpcCombatImporter(
    private val sourcesDir: Path,
    private val dataDir: Path,
    private val npcs: Map<Int, CacheNpc>,
    private val date: LocalDate,
) {

    /** Rewrites `npc_combat.jsonc` in place and returns the report. */
    fun run(areas: List<String>, regions: Set<Int>): String {
        val sources = CombatSources(
            LostCityBranch.read(sourcesDir.resolve(LOSTCITY_377)),
            LostCityBranch.read(sourcesDir.resolve(LOSTCITY_289)),
            OsrsMonsters.parse(Files.readString(sourcesDir.resolve(OSRS_MONSTERS))),
        )
        val combatPath = dataDir.resolve(COMBAT_FILE)
        val file = NpcCombatFile.parse(Files.readString(combatPath))
        val targets = spawnedAttackable(Files.readString(dataDir.resolve(SPAWNS_FILE)), npcs, regions)
        val outcomes = CombatImport(sources).outcomes(file, targets)
        Files.writeString(combatPath, file.withRows(outcomes.filterIsInstance<ImportOutcome.Updated>().map { it.row }))
        return CombatImportReport(areas, date).format(outcomes)
    }

    companion object {
        const val LOSTCITY_377 = "lostcity-content"
        const val LOSTCITY_289 = "lostcity-content-289"
        const val OSRS_MONSTERS = "osrsreboxed-db/docs/monsters-complete.json"
        const val COMBAT_FILE = "game/def/npcs/npc_combat.jsonc"
        const val SPAWNS_FILE = "game/world/npc_spawns.json"
    }
}

/**
 * `./gradlew importNpcCombat -Pareas="12336 12592 12436"`: arguments are the sources directory, Luna's data
 * directory, the report file, then region ids or zone names.
 */
fun main(args: Array<String>) {
    val areas = args.drop(3)
    require(areas.isNotEmpty()) { "name at least one region id or zone, e.g. -Pareas=\"12336 12592 12436\"" }
    val importer = NpcCombatImporter(Paths.get(args[0]), Paths.get(args[1]), CacheNpcs.all, LocalDate.now())
    val report = importer.run(areas, regionsOf(areas, Zone.entries))
    val reportFile = Paths.get(args[2])
    Files.createDirectories(reportFile.parent)
    Files.writeString(reportFile, report)
    println(reportFile)
}
