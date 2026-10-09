package game.idle.ui

import game.testworld.TestWorld
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ClientFontTest {

    /** A `.dat` holding the glyphs' offset into the index (0) then their pixels, as the cache stores a font. */
    private fun dat(vararg pixels: Int): ByteArray = byteArrayOf(0, 0) + pixels.map(Int::toByte).toByteArray()

    /**
     * An `index.dat` for glyphs given as (width, height, column order), every other glyph empty: 4 bytes of header the
     * font skips, the palette size and, past the first colour, 3 bytes per colour, then 7 bytes per glyph.
     */
    private fun index(paletteSize: Int, glyphs: Map<Int, Triple<Int, Int, Int>>): ByteArray {
        val header = listOf(0, 0, 0, 0, paletteSize) + List(3 * (paletteSize - 1).coerceAtLeast(0)) { 0 }
        val entries = (0 until 256).flatMap { code ->
            val (width, height, order) = glyphs[code] ?: Triple(0, 0, 0)
            listOf(0, 0, width shr 8, width and 0xff, height shr 8, height and 0xff, order)
        }
        return (header + entries).map(Int::toByte).toByteArray()
    }

    /** A 3 by 7 "!" (code 33) inked in [columns], row by row. */
    private fun bang(order: Int, columns: Set<Int>, paletteSize: Int = 2): ClientFont {
        val rowMajor = (0 until 7).flatMap { (0 until 3).map { x -> if (x in columns) 1 else 0 } }
        val pixels = if (order == 0) rowMajor else (0 until 3).flatMap { x -> (0 until 7).map { y -> rowMajor[x + y * 3] } }
        return ClientFont.read(dat(*pixels.toIntArray()), index(paletteSize, mapOf(33 to Triple(3, 7, order))))
    }

    @Test
    fun `a glyph inked to both edges is its width plus two`() {
        assertEquals(5, bang(order = 0, columns = setOf(0, 2)).width("!"))
    }

    @Test
    fun `an empty left edge takes one off`() {
        assertEquals(4, bang(order = 0, columns = setOf(1, 2)).width("!"))
    }

    @Test
    fun `an empty right edge takes one off`() {
        assertEquals(4, bang(order = 0, columns = setOf(0, 1)).width("!"))
    }

    @Test
    fun `pixels stored column by column read the same`() {
        assertEquals(4, bang(order = 1, columns = setOf(1, 2)).width("!"))
    }

    @Test
    fun `a font without a palette reads the same`() {
        assertEquals(5, bang(order = 0, columns = setOf(0, 2), paletteSize = 0).width("!"))
    }

    @Test
    fun `an empty glyph has no width`() {
        assertEquals(0, bang(order = 0, columns = setOf(0, 2)).width("a"))
    }

    @Test
    fun `a space is as wide as an i`() {
        val font = ClientFont.read(dat(1, 1, 1, 1, 1, 1, 1), index(2, mapOf('i'.code to Triple(1, 7, 0))))

        assertEquals(font.width("i"), font.width(" "))
    }

    @Test
    fun `the cache's small font measures text as the client draws it`() {
        val font = ClientFont.fromCache(TestWorld.context.cache)

        assertEquals(listOf(18, 189, 162), listOf("Oak", "Shrimps, anchovies (small fishing net)", "Witches experiment second form").map(font::width))
    }

    /** Every glyph 5 pixels wide. */
    private val even = ClientFont(IntArray(256) { 5 })

    @Test
    fun `a text that fits is kept whole`() {
        assertEquals("abcd", even.fit("abcd", 20))
    }

    @Test
    fun `a text too wide is cut with two dots at its end`() {
        assertEquals("ab..", even.fit("abcdef", 20))
    }

    @Test
    fun `a cut leaves no space before its dots`() {
        assertEquals("ab..", even.fit("ab cdef", 25))
    }

    @Test
    fun `a one-letter text too wide is only the dots`() {
        assertEquals("..", even.fit("a", 4))
    }

    @Test
    fun `a text where not even one letter fits before the dots is only the dots`() {
        assertEquals("..", even.fit("abc", 9))
    }

    @Test
    fun `words wrap at spaces into lines that fit`() {
        assertEquals(listOf("ab cd", "ef"), even.wrap("ab cd ef", 25, lines = 2))
    }

    @Test
    fun `a text that takes fewer lines than allowed is kept whole`() {
        assertEquals(listOf("ab"), even.wrap("ab", 25, lines = 2))
    }

    @Test
    fun `a word too wide for a line is cut`() {
        assertEquals(listOf("ab.."), even.wrap("abcdefgh", 20, lines = 2))
    }

    @Test
    fun `text past the last line joins it, cut with two dots`() {
        assertEquals(listOf("ab cd", "ef.."), even.wrap("ab cd ef gh ij", 25, lines = 2))
    }
}
