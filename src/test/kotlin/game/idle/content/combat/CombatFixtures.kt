package game.idle.content.combat

import io.luna.game.model.mob.NpcAggressionProfile.NpcAggressionPolicy
import java.nio.file.Files
import java.nio.file.Path

/** A row as `npc_combat.jsonc` writes it, with Man's values unless told otherwise. */
fun row(
    id: Int = 1,
    comment: String = "Man",
    respawnTicks: Int = 9,
    aggression: Aggression? = null,
    hitpoints: Int = 7,
    maximumHit: Int = 1,
    attackSpeed: Int = 4,
    attackAnimation: Int = 422,
    skills: List<Int> = listOf(1, 1, 1, 1, 1),
    bonuses: List<Int> = listOf(-21, -21, -21, -21, -21, 0),
) = NpcCombatRow(
    id = id,
    comment = comment,
    respawnTicks = respawnTicks,
    aggression = aggression,
    poisonous = false,
    immunePoison = false,
    hitpoints = hitpoints,
    maximumHit = maximumHit,
    attackSpeed = attackSpeed,
    attackAnimation = attackAnimation,
    defenceAnimation = 1834,
    deathAnimation = 836,
    attackBonus = 0,
    magicBonus = 0,
    rangedBonus = 0,
    skills = skills,
    bonuses = bonuses,
)

const val MAN_BLOCK = """  {
    "id": 1, // Man
    "respawn_ticks": 9,
    "aggression": null,
    "poisonous": false,
    "immune_poison": false,
    "hitpoints": 7,
    "maximum_hit": 1,
    "attack_speed": 4,
    "attack_animation": 422,
    "defence_animation": 1834,
    "death_animation": 836,
    "attack_bonus": 0,
    "magic_bonus": 0,
    "ranged_bonus": 0,
    "skills": [
      1,
      1,
      1,
      1,
      1
    ],
    "bonuses": [
      -21,
      -21,
      -21,
      -21,
      -21,
      0
    ]
  }"""

const val GUARD_BLOCK = """  {
    "id": 447, // Jail guard
    "respawn_ticks": 9,
    "aggression": {
      "policy": "COMBAT_LEVEL",
      "tolerance_minutes": 10
    },
    "poisonous": true,
    "immune_poison": true,
    "hitpoints": 32,
    "maximum_hit": 1,
    "attack_speed": 7,
    "attack_animation": 422,
    "defence_animation": 404,
    "death_animation": 2304,
    "attack_bonus": 3,
    "magic_bonus": 4,
    "ranged_bonus": 5,
    "skills": [
      19,
      23,
      21,
      1,
      1
    ],
    "bonuses": [
      8,
      9,
      10,
      4,
      9
    ]
  }"""

const val COMBAT_FILE_HEAD = "/*\n  Resolves combat data related to NPCs.\n */\n[\n"

val COMBAT_FILE_TEXT = "$COMBAT_FILE_HEAD$MAN_BLOCK,\n$GUARD_BLOCK\n]\n"

val JAIL_GUARD = NpcIdentity(447, "Jail guard", 26)
val GIANT_RAT = NpcIdentity(950, "Giant rat", 3)

/** Writes a LostCity content checkout holding [npcFiles] (path under `scripts/` to text) and its two packs. */
fun lostCityTree(root: Path, npcPack: String, seqPack: String, npcFiles: Map<String, String>): Path {
    Files.createDirectories(root.resolve("pack"))
    Files.createDirectories(root.resolve("scripts"))
    Files.writeString(root.resolve("pack/npc.pack"), npcPack)
    Files.writeString(root.resolve("pack/seq.pack"), seqPack)
    npcFiles.forEach { (path, text) ->
        val file = root.resolve("scripts").resolve(path)
        Files.createDirectories(file.parent)
        Files.writeString(file, text)
    }
    return root
}

fun branch(npcs: Map<Int, String> = emptyMap(), configs: String = "", sequences: Map<String, Int> = emptyMap()) =
    LostCityBranch(npcs, LostCityFormats.parseBlocks(configs).associateBy { it.debugname }, sequences)

fun osrsMonster(
    name: String = "Jail guard",
    combatLevel: Int = 26,
    hitpoints: Int = 32,
    maximumHit: Int? = 3,
    attackSpeed: Int? = 5,
    aggressive: Boolean = true,
    attack: Int = 19,
    strength: Int = 23,
    defence: Int = 21,
) = OsrsMonster(
    name = name,
    combatLevel = combatLevel,
    hitpoints = hitpoints,
    maximumHit = maximumHit,
    attackSpeed = attackSpeed,
    aggressive = aggressive,
    attack = attack,
    strength = strength,
    defence = defence,
    ranged = 1,
    magic = 1,
    attackBonus = 9,
    strengthBonus = 5,
    magicBonus = 0,
    rangedBonus = 0,
    defenceBonuses = listOf(8, 9, 10, 4, 9),
)

val COMBAT_LEVEL_TEN = Aggression(NpcAggressionPolicy.COMBAT_LEVEL, 10)
