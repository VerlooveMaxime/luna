package game.idle.flow

import engine.combat.prayer.CombatPrayer
import game.idle.flow.FakeStepType.Companion.step
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class ReflexResolverTest {

    private val trout = 333
    private val lobster = 379
    private val logs = 1511
    private val cake = 1891
    private val twoThirdsCake = 1893
    private val resolver = ReflexResolver(portions = mapOf(trout to setOf(trout), lobster to setOf(lobster), cake to setOf(cake, twoThirdsCake)), doses = mapOf(113 to listOf(119, 117, 115, 113), 2440 to listOf(161, 159, 157, 2440)))
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
    fun `a reflex fires under attack when told so`() {
        assertEquals(ReflexTrigger.UnderAttack, resolvedAlone("when" to "attacked", "below" to "0").trigger)
    }

    @Test
    fun `a reflex fires on hitpoints when told so`() {
        assertEquals(ReflexTrigger.HitpointsBelow(40), resolvedAlone("when" to "hitpoints", "below" to "40").trigger)
    }

    @Test
    fun `a reflex fires only on what reflexes watch`() {
        assertEquals("'poison' is not a choice for When", problemOf("when" to "poison"))
    }

    @Test
    fun `a stop reflex stops the flow`() {
        assertEquals(ReflexAction.StopFlow, resolvedAlone("do" to "stop").action)
    }

    @Test
    fun `a jump follows its step to wherever it stands`() {
        assertEquals(ReflexAction.JumpTo(1), resolvedAlone("do" to "jump", "step" to "3").action)
    }

    @Test
    fun `each step gets its attached reflexes in its own order, numbered as the flow lists them`() {
        val reflexes = listOf(reflex(1, "below" to "40"), reflex(2, "do" to "stop"))

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

        assertEquals("Reflex 2: Below takes 1 to 99 percent of hitpoints, not '0'", error.message)
    }

    @Test
    fun `below takes 1 to 99 percent`() {
        assertEquals(
            listOf("Below takes 1 to 99 percent of hitpoints, not 'half'", "Below takes 1 to 99 percent of hitpoints, not '100'", null),
            listOf(problemOf("below" to "half"), problemOf("below" to "100"), problemOf("below" to "99")),
        )
    }

    @Test
    fun `a reflex does only what reflexes do`() {
        assertEquals("'teleport' is not something a reflex does", problemOf("do" to "teleport"))
    }

    @Test
    fun `an eat reflex eats only food`() {
        assertEquals("'1511' is not food", problemOf("foods" to "333,$logs"))
    }

    @Test
    fun `a jump needs its step picked`() {
        assertEquals("Needs a step", problemOf("do" to "jump"))
    }

    @Test
    fun `a jump to a step that was deleted cannot work`() {
        assertEquals("Jumps to a step that was deleted", problemOf("do" to "jump", "step" to "9"))
    }

    @Test
    fun `a reflex reads its settings by key`() {
        assertEquals(listOf("stop", null), listOf(reflex(1, "do" to "stop")["do"], ReflexSettings()["do"]))
    }

    @Test
    fun `a skill reflex reads a named skill and a level separate from hitpoints`() {
        assertEquals(ReflexTrigger.SkillBelow(2, 25), resolvedAlone("when" to "skill", "skillName" to "STRENGTH", "skillBelow" to "25", "below" to "0").trigger)
    }

    @Test
    fun `a skill reflex needs a skill picked`() {
        assertEquals("Needs a skill", problemOf("when" to "skill"))
    }

    @Test
    fun `an unknown skill is refused`() {
        assertEquals("'flying' is not a skill", problemOf("when" to "skill", "skillName" to "flying"))
    }

    @Test
    fun `hitpoints stays in its own percentage condition`() {
        assertEquals("Use When Hitpoints for hitpoints", problemOf("when" to "skill", "skillName" to "hitpoints"))
    }

    @Test
    fun `a skill condition needs its Below level`() {
        assertEquals("Needs a Below level", problemOf("when" to "skill", "skillName" to "prayer"))
    }

    @ParameterizedTest
    @ValueSource(strings = ["0", "100", "low"])
    fun `a skill Below value is a level from 1 to 99`(value: String) {
        assertEquals("Below takes a level from 1 to 99, not '$value'", problemOf("when" to "skill", "skillName" to "prayer", "skillBelow" to value))
    }

    @ParameterizedTest
    @ValueSource(strings = ["1", "99"])
    fun `the endpoints of the skill Below range are accepted`(value: String) {
        assertEquals(null, problemOf("when" to "skill", "skillName" to "prayer", "skillBelow" to value))
    }

    @Test
    fun `a pray reflex keeps the prayers it lists by their tab buttons, in list order`() {
        assertEquals(ReflexAction.Pray(listOf(CombatPrayer.PROTECT_FROM_MELEE, CombatPrayer.THICK_SKIN)), resolvedAlone("do" to "pray", "prayers" to "5623,5609").action)
    }

    @Test
    fun `a pray reflex needs a prayer picked`() {
        assertEquals("Needs a prayer", problemOf("do" to "pray"))
    }

    @Test
    fun `a pray reflex refuses a key that is no prayer`() {
        assertEquals("'1511' is not a prayer", problemOf("do" to "pray", "prayers" to "5609,$logs"))
    }

    @Test
    fun `two prayers of one group cannot be on together`() {
        assertEquals("Thick skin and Rock skin cannot be on together", problemOf("do" to "pray", "prayers" to "5609,5610,5612"))
    }

    @Test
    fun `a drink reflex resolves every dose in potion list order and lowest dose first`() {
        assertEquals(ReflexAction.Drink(listOf(161, 159, 157, 2440, 119, 117, 115, 113)), resolvedAlone("do" to "drink", "potions" to "2440,113").action)
    }

    @Test
    fun `a drink reflex needs a potion picked`() {
        assertEquals("Needs a potion", problemOf("do" to "drink"))
    }

    @Test
    fun `a drink reflex refuses items outside the potion list`() {
        assertEquals("'1511' is not a potion", problemOf("do" to "drink", "potions" to "1511"))
    }

}
