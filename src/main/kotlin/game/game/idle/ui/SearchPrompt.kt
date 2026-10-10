package game.idle.ui

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import game.idle.flow.option.OptionOrder
import game.idle.flow.option.OptionPacket
import game.idle.flow.option.OptionSearch
import game.idle.flow.option.StepOption
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.overlay.AbstractOverlay
import io.luna.game.model.mob.overlay.OverlayType

/**
 * A row of the chatbox search as the client draws it: [index] is its place in the prompt's ordered options, which a
 * pick names back; a greyed row shows its reason as its note.
 */
data class SearchRow(
    val index: Int,
    val label: String,
    val note: String,
    val greyed: Boolean,
    val picture: WidgetPicture,
    val chosen: Boolean = false,
) {

    /** What the harness log shows of the row. */
    fun describe(): String =
        listOfNotNull("$index $label", note.ifEmpty { null }, PictureEncoding.describe(picture), "greyed".takeIf { greyed })
            .joinToString(" / ")

    companion object {
        /** [option]'s row; a [chosen] one's note says so (S07a), its reason when it is greyed too. */
        fun of(index: Int, option: StepOption, chosen: Boolean = false): SearchRow =
            SearchRow(
                index,
                option.label,
                option.blocked ?: if (chosen) CHOSEN else option.note,
                option.blocked != null,
                WidgetPicture.of(option.icon),
                chosen,
            )

        const val CHOSEN = "chosen"
    }
}

/**
 * A prompt the IdleRS client draws in its chatbox, a search or a name. It takes the input slot, so an "Enter amount" or
 * another prompt replaces it; [serial] tells its answers from an earlier prompt's.
 */
abstract class ChatboxPrompt(val serial: Int) : AbstractOverlay(OverlayType.INPUT)

/**
 * The GE-style search the IdleRS client draws in its chatbox (flow builder v2, S05): [title] over [options] in
 * [OptionOrder]. It opens on its first rows; from 3 letters the client asks for the options matching what was typed
 * (every word in the label, case ignored), and scrolling asks for further pages, so any list works the same way
 * (Maxime, 2026-10-09). [onPick] gets the option a click picked.
 *
 * With [chosen], the values a list holds now, it picks several (S07a, Maxime 2026-10-10): it stays open on a pick and
 * sends the row picked again, chosen or not; the rows chosen when it opens come first, and no row moves while it is open.
 */
class SearchPrompt(
    serial: Int,
    private val title: String,
    options: List<StepOption>,
    private val emptyLine: String,
    private val font: ClientFont,
    private val onPick: (Player, StepOption) -> Unit,
    private val chosen: ((Player) -> Set<String>)? = null,
    chosenAtOpen: Set<String> = emptySet(),
) : ChatboxPrompt(serial) {

    private val ordered: List<StepOption> =
        OptionOrder.ordered(options).sortedBy { it.value !in chosenAtOpen }

    /** The last query's results, kept while the player scrolls through them. */
    private var results: SearchResults = results("")

    override fun open(player: Player) {
        val mode = if (chosen != null) PromptMode.SEVERAL else PromptMode.SEARCH
        player.queue(SearchOpenMessageWriter(serial, mode, title, emptyLine, text = "", MOST_TYPED))
        page(player, offset = 0, count = results.columns * OPENING_GRID_ROWS, query = "")
    }

    /**
     * Sends rows [offset] on of what [query] matches ("" for every option): at most [count], [MAX_PAGE] and what one
     * packet holds; the client asks again for rows a page leaves out.
     */
    fun page(player: Player, offset: Int, count: Int, query: String) {
        if (offset < 0 || count <= 0) {
            return
        }
        if (query != results.query) {
            results = results(query)
        }
        val candidates = results.positions.drop(offset).take(count.coerceAtMost(MAX_PAGE))
        val chosenNow = chosen?.invoke(player).orEmpty()
        val rows = candidates.take(PACKET.fit(candidates.map(ordered::get)).size).map { SearchRow.of(it, ordered[it], ordered[it].value in chosenNow) }
        player.queue(SearchRowsMessageWriter(serial, query, results.positions.size, results.columns, offset, rows))
    }

    /**
     * Row [index] picked: the prompt closes, then [onPick] hears of the option, so a follow-up input it opens stays
     * open; a search that picks several stays open and sends the row again once [onPick] has changed the list. A greyed
     * or unknown row does nothing; the client offers no such pick.
     */
    fun pick(player: Player, index: Int) {
        val option = ordered.getOrNull(index)?.takeIf { it.blocked == null } ?: return
        if (chosen == null) {
            player.overlays.overlayMap.remove(OverlayType.INPUT)
            onPick(player, option)
            return
        }
        onPick(player, option)
        val place = results.positions.indexOf(index)
        if (place >= 0) page(player, offset = place, count = 1, query = results.query)
    }

    private fun results(query: String): SearchResults {
        val positions = ordered.indices.filter { OptionSearch.matches(ordered[it], query) }
        return SearchResults(query, positions, SearchColumns.of(positions.map(ordered::get), font))
    }

    /** What [query] matched, as places in the ordered options, and the columns they show in. */
    private class SearchResults(val query: String, val positions: List<Int>, val columns: Int)

    companion object {
        /** One packet of rows as [SearchRow] encodes them. */
        val PACKET = OptionPacket({ WidgetPicture.of(it).let(PictureEncoding::size) })

        /** The client's window at the top: the rows in view (three, the last cut) and one screen of two below. */
        const val OPENING_GRID_ROWS = 5

        const val MAX_PAGE = 30

        /** The most characters typed in a search. */
        const val MOST_TYPED = 40
    }
}

