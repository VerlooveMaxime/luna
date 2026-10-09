package game.idle.ui

import io.luna.game.model.mob.Player
import io.luna.game.model.mob.overlay.AbstractOverlay
import io.luna.game.model.mob.overlay.OverlayType
import io.luna.net.msg.out.InterfaceMessageWriter
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
    const val HIDEABLE = 30420
    const val HIDE = 30423
    const val SHOW = 30424

    const val TILES = 8
    private const val TILE_BASE = 30500
    private const val TILE_STRIDE = 10

    /** The part of a tile the click goes to. */
    fun tileFace(tile: Int): Int = TILE_BASE + tile * TILE_STRIDE + 1

    fun owns(widgetId: Int): Boolean = widgetId in FIRST_ID until ID_LIMIT

    /** The tile whose face [widgetId] is, or null. */
    fun tile(widgetId: Int): Int? = (0 until TILES).firstOrNull { tileFace(it) == widgetId }
}

/** The gallery as a Luna window; like the builder, not a `StandardInterface`, which looks the id up in the cache. */
class GalleryInterface : AbstractOverlay(OverlayType.WIDGET_STANDARD) {

    override fun open(player: Player) {
        player.queue(InterfaceMessageWriter(GalleryWidgets.GALLERY))
    }

    /** Sent on every open: the client rebuilds the gallery blank each time it closes. */
    override fun onOpen(player: Player) = IdleUi.sendTexts(player, mapOf(GalleryWidgets.OPENED_LINE to OPENED_TEXT))

    companion object {
        const val OPENED_TEXT = "This line was sent by the server when the gallery opened."
    }
}

/**
 * The developer widget gallery (`::widgets`): every kind of code-defined widget on one screen, to check them live.
 * Tiles and buttons answer in the chat box; Hide and Show toggle a nested layer with packet 82.
 */
class WidgetGallery {

    fun open(player: Player) = player.overlays.open(GalleryInterface())

    fun click(player: Player, widgetId: Int) {
        when (widgetId) {
            GalleryWidgets.CLOSE -> player.overlays.closeWindows()
            GalleryWidgets.HIDE -> setNestedLayerHidden(player, hidden = true)
            GalleryWidgets.SHOW -> setNestedLayerHidden(player, hidden = false)
            else -> GalleryWidgets.tile(widgetId)?.let { player.sendMessage("Gallery: clicked tile ${it + 1}.") }
        }
    }

    private fun setNestedLayerHidden(player: Player, hidden: Boolean) {
        player.queue(WidgetVisibilityMessageWriter(GalleryWidgets.HIDEABLE, hidden))
        player.sendMessage(if (hidden) "Gallery: nested layer hidden." else "Gallery: nested layer shown.")
    }
}
