package game.idle.autopilot

import api.predef.woodcutting
import game.idle.IdleState
import game.idle.autopilot.bank.BankStep
import game.idle.autopilot.bank.BankWork
import game.idle.autopilot.bank.EVERYTHING
import game.idle.autopilot.cooking.CookStep
import game.idle.autopilot.cooking.CookingWork
import game.idle.autopilot.drop.DropStep
import game.idle.autopilot.drop.DropWork
import game.idle.autopilot.fighting.FightStep
import game.idle.autopilot.fighting.FightTarget
import game.idle.autopilot.fighting.FightWork
import game.idle.autopilot.firemaking.LightWork
import game.idle.autopilot.firemaking.LightStep
import game.idle.autopilot.fishing.FishStep
import game.idle.autopilot.fishing.FishingWork
import game.idle.autopilot.fishing.FishingMethod
import game.idle.autopilot.making.MakeWork
import game.idle.autopilot.making.MakeStep
import game.idle.autopilot.making.BREAD_DOUGH
import game.idle.autopilot.mining.MineStep
import game.idle.autopilot.mining.MiningWork
import game.idle.autopilot.pickup.PickUpStep
import game.idle.autopilot.pickup.PickUpWork
import game.idle.autopilot.rest.RestStep
import game.idle.autopilot.rest.RestWork
import game.idle.autopilot.smelting.SmeltStep
import game.idle.autopilot.smelting.SmeltingWork
import game.idle.autopilot.smithing.SmithStep
import game.idle.autopilot.smithing.SmithingWork
import game.idle.autopilot.walk.WalkStep
import game.idle.autopilot.walk.WalkWork
import game.idle.autopilot.woodcutting.ChopStep
import game.idle.autopilot.woodcutting.ChopStepType
import game.idle.autopilot.woodcutting.WoodcuttingWork
import game.idle.flow.FakeStepType
import game.idle.flow.FakeStepType.Companion.step
import game.idle.flow.FakeStepWork
import game.idle.flow.Place
import game.idle.flow.Reason
import game.idle.flow.ResolvedStep
import game.idle.flow.StepOutcome
import game.idle.flow.StepTurn
import game.idle.flow.StepTypes
import game.idle.flow.StepWork
import game.idle.flow.WorkSpot
import game.idle.idleState
import game.idle.location.Bank
import game.idle.location.Tile
import game.idle.ui.IdleUi
import game.idle.ui.idleTab
import game.skill.fishing.catchFish.Tool
import game.skill.mining.Ore
import game.skill.smithing.BarType
import game.skill.smithing.smithBar.SmithingTable
import game.skill.woodcutting.cutTree.Tree
import game.testworld.TestWorld
import io.luna.game.model.Position
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.ArrayDeque

class LunaAutopilotPlayerTest {

    private val logs = 1511
    private val bronzeAxe = 1351
    private val runTile = Tile(3190, 3190)

    /** Remembers the run tile it was started with. */
    private class RecordingStep : ResolvedStep {
        var runTile: Tile? = null

        override fun work(player: Player, runTile: Tile): StepWork {
            this.runTile = runTile
            return FakeStepWork(StepOutcome.Worked)
        }
    }

    /** The work inside a step's turn. */
    private fun workOf(step: ResolvedStep): StepWork = (autopilotPlayer().activity(step) as StepTurn).work

    private val ui = IdleUi(StepTypes(listOf(FakeStepType("chop"), FakeStepType("drop")))::summary, idleTab())

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

    private val farTile = Position(3220, 3200)

    @Test
    fun `ending the walk stops a walk the autopilot started`() {
        val autopilot = autopilotPlayer()
        autopilot.player.navigator.navigate(farTile, false)
        TestWorld.tick()
        val stoppedAt = autopilot.player.position

        autopilot.endWalk()
        TestWorld.tick(times = 3)

        assertEquals(stoppedAt, autopilot.player.position)
    }

    @Test
    fun `ending the walk ends a flight's running, the run toggle back as it was`() {
        val autopilot = autopilotPlayer()
        TestWorld.spawnNpc(41, Position(3199, 3200)).combat.target = autopilot.player
        autopilot.body.flee()

        autopilot.endWalk()

        assertFalse(autopilot.player.isRunning)
    }

    @Test
    fun `ending the walk leaves a walk the player clicked alone`() {
        val autopilot = autopilotPlayer()
        autopilot.player.walking.replacePath(ArrayDeque(listOf(Position(3200, 3205))))

        autopilot.endWalk()

        assertFalse(autopilot.player.walking.isEmpty)
    }

    /** Walks far off at its first decision, the way a bank or walk step heads for its target. */
    private class WalkingFarOff(private val player: Player, private val tile: Position) : AutopilotActivity {

        override fun isBusy(): Boolean = !player.walking.isEmpty || player.navigator.isActive

        override fun act() {
            player.navigator.navigate(tile, false)
        }

        override fun stopReason(): String? = null

        override fun blocked(): String? = null

        override fun waiting(): String? = null
    }

    @Test
    fun `stopping a running flow leaves the player where they stand`() {
        val autopilotPlayer = autopilotPlayer()
        val player = autopilotPlayer.player
        player.idleState = IdleState(steps = listOf(chopOak))
        val autopilot = Autopilot<LunaAutopilotPlayer>(WorldTickScheduler(TestWorld.world)) {
            AutopilotDriver(WalkingFarOff(player, farTile), decisionDelayTicks = 1)
        }
        autopilot.start(autopilotPlayer)
        TestWorld.tick(times = 3)
        val stoppedAt = player.position

        autopilot.stop(autopilotPlayer)
        TestWorld.tick(times = 3)

        assertEquals(stoppedAt, player.position)
    }

