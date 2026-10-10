package game.idle.flow

import game.idle.flow.option.GameNames

/** The configure screen's Uses note for a processing step: what one of its products takes (S01). */
object Uses {

    const val NOTHING_PICKED = "@gry@nothing picked yet"

    /**
     * "Iron ore + coal x2" for [items], item ids to counts in order: the first name as the cache has it, the others in
     * lower case, a count past one after an x.
     */
    fun text(items: Map<Int, Int>, names: GameNames): String =
        items.entries.mapIndexed { index, (id, count) ->
            val name = names.item(id).let { if (index == 0) it else it.lowercase() }
            if (count > 1) "$name x$count" else name
        }.joinToString(" + ")
}
