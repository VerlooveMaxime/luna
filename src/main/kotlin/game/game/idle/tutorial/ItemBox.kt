package game.idle.tutorial

import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.DialogueInterface
import io.luna.net.msg.out.WidgetItemModelMessageWriter
import io.luna.net.msg.out.WidgetTextMessageWriter

/**
 * Where an item box draws its items and lines: the 377 cache's chatbox interfaces objbox1 to objbox4 (one item, one
 * to four lines) and doubleobjbox (two items), filled the way LostCity fills them. [texts] take the lines in order,
 * [blanks] are emptied.
 */
data class ItemBoxLayout(val interfaceId: Int, val models: List<Int>, val zoom: Int, val texts: List<Int>, val blanks: List<Int>) {
    companion object {
        /** The tutorial's item models are drawn at these sizes in LostCity. */
        private const val SINGLE_ZOOM = 250
        private const val DOUBLE_ZOOM = 200

        private val ONE_ITEM = listOf(
            ItemBoxLayout(306, listOf(307), SINGLE_ZOOM, listOf(308), emptyList()),
            ItemBoxLayout(310, listOf(311), SINGLE_ZOOM, listOf(313, 312), emptyList()),
            ItemBoxLayout(315, listOf(316), SINGLE_ZOOM, listOf(318, 317, 320), emptyList()),
            ItemBoxLayout(321, listOf(322), SINGLE_ZOOM, listOf(324, 323, 326, 327), emptyList()),
        )

        private val TWO_ITEMS = listOf(
            ItemBoxLayout(4950, listOf(4951, 4957), DOUBLE_ZOOM, listOf(4952), listOf(4953, 4955, 4956)),
            ItemBoxLayout(4950, listOf(4951, 4957), DOUBLE_ZOOM, listOf(4952, 4955), listOf(4953, 4956)),
            ItemBoxLayout(4950, listOf(4951, 4957), DOUBLE_ZOOM, listOf(4952, 4955, 4956), listOf(4953)),
            ItemBoxLayout(4950, listOf(4951, 4957), DOUBLE_ZOOM, listOf(4953, 4952, 4955, 4956), emptyList()),
        )

        fun of(items: Int, lines: Int): ItemBoxLayout {
            require(lines in 1..DialogueBox.MAX_LINES) { "An item box holds 1 to ${DialogueBox.MAX_LINES} lines: $lines" }
            val layouts = when (items) {
                1 -> ONE_ITEM
                2 -> TWO_ITEMS
                else -> throw IllegalArgumentException("An item box shows 1 or 2 items: $items")
            }
            return layouts[lines - 1]
        }
    }
}

/** A chatbox box showing one or two items beside their lines. Luna's own item box shows one item and gives it. */
class ItemBox(private val items: List<Int>, private val lines: List<String>) :
    DialogueInterface(ItemBoxLayout.of(items.size, lines.size).interfaceId) {

    private val layout = ItemBoxLayout.of(items.size, lines.size)

    override fun init(player: Player): Boolean {
        layout.models.zip(items).forEach { (widget, item) -> player.queue(WidgetItemModelMessageWriter(widget, layout.zoom, item)) }
        layout.texts.zip(lines).forEach { (widget, text) -> player.queue(WidgetTextMessageWriter(text, widget)) }
        layout.blanks.forEach { player.queue(WidgetTextMessageWriter("", it)) }
        return true
    }
}
