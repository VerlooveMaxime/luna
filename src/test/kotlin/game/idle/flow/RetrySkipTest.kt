package game.idle.flow

import game.idle.location.Tile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RetrySkipTest {

    private data class Go(override val aim: Aim, override val distance: Int) : Aimed

    private data class Use(override val aim: Aim, override val distance: Int) : Aimed

    private data object Undecided

    private val oak = Aim(1281, Tile(3171, 3444))
    private val willow = Aim(1308, Tile(3170, 3454))
    private var logs = 0
    private val retry = RetrySkip.forTheTurn { logs }
    private val asked = mutableListOf<Set<Aim>>()

    /** Decides [decision], recording the aims set aside it was asked to leave out. */
    private fun decide(decision: Any): Any = retry.decide { skipped -> asked += skipped.toSet(); decision }

    /** Decides [first] unless its aim is set aside, then [fallback]. */
    private fun decide(first: Aimed, fallback: Any): Any = retry.decide { skipped -> if (first.aim in skipped) fallback else first }

    @Test
    fun `the first decision leaves nothing out`() {
        decide(Use(oak, 0))

        assertEquals(listOf(emptySet<Aim>()), asked)
    }

    @Test
    fun `the same decision twice with nothing gained sets its aim aside and decides again`() {
        decide(Use(oak, 0), Undecided)

        val second = decide(Use(oak, 0), Undecided)

        assertEquals(Undecided, second)
    }

    @Test
    fun `the aim set aside stays so for the turn`() {
        decide(Use(oak, 0), Undecided)
        decide(Use(oak, 0), Undecided)
        decide(Undecided)

        decide(Use(oak, 0))

        assertEquals(setOf(oak), asked.last())
    }

    @Test
    fun `progress since makes the same decision no retry`() {
        decide(Use(oak, 0), Undecided)
        logs = 1

        assertEquals(Use(oak, 0), decide(Use(oak, 0), Undecided))
    }

    @Test
    fun `a step closer since makes the same decision no retry`() {
        decide(Go(oak, 5), Undecided)

        assertEquals(Go(oak, 2), decide(Go(oak, 2), Undecided))
    }

    @Test
    fun `another kind of decision on the same aim is no retry`() {
        decide(Go(oak, 2), Undecided)

        assertEquals(Use(oak, 0), decide(Use(oak, 0), Undecided))
    }

    @Test
    fun `the same decision on another aim is no retry`() {
        decide(Use(oak, 0), Undecided)

        assertEquals(Use(willow, 0), decide(Use(willow, 0), Undecided))
    }

    @Test
    fun `a decision aimed at nothing in between starts over`() {
        decide(Use(oak, 0), Undecided)
        decide(Undecided)

        assertEquals(Use(oak, 0), decide(Use(oak, 0), Undecided))
    }

    @Test
    fun `a decision aimed at nothing is never a retry`() {
        decide(Undecided)

        decide(Undecided)

        assertEquals(listOf(emptySet<Aim>(), emptySet()), asked)
    }

    @Test
    fun `the decision taken after setting an aim aside is the one remembered`() {
        decide(Use(oak, 0), Use(willow, 0))
        decide(Use(oak, 0), Use(willow, 0))

        val third = decide(Use(willow, 0), Undecided)

        assertEquals(Undecided, third)
    }
}
