package game.idle.flow

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class RunProgressTest {

    private val oakLogs = 1521
    private val logs = 1511

    @Test
    fun `a run starts with nothing counted and no goal met`() {
        assertEquals(listOf(0, emptySet<Int>(), 0L), listOf(RunProgress().total(3), RunProgress().met, RunProgress().startedAt))
    }

    @Test
    fun `what a step counts adds up over its turns`() {
        assertEquals(5, RunProgress().counted(1, 2).counted(1, 3).total(1))
    }

    @Test
    fun `each step counts on its own`() {
        assertEquals(0, RunProgress().counted(1, 2).total(0))
    }

    @Test
    fun `a turn that counted nothing changes nothing`() {
        val progress = RunProgress(mapOf(1 to 2))

        assertSame(progress, progress.counted(1, 0))
    }

    @Test
    fun `a step met is remembered with the others`() {
        assertEquals(setOf(0, 2), RunProgress().meeting(2).meeting(0).met)
    }

    @Test
    fun `a clock moved by the minute`() {
        val clock = FakeClock(now = 500)

        clock.advanceMinutes(2)

        assertEquals(120_500L, clock.now())
    }

    @Test
    fun `a withdrawal told left out is remembered for its bank step alone`() {
        val progress = RunProgress().telling(3, setOf(oakLogs)).telling(3, setOf(logs))

        assertEquals(listOf(true, true, false), listOf(progress.told(3, oakLogs), progress.told(3, logs), progress.told(2, oakLogs)))
    }

    @Test
    fun `a long way round told is remembered for its cause alone`() {
        val progress = RunProgress().tellingLongWay("tolls and fares are off in Travel")

        assertEquals(listOf(true, false), listOf(progress.toldLongWay("tolls and fares are off in Travel"), progress.toldLongWay("bank stops are off in Travel")))
    }

    private fun feeder(name: String, vararg inputs: Int) = FakeStep(name, needs = listOf(StepNeeds(inputs = inputs.toList())))

    private val bank = FakeBankStep(setOf(oakLogs, logs))

    @Test
    fun `a withdrawal feeding only a step met for the run is left out`() {
        assertEquals(setOf(oakLogs), SkippedFeeds.leftOut(listOf(feeder("light", oakLogs), bank), met = setOf(0), bank))
    }

    @Test
    fun `a withdrawal left out names the first met step it fed`() {
        val steps = listOf(feeder("make", logs), feeder("light", oakLogs), feeder("burn", oakLogs), bank)

        assertEquals(mapOf(oakLogs to 1, logs to 0), SkippedFeeds.fed(steps, met = setOf(2, 1, 0), bank))
    }

    @Test
    fun `a withdrawal a running step still needs is kept`() {
        assertEquals(emptySet<Int>(), SkippedFeeds.leftOut(listOf(feeder("light", oakLogs), feeder("cook", oakLogs), bank), met = setOf(0), bank))
    }

    @Test
    fun `a withdrawal no step takes in is kept`() {
        assertEquals(emptySet<Int>(), SkippedFeeds.leftOut(listOf(feeder("light", oakLogs), bank), met = setOf(1), bank))
    }

    @Test
    fun `nothing is left out while no step is met`() {
        assertEquals(emptySet<Int>(), SkippedFeeds.leftOut(listOf(feeder("light", oakLogs), bank), met = emptySet(), bank))
    }

    @Test
    fun `a step that does not resolve feeds nothing`() {
        assertEquals(emptySet<Int>(), SkippedFeeds.leftOut(listOf(null, bank), met = setOf(0), bank))
    }

    @Test
    fun `a withdrawal a running step needs as a tool or bait is kept`() {
        val feathers = 314
        val fly = FakeStep("fish", needs = listOf(StepNeeds(tools = listOf(ToolNeed(word = null, mapOf(feathers to 1), countable = false)))))
        val fletch = feeder("make", feathers)
        val bank = FakeBankStep(setOf(feathers))

        assertEquals(emptySet<Int>(), SkippedFeeds.leftOut(listOf(fly, fletch, bank), met = setOf(1), bank))
    }
}
