package game.idle.flow.option

/** Names every id as "item 5", "npc 5" or "obj 5", unless [items], [npcs] or [objects] name it. */
class FakeNames(
    private val items: Map<Int, String> = emptyMap(),
    private val npcs: Map<Int, String> = emptyMap(),
    private val objects: Map<Int, String> = emptyMap(),
) : GameNames {

    override fun item(id: Int): String = items[id] ?: "item $id"

    override fun npc(id: Int): String = npcs[id] ?: "npc $id"

    override fun obj(id: Int): String = objects[id] ?: "obj $id"
}

/** A row with only what ordering and size look at. */
fun row(label: String, blocked: String? = null, level: Int = 0, banked: Int = 0, group: Int = 0, note: String = "") =
    StepOption(label, label, OptionIcon.Item(1), note, blocked, level, banked, group)
