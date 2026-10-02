package game.idle.content.combat

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension

/** One `[debugname]` block of a LostCity config file (`.npc`, `.inv`, ...): its `key=value` lines in file order. */
data class ConfigBlock(val debugname: String, val lines: List<Pair<String, String>>) {

    fun value(key: String): String? = lines.lastOrNull { it.first == key }?.second

    /** The `param=name,value` lines, name to value. */
    val params: Map<String, String>
        get() = lines.filter { it.first == "param" }
            .associate { it.second.substringBefore(',') to it.second.substringAfter(',') }
}

/** Parsers for the text formats of LostCity's content repository (github.com/LostCityRS/Content). */
object LostCityFormats {

    private val HEADER = Regex("""^\[(.+)]\s*$""")

    /** The `id=debugname` lines of a `pack/<kind>.pack` file. */
    fun parsePack(text: String): Map<Int, String> =
        text.lines()
            .filter { '=' in it }
            .associate { it.substringBefore('=').trim().toInt() to it.substringAfter('=').trim() }

    /** The blocks of a config file. Comment lines and comments after a value are dropped. */
    fun parseBlocks(text: String): List<ConfigBlock> {
        val blocks = mutableListOf<ConfigBlock>()
        var lines: MutableList<Pair<String, String>>? = null
        for (line in text.lines().map { it.substringBefore("//").trim() }) {
            val header = HEADER.find(line)
            if (header != null) {
                lines = mutableListOf()
                blocks += ConfigBlock(header.groupValues[1], lines)
            } else if ('=' in line) {
                lines?.add(line.substringBefore('=') to line.substringAfter('='))
            }
        }
        return blocks
    }
}

/** A checkout of one LostCity content branch: npc ids, npc configs, and animation ids by debugname. */
class LostCityBranch(
    private val npcDebugnames: Map<Int, String>,
    private val npcs: Map<String, ConfigBlock>,
    val sequences: Map<String, Int>,
) {

    fun npc(id: Int): ConfigBlock? = npcDebugnames[id]?.let(npcs::get)

    /** By debugname: an older branch is matched to our ids through the debugnames of the 377 branch. */
    fun npcNamed(debugname: String): ConfigBlock? = npcs[debugname]

    fun debugname(id: Int): String? = npcDebugnames[id]

    companion object {

        fun read(root: Path): LostCityBranch {
            val configs = Files.walk(root.resolve("scripts")).use { paths ->
                paths.filter { it.extension == "npc" }.toList()
            }
            val npcs = configs.sortedBy { it.toString() }.flatMap { LostCityFormats.parseBlocks(text(it)) }
            return LostCityBranch(
                npcDebugnames = LostCityFormats.parsePack(text(root.resolve("pack/npc.pack"))),
                npcs = npcs.associateBy { it.debugname },
                sequences = LostCityFormats.parsePack(text(root.resolve("pack/seq.pack"))).entries
                    .associate { (id, name) -> name to id },
            )
        }

        /** Undecodable bytes become replacement characters instead of failing the whole import. */
        private fun text(path: Path): String = Files.readAllBytes(path).toString(Charsets.UTF_8)
    }
}
