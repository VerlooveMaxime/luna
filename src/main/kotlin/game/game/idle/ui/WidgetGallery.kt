package game.idle.ui

import io.luna.game.model.mob.Player
import io.luna.game.model.mob.overlay.AbstractOverlay
import io.luna.game.model.mob.overlay.OverlayType
import io.luna.net.msg.out.InterfaceMessageWriter
import io.luna.net.msg.out.WidgetTextMessageWriter
import io.luna.net.msg.out.WidgetVisibilityMessageWriter

/**
 * Ids of the developer widget gallery the client defines in code (`idlers.WidgetGallery` in `luna-client`, the layout
 * lives there). Both files must agree on every id.
 */
object GalleryWidgets {

    const val FIRST_ID = 30400
    const val ID_LIMIT = 30600

    const val GALLERY = 30400
    const val CLOSE = 30404
    const val OPENED_LINE = 30405
    /** The layer holding the tiles, which a dragged tile's move names. */
    const val SCROLL = 30410
    const val HIDEABLE = 30420
    const val HIDE = 30423
    const val SHOW = 30424

    const val ICON_SIZES = 3
    const val ICON_KINDS = 12
    const val ITEM_SIZES = 2
    const val ITEM_KINDS = 2
    const val NPCS = 4
    const val TILES = 8

    fun icon(size: Int, kind: Int): Int = 30430 + size * 20 + kind

    fun item(size: Int, kind: Int): Int = 30491 + size * ITEM_KINDS + kind

    fun npc(npc: Int): Int = 30581 + npc

    fun npcName(npc: Int): Int = 30585 + npc

    private fun tileBase(tile: Int): Int = 30500 + tile * 10

    /** The part of a tile the click and the drag go to. */
    fun tileFace(tile: Int): Int = tileBase(tile) + 1

    fun tilePicture(tile: Int): Int = tileBase(tile) + 3

    fun tileLabel(tile: Int): Int = tileBase(tile) + 4

    fun tileKind(tile: Int): Int = tileBase(tile) + 5

    fun owns(widgetId: Int): Boolean = widgetId in FIRST_ID until ID_LIMIT

    /** The tile whose face [widgetId] is, or null. */
    fun tile(widgetId: Int): Int? = (0 until TILES).firstOrNull { tileFace(it) == widgetId }
}

/** A kind of step as the gallery shows it: its name and its icon. */
data class GalleryKind(val name: String, val icon: WidgetPicture)

/** The gallery as a Luna window; like the builder, not a `StandardInterface`, which looks the id up in the cache. */
class GalleryInterface(private val fill: (Player) -> Unit) : AbstractOverlay(OverlayType.WIDGET_STANDARD) {

    override fun open(player: Player) {
        player.queue(InterfaceMessageWriter(GalleryWidgets.GALLERY))
    }

    /** Sent on every open: the client rebuilds the gallery blank each time it closes. */
    override fun onOpen(player: Player) = fill(player)
}

/**
 * The developer widget gallery (`::widgets`): every kind of code-defined widget on one screen, to check them live.
 * It shows the step icons of [kinds], item icons and npc bodies named by [npcName]; tiles and buttons answer in the chat box, Hide and
 * Show toggle a nested layer with packet 82, and a tile dragged onto another moves there.
 */
class WidgetGallery(private val kinds: List<GalleryKind>, private val npcName: (Int) -> String) {

    /** Which kind each player's tiles show, by place; set when the gallery opens. */
    private val tileKinds = mutableMapOf<String, List<Int>>()

    fun open(player: Player) {
        tileKinds[player.username] = (0 until GalleryWidgets.TILES).toList()
        player.overlays.open(GalleryInterface(::fill))
    }

    fun click(player: Player, widgetId: Int) {
        when (widgetId) {
            GalleryWidgets.CLOSE -> player.overlays.closeWindows()
            GalleryWidgets.HIDE -> setNestedLayerHidden(player, hidden = true)
            GalleryWidgets.SHOW -> setNestedLayerHidden(player, hidden = false)
            else -> GalleryWidgets.tile(widgetId)?.let { player.sendMessage("Gallery: clicked tile ${tileKindAt(player, it) + 1}.") }
        }
    }

    /** A dragged tile dropped on another: the tile at [from] moves to [to], the ones in between shift by one. */
    fun arrange(player: Player, from: Int, to: Int) {
        val order = tileKinds[player.username] ?: return
        if (from !in order.indices || to !in order.indices) return
        val moved = order[from]
        tileKinds[player.username] = order.toMutableList().apply { removeAt(from) }.apply { add(to, moved) }
        sendTiles(player)
        player.sendMessage("Gallery: moved tile ${moved + 1} to place ${to + 1}.")
    }

    private fun tileKindAt(player: Player, place: Int): Int = tileKinds[player.username]?.get(place) ?: place

    private fun fill(player: Player) {
        player.queue(WidgetTextMessageWriter(OPENED_TEXT, GalleryWidgets.OPENED_LINE))
        for (kind in 0 until GalleryWidgets.ICON_KINDS) {
            val icon = kinds.getOrNull(kind)?.icon ?: WidgetPicture.None
            for (size in 0 until GalleryWidgets.ICON_SIZES) {
                player.queue(PictureMessageWriter(GalleryWidgets.icon(size, kind), icon))
            }
        }
        ITEMS.forEachIndexed { kind, item ->
            for (size in 0 until GalleryWidgets.ITEM_SIZES) {
                player.queue(PictureMessageWriter(GalleryWidgets.item(size, kind), WidgetPicture.Item(item)))
            }
        }
        NPCS.forEachIndexed { place, npc ->
            player.queue(PictureMessageWriter(GalleryWidgets.npc(place), WidgetPicture.NpcBody(npc)))
            player.queue(WidgetTextMessageWriter(npcName(npc), GalleryWidgets.npcName(place)))
        }
        sendTiles(player)
    }

    private fun sendTiles(player: Player) {
        for (place in 0 until GalleryWidgets.TILES) {
            val kind = tileKindAt(player, place)
            player.queue(PictureMessageWriter(GalleryWidgets.tilePicture(place), kinds.getOrNull(kind)?.icon ?: WidgetPicture.None))
            player.queue(WidgetTextMessageWriter("Tile ${kind + 1}", GalleryWidgets.tileLabel(place)))
            player.queue(WidgetTextMessageWriter(kinds.getOrNull(kind)?.name.orEmpty(), GalleryWidgets.tileKind(place)))
        }
    }

    private fun setNestedLayerHidden(player: Player, hidden: Boolean) {
        player.queue(WidgetVisibilityMessageWriter(GalleryWidgets.HIDEABLE, hidden))
        player.sendMessage(if (hidden) "Gallery: nested layer hidden." else "Gallery: nested layer shown.")
    }

    companion object {
        const val OPENED_TEXT = "This line was sent by the server when the gallery opened."

        /** Leather gloves (pick-up's icon, S01) and willow logs. */
        val ITEMS = listOf(1059, 1519)

        /** Giant rat, cow, man and the King Black Dragon: four sizes of body in the same frame. */
        val NPCS = listOf(86, 81, 1, 50)
    }
}
