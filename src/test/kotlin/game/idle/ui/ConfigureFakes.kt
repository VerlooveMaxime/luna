package game.idle.ui

import game.idle.flow.FakeStepType
import game.idle.flow.StepAmount
import game.idle.flow.StepField
import game.idle.flow.StepRadius
import game.idle.flow.StepTypes
import game.idle.flow.option.OptionIcon
import game.idle.flow.option.OptionSource
import game.idle.flow.option.StepOption
import game.idle.flow.option.StepTarget
import io.luna.game.model.mob.Skill

/** The fake kinds' tree target, kept under their one setting: oak, with its logs' icon. */
val TREES = StepTarget(FakeStepType.WORD, OptionSource { listOf(StepOption("oak", "Oak", OptionIcon.Item(1521))) })

val TREE_SEARCH = StepField.Search("Tree", TREES, "Which tree?")

val AMOUNT = StepAmount.field("Amount", unbounded = "until the bag is full", button = "Full")

/** A note counting the items the steps before get. */
val INPUT_NOTE = StepField.Note("Input") { _, before -> "${before.gathered.size} items before it" }

val WITHIN = StepRadius.field()

val TILE = StepField.MapTile("tile", "Tile")

/**
 * Kinds with every sort of configure field: chop (a tree search on row 0, an amount on row 1, a note on row 2, within
 * on the right's first row, 5), drop (none), walk (a tile).
 */
val CONFIGURED_TYPES = StepTypes(
    listOf(
        FakeStepType("chop", listOf(TREE_SEARCH, AMOUNT, INPUT_NOTE, WITHIN), skill = Skill.WOODCUTTING, target = TREES),
        FakeStepType("drop"),
        FakeStepType("walk", listOf(TILE)),
    ),
)
