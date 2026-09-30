package game.idle.location.survey

import engine.bank.Banking
import game.skill.woodcutting.cutTree.TreeStump
import io.luna.game.cache.Cache
import io.luna.game.cache.codec.MapDecoder
import io.luna.game.cache.codec.ObjectDefinitionDecoder
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * The [SurveyMap] of the cache on disk, read without booting the server. Needs the `luna/` working directory, the
 * one Luna's [Cache] resolves `data/game/cache` against, and a heap of 1 GB or more.
 */
object CacheMap {

    private val DATA_FILE: Path = Paths.get("data", "game", "cache", "main_file_cache.dat")

    val isPresent: Boolean get() = Files.exists(DATA_FILE)

    // Read once per JVM: decoding stores the object definitions in GameObjectDefinition.ALL, which locks on first store.
    val map: SurveyMap by lazy { read() }

    private fun read(): SurveyMap {
        val cache = Cache().also { it.open() }
        return cache.use {
            // Neither decoder reads the context; only a booted server has one.
            cache.runDecoders(null, ObjectDefinitionDecoder(), MapDecoder())
            cache.waitForDecoders()
            Banking.loadBankingObjects()
            // A decoder that fails logs its cause and leaves the table unset, so this throws right after the log.
            val objects = cache.mapIndexTable.objectSet
            SurveyMap.from(objects, TreeStump.TREE_ID_MAP.mapValues { it.value.tree }, Banking.bankingObjects)
        }
    }
}
