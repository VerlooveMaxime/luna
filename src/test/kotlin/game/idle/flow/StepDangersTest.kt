package game.idle.flow

import game.idle.danger.Aggression
import game.idle.danger.AggressiveSpawn
import game.idle.danger.DangerCatalog
import game.idle.danger.Threat
import game.idle.flow.FakeStepType.Companion.step
import game.idle.flow.option.FakeNames
import game.idle.flow.option.OptionFacts
import game.idle.location.Area
import game.idle.location.Tile
import io.luna.game.model.mob.Player
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class StepDangersTest {

    private val willows = Tile(3087, 3229)
    private val edgeville = Tile(3101, 3509)
    private val darkWizard = Threat("Dark wizard", 7, Aggression.BelowCombat(15))
    private val scorpion = Threat("Scorpion", 14, Aggression.Always)
    private val catalog = DangerCatalog(
        listOf(
            AggressiveSpawn("Dark wizard", 7, Tile(3084, 3236), reach = 10, darkWizard.aggression),
            AggressiveSpawn("Scorpion", 14, Tile(3101, 3519), reach = 10, Aggression.Always),
        ),
    )

    /** A step working at its own spot when one is picked, as the kinds with a work spot do. */
    private class SpottedStep(private val settings: StepSettings) : ResolvedStep {

        override fun after(context: FlowContext): FlowContext = StepSpot.of(settings, context).after(context)

        override fun work(player: Player, runTile: Tile): StepWork = FakeStepWork(StepOutcome.Finished)
    }

    /** A step that takes the flow to [tile], as a walk step does. */
    private class GoingTo(private val tile: Tile) : ResolvedStep {

        override fun after(context: FlowContext): FlowContext = context.copy(workSpot = WorkSpot.At(tile))

        override fun work(player: Player, runTile: Tile): StepWork = FakeStepWork(StepOutcome.Finished)
    }

    private val types = StepTypes(
        listOf(
            FakeStepType("chop", listOf(StepSpot.field(), StepRadius.field()), resolved = { StepSpot.read(it); SpottedStep(it) }),
            FakeStepType("rest"),
            FakeStepType("walk", listOf(StepField.MapTile("tile", "Tile")), resolved = { GoingTo(TileText.read(it["tile"].orEmpty(), "Tile")) }),
        ),
    )
    private val dangers = StepDangers(FlowResolver(types), catalog, FakeNames())

    private val newCharacter = OptionFacts(here = edgeville)

    private fun chop(vararg values: Pair<String, String>) = StepSettings("chop", values.toMap())

    @Test
    fun `a step with Within works that far around its own spot`() {
        assertEquals(Area(willows, 5), dangers.area(listOf(chop("spot" to "3087 3229", "within" to "5")), 0, newCharacter))
    }

    @Test
    fun `without a Within set the step works the default distance around`() {
        assertEquals(Area(willows, StepRadius.DEFAULT), dangers.area(listOf(chop("spot" to "3087 3229")), 0, newCharacter))
    }

    @Test
    fun `a step from the flow works where the flow went before it`() {
        val steps = listOf(StepSettings("walk", mapOf("tile" to "3087 3229")), chop("within" to "3"))

        assertEquals(Area(willows, 3), dangers.area(steps, 1, newCharacter))
    }

    @Test
    fun `a step from the flow with nothing before it works where the player stands`() {
        assertEquals(Area(edgeville, StepRadius.DEFAULT), dangers.area(listOf(chop()), 0, newCharacter))
    }

    @Test
    fun `a step from the flow with nothing before it and no player tile works nowhere known`() {
        assertNull(dangers.area(listOf(chop()), 0, OptionFacts()))
    }

    @Test
    fun `a step without Within works on its tile alone`() {
        assertEquals(Area(willows, 0), dangers.area(listOf(StepSettings("walk", mapOf("tile" to "3087 3229")), step("rest")), 1, newCharacter))
    }

    @Test
    fun `a walk step works on the tile it goes to`() {
        assertEquals(Area(willows, 0), dangers.area(listOf(StepSettings("walk", mapOf("tile" to "3087 3229"))), 0, newCharacter))
    }

    @Test
    fun `a step that cannot work works nowhere, its reason shows instead`() {
        assertNull(dangers.area(listOf(chop("spot" to "nowhere")), 0, newCharacter))
    }

    @Test
    fun `a step of a kind that is none works nowhere`() {
        assertNull(dangers.area(listOf(StepSettings("dig")), 0, newCharacter))
    }

    @Test
    fun `the threats are the npcs in reach of where the step works that attack the player`() {
        assertEquals(listOf(darkWizard), dangers.of(listOf(chop("spot" to "3087 3229")), 0, newCharacter))
    }

    @Test
    fun `an npc a player's combat level is safe from is no threat to them`() {
        assertEquals(emptyList<Threat>(), dangers.of(listOf(chop("spot" to "3087 3229")), 0, newCharacter.copy(combat = 15)))
    }

    @Test
    fun `an npc attacking any level threatens the strongest player`() {
        assertEquals(listOf(scorpion), dangers.of(listOf(chop()), 0, newCharacter.copy(combat = 126)))
    }

    @Test
    fun `a step working nowhere known has no threats`() {
        assertEquals(emptyList<Threat>(), dangers.of(listOf(chop()), 0, OptionFacts()))
    }
}
