package game.idle.ui

import game.idle.IdleState
import game.idle.flow.FakeStepType.Companion.step
import game.idle.flow.FlowResolver
import game.idle.flow.ReflexSettings
import game.idle.flow.StepField
import game.idle.flow.StepSettings
import game.idle.flow.option.FakeNames
import game.idle.flow.option.OptionFacts
import io.luna.game.model.mob.Skill
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class BuilderConfigureTest {

    /** Five pixels a glyph: a field holds 20 characters, a note 32, a warning line 98. */
    private val font = ClientFont(IntArray(256) { 5 })
    private val configure = BuilderConfigure(FlowResolver(CONFIGURED_TYPES), REFLEXES, REFLEX_FORM, FakeNames(), font)

    private val oak = step("chop", "oak")
    private val facts = OptionFacts(levels = mapOf(Skill.WOODCUTTING to 15))

    private fun updates(draft: StepDraft, state: IdleState = IdleState(steps = listOf(oak))): Map<Int, WidgetUpdate> =
        configure.updates(state, draft, facts).associateBy {
            when (it) {
                is WidgetUpdate.Text -> it.id
                is WidgetUpdate.Picture -> it.id
                is WidgetUpdate.Visible -> it.id
                is WidgetUpdate.Colour -> it.id
                is WidgetUpdate.Placement -> placementKey(it.list)
            }
        }

    private fun text(draft: StepDraft, id: Int, state: IdleState = IdleState(steps = listOf(oak))): String =
        (updates(draft, state).getValue(id) as WidgetUpdate.Text).text

    private fun visible(draft: StepDraft, id: Int): Boolean = (updates(draft).getValue(id) as WidgetUpdate.Visible).visible

    private fun picture(draft: StepDraft, id: Int): WidgetPicture = (updates(draft).getValue(id) as WidgetUpdate.Picture).picture

    private fun colour(draft: StepDraft, id: Int): Int = (updates(draft).getValue(id) as WidgetUpdate.Colour).rgb

    private val chopping = StepDraft(0, oak, new = false)
    private val nothingPicked = StepDraft(0, step("chop"), new = true)
    private val walking = StepDraft(1, step("walk"), new = true)

    @Test
    fun `the header shows the target's picture with the kind's icon in the corner`() {
        assertEquals(
            listOf(WidgetPicture.Item(1521), WidgetPicture.Media("chop", 0), true),
            listOf(picture(chopping, BuilderWidgets.HEADER_PICTURE), picture(chopping, BuilderWidgets.HEADER_CORNER), visible(chopping, BuilderWidgets.HEADER_CORNER_LAYER)),
        )
    }

    @Test
    fun `the header shows the kind's icon alone when nothing is picked`() {
        assertEquals(
            listOf(WidgetPicture.Media("chop", 0), false),
            listOf(picture(nothingPicked, BuilderWidgets.HEADER_PICTURE), visible(nothingPicked, BuilderWidgets.HEADER_CORNER_LAYER)),
        )
    }

    @Test
    fun `the header names the step and the level of its skill`() {
        assertEquals("Step 1: Chop label @gry@(Woodcutting 15)", text(chopping, BuilderWidgets.HEADER_NAME))
    }

    @Test
    fun `a kind without a skill shows no level`() {
        assertEquals("Step 2: Walk label", text(walking, BuilderWidgets.HEADER_NAME))
    }

    @Test
    fun `the header says what the kind does`() {
        assertEquals("Does chop things.", text(chopping, BuilderWidgets.HEADER_DESCRIPTION))
    }

    @Test
    fun `a step of a kind that is gone shows its kind and nothing else`() {
        val gone = StepDraft(0, StepSettings("gone"), new = false)

        assertEquals(
            listOf<Any>("Step 1: Gone", "", WidgetPicture.None, false),
            listOf(
                text(gone, BuilderWidgets.HEADER_NAME),
                text(gone, BuilderWidgets.HEADER_DESCRIPTION),
                picture(gone, BuilderWidgets.HEADER_PICTURE),
                visible(gone, BuilderWidgets.row(0)),
            ),
        )
    }

    @Test
    fun `a search row shows its label and the picked option's picture and label`() {
        assertEquals(
            listOf<Any>(true, "Tree", true, WidgetPicture.Item(1521), "Oak", "", false, ""),
            listOf(
                visible(chopping, BuilderWidgets.row(0)),
                text(chopping, BuilderWidgets.rowLabel(0)),
                visible(chopping, BuilderWidgets.rowField(0)),
                picture(chopping, BuilderWidgets.rowPicture(0)),
                text(chopping, BuilderWidgets.rowText(0)),
                text(chopping, BuilderWidgets.rowPlainText(0)),
                visible(chopping, BuilderWidgets.rowButton(0)),
                text(chopping, BuilderWidgets.rowNote(0)),
            ),
        )
    }

    @Test
    fun `a search with nothing picked invites a search`() {
        assertEquals(
            listOf<Any>("@gry@Search...", WidgetPicture.None),
            listOf(text(nothingPicked, BuilderWidgets.rowText(0)), picture(nothingPicked, BuilderWidgets.rowPicture(0))),
        )
    }

    @Test
    fun `a search value that is no option shows as it is kept, fitted to the field`() {
        assertEquals("a tree nobody ever..", text(StepDraft(0, step("chop", "a tree nobody ever saw"), new = false), BuilderWidgets.rowText(0)))
    }

    @Test
    fun `a typed row shows its value and the button that removes it`() {
        val draft = chopping.with("amount", "5")

        assertEquals(
            listOf<Any>("5 per lap", "", true, "Full"),
            listOf(
                text(draft, BuilderWidgets.rowPlainText(1)),
                text(draft, BuilderWidgets.rowText(1)),
                visible(draft, BuilderWidgets.rowButton(1)),
                text(draft, BuilderWidgets.rowButtonText(1)),
            ),
        )
    }

    @Test
    fun `a typed row without such a button hides it`() {
        assertEquals(listOf<Any>("10 tiles", false), listOf(text(chopping, BuilderWidgets.rowPlainText(6)), visible(chopping, BuilderWidgets.rowButton(6))))
    }

    @Test
    fun `the field being typed is framed yellow, the others keep their edge`() {
        val typing = chopping.copy(typing = Typing("amount"))

        assertEquals(
            listOf(BuilderWidgets.TYPING, BuilderWidgets.FIELD_EDGE, BuilderWidgets.FIELD_EDGE),
            listOf(colour(typing, BuilderWidgets.rowFrame(1)), colour(typing, BuilderWidgets.rowFrame(6)), colour(typing, BuilderWidgets.rowFrame(0))),
        )
    }

    @Test
    fun `a note row hides its field and shows the note, worked out from the steps before`() {
        val draft = StepDraft(1, step("chop"), new = true)
        val state = IdleState(steps = listOf(step("chop", "gather")))

        assertEquals(
            listOf<Any>(false, "1 items before it"),
            listOf((updates(draft, state).getValue(BuilderWidgets.rowField(2)) as WidgetUpdate.Visible).visible, text(draft, BuilderWidgets.rowNote(2), state)),
        )
    }

    @Test
    fun `a tile row shows the tile picked`() {
        assertEquals("3086, 3233", text(walking.with("tile", "3086 3233"), BuilderWidgets.rowPlainText(0)))
    }

    @Test
    fun `a tile row without a tile invites a pick on the map`() {
        assertEquals("@gry@Pick on the world map", text(walking, BuilderWidgets.rowPlainText(0)))
    }

    @Test
    fun `a row the kind has no setting for is hidden, its contents left alone`() {
        assertEquals(listOf(false, false), listOf(visible(chopping, BuilderWidgets.row(3)), updates(chopping).containsKey(BuilderWidgets.rowLabel(3))))
    }

    @Test
    fun `the warnings say why the step as edited cannot work`() {
        val bad = StepDraft(0, step("chop", "bad"), new = false)

        assertEquals(listOf("@red@! 'bad' is refused", "", ""), (0 until 3).map { text(bad, BuilderWidgets.warning(it)) })
    }

    @Test
    fun `a step that can work has no warnings`() {
        assertEquals(listOf("@gre@No warnings.", "", ""), (0 until 3).map { text(chopping, BuilderWidgets.warning(it)) })
    }

    @Test
    fun `the running step's block is its warning`() {
        val state = IdleState(steps = listOf(oak), running = true, blocked = "Autopilot: you need an axe.")

        assertEquals("@red@! You need an axe.", text(chopping, BuilderWidgets.warning(0), state))
    }

    @Test
    fun `another step's block is not this one's warning`() {
        val state = IdleState(steps = listOf(oak, oak), running = true, stepIndex = 1, blocked = "Autopilot: you need an axe.")

        assertEquals("@gre@No warnings.", text(chopping, BuilderWidgets.warning(0), state))
    }

    @Test
    fun `a block seen while the flow is stopped is no warning`() {
        val state = IdleState(steps = listOf(oak), blocked = "Autopilot: you need an axe.")

        assertEquals("@gre@No warnings.", text(chopping, BuilderWidgets.warning(0), state))
    }

    @Test
    fun `a new step does not take the block of the step it would follow`() {
        val state = IdleState(steps = listOf(oak), running = true, blocked = "Autopilot: you need an axe.")

        assertEquals("@gre@No warnings.", text(StepDraft(0, oak, new = true), BuilderWidgets.warning(0), state))
    }

    @Test
    fun `a saved step can be deleted and saved`() {
        assertEquals(listOf("Delete", "Back", "Save"), listOf(BuilderWidgets.DELETE, BuilderWidgets.BACK, BuilderWidgets.SAVE).map { text(chopping, it) })
    }

    @Test
    fun `a new step cannot be deleted`() {
        assertEquals("@gry@Delete", text(nothingPicked, BuilderWidgets.DELETE))
    }

    @Test
    fun `nothing is deleted or saved while the flow runs`() {
        val state = IdleState(steps = listOf(oak), running = true)

        assertEquals(listOf("@gry@Delete", "@gry@Save"), listOf(text(chopping, BuilderWidgets.DELETE, state), text(chopping, BuilderWidgets.SAVE, state)))
    }

    @Test
    fun `a draft changed keeps its step and stops typing`() {
        assertEquals(StepDraft(0, step("chop", "willow"), new = false), chopping.copy(typing = Typing("amount")).with("word", "willow"))
    }

    @Test
    fun `a draft set to nothing drops the setting`() {
        assertEquals(StepSettings("chop"), chopping.with("word", "").settings)
    }

    @Test
    fun `a draft stops typing`() {
        assertEquals(chopping, chopping.copy(typing = Typing("amount")).notTyping())
    }

    @Test
    fun `a draft edited since is the same step`() {
        assertTrue(chopping.sameStep(chopping.with("amount", "5")))
    }

    @Test
    fun `a draft of another slot, another kind or a new one is another step`() {
        assertFalse(chopping.sameStep(chopping.copy(slot = 1)))
        assertFalse(chopping.sameStep(chopping.copy(new = true)))
        assertFalse(chopping.sameStep(StepDraft(0, step("drop"), new = false)))
        assertFalse(chopping.sameStep(chopping.copy(subject = DraftSubject.REFLEX)))
    }

    @Test
    fun `fields fill the left column's rows, then the right's`() {
        assertEquals(mapOf(0 to TREE_SEARCH, 1 to AMOUNT, 2 to INPUT_NOTE, 6 to WITHIN), ConfigureRows.of(listOf(TREE_SEARCH, WITHIN, AMOUNT, INPUT_NOTE)))
    }

    @Test
    fun `a column holds six rows at most`() {
        assertThrows<IllegalArgumentException> { ConfigureRows.of(List(7) { TREE_SEARCH }) }
        assertThrows<IllegalArgumentException> { ConfigureRows.of(List(7) { WITHIN }) }
    }

    private val lighting = StepDraft(1, StepSettings("light", mapOf("input" to "earlier", "logs" to "1511,1521")), new = false)
    private val banking = StepDraft(1, StepSettings("bank", mapOf("deposit" to "chosen")), new = false)

    @Test
    fun `a toggle row shows its two buttons in the field's place, the chosen one lit`() {
        assertEquals(
            listOf<Any>(false, false, true, false, "@whi@Earlier steps", "The bank", BuilderWidgets.LIT, BuilderWidgets.FIELD_EDGE),
            listOf(
                visible(lighting, BuilderWidgets.rowField(0)),
                visible(lighting, BuilderWidgets.rowButton(0)),
                visible(lighting, BuilderWidgets.toggles(0, 2)),
                visible(lighting, BuilderWidgets.toggles(0, 3)),
                text(lighting, BuilderWidgets.toggleText(0, 2, 0)),
                text(lighting, BuilderWidgets.toggleText(0, 2, 1)),
                colour(lighting, BuilderWidgets.toggleFrame(0, 2, 0)),
                colour(lighting, BuilderWidgets.toggleFrame(0, 2, 1)),
            ),
        )
    }

    @Test
    fun `a toggle of three shows the narrower buttons`() {
        assertEquals(
            listOf<Any>(false, true, "Everything", "@whi@Chosen"),
            listOf(
                visible(banking, BuilderWidgets.toggles(6, 2)),
                visible(banking, BuilderWidgets.toggles(6, 3)),
                text(banking, BuilderWidgets.toggleText(6, 3, 0)),
                text(banking, BuilderWidgets.toggleText(6, 3, 2)),
            ),
        )
    }

    @Test
    fun `a row of another sort hides both toggles`() {
        assertEquals(listOf(false, false), listOf(visible(chopping, BuilderWidgets.toggles(0, 2)), visible(chopping, BuilderWidgets.toggles(0, 3))))
    }

    @Test
    fun `a list's first row shows its label alone, the list drawn over the field's place`() {
        assertEquals(
            listOf<Any>(true, "Logs", false, false),
            listOf(
                visible(lighting, BuilderWidgets.row(2)),
                text(lighting, BuilderWidgets.rowLabel(2)),
                visible(lighting, BuilderWidgets.rowField(2)),
                visible(lighting, BuilderWidgets.rowButton(2)),
            ),
        )
    }

    @Test
    fun `the rows under a list hide`() {
        assertEquals(listOf(false, false, false), (3..5).map { visible(lighting, BuilderWidgets.row(it)) })
    }

    @Test
    fun `a list is placed on its rows, a line to add or remove then one per item`() {
        assertEquals(
            listOf<Any>(true, WidgetUpdate.Placement(0, firstRow = 2, rows = 4, lines = 3), "+ Add or remove logs..."),
            listOf(visible(lighting, BuilderWidgets.list(0)), updates(lighting, lightFlow).getValue(placementKey(0)), text(lighting, BuilderWidgets.listAddText(0))),
        )
    }

    @Test
    fun `a list's line shows its item's picture and name, without an amount`() {
        assertEquals(
            listOf<Any>(WidgetPicture.Item(1521), "item 1521", false),
            listOf(
                picture(lighting, BuilderWidgets.linePicture(0, 1)),
                text(lighting, BuilderWidgets.lineName(0, 1)),
                visible(lighting, BuilderWidgets.lineAmount(0, 1)),
            ),
        )
    }

    @Test
    fun `lines past the list's items are left alone`() {
        assertFalse(updates(lighting, lightFlow).containsKey(BuilderWidgets.lineName(0, 2)))
    }

    @Test
    fun `a column without a list hides its list`() {
        assertEquals(listOf(false, false), listOf(visible(lighting, BuilderWidgets.list(1)), visible(banking, BuilderWidgets.list(0))))
    }

    @Test
    fun `a list on the right column is placed from its own column's rows`() {
        assertEquals(WidgetUpdate.Placement(1, firstRow = 1, rows = 3, lines = 1), updates(banking, lightFlow).getValue(placementKey(1)))
    }

    @Test
    fun `a column holds one list at most`() {
        assertThrows<IllegalArgumentException> { ConfigureRows.of(listOf(LOGS_LIST, StepField.Items("more", "More", LOGS, "More?", "+ More", rows = 1))) }
    }

    @Test
    fun `a list takes as many rows as it says`() {
        assertEquals(mapOf(0 to INPUT_TOGGLE, 1 to AMOUNT, 2 to LOGS_LIST), ConfigureRows.of(listOf(INPUT_TOGGLE, AMOUNT, LOGS_LIST)))
    }

    @Test
    fun `the list of a column is found among the fields`() {
        assertEquals(listOf(CHOSEN_LIST, null), listOf(ConfigureRows.list(listOf(LOGS_LIST, CHOSEN_LIST), 1), ConfigureRows.list(listOf(CHOSEN_LIST), 0)))
    }

    private val lightFlow = IdleState(steps = listOf(oak, StepSettings("light")))

    private val gatheredDeposit = StepDraft(1, StepSettings("bank"), new = false)

    @Test
    fun `a field the settings do not show takes no row`() {
        assertEquals(
            listOf<Any>(true, "Banks", "1 gathered", false),
            listOf(
                visible(gatheredDeposit, BuilderWidgets.row(7)),
                text(gatheredDeposit, BuilderWidgets.rowLabel(7)),
                text(gatheredDeposit, BuilderWidgets.rowNote(7), IdleState(steps = listOf(step("chop", "gather")))),
                visible(gatheredDeposit, BuilderWidgets.list(1)),
            ),
        )
    }

    @Test
    fun `a field the settings show replaces the one they hide`() {
        assertEquals(listOf(true, "Chosen"), listOf(visible(banking, BuilderWidgets.list(1)), text(banking, BuilderWidgets.rowLabel(7))))
    }

    @Test
    fun `the rows keep only the fields the settings show`() {
        assertEquals(listOf(DEPOSIT_TOGGLE, BANKS_NOTE), ConfigureRows.shown(listOf(DEPOSIT_TOGGLE, BANKS_NOTE, CHOSEN_LIST), StepSettings("bank")))
    }

    private val stocking = StepDraft(0, StepSettings("stock", mapOf("withdraw" to "1511:14,1521")), new = true)

    @Test
    fun `a withdrawal's line shows its amount box and All button`() {
        assertEquals(
            listOf<Any>(true, "14", true, "All"),
            listOf(
                visible(stocking, BuilderWidgets.lineAmount(0, 0)),
                text(stocking, BuilderWidgets.lineAmountText(0, 0)),
                visible(stocking, BuilderWidgets.lineAmount(0, 1)),
                text(stocking, BuilderWidgets.lineAmountText(0, 1)),
            ),
        )
    }

    @Test
    fun `a withdrawal's big amount shows as a stack does`() {
        val big = stocking.copy(settings = StepSettings("stock", mapOf("withdraw" to "1511:150000")))

        assertEquals("150K", text(big, BuilderWidgets.lineAmountText(0, 0)))
    }

    @Test
    fun `a withdrawal's name is fitted to the room the amount box leaves`() {
        val long = stocking.copy(settings = StepSettings("stock", mapOf("withdraw" to "1511")))
        val configure = BuilderConfigure(FlowResolver(CONFIGURED_TYPES), REFLEXES, REFLEX_FORM, FakeNames(mapOf(1511 to "Logs of a very long name")), font)

        val name = configure.updates(IdleState(), long, facts).filterIsInstance<WidgetUpdate.Text>().single { it.id == BuilderWidgets.lineName(0, 0) }

        assertEquals("Logs of a..", name.text)
    }

    @Test
    fun `the withdrawal being typed is framed yellow, the others keep their edge`() {
        val typing = stocking.copy(typing = Typing("withdraw", 1521))

        assertEquals(
            listOf(BuilderWidgets.FIELD_EDGE, BuilderWidgets.TYPING),
            listOf(colour(typing, BuilderWidgets.lineAmountFrame(0, 0)), colour(typing, BuilderWidgets.lineAmountFrame(0, 1))),
        )
    }

    private val sawing = StepDraft(0, StepSettings("saw"), new = true)

    @Test
    fun `a warning that does not stop the step shows in yellow`() {
        assertEquals(listOf("@yel@! You carry no saw and no bank step withdraws one.", "", ""), (0 until 3).map { text(sawing, BuilderWidgets.warning(it), IdleState()) })
    }

    @Test
    fun `a step with what it needs carried has no warnings`() {
        val carried = configure.updates(IdleState(), sawing, facts.copy(bag = setOf(SAW))).filterIsInstance<WidgetUpdate.Text>()

        assertEquals("@gre@No warnings.", carried.single { it.id == BuilderWidgets.warning(0) }.text)
    }

    @Test
    fun `the red reason comes before the yellow warnings`() {
        val state = IdleState(steps = listOf(StepSettings("saw")), running = true, blocked = "Autopilot: you need a saw.")

        assertEquals(
            listOf("@red@! You need a saw.", "@yel@! You carry no saw and no bank step withdraws one."),
            (0 until 2).map { text(sawing.copy(new = false), BuilderWidgets.warning(it), state) },
        )
    }

    @Test
    fun `a step edited is warned about in its place in the flow`() {
        val state = IdleState(steps = listOf(oak, oak))

        assertEquals("@yel@! You carry no saw and no bank step withdraws one.", text(StepDraft(1, StepSettings("saw"), new = false), BuilderWidgets.warning(0), state))
    }

    @Test
    fun `a draft in the flow replaces the step it edits`() {
        assertEquals(listOf(oak, step("drop")) to 0, StepDraft(0, oak, new = false).inFlow(listOf(step("walk"), step("drop"))))
    }

    @Test
    fun `a new draft goes after the flow's steps`() {
        assertEquals(listOf(step("walk"), oak) to 1, StepDraft(0, oak, new = true).inFlow(listOf(step("walk"))))
    }

    @Test
    fun `a draft of a step deleted since goes after the flow's steps`() {
        assertEquals(listOf(oak) to 0, StepDraft(2, oak, new = false).inFlow(emptyList()))
    }

    private val troutReflex = ReflexSettings(1, mapOf("foods" to "333"))
    private val eating = StepDraft(0, REFLEX_FORM.settings(troutReflex), new = false, subject = DraftSubject.REFLEX)
    private val running = StepDraft(0, REFLEX_FORM.settings(ReflexSettings(1, mapOf("do" to "run", "then" to "jump", "step" to "5"))), new = false, subject = DraftSubject.REFLEX)
    private val cows = IdleState(steps = listOf(step("chop", "oak").copy(id = 5)), reflexes = listOf(troutReflex))

    @Test
    fun `a reflex's header shows its first food, its number and what it does`() {
        assertEquals(
            listOf<Any>(WidgetPicture.Item(333), false, "Reflex 1: Eat", "Eats when hitpoints fall below a share of full."),
            listOf(
                picture(eating, BuilderWidgets.HEADER_PICTURE),
                visible(eating, BuilderWidgets.HEADER_CORNER_LAYER),
                text(eating, BuilderWidgets.HEADER_NAME, cows),
                text(eating, BuilderWidgets.HEADER_DESCRIPTION, cows),
            ),
        )
    }

    @Test
    fun `a new reflex is numbered after the flow's reflexes`() {
        val new = StepDraft(1, REFLEX_FORM.settings(ReflexSettings()), new = true, subject = DraftSubject.REFLEX)

        assertEquals("Reflex 2: Eat", text(new, BuilderWidgets.HEADER_NAME, cows))
    }

    @Test
    fun `an eat reflex's rows are its Do toggle, its When and its food list`() {
        assertEquals(
            listOf<Any>("@whi@Eat", "Run away", "Hitpoints below 50%", WidgetUpdate.Placement(0, firstRow = 2, rows = 4, lines = 2), "item 333"),
            listOf(
                text(eating, BuilderWidgets.toggleText(0, 2, 0), cows),
                text(eating, BuilderWidgets.toggleText(0, 2, 1), cows),
                text(eating, BuilderWidgets.rowPlainText(1), cows),
                updates(eating, cows).getValue(placementKey(0)),
                text(eating, BuilderWidgets.lineName(0, 0), cows),
            ),
        )
    }

    @Test
    fun `an eat reflex with no food picked offers to pick some`() {
        val anyFood = eating.copy(settings = REFLEX_FORM.settings(ReflexSettings(1)))

        assertEquals("+ Any food: pick some...", text(anyFood, BuilderWidgets.listAddText(0), cows))
    }

    @Test
    fun `a reflex that jumps shows Then and the step it jumps to`() {
        assertEquals(
            listOf("@whi@Jump to a step", "Step 1: Chop label"),
            listOf(text(running, BuilderWidgets.toggleText(2, 2, 1), cows), text(running, BuilderWidgets.rowText(3), cows)),
        )
    }

    @Test
    fun `a reflex that jumps to a deleted step says so in red`() {
        assertEquals("@red@! jumps to a step that was deleted", text(running, BuilderWidgets.warning(0), IdleState(reflexes = listOf(troutReflex))))
    }

    @Test
    fun `a jump to a deleted step says so in its field`() {
        assertEquals("Deleted step", text(running, BuilderWidgets.rowText(3), IdleState(reflexes = listOf(troutReflex))))
    }

    @Test
    fun `a new reflex is checked in the place it would take`() {
        val new = running.copy(slot = 1, new = true)

        assertEquals("@gre@No warnings.", text(new, BuilderWidgets.warning(0), cows))
    }

    @Test
    fun `an eat reflex whose food is neither carried nor withdrawn warns in yellow`() {
        assertEquals("@yel@! You carry no food and no bank step withdraws any.", text(eating, BuilderWidgets.warning(0), cows))
    }

    @Test
    fun `an eat reflex whose food is carried has no warning`() {
        val carried = configure.updates(cows, eating, facts.copy(bag = setOf(333))).filterIsInstance<WidgetUpdate.Text>()

        assertEquals("@gre@No warnings.", carried.single { it.id == BuilderWidgets.warning(0) }.text)
    }
}
