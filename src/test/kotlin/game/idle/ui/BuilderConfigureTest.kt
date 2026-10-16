package game.idle.ui

import game.idle.IdleState
import game.idle.flow.FakeStepType.Companion.step
import game.idle.flow.FlowResolver
import game.idle.flow.ReflexForm
import game.idle.flow.ReflexSettings
import game.idle.flow.StepIcon
import game.idle.flow.RunProgress
import game.idle.flow.StepField
import game.idle.flow.StepSettings
import game.idle.flow.option.FakeNames
import game.idle.flow.option.OptionFacts
import game.idle.font.ClientFont
import io.luna.game.model.mob.Skill
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class BuilderConfigureTest {

    /** Five pixels a glyph: a field holds 20 characters, a note 32, a warning line 98. */
    private val font = ClientFont(IntArray(256) { 5 })
    private val configure = BuilderConfigure(FlowResolver(CONFIGURED_TYPES), REFLEXES, REFLEX_FORM, FakeNames(), font, CONFIGURED_DANGERS)

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

    private val fishing = StepDraft(1, step("fish"), new = true)

    @Test
    fun `a work spot with no tile picked reads From the flow, with no x`() {
        assertEquals(listOf<Any>("From the flow", false), listOf(text(fishing, BuilderWidgets.rowPlainText(6)), visible(fishing, BuilderWidgets.rowButton(6))))
    }

    @Test
    fun `a work spot shows the tile picked, with an x to put it back`() {
        val picked = fishing.with("spot", "3101 3509")

        assertEquals(
            listOf<Any>("3101, 3509", true, "x"),
            listOf(text(picked, BuilderWidgets.rowPlainText(6)), visible(picked, BuilderWidgets.rowButton(6)), text(picked, BuilderWidgets.rowButtonText(6))),
        )
    }

    @Test
    fun `a tile the step needs has no x, a tile picked or not`() {
        assertFalse(visible(walking.with("tile", "3086 3233"), BuilderWidgets.rowButton(0)))
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
    fun `the aggressive npcs in reach of where the step works are its last yellow lines`() {
        val sent = configure.updates(IdleState(steps = listOf(oak)), chopping, facts.copy(here = DANGEROUS))

        assertEquals(
            listOf("@yel@! Dark wizard wanders here, level 7, attacks below combat 15", ""),
            (0 until 2).map { line -> (sent.single { it is WidgetUpdate.Text && it.id == BuilderWidgets.warning(line) } as WidgetUpdate.Text).text },
        )
    }

    @Test
    fun `a player the npcs in reach leave alone sees no warning`() {
        val sent = configure.updates(IdleState(steps = listOf(oak)), chopping, facts.copy(here = DANGEROUS, combat = 15))

        assertEquals("@gre@No warnings.", (sent.single { it is WidgetUpdate.Text && it.id == BuilderWidgets.warning(0) } as WidgetUpdate.Text).text)
    }

    @Test
    fun `the running step's block is its warning`() {
        val state = IdleState(steps = listOf(oak), running = true, blocked = "Needs an axe you can use")

        assertEquals("@red@! Needs an axe you can use", text(chopping, BuilderWidgets.warning(0), state))
    }

    @Test
    fun `what the running step waits for is its first yellow line`() {
        val state = IdleState(steps = listOf(oak), running = true, waiting = "No tree in reach")

        assertEquals("@yel@! No tree in reach", text(chopping, BuilderWidgets.warning(0), state))
    }

    @Test
    fun `another step's wait is not this one's warning`() {
        val state = IdleState(steps = listOf(oak, oak), running = true, stepIndex = 1, waiting = "No tree in reach")

        assertEquals("@gre@No warnings.", text(chopping, BuilderWidgets.warning(0), state))
    }

    @Test
    fun `another step's block is not this one's warning`() {
        val state = IdleState(steps = listOf(oak, oak), running = true, stepIndex = 1, blocked = "Needs an axe you can use")

        assertEquals("@gre@No warnings.", text(chopping, BuilderWidgets.warning(0), state))
    }

    @Test
    fun `a block seen while the flow is stopped is no warning`() {
        val state = IdleState(steps = listOf(oak), blocked = "Needs an axe you can use")

        assertEquals("@gre@No warnings.", text(chopping, BuilderWidgets.warning(0), state))
    }

    @Test
    fun `a new step does not take the block of the step it would follow`() {
        val state = IdleState(steps = listOf(oak), running = true, blocked = "Needs an axe you can use")

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
    fun `a list's line shows its option's picture and label, without an amount`() {
        assertEquals(
            listOf<Any>(WidgetPicture.Item(1521), "Oak logs", false),
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
        val long = stocking.copy(settings = StepSettings("stock", mapOf("withdraw" to "1513")))
        val configure = BuilderConfigure(FlowResolver(CONFIGURED_TYPES), REFLEXES, REFLEX_FORM, FakeNames(mapOf(1513 to "Logs of a very long name")), font, CONFIGURED_DANGERS)

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
        assertEquals(listOf("@yel@! You carry no saw and no bank step withdraws one", "", ""), (0 until 3).map { text(sawing, BuilderWidgets.warning(it), IdleState()) })
    }

    @Test
    fun `a step with what it needs carried has no warnings`() {
        val carried = configure.updates(IdleState(), sawing, facts.copy(bag = setOf(SAW))).filterIsInstance<WidgetUpdate.Text>()

        assertEquals("@gre@No warnings.", carried.single { it.id == BuilderWidgets.warning(0) }.text)
    }

    @Test
    fun `the red reason comes before the yellow warnings`() {
        val state = IdleState(steps = listOf(StepSettings("saw")), running = true, blocked = "Needs a saw")

        assertEquals(
            listOf("@red@! Needs a saw", "@yel@! You carry no saw and no bank step withdraws one"),
            (0 until 2).map { text(sawing.copy(new = false), BuilderWidgets.warning(it), state) },
        )
    }

    @Test
    fun `a step edited is warned about in its place in the flow`() {
        val state = IdleState(steps = listOf(oak, oak))

        assertEquals("@yel@! You carry no saw and no bank step withdraws one", text(StepDraft(1, StepSettings("saw"), new = false), BuilderWidgets.warning(0), state))
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
    private val running = StepDraft(0, REFLEX_FORM.settings(ReflexSettings(1, mapOf("do" to "jump", "step" to "5"))), new = false, subject = DraftSubject.REFLEX)
    private val cows = IdleState(steps = listOf(step("chop", "oak").copy(id = 5)), reflexes = listOf(troutReflex))

    @Test
    fun `a reflex's header shows its first food, its number and what it does`() {
        assertEquals(
            listOf<Any>(WidgetPicture.Item(333), false, "Reflex 1: Eat", "Eats the first of its foods the bag holds."),
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
    fun `an eat reflex's rows are When and Below on the left, Do and its food list on the right`() {
        assertEquals(
            listOf<Any>("@whi@Hitpoints", "Skill", "50%", "@whi@Eat", "Pray", "Stop", "Jump", WidgetUpdate.Placement(1, firstRow = 1, rows = 4, lines = 2), "Trout"),
            listOf(
                text(eating, BuilderWidgets.toggleText(0, 2, 0), cows),
                text(eating, BuilderWidgets.toggleText(0, 2, 1), cows),
                text(eating, BuilderWidgets.rowPlainText(2), cows),
                text(eating, BuilderWidgets.toggleText(6, 5, 0), cows),
                text(eating, BuilderWidgets.toggleText(6, 5, 2), cows),
                text(eating, BuilderWidgets.toggleText(6, 5, 3), cows),
                text(eating, BuilderWidgets.toggleText(6, 5, 4), cows),
                updates(eating, cows).getValue(placementKey(1)),
                text(eating, BuilderWidgets.lineName(1, 0), cows),
            ),
        )
    }

    @Test
    fun `an eat reflex with no food picked offers to pick some`() {
        val anyFood = eating.copy(settings = REFLEX_FORM.settings(ReflexSettings(1)))

        assertEquals("+ Any food: pick some...", text(anyFood, BuilderWidgets.listAddText(1), cows))
    }

    @Test
    fun `a reflex that jumps shows the step it jumps to under Do`() {
        assertEquals(
            listOf("@whi@Jump", "Step 1: Chop label"),
            listOf(text(running, BuilderWidgets.toggleText(6, 5, 4), cows), text(running, BuilderWidgets.rowText(7), cows)),
        )
    }

    @Test
    fun `a reflex that jumps to a deleted step says so in red`() {
        assertEquals("@red@! Jumps to a step that was deleted", text(running, BuilderWidgets.warning(0), IdleState(reflexes = listOf(troutReflex))))
    }

    @Test
    fun `a jump to a deleted step says so in its field`() {
        assertEquals("Deleted step", text(running, BuilderWidgets.rowText(7), IdleState(reflexes = listOf(troutReflex))))
    }

    @Test
    fun `a new reflex is checked in the place it would take`() {
        val new = running.copy(slot = 1, new = true)

        assertEquals("@gre@No warnings.", text(new, BuilderWidgets.warning(0), cows))
    }

    @Test
    fun `an eat reflex whose food is neither carried nor withdrawn warns in yellow`() {
        assertEquals("@yel@! You carry no food and no bank step withdraws any", text(eating, BuilderWidgets.warning(0), cows))
    }

    @Test
    fun `an eat reflex whose food is carried has no warning`() {
        val carried = configure.updates(cows, eating, facts.copy(bag = setOf(333))).filterIsInstance<WidgetUpdate.Text>()

        assertEquals("@gre@No warnings.", carried.single { it.id == BuilderWidgets.warning(0) }.text)
    }

    @Test
    fun `an eat reflex under attack warns in yellow that it eats at full hitpoints`() {
        val attacked = eating.copy(settings = REFLEX_FORM.settings(ReflexSettings(1, mapOf("when" to "attacked", "foods" to "333"))))
        val sent = configure.updates(cows, attacked, facts.copy(bag = setOf(333))).filterIsInstance<WidgetUpdate.Text>()

        assertEquals("@yel@! Eats while attacked, even at full hitpoints", sent.single { it.id == BuilderWidgets.warning(0) }.text)
    }

    private val jumpAway = ReflexSettings(2, mapOf("do" to "jump", "step" to "9"))
    private val guarded = IdleState(steps = listOf(oak), reflexes = listOf(troutReflex, jumpAway))
    private val attaching = StepDraft(0, oak.copy(reflexes = listOf(2, 1)), new = false, tab = StepTab.REFLEXES)

    @Test
    fun `a step's screen shows its tabs, Settings lit by default, and hides the Reflexes page`() {
        val sent = updates(chopping)

        assertEquals(
            listOf<Any>(true, "@whi@Settings", BuilderWidgets.LIT, "Reflexes", BuilderWidgets.UNLIT, false),
            listOf(
                (sent.getValue(BuilderWidgets.STEP_TABS) as WidgetUpdate.Visible).visible,
                (sent.getValue(BuilderWidgets.SETTINGS_TAB) as WidgetUpdate.Text).text,
                (sent.getValue(BuilderWidgets.SETTINGS_TAB_FRAME) as WidgetUpdate.Colour).rgb,
                (sent.getValue(BuilderWidgets.STEP_REFLEXES_TAB) as WidgetUpdate.Text).text,
                (sent.getValue(BuilderWidgets.STEP_REFLEXES_TAB_FRAME) as WidgetUpdate.Colour).rgb,
                (sent.getValue(BuilderWidgets.ATTACHED) as WidgetUpdate.Visible).visible,
            ),
        )
    }

    @Test
    fun `the Reflexes tab lights its tab, hides the rows and lists and shows the page`() {
        val sent = updates(attaching, guarded)

        assertEquals(
            listOf<Any>("Settings", "@whi@Reflexes", BuilderWidgets.LIT, List(BuilderWidgets.ROWS + 2) { false }, true),
            listOf(
                (sent.getValue(BuilderWidgets.SETTINGS_TAB) as WidgetUpdate.Text).text,
                (sent.getValue(BuilderWidgets.STEP_REFLEXES_TAB) as WidgetUpdate.Text).text,
                (sent.getValue(BuilderWidgets.STEP_REFLEXES_TAB_FRAME) as WidgetUpdate.Colour).rgb,
                ((0 until BuilderWidgets.ROWS).map(BuilderWidgets::row) + BuilderWidgets.list(0) + BuilderWidgets.list(1)).map {
                    (sent.getValue(it) as WidgetUpdate.Visible).visible
                },
                (sent.getValue(BuilderWidgets.ATTACHED) as WidgetUpdate.Visible).visible,
            ),
        )
    }

    @Test
    fun `a reflex's screen hides the tabs and the Reflexes page`() {
        val sent = updates(eating, cows)

        assertEquals(
            listOf(false, false),
            listOf(BuilderWidgets.STEP_TABS, BuilderWidgets.ATTACHED).map { (sent.getValue(it) as WidgetUpdate.Visible).visible },
        )
    }

    @Test
    fun `the Reflexes page is placed over the rows, a line to attach then one per reflex attached`() {
        assertEquals(
            WidgetUpdate.Placement(BuilderWidgets.ATTACHED_LIST, firstRow = 0, rows = 6, lines = 3),
            updates(attaching, guarded).getValue(placementKey(BuilderWidgets.ATTACHED_LIST)),
        )
    }

    @Test
    fun `a reflex attached shows its number in the flow, its picture and its sentence, in the step's order`() {
        val sent = updates(attaching, guarded)

        assertEquals(
            listOf<Any>("2", WidgetPicture.Media("headicons_hint", 0), "1", WidgetPicture.Item(333), "Hitpoints below 50%: eat trout"),
            listOf(
                (sent.getValue(BuilderWidgets.attachedNumber(0)) as WidgetUpdate.Text).text,
                (sent.getValue(BuilderWidgets.attachedPicture(0)) as WidgetUpdate.Picture).picture,
                (sent.getValue(BuilderWidgets.attachedNumber(1)) as WidgetUpdate.Text).text,
                (sent.getValue(BuilderWidgets.attachedPicture(1)) as WidgetUpdate.Picture).picture,
                (sent.getValue(BuilderWidgets.attachedSentence(1)) as WidgetUpdate.Text).text,
            ),
        )
    }

    @Test
    fun `a reflex attached that cannot work shows in red`() {
        assertEquals(
            "@red@Hitpoints below 50%: jump to a deleted step",
            text(attaching, BuilderWidgets.attachedSentence(0), guarded),
        )
    }

    @Test
    fun `a flow with reflexes lets the page attach or detach them, and attach all`() {
        assertEquals(
            listOf("+ Attach or detach reflexes...", "Attach all"),
            listOf(text(attaching, BuilderWidgets.ATTACH_TEXT, guarded), text(attaching, BuilderWidgets.ATTACH_ALL_TEXT, guarded)),
        )
    }

    @Test
    fun `the page's line and Attach all are greyed without reflexes or while the flow runs`() {
        assertEquals(
            listOf("@gry@+ Attach or detach reflexes...", "@gry@Attach all"),
            listOf(
                text(attaching, BuilderWidgets.ATTACH_TEXT, IdleState(steps = listOf(oak))),
                text(attaching, BuilderWidgets.ATTACH_ALL_TEXT, guarded.copy(running = true)),
            ),
        )
    }

    @Test
    fun `a step's attached reflexes stand where the flow holds them, in the step's order, unknown ones left out`() {
        val draft = StepDraft(0, oak.copy(reflexes = listOf(2, 7, 1)), new = false)

        assertEquals(listOf(1, 0), draft.attached(guarded.reflexes))
    }

    @Test
    fun `attaching sets the step's reflexes and stops typing`() {
        val typing = chopping.copy(typing = Typing("amount"))

        assertEquals(chopping.copy(settings = oak.copy(reflexes = listOf(3))), typing.attaching(listOf(3)))
    }

    @Test
    fun `a draft shown on another tab is the same step`() {
        assertTrue(chopping.sameStep(attaching))
    }

    private val mining = StepDraft(1, StepSettings("mine"), new = true)

    @Test
    fun `the Until row shows five buttons on the right, None lit without a goal`() {
        assertEquals(
            listOf<Any>("Until", false, false, true, "@whi@None", "Level", "Bank", BuilderWidgets.LIT, BuilderWidgets.FIELD_EDGE),
            listOf(
                text(mining, BuilderWidgets.rowLabel(6)),
                visible(mining, BuilderWidgets.toggles(6, 2)),
                visible(mining, BuilderWidgets.toggles(6, 3)),
                visible(mining, BuilderWidgets.toggles(6, 5)),
                text(mining, BuilderWidgets.toggleText(6, 5, 0)),
                text(mining, BuilderWidgets.toggleText(6, 5, 1)),
                text(mining, BuilderWidgets.toggleText(6, 5, 4)),
                colour(mining, BuilderWidgets.toggleFrame(6, 5, 0)),
                colour(mining, BuilderWidgets.toggleFrame(6, 5, 1)),
            ),
        )
    }

    @Test
    fun `a row of another sort hides the five buttons too`() {
        assertFalse(visible(chopping, BuilderWidgets.toggles(0, 5)))
    }

    @Test
    fun `a level goal shows the kind's skill picked, the level to type and When met`() {
        val draft = mining.with("goal", "level")

        assertEquals(
            listOf<Any>("Skill", "Mining", "Level", "@gry@not set yet", "When met", "@whi@Skip this step", false),
            listOf(
                text(draft, BuilderWidgets.rowLabel(7)),
                text(draft, BuilderWidgets.rowText(7)),
                text(draft, BuilderWidgets.rowLabel(8)),
                text(draft, BuilderWidgets.rowPlainText(8)),
                text(draft, BuilderWidgets.rowLabel(9)),
                text(draft, BuilderWidgets.toggleText(9, 2, 0)),
                visible(draft, BuilderWidgets.row(10)),
            ),
        )
    }

    @Test
    fun `a bank goal shows the item to search and the count to type`() {
        val draft = mining.with("goal", "bank").with("goalCount", "100")

        assertEquals(
            listOf<Any>("Item", "@gry@Search...", "Count", "100 in the bank"),
            listOf(text(draft, BuilderWidgets.rowLabel(7)), text(draft, BuilderWidgets.rowText(7)), text(draft, BuilderWidgets.rowLabel(8)), text(draft, BuilderWidgets.rowPlainText(8))),
        )
    }

    @Test
    fun `the second When row keeps Under attack visible and only the chosen toggle lit`() {
        val draft = eating.copy(settings = REFLEX_FORM.settings(ReflexSettings(1, mapOf("when" to "attacked"))))

        assertEquals(listOf("Hitpoints", "Skill", "@whi@Under attack"), listOf(text(draft, BuilderWidgets.toggleText(0, 2, 0)), text(draft, BuilderWidgets.toggleText(0, 2, 1)), text(draft, BuilderWidgets.toggleText(1, 1, 0))))
        assertFalse(visible(draft, BuilderWidgets.row(2)))
    }

    @Test
    fun `a skill drink shows its values on the left and potions on the right`() {
        val draft = eating.copy(settings = REFLEX_FORM.settings(ReflexSettings(1, mapOf("when" to "skill", "skillName" to "strength", "skillBelow" to "25", "do" to "drink", "potions" to "113"))))

        assertEquals(listOf("Strength", "25", "@whi@Drink"), listOf(text(draft, BuilderWidgets.rowText(2)), text(draft, BuilderWidgets.rowPlainText(3)), text(draft, BuilderWidgets.toggleText(6, 5, 1))))
        assertEquals(WidgetUpdate.Placement(1, firstRow = 1, rows = 4, lines = 2), updates(draft).getValue(placementKey(1)))
    }

    @Test
    fun `a drink reflex without potions shows the red reason`() {
        val draft = eating.copy(settings = REFLEX_FORM.settings(ReflexSettings(1, mapOf("do" to "drink"))))

        assertEquals("@red@! Needs a potion", text(draft, BuilderWidgets.warning(0)))
    }

    @Test
    fun `a pray reflex lights Pray and lists its prayers with their tab icons and names`() {
        val draft = eating.copy(settings = REFLEX_FORM.settings(ReflexSettings(1, mapOf("when" to "attacked", "do" to "pray", "prayers" to "5623,5609"))))

        assertEquals(
            listOf<Any>("@whi@Pray", WidgetPicture.Media("prayeron", 14), "Protect from melee", "Thick skin", WidgetPicture.Media("prayeron", 14)),
            listOf(text(draft, BuilderWidgets.toggleText(6, 5, 2)), picture(draft, BuilderWidgets.linePicture(1, 0)), text(draft, BuilderWidgets.lineName(1, 0)), text(draft, BuilderWidgets.lineName(1, 1)), picture(draft, BuilderWidgets.HEADER_PICTURE)),
        )
    }

    @Test
    fun `a pray reflex's header names and describes praying`() {
        val draft = eating.copy(settings = REFLEX_FORM.settings(ReflexSettings(1, mapOf("do" to "pray", "prayers" to "5609"))))

        assertEquals(listOf("Reflex 1: Pray", "Keeps its prayers on while its condition holds."), listOf(text(draft, BuilderWidgets.HEADER_NAME), text(draft, BuilderWidgets.HEADER_DESCRIPTION)))
    }

    @Test
    fun `a pray reflex without prayers shows the red reason and the Prayer skill`() {
        val draft = eating.copy(settings = REFLEX_FORM.settings(ReflexSettings(1, mapOf("do" to "pray"))))

        assertEquals("@red@! Needs a prayer", text(draft, BuilderWidgets.warning(0)))
        assertEquals(WidgetPicture.of(StepIcon.Skill(Skill.PRAYER)), picture(draft, BuilderWidgets.HEADER_PICTURE))
    }

    @Test
    fun `a list line whose value the search no longer offers shows the item itself`() {
        val draft = eating.copy(settings = REFLEX_FORM.settings(ReflexSettings(1, mapOf("foods" to "1891"))))

        assertEquals(listOf<Any>(WidgetPicture.Item(1891), "item 1891"), listOf(picture(draft, BuilderWidgets.linePicture(1, 0)), text(draft, BuilderWidgets.lineName(1, 0))))
    }

}
