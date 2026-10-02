package game.idle.content.audit

import api.bot.zone.Zone
import api.drops.DropTableHandler
import api.event.Matcher
import io.luna.game.event.Event
import io.luna.game.event.impl.NpcClickEvent.NpcFirstClickEvent
import io.luna.game.event.impl.NpcClickEvent.NpcFourthClickEvent
import io.luna.game.event.impl.NpcClickEvent.NpcSecondClickEvent
import io.luna.game.event.impl.NpcClickEvent.NpcThirdClickEvent
import io.luna.game.event.impl.ObjectClickEvent.ObjectFirstClickEvent
import io.luna.game.event.impl.ObjectClickEvent.ObjectSecondClickEvent
import io.luna.game.event.impl.ObjectClickEvent.ObjectThirdClickEvent
import io.luna.game.model.World
import io.luna.game.model.def.GameObjectDefinition
import io.luna.game.model.def.NpcCombatDefinition
import io.luna.game.model.def.NpcDefinition

/** What plugins and data files registered, by id. [LunaRegistries] reads Luna's own; tests pass their own. */
interface ContentRegistries {

    /** Definition action index to the npc ids whose click on that option reaches a handler. */
    fun npcHandlers(): Map<Int, Set<Int>>

    /** Definition action index to the object ids whose click on that option reaches a handler. */
    fun objectHandlers(): Map<Int, Set<Int>>

    /** Null when the npc has no combat definition. */
    fun combatStats(npcId: Int): CombatStats?

    fun hasDropTable(npcId: Int): Boolean
}

/**
 * Luna's registries. A click handler is a key of one of Luna's click matchers, which `npc1` to `npc4`, `object1` to
 * `object3` and `on(...).match(...)` register into. A listener added with a bare `on(...)` would not be seen; none
 * listens to npc or object clicks that way as of 2026-10-02. Which option sends which event is in
 * `.memory/codebase/client-click-protocol.md`: npc option 2 is an attack, object options 4 and 5 are never decoded.
 */
object LunaRegistries : ContentRegistries {

    override fun npcHandlers(): Map<Int, Set<Int>> = mapOf(
        0 to keys<NpcFirstClickEvent>(),
        2 to keys<NpcSecondClickEvent>(),
        3 to keys<NpcThirdClickEvent>(),
        4 to keys<NpcFourthClickEvent>(),
    )

    override fun objectHandlers(): Map<Int, Set<Int>> = mapOf(
        0 to keys<ObjectFirstClickEvent>(),
        1 to keys<ObjectSecondClickEvent>(),
        2 to keys<ObjectThirdClickEvent>(),
    )

    override fun combatStats(npcId: Int): CombatStats? =
        NpcCombatDefinition.ALL.get(npcId).map {
            CombatStats(it.maximumHit, it.attackLevel, it.strengthLevel, it.defenceLevel, it.rangedLevel, it.magicLevel)
        }.orElse(null)

    override fun hasDropTable(npcId: Int): Boolean = DropTableHandler.getDropTable(npcId) != null

    private inline fun <reified E : Event> keys(): Set<Int> = Matcher.get<E, Int>().keys()
}

/** Reads [ContentFacts] from a booted world. Game thread only: it walks every npc and object of the world. */
class LunaContentFacts(private val world: World, private val registries: ContentRegistries) {

    fun collect(): ContentFacts {
        val spawns = regionCounts(world.npcs.asSequence().map { it.position.regionId to it.id })
        val npcs = spawns.map { it.id }.distinct().map(::npcKind).associateBy { it.id }
        // Most placed objects are walls and scenery without a menu; only the rest can lack a handler.
        val objects = regionCounts(world.objects.asSequence().map { it.position.regionId to it.id })
        val objectKinds = objects.map { it.id }.distinct().map(::objectKind)
            .filter { kind -> kind.actions.any { it.isNotEmpty() } }
            .associateBy { it.id }
        return ContentFacts(
            npcs = npcs,
            objects = objectKinds,
            spawns = spawns,
            placements = objects.filter { it.id in objectKinds },
            combatDefinitions = npcs.keys.mapNotNull { id -> registries.combatStats(id)?.let { id to it } }.toMap(),
            dropTableIds = npcs.keys.filter(registries::hasDropTable).toSet(),
            npcHandlers = registries.npcHandlers(),
            objectHandlers = registries.objectHandlers(),
        )
    }

    private fun regionCounts(regionAndId: Sequence<Pair<Int, Int>>): List<RegionCount> =
        regionAndId.groupingBy { it }.eachCount().map { (key, count) -> RegionCount(key.first, key.second, count) }

    private fun npcKind(id: Int): NpcKind {
        val definition = NpcDefinition.ALL.retrieve(id)
        return NpcKind(id, definition.name, definition.combatLevel, menu(definition.actions))
    }

    private fun objectKind(id: Int): ObjectKind {
        val definition = GameObjectDefinition.ALL.retrieve(id)
        return ObjectKind(id, definition.name, menu(definition.actions))
    }

    /** Luna's decoders store an absent action as "null"; the client shows no "hidden" option either, in any case. */
    private fun menu(actions: List<String>): List<String> =
        actions.take(MENU_SIZE).map { if (it == "null" || it.equals("hidden", ignoreCase = true)) "" else it }

    private companion object {
        const val MENU_SIZE = 5
    }
}

/** Region id to the lower-case name of the bot [Zone] holding it. */
fun zoneOfRegion(zones: List<Zone>): Map<Int, String> =
    zones.flatMap { zone -> zone.regions.map { it to zone.name.lowercase() } }.toMap()
