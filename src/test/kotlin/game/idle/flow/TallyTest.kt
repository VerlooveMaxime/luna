package game.idle.flow

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TallyTest {

    private var reading = 0

    private fun rising() = Tally(Meter.Rises { reading })

    private fun falling() = Tally(Meter.Falls { reading })

    @Test
    fun `a rising meter counts what it rose by`() {
        val tally = rising()
        reading = 3

        tally.observe()

        assertEquals(3, tally.total())
    }

    @Test
    fun `a rising meter ignores a fall`() {
        reading = 3
        val tally = rising()
        reading = 1

        tally.observe()

        assertEquals(0, tally.total())
    }

    @Test
    fun `a rise after a fall is counted on top`() {
        val tally = rising()
        reading = 3
        tally.observe()
        reading = 1
        tally.observe()
        reading = 2

        tally.observe()

        assertEquals(4, tally.total())
    }

    @Test
    fun `a rise and a fall between two readings cancel, which is why the turn reads on every poll`() {
        val tally = rising()
        reading = 3
        reading = 0

        tally.observe()

        assertEquals(0, tally.total())
    }

    @Test
    fun `a falling meter counts what it fell by`() {
        reading = 5
        val tally = falling()
        reading = 2

        tally.observe()

        assertEquals(3, tally.total())
    }

    @Test
    fun `a falling meter ignores a rise and counts the fall after it`() {
        reading = 5
        val tally = falling()
        reading = 8
        tally.observe()
        reading = 6

        tally.observe()

        assertEquals(2, tally.total())
    }

    @Test
    fun `a meter that counts nothing stays at zero`() {
        val tally = Tally(Meter.None)
        reading = 7

        tally.observe()

        assertEquals(0, tally.total())
    }

    @Test
    fun `the count starts at the reading when the tally was built`() {
        reading = 10
        val tally = rising()

        tally.observe()

        assertEquals(0, tally.total())
    }
}
