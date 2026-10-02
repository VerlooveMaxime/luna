package game.idle.content.combat

import com.google.gson.JsonObject
import com.google.gson.JsonParser

/** An OSRS monster from osrsreboxed-db's `monsters-complete.json`, reduced to what a Luna combat row holds. */
data class OsrsMonster(
    val name: String,
    val combatLevel: Int,
    val hitpoints: Int,
    val maximumHit: Int?,
    val attackSpeed: Int?,
    val aggressive: Boolean,
    val attack: Int,
    val strength: Int,
    val defence: Int,
    val ranged: Int,
    val magic: Int,
    val attackBonus: Int,
    val strengthBonus: Int,
    val magicBonus: Int,
    val rangedBonus: Int,
    val defenceBonuses: List<Int>,
)

/**
 * OSRS monsters by name and combat level. OSRS ids are not ours, so that pair is the match; several entries for one
 * pair only count when they agree, since variants with different stats would otherwise be picked at random.
 */
class OsrsMonsters(monsters: List<OsrsMonster>) {

    private val byNameAndLevel = monsters.groupBy { it.name.lowercase() to it.combatLevel }

    fun matching(name: String, combatLevel: Int): OsrsMonster? =
        byNameAndLevel[name.lowercase() to combatLevel]?.distinct()?.singleOrNull()

    companion object {

        /** Entries without hitpoints are pages the wiki never filled in and are left out. */
        fun parse(json: String): OsrsMonsters =
            OsrsMonsters(
                JsonParser.parseString(json).asJsonObject.entrySet()
                    .map { it.value.asJsonObject }
                    .filter { it.intOrNull("hitpoints") != null }
                    .map(::monster),
            )

        private fun monster(json: JsonObject) = OsrsMonster(
            name = json["name"].asString,
            combatLevel = json.int("combat_level"),
            hitpoints = json.int("hitpoints"),
            maximumHit = json.intOrNull("max_hit"),
            attackSpeed = json.intOrNull("attack_speed"),
            aggressive = json["aggressive"].asBoolean,
            attack = json.int("attack_level"),
            strength = json.int("strength_level"),
            defence = json.int("defence_level"),
            ranged = json.int("ranged_level"),
            magic = json.int("magic_level"),
            attackBonus = json.int("attack_bonus"),
            strengthBonus = json.int("strength_bonus"),
            magicBonus = json.int("attack_magic"),
            rangedBonus = json.int("attack_ranged"),
            defenceBonuses = listOf("stab", "slash", "crush", "magic", "ranged").map { json.int("defence_$it") },
        )

        private fun JsonObject.intOrNull(key: String): Int? = get(key)?.takeUnless { it.isJsonNull }?.asInt

        /** A missing number counts as 0, as the wiki infobox shows a blank stat. */
        private fun JsonObject.int(key: String): Int = intOrNull(key) ?: 0
    }
}
