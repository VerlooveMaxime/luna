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
    private var progress = 0
    private val objects = RetrySkip.forTheTurn(progress = { progress })
    private val npcs = RetrySkip.untilProgress(progress = { progress })
    private val asked = mutableListOf<Set<Aim>>()

    /** Decides [decision] under [rule] whatever is set aside, recording the aims it was asked to leave out. */
    private fun decide(decision: Any, rule: RetrySkip = objects): Any = rule.decide { skipped -> asked += skipped.toSet(); decision }

    /** Decides [first] under [rule] unless its aim is set aside, then [fallback]; records the aims it was asked to leave out. */
    private fun decideOr(first: Aimed, fallback: Any, rule: RetrySkip = objects): Any =
        rule.decide { skipped -> asked += skipped.toSet(); if (first.aim in skipped) fallback else first }

    @Test
    fun `the first decision leaves nothing out`() {
        decide(Use(oak, 0))

        assertEquals(listOf(emptySet<Aim>()), asked)
    }

    @Test
    fun `the same decision twice with nothing gained sets its aim aside and decides again`() {
        decideOr(Use(oak, 0), Undecided)

        val second = decideOr(Use(oak, 0), Undecided)

        assertEquals(Undecided, second)
    }

    @Test
    fun `an aim set aside is told to whoever listens`() {
        val told = mutableListOf<Aim>()
        val rule = RetrySkip.forTheTurn(progress = { progress }, setAside = { told += it })
        decideOr(Use(oak, 0), Undecided, rule)

        decideOr(Use(oak, 0), Undecided, rule)

        assertEquals(listOf(oak), told)
    }

    @Test
    fun `an aim decided on once is not told`() {
        val told = mutableListOf<Aim>()
        val rule = RetrySkip.untilProgress(progress = { progress }, setAside = { told += it })

        decideOr(Use(oak, 0), Undecided, rule)

        assertEquals(emptyList<Aim>(), told)
    }

    @Test
    fun `the aim set aside stays so for the turn`() {
        decideOr(Use(oak, 0), Undecided)
        decideOr(Use(oak, 0), Undecided)
        decide(Undecided)

        decide(Use(oak, 0))

        assertEquals(setOf(oak), asked.last())
    }

    @Test
    fun `progress since makes the same decision no retry`() {
        decideOr(Use(oak, 0), Undecided)
        progress = 1

        assertEquals(Use(oak, 0), decideOr(Use(oak, 0), Undecided))
    }

    @Test
    fun `a step closer since makes the same decision no retry`() {
        decideOr(Go(oak, 5), Undecided)

        assertEquals(Go(oak, 2), decideOr(Go(oak, 2), Undecided))
    }

    @Test
    fun `another kind of decision on the same aim is no retry`() {
        decideOr(Go(oak, 2), Undecided)

        assertEquals(Use(oak, 0), decideOr(Use(oak, 0), Undecided))
    }

    @Test
    fun `the same decision on another aim is no retry`() {
        decideOr(Use(oak, 0), Undecided)

        assertEquals(Use(willow, 0), decideOr(Use(willow, 0), Undecided))
    }

    @Test
    fun `a decision aimed at nothing in between starts over`() {
        decideOr(Use(oak, 0), Undecided)
        decide(Undecided)

        assertEquals(Use(oak, 0), decideOr(Use(oak, 0), Undecided))
    }

    @Test
    fun `a decision aimed at nothing is never a retry`() {
        decide(Undecided)

        decide(Undecided)

        assertEquals(listOf(emptySet<Aim>(), emptySet()), asked)
    }

    @Test
    fun `the decision taken after setting an aim aside is the one remembered`() {
        decideOr(Use(oak, 0), Use(willow, 0))
        decideOr(Use(oak, 0), Use(willow, 0))

        val third = decideOr(Use(willow, 0), Undecided)

        assertEquals(Undecided, third)
    }

    @Test
    fun `an npc tried twice with nothing gained is set aside for another`() {
        decideOr(Use(oak, 0), Use(willow, 0), npcs)

        val second = decideOr(Use(oak, 0), Use(willow, 0), npcs)

        assertEquals(Use(willow, 0), second)
    }

    @Test
    fun `an npc set aside stays so while nothing is gained and others are around`() {
        decideOr(Use(oak, 0), Use(willow, 0), npcs)
        decideOr(Use(oak, 0), Use(willow, 0), npcs)

        decide(Use(oak, 0), npcs)

        assertEquals(setOf(oak), asked.last())
    }

    @Test
    fun `progress brings every npc set aside back`() {
        decideOr(Use(oak, 0), Use(willow, 0), npcs)
        decideOr(Use(oak, 0), Use(willow, 0), npcs)
        progress = 1

        decide(Use(oak, 0), npcs)

        assertEquals(emptySet<Aim>(), asked.last())
    }

    @Test
    fun `once every npc around is set aside they all come back and the lone one is tried again`() {
        decideOr(Use(oak, 0), Undecided, npcs)

        val second = decideOr(Use(oak, 0), Undecided, npcs)

        assertEquals(Use(oak, 0), second)
    }

    @Test
    fun `npcs coming back are decided over none set aside`() {
        decideOr(Use(oak, 0), Undecided, npcs)

        decideOr(Use(oak, 0), Undecided, npcs)

        assertEquals(listOf(emptySet<Aim>(), emptySet(), setOf(oak), emptySet()), asked)
    }

    @Test
    fun `a decision aimed at no npc with none set aside is decided once`() {
        decide(Undecided, npcs)

        assertEquals(listOf(emptySet<Aim>()), asked)
    }
}
