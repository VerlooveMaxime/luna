package game.idle.content.audit

/** A cache definition as the client shows it: [actions] holds its five menu options, "" where the menu has none. */
sealed interface Kind {
    val id: Int
    val name: String
    val actions: List<String>

    /** 0 for objects and for npcs that cannot fight. */
    val combatLevel: Int

    /** Whether a click on the option at [index] starts combat instead of reaching a click handler. */
    fun startsCombat(index: Int): Boolean
}

data class NpcKind(
    override val id: Int,
    override val name: String,
    override val combatLevel: Int,
    override val actions: List<String>,
) : Kind {

    val attackable: Boolean get() = startsCombat(ATTACK_INDEX)

    /**
     * Only for an "Attack" label: another label on that option is sent as an attack all the same, so nothing
     * handles it.
     */
    override fun startsCombat(index: Int): Boolean = index == ATTACK_INDEX && actions[index] == "Attack"

    companion object {
        /** The client sends a click on option 2 of an npc as an attack, whatever its label. */
        const val ATTACK_INDEX = 1
    }
}

data class ObjectKind(override val id: Int, override val name: String, override val actions: List<String>) : Kind {
    override val combatLevel: Int get() = 0

    override fun startsCombat(index: Int): Boolean = false
}

/** The parts of an npc combat definition the placeholder rule reads. */
data class CombatStats(
    val maximumHit: Int,
    val attack: Int,
    val strength: Int,
    val defence: Int,
    val ranged: Int,
    val magic: Int,
) {
    val everySkillZero: Boolean get() = listOf(attack, strength, defence, ranged, magic).all { it == 0 }

    /** The usual npc melee max hit for this strength with no strength bonus, `floor(0.5 + (str + 8) * 64 / 640)`. */
    val strengthMaxHit: Int get() = ((strength + 8) * 64 + 320) / 640
}

/** How many npcs or objects of one id stand in one 64 by 64 map region, all floors together. */
data class RegionCount(val region: Int, val id: Int, val count: Int)

/**
 * What the running server holds, read once for [ContentAudit]: the spawned npcs, the placed objects that have a menu
 * option, the definitions of both, and what data files and plugins registered for them.
 */
data class ContentFacts(
    val npcs: Map<Int, NpcKind>,
    val objects: Map<Int, ObjectKind>,
    val spawns: List<RegionCount>,
    val placements: List<RegionCount>,
    /** Spawned npc ids that have a combat definition, to its stats. */
    val combatDefinitions: Map<Int, CombatStats>,
    /** Spawned npc ids that have a drop table. */
    val dropTableIds: Set<Int>,
    /** Definition action index to the npc ids whose click on that option reaches a handler. */
    val npcHandlers: Map<Int, Set<Int>>,
    /** Definition action index to the object ids whose click on that option reaches a handler. */
    val objectHandlers: Map<Int, Set<Int>>,
)