    @Test
    fun `a chop step runs its woodcutting work in a turn`() {
        assertInstanceOf(WoodcuttingWork::class.java, workOf(ChopStep(Tree.NORMAL, radius = 10, WorkSpot.RunTile)))
    }

    @Test
    fun `a drop step runs its drop work in a turn`() {
        assertInstanceOf(DropWork::class.java, workOf(DropStep(setOf(logs))))
    }

    @Test
    fun `a rest step runs its rest work in a turn`() {
        assertInstanceOf(RestWork::class.java, workOf(RestStep(100)))
    }

    @Test
    fun `a bank step runs its bank work in a turn`() {
        val step = BankStep(listOf(Bank("test", "Test bank", Tile(3210, 3200))), EVERYTHING)

        assertInstanceOf(BankWork::class.java, workOf(step))
    }

    @Test
    fun `a fight step runs its fight work in a turn`() {
        val step = FightStep(FightTarget("chicken", setOf(41), "Chicken", 1..1), radius = 10, WorkSpot.RunTile)

        assertInstanceOf(FightWork::class.java, workOf(step))
    }

    @Test
    fun `a pick-up step runs its pick-up work in a turn`() {
        assertInstanceOf(PickUpWork::class.java, workOf(PickUpStep(setOf(526), radius = 10, WorkSpot.RunTile)))
    }

    @Test
    fun `a light step runs its light work in a turn`() {
        assertInstanceOf(LightWork::class.java, workOf(LightStep(setOf(logs))))
    }

    @Test
    fun `a fish step runs its fishing work in a turn`() {
        assertInstanceOf(FishingWork::class.java, workOf(FishStep(FishingMethod(Tool.SMALL_NET), 10, WorkSpot.RunTile)))
    }

    @Test
    fun `a cook step runs its cook work in a turn`() {
        assertInstanceOf(CookingWork::class.java, workOf(CookStep(setOf(317), 10, WorkSpot.RunTile)))
    }

    @Test
    fun `a mine step runs its mining work in a turn`() {
        assertInstanceOf(MiningWork::class.java, workOf(MineStep(Ore.COPPER, 10, WorkSpot.RunTile)))
    }

    @Test
    fun `a smelt step runs its smelt work in a turn`() {
        assertInstanceOf(SmeltingWork::class.java, workOf(SmeltStep(BarType.BRONZE, 10, WorkSpot.RunTile)))
    }

    @Test
    fun `a smith step runs its smith work in a turn`() {
        val step = SmithStep(BarType.BRONZE, SmithingTable.DAGGER, 10, WorkSpot.RunTile)

        assertInstanceOf(SmithingWork::class.java, workOf(step))
    }

    @Test
    fun `a make step runs its make work in a turn`() {
        assertInstanceOf(MakeWork::class.java, workOf(MakeStep(BREAD_DOUGH)))
    }

    @Test
    fun `a walk step runs its walk work in a turn`() {
        assertInstanceOf(WalkWork::class.java, workOf(WalkStep(Tile(3210, 3200))))
    }

    @Test
    fun `a step's turn checks what it needs before it looks`() {
        val turn = autopilotPlayer().activity(ChopStep(Tree.NORMAL, radius = 10, WorkSpot.RunTile))

        turn.act()

        assertEquals(Reason.NeedsTool(ChopStepType.AXE), turn.blocked())
    }

    @Test
    fun `a gather step's turn ends on a full bag`() {
        val autopilot = autopilotPlayer()
        autopilot.player.inventory.add(Item(bronzeAxe))
        autopilot.player.inventory.add(Item(logs, 27))
        val turn = autopilot.activity(ChopStep(Tree.NORMAL, radius = 10, WorkSpot.RunTile))

        turn.act()

        assertTrue(turn.isDone())
    }

    @Test
    fun `a counting step's turn ends at its amount`() {
        val autopilot = autopilotPlayer()
        autopilot.player.inventory.add(Item(bronzeAxe))
        val turn = autopilot.activity(ChopStep(Tree.NORMAL, radius = 10, WorkSpot.RunTile, amount = 1))
        turn.act()
        autopilot.player.inventory.add(Item(logs))

        turn.act()

        assertTrue(turn.isDone())
    }

    @Test
    fun `a step with its own spot starting outside its area walks to the spot first`() {
        val autopilot = autopilotPlayer()
        val activity = autopilot.activity(ChopStep(Tree.NORMAL, radius = 5, WorkSpot.Own(Tile(3220, 3200))))

        activity.act()

        assertEquals(Position(3220, 3200), autopilot.player.navigator.currentTarget)
    }

    @Test
    fun `a step with its own spot starting inside its area works at once`() {
        val step = ChopStep(Tree.NORMAL, radius = 5, WorkSpot.Own(Tile(3205, 3200)))

        assertInstanceOf(StepTurn::class.java, autopilotPlayer().activity(step))
    }

    @Test
    fun `a step's walk to its own spot blocks on the spot when it cannot get there`() {
        val activity = autopilotPlayer().activity(ChopStep(Tree.NORMAL, radius = 5, WorkSpot.Own(Tile(3220, 3200, 1))))

        activity.act()

        assertEquals(Reason.OtherFloor(Place.WORK_SPOT), activity.blocked())
    }
}
