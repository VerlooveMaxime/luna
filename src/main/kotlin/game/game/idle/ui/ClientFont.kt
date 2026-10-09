package game.idle.ui

import io.luna.game.cache.Archive
import io.luna.game.cache.Cache
import io.netty.buffer.ByteBuf
import java.nio.ByteBuffer

/**
 * How wide the IdleRS client draws text in one of its fonts, so the server can lay text out as the client will (the
 * search's columns, S05). Read from the same cache the client loads; each glyph's advance is worked out as the
 * client's `JagFont` does: its width plus 2, less one for each side whose edge column is nearly empty. A space is as
 * wide as an "i", as in the client's plain fonts.
 */
class ClientFont(private val advances: IntArray) {

    /** The width of [text] in pixels; texts reach the client as ISO-8859-1, one glyph per byte. */
    fun width(text: String): Int = text.sumOf { advances[it.code and 0xff] }

    companion object {
        /** The small plain font the search rows are drawn in. */
        const val SMALL = "p11_full"

        private const val GLYPHS = 256
        private const val TITLE_ARCHIVE = 1

        fun fromCache(cache: Cache, name: String = SMALL): ClientFont {
            val title = Archive.decode(cache.getFile(0, TITLE_ARCHIVE))
            return read(bytes(title.getFileData("$name.dat")), bytes(title.getFileData("index.dat")))
        }

        /** Parses a font from its `.dat` file (offset into the index, then pixels) and the archive's `index.dat`. */
        fun read(dat: ByteArray, index: ByteArray): ClientFont {
            val pixels = ByteBuffer.wrap(dat)
            val glyphs = ByteBuffer.wrap(index)
            glyphs.position(unsignedShort(pixels) + 4)
            val paletteSize = unsignedByte(glyphs)
            if (paletteSize > 0) {
                glyphs.position(glyphs.position() + 3 * (paletteSize - 1))
            }
            val advances = IntArray(GLYPHS) { advance(glyphs, pixels) }
            advances[' '.code] = advances['i'.code]
            return ClientFont(advances)
        }

        private fun advance(glyphs: ByteBuffer, pixels: ByteBuffer): Int {
            glyphs.position(glyphs.position() + 2)
            val width = unsignedShort(glyphs)
            val height = unsignedShort(glyphs)
            val columnMajor = unsignedByte(glyphs) == 1
            val glyph = ByteArray(width * height)
            for (i in glyph.indices) {
                val x = if (columnMajor) i / height else i % width
                val y = if (columnMajor) i % height else i / width
                glyph[x + y * width] = pixels.get()
            }
            val edgeRows = height / 7 until height
            val leftInk = edgeRows.sumOf { glyph[it * width].toInt() }
            val rightInk = edgeRows.sumOf { glyph[width - 1 + it * width].toInt() }
            return width + 2 - (if (leftInk <= height / 7) 1 else 0) - (if (rightInk <= height / 7) 1 else 0)
        }

        private fun unsignedByte(buffer: ByteBuffer): Int = buffer.get().toInt() and 0xff

        private fun unsignedShort(buffer: ByteBuffer): Int = buffer.getShort().toInt() and 0xffff

        private fun bytes(buffer: ByteBuf): ByteArray = ByteArray(buffer.readableBytes()).also { buffer.readBytes(it) }
    }
}
