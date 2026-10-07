package game.idle.autopilot.pickup

import api.drops.DropTableHandler

/** The items an npc can drop. */
fun interface DropLookup {
    fun itemsOf(npc: Int): Set<Int>
}

/** Luna's drop registry: an npc's own table and the shared gem and rare tables it rolls, without the empty slots. */
object LunaDrops : DropLookup {

    /** Asked table by table: a merged table's own list rolls the shared tables instead of listing them. */
    override fun itemsOf(npc: Int): Set<Int> =
        DropTableHandler.getDropTable(npc)?.tableList.orEmpty()
            .flatMap { it.computePossibleItems() }
            .filterNot { it.isNothing() }
            .map { it.id }
            .toSet()
}
