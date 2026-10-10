package game.idle.flow

import game.idle.flow.FakeStepType.Companion.step
import game.idle.flow.option.FakeNames
import game.idle.flow.option.InputSource
import game.idle.flow.option.OptionFacts
import game.idle.location.Tile
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.Skill
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class StepWarningsTest {

    private val bronzeAxe = 1351
    private val runeAxe = 1359
    private val net = 303
    private val bait = 313
    private val tinderbox = 590
    private val logs = 1511
    private val knife = 946
    private val flour = 1933
    private val jug = 1937

    /** A bank step that withdraws [withdraws] and banks [banked] from the bag. */
    private data class FakeBank(override val withdraws: Set<Int>, val banked: Set<Int>) : ResolvedStep, BankMoves {

        override fun banks(id: Int): Boolean = id in banked

        override fun activity(player: Player, runTile: Tile): StepActivity = error("not run")
    }

    /** A step that fights, needing nothing in the bag. */
    private data object FakeFight : ResolvedStep, Fights {

        override fun activity(player: Player, runTile: Tile): StepActivity = error("not run")
    }

    private val axe = ToolNeed("axe", mapOf(bronzeAxe to 1, runeAxe to 41), Skill.WOODCUTTING)

    private fun ids(settings: StepSettings, key: String): Set<Int> = settings[key].orEmpty().split(",").mapNotNull(String::toIntOrNull).toSet()

    private fun needing(kind: String, vararg needs: StepNeeds, inputOf: ((StepSettings) -> InputSource)? = null) =
        FakeStepType(kind, inputOf = inputOf, resolved = { FakeStep(kind, needs = needs.toList()) })

    private val resolver = FlowResolver(
        StepTypes(
            listOf(
                needing("chop", StepNeeds(tools = listOf(axe))),
                needing("fish", StepNeeds(tools = listOf(ToolNeed(word = null, mapOf(net to 1)), ToolNeed(word = null, mapOf(bait to 1), countable = false)))),
                needing(
                    "light", StepNeeds(listOf(ToolNeed(word = null, mapOf(tinderbox to 1))), listOf(logs)),
                    inputOf = { if (it["input"] == "earlier") InputSource.EARLIER_STEPS else InputSource.BANK },
                ),
                needing("make", StepNeeds(listOf(ToolNeed(word = null, mapOf(knife to 1))), listOf(flour)), StepNeeds(inputs = listOf(jug))),
                needing("walk"),
                FakeStepType("bank", resolved = { FakeBank(ids(it, "withdraw"), ids(it, "banks")) }),
                FakeStepType("fight", resolved = { FakeFight }),
            ),
        ),
    )
    private val warnings = StepWarnings(resolver, FakeNames(
        mapOf(net to "Small fishing net", bait to "Fishing bait", logs to "Logs", tinderbox to "Tinderbox", knife to "Knife", flour to "Pot of flour", jug to "Jug of water"),
    ))

    private val woodcutter = OptionFacts(levels = mapOf(Skill.WOODCUTTING to 1))

    private fun bank(vararg values: Pair<String, String>) = StepSettings("bank", values.toMap())

    private fun of(steps: List<StepSettings>, index: Int = 0, facts: OptionFacts = woodcutter, reflexes: List<ReflexSettings> = emptyList()) =
        warnings.of(steps, index, facts, reflexes)

    @Test
    fun `a step that does not resolve has no warnings, its reason shows instead`() {
        assertEquals(emptyList<String>(), of(listOf(step("chop", "bad"))))
    }

    @Test
    fun `a step that needs nothing has no warnings`() {
        assertEquals(emptyList<String>(), of(listOf(step("walk"))))
    }

    @Test
    fun `a tool neither carried nor withdrawn is warned about`() {
        assertEquals(listOf("You carry no axe and no bank step withdraws one."), of(listOf(step("chop"))))
    }

    @Test
    fun `a carried tool is enough`() {
        assertEquals(emptyList<String>(), of(listOf(step("chop")), facts = woodcutter.copy(bag = setOf(bronzeAxe))))
    }

    @Test
    fun `a worn tool is enough`() {
        assertEquals(emptyList<String>(), of(listOf(step("chop")), facts = woodcutter.copy(worn = setOf(bronzeAxe))))
    }

    @Test
    fun `a tool above the player's level does not count`() {
        assertEquals(listOf("You carry no axe and no bank step withdraws one."), of(listOf(step("chop")), facts = woodcutter.copy(bag = setOf(runeAxe))))
    }

    @Test
    fun `a tool the player has the level for counts`() {
        val facts = OptionFacts(levels = mapOf(Skill.WOODCUTTING to 41), bag = setOf(runeAxe))

        assertEquals(emptyList<String>(), of(listOf(step("chop")), facts = facts))
    }

    @Test
    fun `a tool a bank step withdraws is enough, even after the step, since the flow loops`() {
        assertEquals(emptyList<String>(), of(listOf(step("chop"), bank("withdraw" to "$bronzeAxe"))))
    }

    @Test
    fun `a carried tool a bank step banks is warned about, naming that step`() {
        val steps = listOf(step("chop"), step("walk"), bank("banks" to "$bronzeAxe"))

        assertEquals(listOf("Step 3 banks your axe and no bank step withdraws one."), of(steps, facts = woodcutter.copy(bag = setOf(bronzeAxe))))
    }

    @Test
    fun `a second carried tool that no bank step banks is enough`() {
        val facts = OptionFacts(levels = mapOf(Skill.WOODCUTTING to 41), bag = setOf(bronzeAxe, runeAxe))

        assertEquals(emptyList<String>(), of(listOf(step("chop"), bank("banks" to "$bronzeAxe")), facts = facts))
    }

    @Test
    fun `a tool without a word is named after its item, and bait is not counted one by one`() {
        assertEquals(
            listOf("You carry no small fishing net and no bank step withdraws one.", "You carry no fishing bait and no bank step withdraws any."),
            of(listOf(step("fish"))),
        )
    }

    @Test
    fun `an input from the bank that no bank step withdraws is warned about after the tools`() {
        assertEquals(
            listOf("You carry no tinderbox and no bank step withdraws one.", "No bank step withdraws logs."),
            of(listOf(step("light")), facts = woodcutter.copy(bag = emptySet())),
        )
    }

    @Test
    fun `an input any bank step withdraws is enough`() {
        val steps = listOf(step("light"), bank("withdraw" to "$logs"))

        assertEquals(emptyList<String>(), of(steps, facts = woodcutter.copy(bag = setOf(tinderbox))))
    }

    @Test
    fun `an input from earlier steps is never warned about here`() {
        val earlier = StepSettings("light", mapOf("input" to "earlier"))

        assertEquals(emptyList<String>(), of(listOf(earlier), facts = woodcutter.copy(bag = setOf(tinderbox))))
    }

    @Test
    fun `a step that can work several ways is warned about its first way`() {
        assertEquals(listOf("You carry no knife and no bank step withdraws one.", "No bank step withdraws pot of flour."), of(listOf(step("make"))))
    }

    @Test
    fun `a step with one way the flow supplies has no warnings`() {
        assertEquals(emptyList<String>(), of(listOf(step("make"), bank("withdraw" to "$jug"))))
    }

    @Test
    fun `the step warned about is the one at the index given`() {
        assertEquals(listOf("You carry no axe and no bank step withdraws one."), of(listOf(step("walk"), step("chop")), index = 1))
    }

    @Test
    fun `a fight step with no reflex attached warns that nothing eats or runs`() {
        assertEquals(listOf("No reflex eats or runs during this step."), of(listOf(step("fight"))))
    }

    @Test
    fun `a fight step with a reflex attached does not warn`() {
        val fight = step("fight").copy(reflexes = listOf(2))

        assertEquals(emptyList<String>(), of(listOf(fight), reflexes = listOf(ReflexSettings(2))))
    }

    @Test
    fun `a fight step attached to a reflex the flow does not hold still warns`() {
        val fight = step("fight").copy(reflexes = listOf(5))

        assertEquals(listOf(StepWarnings.NO_SURVIVAL), of(listOf(fight), reflexes = listOf(ReflexSettings(2))))
    }
}
