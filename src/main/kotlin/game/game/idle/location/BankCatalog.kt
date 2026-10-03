package game.idle.location

import io.luna.util.GsonUtils
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/** A bank flows can use: [booth] is the tile of the booth the bank step clicks. */
data class Bank(val id: String, val name: String, val booth: Tile)

/** Every bank flows can use, from [PATH]. Loaded once at boot; a bad file fails the boot. */
class BankCatalog(val banks: List<Bank>) {

    private val byId: Map<String, Bank> = banks.associateBy { it.id }

    init {
        val duplicates = banks.groupingBy { it.id }.eachCount().filterValues { it > 1 }.keys
        require(duplicates.isEmpty()) { "Duplicate bank ids: ${duplicates.sorted()}" }
    }

    fun find(id: String): Bank? = byId[id]

    companion object {
        val PATH: Path = Paths.get("data", "idle", "banks.jsonc")

        fun parse(jsonc: String): BankCatalog {
            val file = GsonUtils.GSON.fromJson(jsonc, BanksJson::class.java) ?: BanksJson()
            return BankCatalog(file.banks.map { it.toBank() })
        }

        fun load(path: Path): BankCatalog = parse(Files.readString(path))
    }
}

/*
 * Raw Gson shapes. Every field has a default so Gson uses the no-arg constructor and a missing key can never leave a
 * Kotlin non-null field null; the conversion below turns each gap into a message that names the bank.
 */

internal data class BanksJson(val banks: List<BankJson> = emptyList())

internal data class TileJson(val x: Int = -1, val y: Int = -1, val z: Int = 0)

internal data class BankJson(val id: String = "", val name: String = "", val booth: TileJson? = null) {

    fun toBank(): Bank {
        require(id.isNotBlank()) { "A bank has no id: $this" }
        require(name.isNotBlank()) { "Bank '$id' has no name" }
        val tile = requireNotNull(booth) { "Bank '$id' has no booth" }
        require(tile.x >= 0) { "Bank '$id' has a negative booth x" }
        require(tile.y >= 0) { "Bank '$id' has a negative booth y" }
        require(tile.z in 0..3) { "Bank '$id' has booth floor ${tile.z}, expected 0 to 3" }
        return Bank(id, name, Tile(tile.x, tile.y, tile.z))
    }
}
