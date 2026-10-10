package game.idle.ui

import game.idle.flow.Choice
import game.idle.flow.FakeStep
import game.idle.flow.FakeStepType
import game.idle.flow.FieldColumn
import game.idle.flow.ReflexForm
import game.idle.flow.ReflexResolver
import game.idle.flow.StepAmount
import game.idle.flow.StepField
import game.idle.flow.StepNeeds
import game.idle.flow.StepRadius
import game.idle.flow.StepSettings
import game.idle.flow.StepTypes
import game.idle.flow.ToolNeed
import game.idle.flow.option.FakeNames
import game.idle.flow.option.InputSource
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

/** Two logs a list can hold: logs and oak logs, by item id. */
val LOGS = OptionSource {
    listOf(StepOption("1511", "Logs", OptionIcon.Item(1511)), StepOption("1521", "Oak logs", OptionIcon.Item(1521)))
}

/** Where a fake light step takes its logs: earlier steps unless the setting says the bank. */
val INPUT_TOGGLE = StepField.Toggle(
    "input",
    "Input",
    listOf(Choice("earlier", "Earlier steps"), Choice("bank", "The bank")),
    current = { settings, _ -> settings["input"] ?: "earlier" },
)

val LOGS_LIST = StepField.Items("logs", "Logs", LOGS, "Which logs?", "+ Add or remove logs...", rows = 4)

val DEPOSIT_TOGGLE = StepField.Toggle(
    "deposit",
    "Deposit",
    listOf(Choice("everything", "Everything"), Choice("gathered", "Gathered"), Choice("chosen", "Chosen")),
    current = { settings, _ -> settings["deposit"] ?: "gathered" },
    column = FieldColumn.RIGHT,
)

/** Shown only when the deposit is chosen. */
val CHOSEN_LIST = StepField.Items(
    "chosen", "Chosen", LOGS, "Which items?", "+ Add or remove items...", rows = 3, column = FieldColumn.RIGHT,
    visible = { it["deposit"] == "chosen" },
)

/** Shown unless the deposit is chosen, counting what the flow gathers. */
val BANKS_NOTE = StepField.Note("Banks", FieldColumn.RIGHT, visible = { it["deposit"] != "chosen" }) { _, before -> "${before.lap.size} gathered" }

/** A withdrawal list, with amounts. */
val WITHDRAW_LIST = StepField.Items("withdraw", "Withdraw", LOGS, "Which items?", "+ Add or remove items...", rows = 5, amounts = true)

/** A saw, which the fake "saw" kind needs. */
const val SAW = 8794

/**
 * Kinds with every sort of configure field: chop (a tree search on row 0, an amount on row 1, a note on row 2, within
 * on the right's first row, 6), drop (none), walk (a tile), light (an input toggle on row 0, an amount on row 1, a list
 * of logs on rows 2-5; new ones take logs from earlier steps), bank (a three-button toggle on the right's first row,
 * then on Chosen a list on the right's rows 1-3, else a note on row 1, 7), stock (a withdrawal list on rows 0-4), saw
 * (needs a saw).
 */
val CONFIGURED_TYPES = StepTypes(
    listOf(
        FakeStepType("chop", listOf(TREE_SEARCH, AMOUNT, INPUT_NOTE, WITHIN), skill = Skill.WOODCUTTING, target = TREES),
        FakeStepType("drop"),
        FakeStepType("walk", listOf(TILE)),
        FakeStepType(
            "light",
            listOf(INPUT_TOGGLE, AMOUNT, LOGS_LIST),
            newStep = { StepSettings("light", mapOf("input" to "earlier", "logs" to "1511")) },
            inputOf = { if (it["input"] == "bank") InputSource.BANK else InputSource.EARLIER_STEPS },
        ),
        FakeStepType("bank", listOf(DEPOSIT_TOGGLE, BANKS_NOTE, CHOSEN_LIST)),
        FakeStepType("stock", listOf(WITHDRAW_LIST)),
        FakeStepType("saw", resolved = { FakeStep("saw", needs = listOf(StepNeeds(tools = listOf(ToolNeed("saw", mapOf(SAW to 1)))))) }),
    ),
)

/** Where a list's placement goes among updates keyed by widget id: below every id. */
fun placementKey(list: Int): Int = -1 - list

/** Trout and lobster, the foods an eat reflex picks from in these tests, by item id. */
val FOODS = OptionSource {
    listOf(StepOption("333", "Trout", OptionIcon.Item(333)), StepOption("379", "Lobster", OptionIcon.Item(379)))
}

/** Trout, lobster and a cake with its slices, as Luna's eat table gives every portion of each food. */
val PORTIONS: Map<Int, Set<Int>> = mapOf(333 to setOf(333), 379 to setOf(379), 1891 to setOf(1891, 1893), 1893 to setOf(1891, 1893))

val REFLEXES = ReflexResolver(PORTIONS)

/** Reflex screens over the configured kinds, trout and lobster named. */
val REFLEX_FORM = ReflexForm(FOODS, PORTIONS, CONFIGURED_TYPES, FakeNames(mapOf(333 to "Trout", 379 to "Lobster")))

