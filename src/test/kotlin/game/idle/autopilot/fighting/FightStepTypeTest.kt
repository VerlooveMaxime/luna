package game.idle.autopilot.fighting

import game.idle.flow.FlowContext
import game.idle.flow.FlowError
import game.idle.flow.StepField
import game.idle.flow.StepSettings
import game.idle.flow.WorkSpot
import game.idle.location.Tile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class FightStepTypeTest {

    private val rats = FightTarget("giant rat", setOf(950))
    private val cows = FightTarget("cow", setOf(81))
    private val type = FightStepType(FightTargetCatalog(listOf(rats, cows)))
    private val walkedTo = WorkSpot.At(Tile(3105, 9517))

    private fun fight(vararg values: Pair<String, String>) = StepSettings("fight", mapOf(*values))

    private fun field(index: Int) = type.fields[index] as StepField.Choice

    @Test
    fun `a fight step reads as its npc, its defaults left out`() {
        assertEquals("fight cow", type.summary(fight("npc" to "cow", "within" to "10", "eatBelow" to "50")))
    }

    @Test
    fun `a fight step writes every setting that is not the default`() {
        val settings = fight("npc" to "giant rat", "amount" to "3", "within" to "5", "eatBelow" to "75")

        assertEquals("fight 3 giant rat within 5 eat below 75%", type.summary(settings))
    }

    @Test
    fun `a fight step without an npc reads with a question mark and is rejected`() {
        assertEquals("fight ?", type.summary(fight()))
        assertRejected("fight needs an npc") { type.resolve(fight(), FlowContext()) }
    }

    @Test
    fun `a fight step fights around the work spot, whatever the case`() {
        val step = type.resolve(fight("npc" to "Giant Rat", "amount" to "3", "within" to "15", "eatBelow" to "75"), FlowContext(workSpot = walkedTo))

        assertEquals(FightStep(rats, 15, walkedTo, amount = 3, eatBelow = 75), step)
    }

    @Test
    fun `a fight step without an amount fights nonstop and eats below half`() {
        val step = type.resolve(fight("npc" to "cow"), FlowContext()) as FightStep

        assertNull(step.amount)
        assertEquals(50, step.eatBelow)
    }

    @Test
    fun `when to eat is a share of hitpoints from 1 to 99 percent`() {
        assertRejected("eat below takes a share of your hitpoints from 1 to 99 percent, not 'half'") { type.resolve(fight("npc" to "cow", "eatBelow" to "half"), FlowContext()) }
        assertRejected("eat below takes a share of your hitpoints from 1 to 99 percent, not '0'") { type.resolve(fight("npc" to "cow", "eatBelow" to "0"), FlowContext()) }
        assertRejected("eat below takes a share of your hitpoints from 1 to 99 percent, not '100'") { type.resolve(fight("npc" to "cow", "eatBelow" to "100"), FlowContext()) }
    }

    @Test
    fun `an npc nobody can fight yet lists the ones they can`() {
        assertRejected("'goblin' is not something you can fight yet. Fight: cow, giant rat") {
            type.resolve(fight("npc" to "goblin"), FlowContext())
        }
    }

    @Test
    fun `the builder offers the npcs of the catalog`() {
        assertEquals(listOf("giant rat", "cow"), field(0).choices(fight()))
    }

    @Test
    fun `the builder offers nonstop and a few amounts`() {
        assertEquals(listOf("", "1", "5", "10"), field(1).choices(fight()))
        assertEquals("nonstop", field(1).display(""))
    }

    @Test
    fun `the builder offers three shares to eat below, shown as percentages`() {
        assertEquals(listOf("25", "50", "75"), field(3).choices(fight()))
        assertEquals("75%", field(3).display("75"))
    }

    @Test
    fun `a new fight step eats below half`() {
        assertEquals("50", field(3).default)
    }

    @Test
    fun `a fight step tells later steps nothing new`() {
        val context = FlowContext(walkedTo, gathered = setOf(526))

        assertEquals(context, FightStep(rats, 10, walkedTo).after(context))
    }

    private fun assertRejected(message: String, action: () -> Unit) {
        assertEquals(message, assertThrows<FlowError> { action() }.message)
    }
}
