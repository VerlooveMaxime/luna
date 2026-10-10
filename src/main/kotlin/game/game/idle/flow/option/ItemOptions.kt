package game.idle.flow.option

import io.luna.game.model.def.ItemDefinition

/**
 * Every item a setting can name: the cache's named items that are not notes ([names]), by name; [notes] maps each note's
 * id to the item it stands for.
 */
class ItemCatalog(private val names: Map<Int, String>, private val notes: Map<Int, Int> = emptyMap()) {

    private val idsByName: Map<String, List<Int>> =
        names.entries.groupBy({ it.value }, { it.key }).mapValues { (_, ids) -> ids.sorted() }

    private val notesOf: Map<Int, List<Int>> = notes.entries.groupBy({ it.value }, { it.key })

    /** One item per name, id to name: the one the bank holds, else the lowest id (several items share a name). */
    fun items(facts: OptionFacts): Map<Int, String> =
        idsByName.entries.associate { (name, ids) -> (ids.firstOrNull { facts.banked(it) > 0 } ?: ids.first()) to name }

    /** The item a note stands for, [id] itself for anything else. */
    fun unnoted(id: Int): Int = notes[id] ?: id

    /**
     * [id]'s item and every other item of its name, their notes too: the search shows one row per name, so a pick-up step
     * takes them all (S07d). None when [id] names no item.
     */
    fun alike(id: Int): Set<Int> {
        val ids = names[unnoted(id)]?.let(idsByName::getValue) ?: return emptySet()
        return (ids + ids.flatMap { notesOf[it].orEmpty() }).toSet()
    }

    companion object {
        /** Over the cache's item definitions, which a booted server has loaded before its plugins. */
        fun fromCache(): ItemCatalog {
            val named = ItemDefinition.ALL.stream().filter { it.name != "null" }.toList()
            return ItemCatalog(
                named.filter { !it.isNoted }.associate { it.id() to it.name },
                named.filter { it.isNoted }.associate { it.id() to it.unnotedId.asInt },
            )
        }
    }
}

/** Any item, with the bank's counts: what a bank step withdraws, an "until" counts. */
class ItemOptions(private val catalog: ItemCatalog) : OptionSource {

    override fun options(context: OptionContext): List<StepOption> =
        catalog.items(context.facts).map { (id, name) ->
            StepOption(
                value = id.toString(),
                label = name,
                icon = OptionIcon.Item(id),
                note = context.facts.bankNote(id),
                banked = context.facts.banked(id),
            )
        }
}

/**
 * Any item, for a list that takes items out of the bag (S07f: Drop's and the bank's Chosen): what the flow gathers or
 * makes first, then what the bag holds, then every other item; one row per name, named with [names].
 */
class CarriedItemOptions(private val catalog: ItemCatalog, private val names: GameNames) : OptionSource {

    override fun options(context: OptionContext): List<StepOption> {
        val gathered = rows(context.before.lap, "gathered", GATHERED)
        val carried = rows(context.facts.bag.map(catalog::unnoted), "in your bag", CARRIED)
        val near = (gathered + carried).distinctBy { it.label }
        val nearNames = near.map { it.label }.toSet()
        val others = catalog.items(context.facts).filterValues { it !in nearNames }
        return near + others.map { (id, name) -> row(id, name, "any item", group = 0) }
    }

    private fun rows(ids: Collection<Int>, note: String, group: Int): List<StepOption> = ids.map { row(it, names.item(it), note, group) }

    private fun row(id: Int, name: String, note: String, group: Int) =
        StepOption(value = id.toString(), label = name, icon = OptionIcon.Item(id), note = note, group = group)

    private companion object {
        const val GATHERED = -2
        const val CARRIED = -1
    }
}
