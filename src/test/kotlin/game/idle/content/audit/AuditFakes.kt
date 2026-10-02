package game.idle.content.audit

/** An npc kind whose menu starts with [actions]; the rest of its five options are empty. */
fun npc(id: Int, vararg actions: String, name: String = "Npc", level: Int = 0): NpcKind =
    NpcKind(id, name, level, menu(actions))

fun obj(id: Int, vararg actions: String, name: String = "Object"): ObjectKind = ObjectKind(id, name, menu(actions))

private fun menu(actions: Array<out String>): List<String> = actions.toList() + List(5 - actions.size) { "" }

/** Jail guard 447's skills; its max hit of 1 is the placeholder, 3 is what its strength gives. */
fun stats(maximumHit: Int = 3, strength: Int = 23, otherSkills: Int = 20): CombatStats =
    CombatStats(maximumHit, otherSkills, strength, otherSkills, otherSkills, otherSkills)

const val LUMBRIDGE_WEST = 12849
const val LUMBRIDGE_EAST = 12850
const val DRAYNOR = 12338
const val UNZONED_SOUTH = 12336
const val UNZONED_NORTH = 12337

val ZONES: Map<Int, String> = mapOf(LUMBRIDGE_WEST to "lumbridge", LUMBRIDGE_EAST to "lumbridge", DRAYNOR to "draynor")

fun facts(
    npcs: List<NpcKind> = emptyList(),
    objects: List<ObjectKind> = emptyList(),
    spawns: List<RegionCount> = emptyList(),
    placements: List<RegionCount> = emptyList(),
    combatDefinitions: Map<Int, CombatStats> = emptyMap(),
    dropTableIds: Set<Int> = emptySet(),
    npcHandlers: Map<Int, Set<Int>> = emptyMap(),
    objectHandlers: Map<Int, Set<Int>> = emptyMap(),
): ContentFacts = ContentFacts(
    npcs.associateBy { it.id },
    objects.associateBy { it.id },
    spawns,
    placements,
    combatDefinitions,
    dropTableIds,
    npcHandlers,
    objectHandlers,
)

/** The facts of [kind] standing [count] times in [region], with nothing registered for it. */
fun spawned(kind: NpcKind, count: Int = 1, region: Int = LUMBRIDGE_EAST): ContentFacts =
    facts(npcs = listOf(kind), spawns = listOf(RegionCount(region, kind.id, count)))

fun placed(kind: ObjectKind, count: Int = 1, region: Int = LUMBRIDGE_EAST): ContentFacts =
    facts(objects = listOf(kind), placements = listOf(RegionCount(region, kind.id, count)))

fun audit(facts: ContentFacts): List<AreaAudit> = ContentAudit(facts, ZONES).areas()

fun area(facts: ContentFacts): AreaAudit = audit(facts).single()
