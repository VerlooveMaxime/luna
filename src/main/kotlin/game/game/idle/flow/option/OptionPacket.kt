package game.idle.flow.option

/**
 * Keeps a page of search rows within one packet the client can take: its incoming buffer holds 5,000 bytes. A row
 * counts as the search prompt sends it (S05): its index as a short, a flags byte, its label and the line under it as
 * newline-ended strings, and its icon, whose size [iconSize] knows; the value stays on the server.
 */
class OptionPacket(private val iconSize: (OptionIcon) -> Int, private val budget: Int = BUDGET) {

    fun size(option: StepOption): Int = INDEX + FLAGS + text(option.label) + text(option.blocked ?: option.note) + iconSize(option.icon)

    /** The first of [options] that fit one packet together; the client asks again for the rest. */
    fun fit(options: List<StepOption>): List<StepOption> {
        val totals = options.runningFold(0) { total, option -> total + size(option) }.drop(1)
        return options.zip(totals).takeWhile { (_, total) -> total <= budget }.map { (option, _) -> option }
    }

    private fun text(line: String): Int = line.length + 1

    companion object {
        /** The client's 5,000-byte buffer less room for the packet's and the page's own headers. */
        const val BUDGET = 4900

        private const val INDEX = 2

        private const val FLAGS = 1
    }
}

/** How a search matches what the player typed (S05): every typed word is in the label, whatever the case. */
object OptionSearch {

    fun matches(option: StepOption, typed: String): Boolean {
        val label = option.label.lowercase()
        return words(typed).all { it in label }
    }

    /** The typed words, lower case: the same query whatever spaces the player put between them. */
    fun words(typed: String): List<String> = typed.lowercase().split(' ').filter { it.isNotEmpty() }
}
