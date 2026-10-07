package game.idle.flow.option

import io.luna.game.model.def.ItemDefinition

/** Every item a setting can name: the cache's named items that are not notes, by name. */
class ItemCatalog(names: Map<Int, String>) {

    private val idsByName: Map<String, List<Int>> =
        names.entries.groupBy({ it.value }, { it.key }).mapValues { (_, ids) -> ids.sorted() }

    /** One item per name, id to name: the one the bank holds, else the lowest id (several items share a name). */
    fun items(facts: OptionFacts): Map<Int, String> =
        idsByName.entries.associate { (name, ids) -> (ids.firstOrNull { facts.banked(it) > 0 } ?: ids.first()) to name }

    companion object {
        /** Over the cache's item definitions, which a booted server has loaded before its plugins. */
        fun fromCache(): ItemCatalog =
            ItemCatalog(
                ItemDefinition.ALL.stream()
                    .filter { !it.isNoted && it.name != "null" }
                    .toList()
                    .associate { it.id() to it.name },
            )
    }
}

/** Any item, with the bank's counts: what a bank step withdraws or deposits, a drop step drops, an "until" counts. */
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
