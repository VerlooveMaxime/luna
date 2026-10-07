package engine.spawn

import api.predef.*
import api.predef.ext.*
import com.google.common.collect.ImmutableList
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.luna.game.model.Position
import io.luna.game.model.item.Item
import io.luna.util.GsonUtils
import io.luna.util.parser.JsonFileParser
import org.apache.logging.log4j.LogManager
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Loads the global item spawn JSON file.
 *
 * @author lare96
 */
internal class ItemSpawnFileParser : JsonFileParser<PersistentGroundItem>(PATH) {

    /**
     * Additional non-stackable items to be added.
     */
    private val items = ConcurrentLinkedQueue<PersistentGroundItem>()

    companion object {

        /**
         * The path to the file.
         */
        private val PATH = Paths.get("data", "game", "world", "item_spawns.json")

        /**
         * Reads one row of the file, for this parser and for code that lists the spawns without placing them.
         */
        fun readSpawn(token: JsonObject): ItemSpawn {
            val nameOrId = if (token.has("name")) token["name"].asString else token["id"].asInt
            val amount = token["amount"].asInt
            val position = GsonUtils.getAsType(token["position"], Position::class.java)
            val id = if (nameOrId is Int) nameOrId else Item.byName(nameOrId as String).id
            val respawn = if (token.has("respawn_ticks")) token["respawn_ticks"].asInt else
                PersistentGroundItem.DEFAULT_RESPAWN_TICKS
            return ItemSpawn(id, amount, position, respawn)
        }

        /**
         * Reads every row of the file at [path].
         */
        fun readSpawns(path: Path = PATH): List<ItemSpawn> =
            GsonUtils.readAsType(path, JsonArray::class.java).map { readSpawn(it.asJsonObject) }
    }

    override fun convert(token: JsonObject): PersistentGroundItem {
        val (id, spawnAmount, position, respawn) = readSpawn(token)
        var amount = spawnAmount
        val def = itemDef(id)
        if (!def.isStackable && amount > 1) {
            // Handle non-stackable items with an amount > 1.
            amount--
            repeat(amount) {
                items += PersistentGroundItem(id, 1, position, respawn)
            }
            amount = 1
        }
        return PersistentGroundItem(id, amount, position, respawn)
    }

    override fun onCompleted(tokenObjects: ImmutableList<PersistentGroundItem>) {
        logger.debug("Loaded ${tokenObjects.size} global item spawns!")
        gameService.sync {
            world.scheduleOnce(1) {
                tokenObjects.forEach { world.addItem(it) }
                while(true) { world.addItem(items.poll() ?: break) }
            }
        }
    }

    /**
     * Manually adds a persistent item to be spawned.
     */
    fun add(item: PersistentGroundItem) {
        items.add(item)
    }
}
/**
 * One row of the item spawn file: the item, how many, where, and how many ticks it takes to come back once taken.
 */
data class ItemSpawn(val id: Int, val amount: Int, val position: Position, val respawnTicks: Int)
