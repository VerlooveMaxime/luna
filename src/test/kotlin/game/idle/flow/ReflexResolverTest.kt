package game.idle.flow

import game.idle.flow.FakeStepType.Companion.step
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ReflexResolverTest {

    private val trout = 333
    private val lobster = 379
    private val logs = 1511
    private val cake = 1891
    private val twoThirdsCake = 1893
    private val resolver = ReflexResolver(portions = mapOf(trout to setOf(trout), lobster to setOf(lobster), cake to setOf(cake, twoThirdsCake)))
    private val fight = step("fight").copy(id = 7, reflexes = listOf(2, 1))
    private val bank = step("bank").copy(id = 3)

    private fun reflex(id: Int, vararg values: Pair<String, String>) = ReflexSettings(id, mapOf(*values))

    private fun resolvedAlone(vararg values: Pair<String, String>): ResolvedReflex {
        val steps = listOf(fight.copy(reflexes = listOf(1)), bank)
        return resolver.resolve(steps, listOf(reflex(1, *values))).first().single()
    }

    private fun problemOf(vararg values: Pair<String, String>): String? = resolver.problems(listOf(fight, bank), listOf(reflex(1, *values))).single()

    @Test
    fun `a reflex with no settings eats any food below half hitpoints`() {
        assertEquals(ResolvedReflex(1, ReflexTrigger.HitpointsBelow(50), ReflexAction.Eat(emptySet())), resolvedAlone())
    }

    @Test
    fun `an eat reflex eats the foods it lists`() {
        assertEquals(ReflexAction.Eat(setOf(lobster, trout)), resolvedAlone("do" to "eat", "foods" to "379,333").action)
    }

    @Test
    fun `an eat reflex eats every portion of the foods it lists`() {
        assertEquals(ReflexAction.Eat(setOf(cake, twoThirdsCake)), resolvedAlone("foods" to "$cake").action)
    }

    @Test
    fun `a reflex fires below the share of hitpoints it holds`() {
        assertEquals(ReflexTrigger.HitpointsBelow(30), resolvedAlone("below" to "30").trigger)
    }

    @Test
    fun `a run away reflex stops the flow unless told otherwise`() {
        assertEquals(ReflexAction.RunAway(ReflexThen.StopFlow), resolvedAlone("do" to "run").action)
    }

    @Test
    fun `a jump follows its step to wherever it stands`() {
        assertEquals(ReflexAction.RunAway(ReflexThen.JumpTo(1)), resolvedAlone("do" to "run", "then" to "jump", "step" to "3").action)
    }

    @Test
    fun `each step gets its attached reflexes in its own order, numbered as the flow lists them`() {
        val reflexes = listOf(reflex(1, "below" to "40"), reflex(2, "do" to "run"))

        val resolved = resolver.resolve(listOf(fight, bank), reflexes)

        assertEquals(listOf(listOf(2, 1), emptyList()), resolved.map { step -> step.map { it.number } })
    }

    @Test
    fun `a step attached to a reflex the flow does not hold is refused`() {
        val error = assertThrows<FlowError> { resolver.resolve(listOf(bank, fight), listOf(reflex(1))) }

        assertEquals("Step 2: attached to a reflex the flow does not hold", error.message)
    }

    @Test
    fun `a reflex that cannot work is refused with its number, attached or not`() {
        val reflexes = listOf(reflex(1), reflex(2, "below" to "0"))

        val error = assertThrows<FlowError> { resolver.resolve(listOf(bank), reflexes) }

        assertEquals("Reflex 2: hitpoints below takes 1 to 99 percent, not '0'", error.message)
    }

    @Test
    fun `hitpoints below takes 1 to 99 percent`() {
        assertEquals(
            listOf("hitpoints below takes 1 to 99 percent, not 'half'", "hitpoints below takes 1 to 99 percent, not '100'", null),
            listOf(problemOf("below" to "half"), problemOf("below" to "100"), problemOf("below" to "99")),
        )
    }

    @Test
    fun `a reflex does only what reflexes do`() {
        assertEquals("'pray' is not something a reflex does", problemOf("do" to "pray"))
    }

    @Test
    fun `an eat reflex eats only food`() {
        assertEquals("item 1511 is not something you can eat", problemOf("foods" to "333,$logs"))
    }

    @Test
    fun `a run away reflex stops or jumps afterwards, nothing else`() {
        assertEquals("'rest' is not what a reflex does after running away", problemOf("do" to "run", "then" to "rest"))
    }

    @Test
    fun `a jump needs its step picked`() {
        assertEquals("pick the step to jump to", problemOf("do" to "run", "then" to "jump"))
    }

    @Test
    fun `a jump to a step that was deleted cannot work`() {
        assertEquals("jumps to a step that was deleted", problemOf("do" to "run", "then" to "jump", "step" to "9"))
    }

    @Test
    fun `a reflex reads its settings by key`() {
        assertEquals(listOf("run", null), listOf(reflex(1, "do" to "run")["do"], ReflexSettings()["do"]))
    }
}
