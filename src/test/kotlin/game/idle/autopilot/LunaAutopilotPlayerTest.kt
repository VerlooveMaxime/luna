package game.idle.autopilot

import api.predef.woodcutting
import game.idle.IdleState
import game.idle.autopilot.bank.BankActivity
import game.idle.autopilot.bank.BankStep
import game.idle.autopilot.cooking.CookStep
import game.idle.autopilot.cooking.CookingActivity
import game.idle.autopilot.drop.DropActivity
import game.idle.autopilot.drop.DropStep
import game.idle.autopilot.firemaking.LightActivity
import game.idle.autopilot.firemaking.LightStep
import game.idle.autopilot.fishing.FishStep
import game.idle.autopilot.fishing.FishingActivity
import game.idle.autopilot.fishing.FishingMethod
import game.idle.autopilot.making.MakeActivity
import game.idle.autopilot.making.MakeStep
import game.idle.autopilot.making.Recipe
import game.idle.autopilot.mining.MineStep
import game.idle.autopilot.mining.MiningActivity
import game.idle.autopilot.smelting.SmeltStep
import game.idle.autopilot.smelting.SmeltingActivity
import game.idle.autopilot.smithing.SmithStep
import game.idle.autopilot.smithing.SmithingActivity
import game.idle.autopilot.walk.WalkActivity
import game.idle.autopilot.walk.WalkStep
import game.idle.autopilot.woodcutting.ChopStep
import game.idle.autopilot.woodcutting.WoodcuttingActivity
import game.idle.flow.FakeStepActivity
import game.idle.flow.FakeStepType
import game.idle.flow.FakeStepType.Companion.step
import game.idle.flow.ResolvedStep
import game.idle.flow.StepActivity
import game.idle.flow.StepTypes
import game.idle.flow.WorkSpot
import game.idle.idleState
import game.idle.location.Bank
import game.idle.location.Tile
import game.idle.ui.IdleUi
import game.skill.mining.Ore
import game.skill.smithing.BarType
import game.skill.smithing.smithBar.SmithingTable
import game.skill.woodcutting.cutTree.Tree
import game.testworld.TestWorld
import io.luna.game.model.Position
import io.luna.game.model.mob.Player
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LunaAutopilotPlayerTest {

    private val logs = 1511
    private val runTile = Tile(3190, 3190)

    /** Remembers the run tile it was started with. */
    private class RecordingStep : ResolvedStep {
        var runTile: Tile? = null

        override fun activity(player: Player, runTile: Tile): StepActivity {
            this.runTile = runTile
            return FakeStepActivity("recorded", mutableListOf())
        }
    }

    private val ui = IdleUi(StepTypes(listOf(FakeStepType("chop"), FakeStepType("drop")))::summary)

    private val chopOak = step("chop", "oak")

    private fun autopilotPlayer() = LunaAutopilotPlayer(TestWorld.login("autopilot", Position(3200, 3200)), ui)

    @AfterEach
    fun resetWorld() = TestWorld.reset()

    @Test
    fun `the tile is the player's`() {
        assertEquals(Tile(3200, 3200), autopilotPlayer().tile)
    }

    @Test
    fun `a step starts with the run tile the flow was started on`() {
        val autopilot = autopilotPlayer()
        autopilot.player.idleState = IdleState(runTile = runTile)
        val step = RecordingStep()

        autopilot.activity(step)

        assertEquals(runTile, step.runTile)
    }

    @Test
    fun `a step of a flow without a run tile starts from the player's tile`() {
        val step = RecordingStep()

        autopilotPlayer().activity(step)

        assertEquals(Tile(3200, 3200), step.runTile)
    }

    @Test
    fun `the username is the player's`() {
        assertEquals("autopilot", autopilotPlayer().username)
    }

    @Test
    fun `the idle state written is the player's`() {
        val autopilot = autopilotPlayer()

        autopilot.idleState = IdleState().started(runTile)

        assertTrue(autopilot.player.idleState.running)
    }

    @Test
    fun `the idle state read is the player's`() {
        val autopilot = autopilotPlayer()
        autopilot.player.idleState = IdleState(stepIndex = 2)

        assertEquals(2, autopilot.idleState.stepIndex)
    }

    private fun overlayTexts(autopilot: LunaAutopilotPlayer): List<String> =
        TestWorld.messages(autopilot.player)
            .filter { it.type == "StatusOverlayMessageWriter" }
            .map { it.fields.getValue("text").toString() }

    @Test
    fun `starting a flow shows it on the status overlay`() {
        val autopilot = autopilotPlayer()

        autopilot.idleState = IdleState(steps = listOf(chopOak, step("drop"))).started(runTile)

        assertEquals(listOf("@gre@Autopilot@whi@ step 1/2|@yel@chop oak"), overlayTexts(autopilot))
    }

    @Test
    fun `stopping a flow clears the status overlay`() {
        val autopilot = autopilotPlayer()

        autopilot.idleState = IdleState(steps = listOf(chopOak)).stopped()

        assertEquals(listOf(""), overlayTexts(autopilot))
    }

    @Test
    fun `saving a step refreshes the status overlay`() {
        val autopilot = autopilotPlayer()
        autopilot.idleState = IdleState(steps = listOf(chopOak, step("drop"))).started(runTile)

        autopilot.saveStep(1)

        assertEquals("@gre@Autopilot@whi@ step 2/2|@yel@drop", overlayTexts(autopilot).last())
    }

    @Test
    fun `a lap is counted on the player's state`() {
        val autopilot = autopilotPlayer()
        autopilot.player.idleState = IdleState(steps = listOf(chopOak), running = true)

        autopilot.lapCompleted()

        assertEquals(1, autopilot.player.idleState.laps)
    }

    @Test
    fun `telling the player sends a chat box line`() {
        val autopilot = autopilotPlayer()

        autopilot.tell("Chopping at the test grove.")

        assertEquals(listOf("Chopping at the test grove."), TestWorld.chatbox(autopilot.player))
    }

    @Test
    fun `saving a step keeps the rest of the idle state`() {
        val autopilot = autopilotPlayer()
        autopilot.player.idleState = IdleState(steps = listOf(chopOak), running = true)

        autopilot.saveStep(1)

        assertEquals(IdleState(steps = listOf(chopOak), stepIndex = 1, running = true), autopilot.player.idleState)
    }

    @Test
    fun `a chop step runs as a woodcutting activity`() {
        val step = ChopStep(Tree.NORMAL, radius = 10, WorkSpot.RunTile)

        assertInstanceOf(WoodcuttingActivity::class.java, autopilotPlayer().activity(step))
    }

    @Test
    fun `a drop step runs as a drop activity`() {
        assertInstanceOf(DropActivity::class.java, autopilotPlayer().activity(DropStep(setOf(logs))))
    }

    @Test
    fun `a bank step runs as a bank activity`() {
        val step = BankStep(listOf(Bank("test", "Test bank", Tile(3210, 3200))))

        assertInstanceOf(BankActivity::class.java, autopilotPlayer().activity(step))
    }

    @Test
    fun `a light step runs as a light activity`() {
        assertInstanceOf(LightActivity::class.java, autopilotPlayer().activity(LightStep(setOf(logs))))
    }

    @Test
    fun `a fish step runs as a fishing activity`() {
        assertInstanceOf(FishingActivity::class.java, autopilotPlayer().activity(FishStep(FishingMethod.SHRIMP, 10, WorkSpot.RunTile)))
    }

    @Test
    fun `a cook step runs as a cooking activity`() {
        assertInstanceOf(CookingActivity::class.java, autopilotPlayer().activity(CookStep(setOf(317), 10, WorkSpot.RunTile)))
    }

    @Test
    fun `a mine step runs as a mining activity`() {
        assertInstanceOf(MiningActivity::class.java, autopilotPlayer().activity(MineStep(Ore.COPPER, 10, WorkSpot.RunTile)))
    }

    @Test
    fun `a smelt step runs as a smelting activity`() {
        assertInstanceOf(SmeltingActivity::class.java, autopilotPlayer().activity(SmeltStep(BarType.BRONZE, 10, WorkSpot.RunTile)))
    }

    @Test
    fun `a smith step runs as a smithing activity`() {
        val step = SmithStep(BarType.BRONZE, SmithingTable.DAGGER, 10, WorkSpot.RunTile)

        assertInstanceOf(SmithingActivity::class.java, autopilotPlayer().activity(step))
    }

    @Test
    fun `a make step runs as a make activity`() {
        assertInstanceOf(MakeActivity::class.java, autopilotPlayer().activity(MakeStep(Recipe(2307, "bread dough", 1933, 1929))))
    }

    @Test
    fun `a walk step runs as a walk activity`() {
        assertInstanceOf(WalkActivity::class.java, autopilotPlayer().activity(WalkStep(Tile(3210, 3200))))
    }
}
