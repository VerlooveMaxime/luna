package game.idle.content.combat

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class OsrsMonstersTest {

    private fun monsterJson(id: Int, name: String = "Jail guard", hitpoints: String = "32", maxHit: String = "3") = """
        "$id": {
          "id": $id, "name": "$name", "combat_level": 26, "hitpoints": $hitpoints, "max_hit": $maxHit,
          "attack_speed": 5, "aggressive": true,
          "attack_level": 19, "strength_level": 23, "defence_level": 21, "ranged_level": 1, "magic_level": 1,
          "attack_bonus": 9, "strength_bonus": 5, "attack_magic": 0, "attack_ranged": 0,
          "defence_stab": 8, "defence_slash": 9, "defence_crush": 10, "defence_magic": 4, "defence_ranged": 9
        }
    """

    private fun parse(vararg monsters: String) = OsrsMonsters.parse(monsters.joinToString(",", "{", "}"))

    @Test
    fun `a monster reads every stat the importer uses`() {
        assertEquals(osrsMonster(), parse(monsterJson(4276)).matching("Jail guard", 26))
    }

    @Test
    fun `the name matches in any case`() {
        assertEquals(osrsMonster(), parse(monsterJson(4276)).matching("JAIL GUARD", 26))
    }

    @Test
    fun `another combat level does not match`() {
        assertNull(parse(monsterJson(4276)).matching("Jail guard", 27))
    }

    @Test
    fun `entries that agree count as one match`() {
        assertEquals(osrsMonster(), parse(monsterJson(4276), monsterJson(4277)).matching("Jail guard", 26))
    }

    @Test
    fun `entries that disagree are no match`() {
        assertNull(parse(monsterJson(4276), monsterJson(4277, hitpoints = "40")).matching("Jail guard", 26))
    }

    @Test
    fun `a monster without hitpoints is left out`() {
        assertNull(parse(monsterJson(4276, hitpoints = "null")).matching("Jail guard", 26))
    }

    @Test
    fun `a blank max hit stays unknown`() {
        assertNull(parse(monsterJson(4276, maxHit = "null")).matching("Jail guard", 26)?.maximumHit)
    }

    @Test
    fun `a missing number counts as 0`() {
        val json = monsterJson(4276).replace("\"attack_bonus\": 9, ", "")

        assertEquals(0, parse(json).matching("Jail guard", 26)?.attackBonus)
    }
}