/**
 * The chatbox prompt in name mode (flow builder v2, S05b): [title] over a typed line that starts with [text] and takes
 * up to [mostCharacters] characters. Enter sends the line; [onName] gets it trimmed. An empty name, or one past
 * [mostCharacters], only closes the prompt (Maxime, 2026-10-09: Enter on an empty line saves nothing).
 */
class NamePrompt(
    serial: Int,
    private val title: String,
    private val text: String,
    private val mostCharacters: Int,
    private val onName: (Player, String) -> Unit,
) : ChatboxPrompt(serial) {

    override fun open(player: Player) {
        player.queue(SearchOpenMessageWriter(serial, PromptMode.NAME, title, emptyLine = "", text, mostCharacters))
    }

    /** The line [typed] when Enter was pressed: the prompt closes, then [onName] hears of the name, as a pick does. */
    fun name(player: Player, typed: String) {
        player.overlays.overlayMap.remove(OverlayType.INPUT)
        val name = typed.trim()
        if (name.isNotEmpty() && name.length <= mostCharacters) {
            onName(player, name)
        }
    }
}

/**
 * The columns of the client's search grid: 3, or 2 when a row's label or note is wider than a third of the chatbox
 * leaves, so no text is cut; chosen once per list or per search, so the grid never changes while scrolling (Maxime,
 * 2026-10-09). The sizes mirror the client's `SearchGrid`.
 */
object SearchColumns {

    /** The chatbox's width left of the scrollbar. */
    private const val WIDTH = 463

    /** Where a cell's text starts, right of its icon, and the room kept after it. */
    private const val TEXT_X = 30
    private const val PAD = 2

    fun textRoom(columns: Int): Int = WIDTH / columns - TEXT_X - PAD

    fun of(options: List<StepOption>, font: ClientFont): Int =
        if (options.all { fits(font.width(it.label)) && fits(font.width(it.blocked ?: it.note)) }) 3 else 2

    private fun fits(width: Int): Boolean = width <= textRoom(3)
}

/** The last serial a player's search prompts got, so each new one differs from the one before. */
private var Player.lastSearchSerial by Attr.int { -1 }

/** Opens chatbox prompts, searches and names, and hands them the client's answers. */
object SearchPrompts {

    /** Serials fit the byte the client sends back. */
    const val SERIALS = 256

    /** Opens a search over [options], its labels measured with [font], the client's search font. */
    fun open(
        player: Player,
        title: String,
        options: List<StepOption>,
        font: ClientFont,
        emptyLine: String = "",
        onPick: (Player, StepOption) -> Unit,
    ) {
        player.overlays.open(SearchPrompt(newSerial(player), title, options, emptyLine, font, onPick))
    }

    /**
     * Opens a search over [options] that picks several (S07a): [chosen] says which values the list holds now, and
     * [onToggle] hears of each row clicked, which adds or takes out its item.
     */
    fun openSeveral(
        player: Player,
        title: String,
        options: List<StepOption>,
        font: ClientFont,
        chosen: (Player) -> Set<String>,
        onToggle: (Player, StepOption) -> Unit,
    ) {
        player.overlays.open(SearchPrompt(newSerial(player), title, options, emptyLine = "", font, onToggle, chosen, chosen(player)))
    }

    /** Opens the name prompt: [title] over a line starting with [text], up to [mostCharacters] characters long. */
    fun openName(player: Player, title: String, text: String, mostCharacters: Int, onName: (Player, String) -> Unit) {
        player.overlays.open(NamePrompt(newSerial(player), title, text, mostCharacters, onName))
    }

    fun nextSerial(last: Int): Int = (last + 1) % SERIALS

    private fun newSerial(player: Player): Int = nextSerial(player.lastSearchSerial).also { player.lastSearchSerial = it }

    /** The client asks for rows of the prompt [serial] names; a request for another prompt is ignored. */
    fun page(player: Player, serial: Int, offset: Int, count: Int, query: String) {
        current(player, serial, SearchPrompt::class.java)?.page(player, offset, count, query)
    }

    /** Row [index] of the prompt [serial] names was picked; a pick for another prompt does nothing. */
    fun pick(player: Player, serial: Int, index: Int) {
        current(player, serial, SearchPrompt::class.java)?.pick(player, index)
    }

    /** Enter pressed on the name prompt [serial] names, its line [typed]; a name for another prompt does nothing. */
    fun name(player: Player, serial: Int, typed: String) {
        current(player, serial, NamePrompt::class.java)?.name(player, typed)
    }

    /** The client closed the prompt [serial] names without a pick: Escape or a click elsewhere. */
    fun close(player: Player, serial: Int) {
        if (current(player, serial, ChatboxPrompt::class.java) != null) {
            player.overlays.overlayMap.remove(OverlayType.INPUT)
        }
    }

    /** The prompt of [kind] open in [player]'s chatbox, whatever its serial. */
    fun <T : ChatboxPrompt> opened(player: Player, kind: Class<T>): T? = player.overlays.getOverlay(kind)

    private fun <T : ChatboxPrompt> current(player: Player, serial: Int, kind: Class<T>): T? =
        opened(player, kind)?.takeIf { it.serial == serial }
}
