package game.idle.content.audit

/** A menu option, numbered as the client numbers it: definition action index plus one. */
data class MenuOption(val number: Int, val label: String)

/** A kind found wanting in an area and how many of it stand there. */
data class Shortfall(val kind: Kind, val count: Int)

/** An attackable kind whose combat definition looks made up, with the stats that give it away. */
data class PlaceholderCombat(val shortfall: Shortfall, val stats: CombatStats)

/** What one area lacks: a bot travel zone ([zone] true), or one map region outside every zone. */
data class AreaAudit(
    val name: String,
    val zone: Boolean,
    val regions: List<Int>,
    val npcCount: Int,
    val npcKindCount: Int,
    val objectCount: Int,
    val objectKindCount: Int,
    val withoutCombatDefinition: List<Shortfall>,
    val placeholderCombat: List<PlaceholderCombat>,
    val withoutDropTable: List<Shortfall>,
    val unhandledNpcOptions: Map<MenuOption, List<Shortfall>>,
    val unhandledObjectOptions: Map<MenuOption, List<Shortfall>>,
) {
    val findingCount: Int
        get() = withoutCombatDefinition.size + placeholderCombat.size + withoutDropTable.size +
            unhandledNpcOptions.values.sumOf { it.size } + unhandledObjectOptions.values.sumOf { it.size }
}

/**
 * Lists, per area, what its spawned npcs and placed objects lack: attackable npcs without a combat definition (Luna
 * then fights them with Man's stats), with a placeholder one, or without a drop table, and menu options whose click
 * reaches no handler. [zoneOfRegion] names the zone holding a region; a region outside every zone is an area of its
 * own.
 */
class ContentAudit(private val facts: ContentFacts, private val zoneOfRegion: Map<Int, String>) {

    private data class AreaKey(val name: String, val zone: Boolean)

    /** Zones by name, then the regions outside every zone by id. */
    fun areas(): List<AreaAudit> {
        val regions = (facts.spawns + facts.placements).map { it.region }.distinct().sorted()
        val byArea = regions.groupBy { region ->
            zoneOfRegion[region]?.let { AreaKey(it, zone = true) } ?: AreaKey("region $region", zone = false)
        }
        val (zones, unzoned) = byArea.map { (area, areaRegions) -> audit(area, areaRegions) }.partition { it.zone }
        return zones.sortedBy { it.name } + unzoned
    }

    private fun audit(area: AreaKey, regions: List<Int>): AreaAudit {
        val inArea = regions.toSet()
        val npcs = countsIn(facts.spawns, inArea).map { (id, count) -> facts.npcs.getValue(id) to count }
        val objects = countsIn(facts.placements, inArea).map { (id, count) -> facts.objects.getValue(id) to count }
        val attackable = npcs.filter { (kind, _) -> kind.attackable }
        return AreaAudit(
            name = area.name,
            zone = area.zone,
            regions = regions,
            npcCount = npcs.sumOf { it.second },
            npcKindCount = npcs.size,
            objectCount = objects.sumOf { it.second },
            objectKindCount = objects.size,
            withoutCombatDefinition = shortfalls(attackable.filter { it.first.id !in facts.combatDefinitions }),
            placeholderCombat = placeholders(attackable),
            withoutDropTable = shortfalls(attackable.filter { it.first.id !in facts.dropTableIds }),
            unhandledNpcOptions = unhandledOptions(npcs, facts.npcHandlers),
            unhandledObjectOptions = unhandledOptions(objects, facts.objectHandlers),
        )
    }

    private fun countsIn(counts: List<RegionCount>, regions: Set<Int>): Map<Int, Int> =
        counts.filter { it.region in regions }.groupingBy { it.id }.fold(0) { total, count -> total + count.count }

    /** An option that starts combat is left to the two combat rules. */
    private fun unhandledOptions(
        kinds: List<Pair<Kind, Int>>,
        handlers: Map<Int, Set<Int>>,
    ): Map<MenuOption, List<Shortfall>> {
        val unhandled = kinds.flatMap { (kind, count) ->
            kind.actions.withIndex()
                .filter { (index, label) -> label.isNotEmpty() && !kind.startsCombat(index) }
                .filter { (index, _) -> kind.id !in handlers.getOrDefault(index, emptySet()) }
                .map { (index, label) -> MenuOption(index + 1, label) to Shortfall(kind, count) }
        }
        return unhandled.groupBy({ it.first }, { it.second })
            .mapValues { (_, shortfalls) -> shortfalls.sortedWith(MOST_FIRST) }
            .entries
            .sortedWith(compareByDescending<Map.Entry<MenuOption, List<Shortfall>>> { it.value.sumOf(Shortfall::count) }
                .thenBy { it.key.number }
                .thenBy { it.key.label })
            .associate { it.key to it.value }
    }

    /**
     * Half the strength max hit leaves room for npcs that really hit soft, while catching the max hit of 1 that many
     * rows of `npc_combat.jsonc` carry whatever the npc (Kalphite Queen, strength 300). A row with every skill at 0
     * was never filled in.
     */
    private fun placeholders(attackable: List<Pair<NpcKind, Int>>): List<PlaceholderCombat> =
        attackable.mapNotNull { (kind, count) ->
            facts.combatDefinitions[kind.id]
                ?.takeIf { it.everySkillZero || it.maximumHit * 2 < it.strengthMaxHit }
                ?.let { PlaceholderCombat(Shortfall(kind, count), it) }
        }.sortedWith(compareBy(MOST_FIRST) { it.shortfall })

    private fun shortfalls(kinds: List<Pair<Kind, Int>>): List<Shortfall> =
        kinds.map { (kind, count) -> Shortfall(kind, count) }.sortedWith(MOST_FIRST)

    private companion object {
        val MOST_FIRST: Comparator<Shortfall> = compareByDescending<Shortfall> { it.count }.thenBy { it.kind.id }
    }
}
