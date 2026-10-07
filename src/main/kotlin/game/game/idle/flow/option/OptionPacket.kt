package game.idle.flow.option

/**
 * Keeps search rows within one packet the client can take: its incoming buffer holds 5,000 bytes. A list that [fits] is
 * sent once and the client filters it; a longer one is queried and answered with [matches] (Maxime, 2026-10-07). A row
 * counts as the search prompt is to send it (S05): its label and the line under it as newline-ended strings, the icon
 * as a kind byte and a two-byte id, and a flags byte; the value stays on the server.
 */
class OptionPacket(private val budget: Int = BUDGET) {

    fun size(option: StepOption): Int = text(option.label) + text(option.blocked ?: option.note) + ICON + FLAGS

    fun fits(options: List<StepOption>): Boolean = options.sumOf(::size) <= budget

    /** The rows whose label holds every word of [typed], whatever the case, in order and cut to one packet. */
    fun matches(options: List<StepOption>, typed: String): OptionMatches {
        val words = typed.lowercase().split(' ').filter { it.isNotEmpty() }
        val hits = OptionOrder.ordered(options.filter { option -> words.all { it in option.label.lowercase() } })
        val totals = hits.runningFold(0) { total, option -> total + size(option) }.drop(1)
        val kept = hits.zip(totals).takeWhile { (_, total) -> total <= budget }.map { (option, _) -> option }
        return OptionMatches(kept, cut = kept.size < hits.size)
    }

    private fun text(line: String): Int = line.length + 1

    companion object {
        /** The client's 5,000-byte buffer less room for the packet's and the list's own headers. */
        const val BUDGET = 4900

        private const val ICON = 3

        private const val FLAGS = 1
    }
}

/** The rows a query answers with; [cut] when more matched than one packet holds, so the player should type more. */
data class OptionMatches(val options: List<StepOption>, val cut: Boolean)
