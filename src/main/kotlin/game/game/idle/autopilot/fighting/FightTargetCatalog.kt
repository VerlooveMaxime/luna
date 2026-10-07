package game.idle.autopilot.fighting

import io.luna.util.GsonUtils
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/** Something the fight step can fight: every npc of [npcs] goes by [name] in a flow. */
data class FightTarget(val name: String, val npcs: Set<Int>)

/** Every fight target flows can use, from [PATH]. Loaded once at boot; a bad file fails the boot. */
class FightTargetCatalog(val targets: List<FightTarget>) {

    private val byName: Map<String, FightTarget> = targets.associateBy { it.name }

    init {
        val duplicates = targets.groupingBy { it.name }.eachCount().filterValues { it > 1 }.keys
        require(duplicates.isEmpty()) { "Duplicate fight target names: ${duplicates.sorted()}" }
    }

    fun find(name: String): FightTarget? = byName[name]

    companion object {
        val PATH: Path = Paths.get("data", "idle", "fight_targets.jsonc")

        fun parse(jsonc: String): FightTargetCatalog {
            val file = GsonUtils.GSON.fromJson(jsonc, FightTargetsJson::class.java) ?: FightTargetsJson()
            return FightTargetCatalog(file.targets.map { it.toTarget() })
        }

        fun load(path: Path): FightTargetCatalog = parse(Files.readString(path))
    }
}

/* Raw Gson shapes: every field has a default, so a missing key becomes a message naming the target. */

internal data class FightTargetsJson(val targets: List<FightTargetJson> = emptyList())

internal data class FightTargetJson(val name: String = "", val npcs: List<Int> = emptyList()) {

    fun toTarget(): FightTarget {
        require(name.isNotBlank()) { "A fight target has no name: $this" }
        require(name == name.lowercase().trim()) { "Fight target '$name' must be named in lower case" }
        require(npcs.isNotEmpty()) { "Fight target '$name' has no npcs" }
        return FightTarget(name, npcs.toSet())
    }
}
