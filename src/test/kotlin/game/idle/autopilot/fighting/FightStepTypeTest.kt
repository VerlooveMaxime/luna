package game.idle.autopilot.fighting

import game.idle.flow.FlowContext
import game.idle.flow.FlowError
import game.idle.flow.WorkSpot
import game.idle.location.Tile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class FightStepTypeTest {

    private val rats = FightTarget("giant rat", setOf(950))
    private val cows = FightTarget("cow", setOf(81))
    private val type = FightStepType(FightTargetCatalog(listOf(rats, cows)))
    private val walkedTo = WorkSpot.At(Tile(3105, 9517))
    private val usage = "fight [<n>] <npc> [within <r>] [eat below <p>%]"

    @Test
    fun `a fight line names its npc, fights nonstop within the default radius and eats below half`() {
        assertEquals(listOf("cow", "nonstop", "10", "50%"), type.parse(listOf("cow")))
    }

    @Test
    fun `an npc's name may take several words`() {
        assertEquals("giant rat", type.parse(listOf("giant", "rat"))[0])
    }

    @Test
    fun `a fight line may start with a count`() {
        assertEquals("3", type.parse(listOf("3", "cow"))[1])
    }

    @Test
    fun `a fight line may give a radius`() {
        assertEquals("5", type.parse(listOf("giant", "rat", "within", "5"))[2])
    }

    @Test
    fun `a fight line may say when to eat`() {
        assertEquals("75%", type.parse(listOf("giant", "rat", "eat", "below", "75%"))[3])
    }

    @Test
    fun `a fight line may give a radius and when to eat`() {
        assertEquals(listOf("cow", "1", "5", "25%"), type.parse("1 cow within 5 eat below 25%".split(" ")))
    }

    @Test
    fun `fight without an npc`() {
        assertRejected("fight needs an npc: $usage") { type.parse(listOf("2")) }
    }

    @Test
    fun `fight with only a radius has no npc`() {
        assertRejected("fight needs an npc: $usage") { type.parse(listOf("within", "5")) }
    }

    @Test
    fun `a radius after when to eat is rejected`() {
        assertRejected("Unexpected 'eat below 50% within 5' in the fight step: $usage") { type.parse("cow eat below 50% within 5".split(" ")) }
    }

    @Test
    fun `eat must be followed by below`() {
        assertRejected("Unexpected 'eat at 50%' in the fight step: $usage") { type.parse("cow eat at 50%".split(" ")) }
    }

    @Test
    fun `when to eat starts with eat`() {
        assertRejected("Unexpected 'drink below 50%' in the fight step: $usage") { EatBelow.parse("drink below 50%".split(" "), usage) }
    }

    @Test
    fun `when to eat is a share with a percent sign`() {
        assertRejected("eat below takes a share of your hitpoints from 1% to 99%, not '50'") { type.parse("cow eat below 50".split(" ")) }
    }

    @Test
    fun `when to eat is a number`() {
        assertRejected("eat below takes a share of your hitpoints from 1% to 99%, not 'half%'") { type.parse("cow eat below half%".split(" ")) }
    }

    @Test
    fun `when to eat is at least 1 percent`() {
        assertRejected("eat below takes a share of your hitpoints from 1% to 99%, not '0%'") { type.parse("cow eat below 0%".split(" ")) }
    }

    @Test
    fun `when to eat is below 100 percent`() {
        assertRejected("eat below takes a share of your hitpoints from 1% to 99%, not '100%'") { type.parse("cow eat below 100%".split(" ")) }
    }

    @Test
    fun `a line leaves out the defaults`() {
        assertEquals("fight cow", type.line(listOf("cow", "nonstop", "10", "50%")))
    }

    @Test
    fun `a line writes every value that is not the default`() {
        assertEquals("fight 3 giant rat within 5 eat below 75%", type.line(listOf("giant rat", "3", "5", "75%")))
    }

    @Test
    fun `a fight step fights around the work spot, whatever the case`() {
        val step = type.resolve(listOf("Giant Rat", "3", "15", "75%"), FlowContext(workSpot = walkedTo))

        assertEquals(FightStep(rats, 15, walkedTo, amount = 3, eatBelow = 75), step)
    }

    @Test
    fun `a nonstop fight has no amount`() {
        assertEquals(null, (type.resolve(listOf("cow", "nonstop", "10", "50%"), FlowContext()) as FightStep).amount)
    }

    @Test
    fun `an npc nobody can fight yet lists the ones they can`() {
        assertRejected("'goblin' is not something you can fight yet. Fight: cow, giant rat") {
            type.resolve(listOf("goblin", "nonstop", "10", "50%"), FlowContext())
        }
    }

    @Test
    fun `the builder offers the npcs of the catalog`() {
        assertEquals(listOf("giant rat", "cow"), type.fields[0].choices(emptyList()))
    }

    @Test
    fun `the builder offers nonstop and a few amounts`() {
        assertEquals(listOf("nonstop", "1", "5", "10"), type.fields[1].choices(emptyList()))
    }

    @Test
    fun `the builder offers three shares to eat below`() {
        assertEquals(listOf("25%", "50%", "75%"), type.fields[3].choices(emptyList()))
    }

    @Test
    fun `a new fight step eats below half`() {
        assertEquals("50%", type.fields[3].default)
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
