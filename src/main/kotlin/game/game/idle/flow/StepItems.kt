package game.idle.flow

/** One item a list setting holds: its id and, for a withdrawal, how many ([amount], null for all). */
data class StepItem(val id: Int, val amount: Int? = null)

/**
 * Settings that hold several items (Maxime, 2026-10-10), kept as text so a save stays plain: item ids joined by commas,
 * an amount after a colon ("1511:14,590"). Text that names no item is skipped.
 */
object StepItems {

    /** A list holds at most a bag's worth of items. */
    const val MOST = 28

    fun read(settings: StepSettings, key: String): List<StepItem> = parse(settings[key])

    /** The items [text] holds, none for null. */
    fun parse(text: String?): List<StepItem> = text.orEmpty().split(",").mapNotNull(::item).distinctBy { it.id }

    fun ids(settings: StepSettings, key: String): List<Int> = read(settings, key).map { it.id }

    fun text(items: List<StepItem>): String = items.joinToString(",") { item -> item.amount?.let { "${item.id}:$it" } ?: "${item.id}" }

    /** [settings] with [id] taken out of [key]'s list if it is there, else added at its end. */
    fun toggled(settings: StepSettings, key: String, id: Int): StepSettings {
        val items = read(settings, key)
        val kept = items.filter { it.id != id }
        return settings.with(key, text(if (kept.size < items.size) kept else items + StepItem(id)))
    }

    /** [settings] with [id]'s amount in [key]'s list set to [amount] (null for all); a list without [id] is left as it is. */
    fun withAmount(settings: StepSettings, key: String, id: Int, amount: Int?): StepSettings =
        settings.with(key, text(read(settings, key).map { if (it.id == id) it.copy(amount = amount) else it }))

    private fun item(text: String): StepItem? {
        val parts = text.split(":")
        val id = parts[0].toIntOrNull() ?: return null
        return StepItem(id, parts.getOrNull(1)?.toIntOrNull()?.takeIf { it >= 1 })
    }
}
