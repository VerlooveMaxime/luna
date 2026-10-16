package engine.spawn

import api.predef.*
import com.google.common.collect.ImmutableList
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.luna.game.model.Direction
import io.luna.game.model.Position
import io.luna.game.model.collision.CollisionFlag
import io.luna.game.model.def.NpcDefinition
import io.luna.game.model.mob.movement.wandering.WanderingFrequency
import io.luna.game.model.path.route.RouteStrategy
import io.luna.util.GsonUtils
import io.luna.util.parser.JsonFileParser
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*

/**
 * Loads the global [PersistentNpc] spawn JSON file.
 *
 * @author lare96
 */
internal class NpcSpawnFileParser : JsonFileParser<PersistentNpc>(PATH) {

    companion object {

        /**
         * The path to the file.
         */
        private val PATH = Paths.get("data", "game", "world", "npc_spawns.jsonc")

        /**
         * Reads one row of the file, for this parser and for code that lists the spawns without placing them.
         */
        fun readSpawn(token: JsonObject): NpcSpawn {
            val nameOrId = if (token.has("name")) token["name"].asString else token["id"].asInt
            val position = GsonUtils.getAsType(token["position"], Position::class.java)
            val id = if (nameOrId is Int) nameOrId else NpcDefinition.ALL.find {
                it.name.contentEquals(nameOrId as String, true)
            }?.id()
            val respawn = if (token.has("respawn_ticks")) token["respawn_ticks"].asInt else null
            val defaultDirection =
                if (token.has("default_direction")) Direction.valueOf(token["default_direction"].asString) else null
            val routeStrategy =
                if (token.has("route_strategy")) RouteStrategy.valueOf(token["route_strategy"].asString) else null
            val wander = if (token.has("wander")) token["wander"] else null
            var radius: Int? = null
            var frequency: WanderingFrequency? = null
            if (wander != null) {
                if (wander.isJsonPrimitive && wander.asJsonPrimitive.isBoolean && wander.asBoolean) {
                    radius = 3
                    frequency = WanderingFrequency.NORMAL
                } else if (wander.isJsonObject) {
                    val wanderObject = wander.asJsonObject
                    radius = wanderObject["radius"].asInt
                    frequency = if (wanderObject.has("frequency"))
                        WanderingFrequency.valueOf(wanderObject["frequency"].asString) else WanderingFrequency.NORMAL
                } else {
                    throw IllegalStateException("Invalid JSON wander value.")
                }
            }
            if (id == null) {
                throw IllegalStateException("No NPC found for ID/name [$nameOrId].")
            }
            return NpcSpawn(id, position, respawn, radius, frequency, defaultDirection, routeStrategy)
        }

        /**
         * Reads every row of the file at [path].
         */
        fun readSpawns(path: Path = PATH): List<NpcSpawn> =
            GsonUtils.readAsType(path, JsonArray::class.java).map { readSpawn(it.asJsonObject) }
    }

    override fun convert(token: JsonObject): PersistentNpc {
        val (id, position, respawn, radius, frequency, defaultDirection, routeStrategy) = readSpawn(token)
        val npc = PersistentNpc(id, position, respawn, radius, frequency)
        npc.defaultDirection = Optional.ofNullable(defaultDirection)
        if (routeStrategy != null) {
            npc.routeStrategy = routeStrategy
        }
        return npc
    }

    override fun onCompleted(tokenObjects: ImmutableList<PersistentNpc>) {
        if (tokenObjects.isNotEmpty()) {
            gameService.sync {
                checkIndoorsSpawns(tokenObjects)
                tokenObjects.forEach { world.npcs.add(it) }
            }
            logger.debug("Loaded ${tokenObjects.size} global NPC spawns!")
        }
    }

    /**
     * Warns about [RouteStrategy.INDOORS] spawns that are not on a roofed tile. Such an NPC could never move, so the
     * strategy was most likely given to the wrong spawn or the map has no roof there.
     */
    private fun checkIndoorsSpawns(spawns: List<PersistentNpc>) {
        val view = world.collisionManager.view(false)
        spawns.filter { it.routeStrategy == RouteStrategy.INDOORS }.forEach {
            val pos = it.position
            if (view.get(pos.x, pos.y, pos.z) and CollisionFlag.ROOF == 0) {
                logger.warn("Indoors NPC [${it.id}] at $pos is not on a roofed tile.")
            }
        }
    }
}

/**
 * One row of the npc spawn file: the npc, where, how many ticks it takes to come back once killed (null for the
 * combat definition's delay), how far it wanders and how often (null for an npc that stands still), the way it
 * faces, and how it routes (null for the default).
 */
data class NpcSpawn(
    val id: Int,
    val position: Position,
    val respawnTicks: Int?,
    val wanderRadius: Int?,
    val wanderFrequency: WanderingFrequency?,
    val defaultDirection: Direction?,
    val routeStrategy: RouteStrategy?,
)
