package game.idle

import game.idle.flow.StopSettings
import game.idle.flow.RunProgress
import game.idle.flow.ReflexSettings
import game.idle.flow.StepSettings
import game.idle.location.Tile
import game.idle.movement.TravelAllowance
import io.luna.game.model.mob.attr.Attribute
import io.luna.util.GsonUtils
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class IdleStateTest {

    private val runTile = Tile(3200, 3200)
    private val chop = StepSettings("chop", mapOf("tree" to "normal"))
    private val state = IdleState(steps = listOf(chop), stepIndex = 1, running = true, runTile = runTile, stage = 3, resets = 2)

    @Test
    fun `new state has no flow and is not running`() {
        val fresh = IdleState()

        assertEquals(emptyList<StepSettings>(), fresh.steps)
        assertFalse(fresh.running)
        assertEquals(listOf(0, 0, 0), listOf(fresh.stepIndex, fresh.stage, fresh.resets))
    }

    @Test
    fun `a new flow starts at step zero, stopped, with no run tile`() {
        val drop = StepSettings("drop")

        val changed = state.withFlow(listOf(drop))

        assertEquals(IdleState(steps = listOf(drop), stepIndex = 0, running = false, stage = 3, resets = 2), changed)
    }

    @Test
    fun `a new flow of steps alone keeps the reflexes`() {
        val reflexes = listOf(ReflexSettings(1))

        assertEquals(reflexes, state.copy(reflexes = reflexes).withFlow(listOf(chop)).reflexes)
    }

    @Test
    fun `a new flow takes the reflexes it is given`() {
        val reflexes = listOf(ReflexSettings(2, mapOf("do" to "stop")))

        assertEquals(reflexes, state.withFlow(listOf(chop), reflexes).reflexes)
    }

    @Test
    fun `reflexes and the steps they attach to are saved and read back`() {
        val saving = state.copy(steps = listOf(chop.copy(id = 3, reflexes = listOf(1))), reflexes = listOf(ReflexSettings(1, mapOf("below" to "40"))))

        assertEquals(saving, GsonUtils.GSON.fromJson(GsonUtils.GSON.toJson(saving), IdleState::class.java))
    }

    @Test
    fun `moving to a step keeps everything else`() {
        assertEquals(state.copy(stepIndex = 4), state.atStep(4))
    }

    @Test
    fun `stopping only flips the switch`() {
        assertEquals(state.copy(running = false), state.stopped())
    }

    @Test
    fun `starting from the first step takes the player's tile as the run tile`() {
        val started = state.stopped().fromStart().started(Tile(1, 2), 0)

        assertEquals(state.copy(stepIndex = 0, runTile = Tile(1, 2)), started)
    }

    @Test
    fun `a lap is counted, and a new run or a new flow counts from zero again`() {
        val lapped = state.lapped().lapped()

        assertEquals(listOf(2, 0, 0), listOf(lapped.laps, lapped.fromStart().laps, lapped.withFlow(emptyList()).laps))
    }

    @Test
    fun `resuming keeps the run tile it was started on`() {
        assertEquals(state, state.stopped().started(Tile(1, 2), 0))
    }

    @Test
    fun `the run tile is saved and read back`() {
        val json = GsonUtils.GSON.toJson(state)

        assertEquals(state, GsonUtils.GSON.fromJson(json, IdleState::class.java))
    }

    @Test
    fun `steps and saved flows are saved and read back`() {
        val saving = state.copy(savedFlows = listOf(SavedFlow(1, "Oaks", listOf(chop))), savedSlot = 1)

        assertEquals(saving, GsonUtils.GSON.fromJson(GsonUtils.GSON.toJson(saving), IdleState::class.java))
    }

    @Test
    fun `a save from before structured steps loads with an empty flow and keeps the rest`() {
        val saved = GsonUtils.GSON.fromJson(
            """{ "flow": ["chop oak", "drop"], "step_index": 1, "stage": 2, "tutorial_step": 5 }""",
            IdleState::class.java,
        )

        assertEquals(IdleState(stepIndex = 1, stage = 2, tutorialStep = 5), saved)
    }

    @Test
    fun `a new state has finished the tutorial, so only new characters are given one`() {
        assertEquals(IdleState.TUTORIAL_DONE, IdleState().tutorialStep)
    }

    @Test
    fun `a state saved before the tutorial existed loads as finished`() {
        val saved = GsonUtils.GSON.fromJson("""{ "flow": [], "step_index": 2, "running": false }""", IdleState::class.java)

        assertEquals(IdleState.TUTORIAL_DONE, saved.tutorialStep)
    }

    @Test
    fun `a block is never saved`() {
        val json = Attribute.getGsonInstance().toJson(state.copy(blocked = "Needs an axe you can use"))

        assertFalse(json.contains("blocked"))
    }

    @Test
    fun `a state read back has no block`() {
        val gson = Attribute.getGsonInstance()

        assertEquals(state, gson.fromJson(gson.toJson(state.copy(blocked = "Needs an axe you can use")), IdleState::class.java))
    }

    @Test
    fun `starting, stopping, a new flow and a new step all clear the block`() {
        val blocked = state.copy(blocked = "Needs an axe you can use")

        val cleared = listOf(blocked.started(runTile, 0), blocked.stopped(), blocked.withFlow(emptyList()), blocked.atStep(0))

        assertEquals(listOf<String?>(null, null, null, null), cleared.map { it.blocked })
    }

    @Test
    fun `a wait is never saved`() {
        val json = Attribute.getGsonInstance().toJson(state.copy(waiting = "No tree in reach"))

        assertFalse(json.contains("waiting"))
    }

    @Test
    fun `starting, stopping, a new flow and a new step all clear the wait`() {
        val waiting = state.copy(waiting = "No tree in reach")

        val cleared = listOf(waiting.started(runTile, 0), waiting.stopped(), waiting.withFlow(emptyList()), waiting.atStep(0))

        assertEquals(listOf<String?>(null, null, null, null), cleared.map { it.waiting })
    }

    @Test
    fun `what a step does on its way is never saved`() {
        val json = Attribute.getGsonInstance().toJson(state.copy(underWay = "Opening the door"))

        assertFalse(json.contains("underWay"))
    }

    @Test
    fun `starting, stopping, a new flow and a new step all clear what a step does on its way`() {
        val onItsWay = state.copy(underWay = "Opening the door")

        val cleared = listOf(onItsWay.started(runTile, 0), onItsWay.stopped(), onItsWay.withFlow(emptyList()), onItsWay.atStep(0))

        assertEquals(listOf<String?>(null, null, null, null), cleared.map { it.underWay })
    }

    @Test
    fun `a new state has options judged on unboosted levels`() {
        assertFalse(IdleState().countBoostedLevels)
    }

    @Test
    fun `the choice to count boosted levels is saved and read back`() {
        val counting = state.copy(countBoostedLevels = true)

        assertEquals(counting, GsonUtils.GSON.fromJson(GsonUtils.GSON.toJson(counting), IdleState::class.java))
    }

    private val progress = RunProgress(totals = mapOf(0 to 3, 2 to 1), met = setOf(2), startedAt = 1_700_000_000_000)
    private val laps5 = StopSettings("laps", "5")

    @Test
    fun `the run's progress and the stop are saved and read back`() {
        val saving = state.copy(progress = progress, stop = laps5)

        assertEquals(saving, GsonUtils.GSON.fromJson(GsonUtils.GSON.toJson(saving), IdleState::class.java))
    }

    @Test
    fun `a new flow starts with nothing done and keeps the stop unless given one`() {
        val before = state.copy(progress = progress, stop = laps5)

        assertEquals(RunProgress() to laps5, before.withFlow(emptyList()).let { it.progress to it.stop })
        assertEquals(StopSettings("goals"), before.withFlow(emptyList(), stop = StopSettings("goals")).stop)
    }

    @Test
    fun `a new flow keeps what its trips may spend unless given it`() {
        val before = state.copy(travel = TravelAllowance(coins = true))

        assertEquals(
            listOf(TravelAllowance(coins = true), TravelAllowance(runes = true)),
            listOf(before.withFlow(emptyList()).travel, before.withFlow(emptyList(), travel = TravelAllowance(runes = true)).travel),
        )
    }

    @Test
    fun `what the flow's trips may spend is saved`() {
        val saving = state.copy(travel = TravelAllowance(coins = true, charges = true))

        assertEquals(saving, GsonUtils.GSON.fromJson(GsonUtils.GSON.toJson(saving), IdleState::class.java))
    }

    @Test
    fun `a new run starts with nothing done`() {
        assertEquals(RunProgress(), state.copy(progress = progress).fromStart().progress)
    }

    @Test
    fun `starting from the first step takes the time as the run's start`() {
        val started = state.stopped().fromStart().started(Tile(1, 2), now = 42)

        assertEquals(42L, started.progress.startedAt)
    }

    @Test
    fun `resuming keeps the start it was started at`() {
        val before = state.copy(progress = progress)

        assertEquals(before, before.stopped().started(Tile(1, 2), now = 42))
    }

    @Test
    fun `the run's progress is written on its own`() {
        assertEquals(state.copy(progress = progress), state.withProgress(progress))
    }
}
