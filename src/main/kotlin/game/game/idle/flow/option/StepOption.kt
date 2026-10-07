package game.idle.flow.option

/** The picture a search row shows left of its label. */
sealed interface OptionIcon {

    data class Item(val id: Int) : OptionIcon

    data class Npc(val id: Int) : OptionIcon

    data class Skill(val id: Int) : OptionIcon

    /** The minimap's bank icon. */
    data object Bank : OptionIcon
}

/**
 * One row a setting's search offers. [value] is what the setting keeps when the row is picked, [note] the line under
 * the label, and [blocked] why the row cannot be picked yet (it shows greyed with that reason), null when it can.
 * [group], [banked] and [level] order the rows ([OptionOrder]): a source keeps rows on top with a lower group.
 */
data class StepOption(
    val value: String,
    val label: String,
    val icon: OptionIcon,
    val note: String = "",
    val blocked: String? = null,
    val level: Int = 0,
    val banked: Int = 0,
    val group: Int = 0,
)

/**
 * The order a search shows its rows in (Maxime, 2026-10-07): greyed rows last; the others by group, then those the bank
 * holds first, then by level, then by name; greyed rows by level, the closest to unlock first, then by name.
 */
object OptionOrder {

    private val ORDER: Comparator<StepOption> =
        compareBy<StepOption> { it.blocked != null }
            .thenBy { it.group }
            .thenBy { it.blocked != null || it.banked == 0 }
            .thenBy { it.level }
            .thenBy { it.label.lowercase() }

    fun ordered(options: List<StepOption>): List<StepOption> = options.sortedWith(ORDER)
}
