package game.idle.content.combat

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.luna.game.model.mob.NpcAggressionProfile.NpcAggressionPolicy

/** One row of `data/game/def/npcs/npc_combat.jsonc`, with the npc name its id line carries as a comment. */
data class NpcCombatRow(
    val id: Int,
    val comment: String,
    val respawnTicks: Int,
    val aggression: Aggression?,
    val poisonous: Boolean,
    val immunePoison: Boolean,
    val hitpoints: Int,
    val maximumHit: Int,
    val attackSpeed: Int,
    val attackAnimation: Int,
    val defenceAnimation: Int,
    val deathAnimation: Int,
    val attackBonus: Int,
    val magicBonus: Int,
    val rangedBonus: Int,
    val skills: List<Int>,
    val bonuses: List<Int>,
)

/**
 * `npc_combat.jsonc` as text plus its parsed rows. [withRows] renders only the rows it is given, in the file's own
 * layout, and keeps every other byte, so an import shows in a diff as the rows it changed and nothing else.
 */
class NpcCombatFile private constructor(private val text: String, private val blocks: List<Block>) {

    private class Block(val range: IntRange, val row: NpcCombatRow)

    val rows: List<NpcCombatRow> get() = blocks.map { it.row }

    fun row(id: Int): NpcCombatRow? = blocks.firstOrNull { it.row.id == id }?.row

    /** The file with each of [rows] in place of the row of the same id. Rows the file does not have are refused. */
    fun withRows(rows: Collection<NpcCombatRow>): String {
        val byId = rows.associateBy { it.id }
        val missing = byId.keys - blocks.map { it.row.id }.toSet()
        require(missing.isEmpty()) { "npc_combat.jsonc has no row for $missing" }
        val out = StringBuilder()
        var cursor = 0
        for (block in blocks) {
            val replacement = byId[block.row.id] ?: continue
            out.append(text, cursor, block.range.first).append(render(replacement))
            cursor = block.range.last + 1
        }
        return out.append(text, cursor, text.length).toString()
    }

    companion object {

        // A row opens and closes at two spaces of indent; the aggression object inside it sits at four.
        private val ROW = Regex("""(?s)(?<=\n)  \{\n.*?\n  }""")
        private val ID_COMMENT = Regex(""""id": \d+, // (.*)""")

        fun parse(text: String): NpcCombatFile =
            NpcCombatFile(text, ROW.findAll(text).map { Block(it.range, parseRow(it.value)) }.toList())

        private fun parseRow(block: String): NpcCombatRow {
            // Lenient parsing, which JsonParser uses, skips the comment after the id.
            val json = JsonParser.parseString(block).asJsonObject
            return NpcCombatRow(
                id = json.int("id"),
                comment = ID_COMMENT.find(block)?.groupValues?.get(1) ?: "",
                respawnTicks = json.int("respawn_ticks"),
                aggression = json["aggression"].takeUnless { it.isJsonNull }?.asJsonObject?.let(::aggression),
                poisonous = json["poisonous"].asBoolean,
                immunePoison = json["immune_poison"].asBoolean,
                hitpoints = json.int("hitpoints"),
                maximumHit = json.int("maximum_hit"),
                attackSpeed = json.int("attack_speed"),
                attackAnimation = json.int("attack_animation"),
                defenceAnimation = json.int("defence_animation"),
                deathAnimation = json.int("death_animation"),
                attackBonus = json.int("attack_bonus"),
                magicBonus = json.int("magic_bonus"),
                rangedBonus = json.int("ranged_bonus"),
                skills = json["skills"].asJsonArray.map { it.asInt },
                bonuses = json["bonuses"].asJsonArray.map { it.asInt },
            )
        }

        private fun aggression(json: JsonObject) =
            Aggression(NpcAggressionPolicy.valueOf(json["policy"].asString), json.int("tolerance_minutes"))

        private fun JsonObject.int(key: String): Int = get(key).asInt

        /** The layout every row of the upstream file uses. */
        fun render(row: NpcCombatRow): String {
            val idLine = if (row.comment.isEmpty()) "\"id\": ${row.id}," else "\"id\": ${row.id}, // ${row.comment}"
            val aggression = row.aggression?.let {
                "{\n      \"policy\": \"${it.policy.name}\",\n" +
                    "      \"tolerance_minutes\": ${it.toleranceMinutes}\n    }"
            } ?: "null"
            val fields = listOf(
                idLine,
                "\"respawn_ticks\": ${row.respawnTicks},",
                "\"aggression\": $aggression,",
                "\"poisonous\": ${row.poisonous},",
                "\"immune_poison\": ${row.immunePoison},",
                "\"hitpoints\": ${row.hitpoints},",
                "\"maximum_hit\": ${row.maximumHit},",
                "\"attack_speed\": ${row.attackSpeed},",
                "\"attack_animation\": ${row.attackAnimation},",
                "\"defence_animation\": ${row.defenceAnimation},",
                "\"death_animation\": ${row.deathAnimation},",
                "\"attack_bonus\": ${row.attackBonus},",
                "\"magic_bonus\": ${row.magicBonus},",
                "\"ranged_bonus\": ${row.rangedBonus},",
                "\"skills\": ${array(row.skills)},",
                "\"bonuses\": ${array(row.bonuses)}",
            )
            return "  {\n" + fields.joinToString("\n") { "    $it" } + "\n  }"
        }

        private fun array(values: List<Int>): String = values.joinToString(",\n", "[\n", "\n    ]") { "      $it" }
    }
}
