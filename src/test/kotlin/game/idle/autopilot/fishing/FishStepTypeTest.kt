package game.idle.autopilot.fishing

import game.idle.flow.FlowContext
import game.idle.flow.FlowError
import game.idle.flow.LevelNeed
import game.idle.flow.StepIcon
import game.idle.flow.StepNeeds
import game.idle.flow.StepPick
import game.idle.flow.StepSettings
import game.idle.flow.ToolNeed
import game.idle.flow.WorkSpot
import game.idle.flow.described
import game.idle.flow.option.FakeNames
import game.idle.flow.option.InputSource
import game.idle.flow.option.LunaGameNames
import game.idle.location.Area
import game.idle.location.Tile
import game.skill.fishing.catchFish.Fish
import game.skill.fishing.catchFish.FishingSpot
import game.skill.fishing.catchFish.Tool
import game.testworld.TestWorld
import io.luna.game.model.mob.Skill
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class FishStepTypeTest {

    private val picked = Tile(3101, 3509)
    private val walkedTo = WorkSpot.At(Tile(3086, 3228))

    private fun fish(vararg values: Pair<String, String>) = StepSettings("fish", mapOf(*values))

    private val shrimp = FishingCatch(Fish.SHRIMP, FishingMethod(Tool.SMALL_NET))

    /** Luna's fish name themselves from the item definitions, which need the cache. */
    @BeforeEach
    fun `item definitions are loaded`() {
        TestWorld.world
    }

    @Test
    fun `a fish step without a catch is rejected`() {
        assertRejected("Needs a catch") { FishStepType.resolve(fish(), FlowContext()) }
    }

    @Test
    fun `a fish step fishes around the work spot, whatever the case`() {
        val step = FishStepType.resolve(fish("fish" to "Shrimp", "amount" to "1", "within" to "15"), FlowContext(workSpot = walkedTo))

        assertEquals(FishStep(shrimp, 15, walkedTo, amount = 1), step)
    }

    @Test
    fun `a fish is picked by its name, with the method that catches it`() {
        val step = FishStepType.resolve(fish("fish" to "shark"), FlowContext())

        assertEquals(FishingCatch(Fish.SHARK, FishingMethod(Tool.SHARK_HARPOON)), (step as FishStep).catch)
    }

    @Test
    fun `a method's other fish is its own catch, counted on its own level`() {
        val step = FishStepType.resolve(fish("fish" to "anchovy"), FlowContext()) as FishStep

        assertEquals(listOf(Fish.ANCHOVY, Tool.SMALL_NET, 15), listOf(step.catch.fish, step.catch.method.tool, step.needs().single().level?.level))
    }

    @Test
    fun `every fish of every method is a catch, lowest level first`() {
        assertEquals(
            listOf(
                "shrimp", "karambwanji", "sardine", "herring", "anchovy", "mackerel", "trout", "cod", "pike", "slimy_eel",
                "salmon", "tuna", "cave_eel", "lobster", "bass", "swordfish", "lava_eel", "monkfish", "karambwan", "shark",
            ),
            FishingCatch.ALL.map { it.word },
        )
    }

    @Test
    fun `a fish two methods catch is the first method's catch`() {
        assertEquals(Tool.SMALL_NET, FishingCatch.named("shrimp")?.method?.tool)
    }

    @Test
    fun `slimy eels are caught at their own spots, not as the cave eel spots' by-catch`() {
        assertEquals(Tool.SLIMY_EEL_FISHING_ROD, FishingCatch.named("slimy_eel")?.method?.tool)
    }

    @Test
    fun `a method's fish leave out the junk it pulls up`() {
        assertEquals(listOf(Fish.MACKEREL, Fish.COD, Fish.BASS), FishingMethod(Tool.BIG_NET).fish)
    }

    @Test
    fun `a fish step takes a goal`() {
        assertEquals(true, FishStepType.goal)
    }

    @Test
    fun `what a method only pulls up as by-catch is rejected`() {
        assertRejected("'casket' is not a catch") {
            FishStepType.resolve(fish("fish" to "casket"), FlowContext())
        }
    }

    @Test
    fun `the configure screen searches the catch, types the amount, picks the work spot and the radius`() {
        assertEquals(
            listOf(
                "Catch (left): search fish, 'What would you like to fish?'",
                "Amount (left): typed amount 1..2147483647, button 'Full'",
                "Work spot (right): map spot, none 'From the flow'",
                "Within (right): typed within 1..32",
            ),
            described(FishStepType.fields(FakeNames())),
        )
    }

    @Test
    fun `the configure screen shows the Fishing level`() {
        assertEquals(Skill.FISHING, FishStepType.skill(StepSettings()))
    }

    @Test
    fun `every tool Luna's spot table uses is a method, lowest level first`() {
        val tools = listOf(
            Tool.SMALL_NET, Tool.KARAMBWANJI_NET, Tool.FISHING_ROD, Tool.BIG_NET, Tool.FLY_FISHING_ROD, Tool.PIKE_FISHING_ROD,
            Tool.SLIMY_EEL_FISHING_ROD, Tool.CAVE_EEL_FISHING_ROD, Tool.HARPOON, Tool.LOBSTER_POT, Tool.LAVA_EEL_FISHING_ROD,
            Tool.MONKFISH_NET, Tool.KARAMBWAN_VESSEL, Tool.SHARK_HARPOON,
        )

        assertEquals(tools, FishingMethod.ALL.map { it.tool })
    }

    @Test
    fun `every method works some spot`() {
        assertEquals(emptyList<FishingMethod>(), FishingMethod.ALL.filter { it.spotIds.isEmpty() })
    }

    @Test
    fun `the small net works the sea's and the island's net spots and catches shrimp and anchovies`() {
        val net = FishingMethod(Tool.SMALL_NET)

        assertEquals(setOf(316, 319, 320, 323, 325, 326, 327, 330, 332, 1331, 952), net.spotIds)
        assertEquals(emptySet<Int>(), net.secondClickSpots)
        assertEquals(setOf(317, 321), net.catchIds)
    }

    @Test
    fun `the rod baits the sea spots on their second option`() {
        val rod = FishingMethod(Tool.FISHING_ROD)

        assertEquals(listOf(FishingSpot.NET_BAIT.npcs, FishingSpot.NET_BAIT.npcs), listOf(rod.spotIds, rod.secondClickSpots))
    }

    @Test
    fun `the lobster pot works the cage spots but not Miscellania's, whose catch goes to Fisherman Frodi`() {
        assertEquals(FishingSpot.CAGE_HARPOON.npcs, FishingMethod(Tool.LOBSTER_POT).spotIds)
    }

    @Test
    fun `the steps after a fish step know it gathers its catches`() {
        val after = FishStep(shrimp, 10, walkedTo).after(FlowContext(walkedTo, gathered = setOf(1511)))

        assertEquals(FlowContext(walkedTo, setOf(1511, 317, 321)), after)
    }

    @Test
    fun `a fish step shows a value that is no option as it is kept, without a picture`() {
        assertEquals(StepPick("nothing", null), FishStepType.pick(fish("fish" to "nothing"), LunaGameNames))
    }

    @Test
    fun `a new fish step starts with no settings`() {
        assertEquals(StepSettings("fish"), FishStepType.newSettings(FlowContext()))
    }

    @Test
    fun `a fish step takes nothing in, so its searches count the bank`() {
        assertEquals(InputSource.BANK, FishStepType.input(fish(), FlowContext()))
    }

    private fun assertRejected(message: String, action: () -> Unit) {
        assertEquals(message, assertThrows<FlowError> { action() }.message)
    }

    @Test
    fun `a fish step shows the Fishing icon`() {
        assertEquals(StepIcon.Skill(Skill.FISHING), FishStepType.icon(StepSettings("fish")))
    }

    @Test
    fun `a fish step's target is its fish, named with the tool that catches it`() {
        val target = FishStepType.target(LunaGameNames)

        assertEquals(listOf("fish", "Shrimps (small fishing net)"), listOf(target.key, target.picked(fish("fish" to "shrimp"))?.label))
    }

    @Test
    fun `a fish step's slot says how many fish a lap catches`() {
        assertEquals(listOf("5 per lap"), FishStepType.details(fish("fish" to "shrimp", "amount" to "5", "within" to "15"), FlowContext()))
    }

    @Test
    fun `a fish step without a count fishes until the bag is full, within the default radius`() {
        assertEquals(listOf("until the bag is full"), FishStepType.details(fish(), FlowContext()))
    }

    @Test
    fun `a fish step needs its method's tool in the bag and the tool's level`() {
        val needs = FishStep(shrimp, 10, walkedTo).needs()

        assertEquals(listOf(StepNeeds(tools = listOf(ToolNeed(word = null, mapOf(303 to 1))), level = LevelNeed(Skill.FISHING, 1))), needs)
    }

    @Test
    fun `a fish step with bait needs the bait too, which is not counted one by one`() {
        val tools = FishStep(FishingCatch(Fish.SARDINE, FishingMethod(Tool.FISHING_ROD)), 10, walkedTo).needs().single().tools

        assertEquals(listOf(ToolNeed(word = null, mapOf(307 to 1)), ToolNeed(word = null, mapOf(313 to 1), countable = false)), tools)
    }

    @Test
    fun `a fly fishing step needs feathers too, by that word`() {
        val tools = FishStep(FishingCatch(Fish.TROUT, FishingMethod(Tool.FLY_FISHING_ROD)), 10, walkedTo).needs().single().tools

        assertEquals(listOf(ToolNeed(word = null, mapOf(309 to 1)), ToolNeed(word = "feathers", mapOf(314 to 1), countable = false)), tools)
    }

    @Test
    fun `a fish step with a picked work spot works around it, not where the flow went`() {
        val step = FishStepType.resolve(fish("fish" to "shrimp", "spot" to "3101 3509"), FlowContext(workSpot = walkedTo))

        assertEquals(FishStep(shrimp, 10, WorkSpot.Own(picked)), step)
    }

    @Test
    fun `the steps after a fish step with a picked work spot work there`() {
        val after = FishStep(shrimp, 12, WorkSpot.Own(picked)).after(FlowContext())

        assertEquals(WorkSpot.At(picked), after.workSpot)
    }

    @Test
    fun `a fish step walks to its picked spot from outside its area, and to no spot when it works where the flow went`() {
        val own = FishStep(shrimp, 12, WorkSpot.Own(picked))
        val flows = FishStep(shrimp, 12, WorkSpot.At(picked))

        assertEquals(listOf(Area(picked, 12), null), listOf(own.ownArea, flows.ownArea))
    }
}
