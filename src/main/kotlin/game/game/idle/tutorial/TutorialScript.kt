package game.idle.tutorial

import game.idle.tutorial.TutorialStep.BAKE_BREAD
import game.idle.tutorial.TutorialStep.BAKE_ON_AUTOPILOT
import game.idle.tutorial.TutorialStep.BUILD_CHAIN
import game.idle.tutorial.TutorialStep.CATCH_SHRIMP
import game.idle.tutorial.TutorialStep.COOK_AGAIN
import game.idle.tutorial.TutorialStep.COOK_SHRIMP
import game.idle.tutorial.TutorialStep.CUT_TREE
import game.idle.tutorial.TutorialStep.DESIGN_CHARACTER
import game.idle.tutorial.TutorialStep.DONE
import game.idle.tutorial.TutorialStep.ENTER_MINE
import game.idle.tutorial.TutorialStep.EXTEND_THE_FLOW
import game.idle.tutorial.TutorialStep.FIND_SURVIVAL_EXPERT
import game.idle.tutorial.TutorialStep.LEAVE_SURVIVAL_AREA
import game.idle.tutorial.TutorialStep.LIGHT_FIRE
import game.idle.tutorial.TutorialStep.MAKE_DOUGH
import game.idle.tutorial.TutorialStep.MINED_COPPER
import game.idle.tutorial.TutorialStep.MINED_TIN
import game.idle.tutorial.TutorialStep.MINE_ORE
import game.idle.tutorial.TutorialStep.OPEN_HOUSE_DOOR
import game.idle.tutorial.TutorialStep.OPEN_IDLE_TAB
import game.idle.tutorial.TutorialStep.OPEN_INVENTORY
import game.idle.tutorial.TutorialStep.OPEN_MUSIC
import game.idle.tutorial.TutorialStep.OPEN_QUEST_JOURNAL
import game.idle.tutorial.TutorialStep.OPEN_SKILLS
import game.idle.tutorial.TutorialStep.PROSPECTED_COPPER
import game.idle.tutorial.TutorialStep.PROSPECTED_COPPER_LAST
import game.idle.tutorial.TutorialStep.PROSPECTED_TIN
import game.idle.tutorial.TutorialStep.PROSPECTED_TIN_LAST
import game.idle.tutorial.TutorialStep.PROSPECT_ROCKS
import game.idle.tutorial.TutorialStep.SMELT_BAR
import game.idle.tutorial.TutorialStep.SMITH_DAGGER
import game.idle.tutorial.TutorialStep.STOP_CHAIN
import game.idle.tutorial.TutorialStep.TALK_ABOUT_AUTOPILOT
import game.idle.tutorial.TutorialStep.TALK_ABOUT_CHAIN
import game.idle.tutorial.TutorialStep.TALK_ABOUT_FOOD
import game.idle.tutorial.TutorialStep.TALK_ABOUT_LOOP
import game.idle.tutorial.TutorialStep.TALK_ABOUT_QUESTS
import game.idle.tutorial.TutorialStep.TALK_ABOUT_SMITHING
import game.idle.tutorial.TutorialStep.TALK_ABOUT_SUPPLIES
import game.idle.tutorial.TutorialStep.TALK_TO_CHEF
import game.idle.tutorial.TutorialStep.TALK_TO_GUIDE
import game.idle.tutorial.TutorialStep.TALK_TO_MINING_INSTRUCTOR
import game.idle.tutorial.TutorialStep.TALK_TO_QUEST_GUIDE
import game.idle.tutorial.TutorialStep.WATCH_THE_BAKING
import game.player.Animations
import game.skill.cooking.cookFood.Cooking
import io.luna.game.model.mob.Skill
import io.luna.game.model.mob.overlay.GameTabSet.TabIndex

/** What the player sees at a step: the help box, the arrow, every side tab so far, and the one flashing. */
data class Screen(val help: HelpBox, val arrow: HintTarget, val tabs: Set<TabIndex>, val flash: TabIndex?)

/** Moving on to [step], with [items] handed over. */
data class Progress(val step: TutorialStep, val items: List<Int> = emptyList())

/** The dialogues to open one after the other, and the progress they make once read, if any. */
data class Talk(val dialogues: List<String>, val progress: Progress?) {
    constructor(dialogue: String, progress: Progress?) : this(listOf(dialogue), progress)
}

/** What trying a door, gate or ladder on the path gives. */
sealed interface PassageOutcome {
    data class Locked(val dialogue: String) : PassageOutcome

    data class Pass(val advanceTo: TutorialStep?) : PassageOutcome
}

/**
 * A cook the tutorial scripts instead of Luna's roll: [raw] used on one of [places] always gives [result], with
 * [experience] (none for a burn), the chat [message] named in the data if any, and [animation], then moves the
 * player on to [advanceTo].
 */
data class ScriptedCook(
    val raw: Int,
    val places: Set<Int>,
    val result: Int,
    val experience: Double,
    val message: String?,
    val animation: Int,
    val advanceTo: TutorialStep,
)

/** The island's rules: what each step shows and what moves a player from one step to the next. */
class TutorialScript(private val data: TutorialData) {

    init {
        val missing = DIALOGUES.filter { it !in data.dialogues }
        require(missing.isEmpty()) { "The tutorial needs dialogues that are missing: $missing" }
        val idle = ACTIVITIES.filter { it !in data.busy }
        require(idle.isEmpty()) { "The tutorial needs help boxes for these activities: $idle" }
    }

    fun screen(step: TutorialStep): Screen {
        val screen = data.steps.getValue(step)
        val tabs = data.steps.filterKeys { it <= step }.values.flatMap { it.tabs }.toSet()
        return Screen(screen.help, screen.arrow, tabs, screen.flash)
    }

    /** Closing the character designer, by accepting or by walking away, starts the tutorial proper. */
    fun designerClosed(step: TutorialStep): TutorialStep? = TALK_TO_GUIDE.takeIf { step == DESIGN_CHARACTER }

    fun talkToGuide(step: TutorialStep): Talk =
        if (step < OPEN_HOUSE_DOOR) {
            Talk(GUIDE_WELCOME, Progress(OPEN_HOUSE_DOOR))
        } else {
            Talk(GUIDE_AGAIN, progress = null)
        }

    fun talkToSurvivalExpert(step: TutorialStep): Talk = when {
        step < OPEN_INVENTORY -> Talk(SURVIVAL_WELCOME, Progress(OPEN_INVENTORY).takeIf { step == FIND_SURVIVAL_EXPERT })
        step == OPEN_INVENTORY -> Talk(SURVIVAL_INVENTORY, progress = null)
        step < OPEN_SKILLS -> Talk(SURVIVAL_FIRE, progress = null)
        step == OPEN_SKILLS -> Talk(SURVIVAL_SKILLS, progress = null)
        step == TALK_ABOUT_AUTOPILOT -> Talk(SURVIVAL_AUTOPILOT, Progress(OPEN_IDLE_TAB))
        step < TALK_ABOUT_FOOD -> Talk(SURVIVAL_AUTOPILOT_AGAIN, progress = null)
        step == TALK_ABOUT_FOOD -> Talk(SURVIVAL_FOOD, Progress(CATCH_SHRIMP, listOf(SMALL_FISHING_NET)))
        step < TALK_ABOUT_LOOP -> Talk(SURVIVAL_SHRIMP, progress = null)
        step == TALK_ABOUT_LOOP -> Talk(SURVIVAL_LOOP, Progress(EXTEND_THE_FLOW))
        step < LEAVE_SURVIVAL_AREA -> Talk(SURVIVAL_LOOP_AGAIN, progress = null)
        else -> Talk(SURVIVAL_DONE, progress = null)
    }

    fun talkToQuestGuide(step: TutorialStep): Talk = when {
        step <= TALK_TO_QUEST_GUIDE ->
            Talk(QUEST_GUIDE_WELCOME, Progress(OPEN_QUEST_JOURNAL).takeIf { step == TALK_TO_QUEST_GUIDE })
        step == OPEN_QUEST_JOURNAL -> Talk(QUEST_GUIDE_OPEN_JOURNAL, progress = null)
        step == TALK_ABOUT_QUESTS -> Talk(listOf(QUEST_GUIDE_JOURNAL) + QUEST_GUIDE_EXPLAINS, Progress(ENTER_MINE))
        else -> Talk(QUEST_GUIDE_EXPLAINS, progress = null)
    }

    fun talkToMiningInstructor(step: TutorialStep): Talk = when {
        step <= TALK_TO_MINING_INSTRUCTOR ->
            Talk(DEZZICK_WELCOME, Progress(PROSPECT_ROCKS).takeIf { step == TALK_TO_MINING_INSTRUCTOR })
        step < PROSPECTED_TIN_LAST -> Talk(DEZZICK_PROSPECT_AGAIN, progress = null)
        step < MINE_ORE -> Talk(DEZZICK_PROSPECTED, Progress(MINE_ORE, listOf(BRONZE_PICKAXE)))
        step < SMELT_BAR -> Talk(DEZZICK_MINING, progress = null)
        step == SMELT_BAR -> Talk(DEZZICK_SMELTING, progress = null)
        step == TALK_ABOUT_SMITHING -> Talk(DEZZICK_SMITHING, Progress(SMITH_DAGGER, listOf(HAMMER)))
        step == SMITH_DAGGER -> Talk(DEZZICK_DAGGER, progress = null)
        step == TALK_ABOUT_CHAIN -> Talk(DEZZICK_CHAIN, Progress(BUILD_CHAIN))
        step <= STOP_CHAIN -> Talk(DEZZICK_CHAIN_AGAIN, progress = null)
        else -> Talk(DEZZICK_HELLO, progress = null)
    }

    /** The tools Dezzick makes sure a player still has, once he handed them over; nothing once off the island. */
    fun miningTools(step: TutorialStep): List<Int> = when {
        step < MINE_ORE || step == DONE -> emptyList()
        step < SMITH_DAGGER -> listOf(BRONZE_PICKAXE)
        else -> listOf(BRONZE_PICKAXE, HAMMER)
    }

    /** The boxes showing the tools Dezzick just handed back, one per tool. */
    fun miningToolBoxes(given: List<Int>): List<String> = listOfNotNull(
        DEZZICK_GIVES_PICKAXE.takeIf { BRONZE_PICKAXE in given },
        DEZZICK_GIVES_HAMMER.takeIf { HAMMER in given },
    )

    /** Prospecting the copper and the tin rocks, in either order, moves the player on; a rock is copper or tin. */
    fun prospected(step: TutorialStep, rock: Int): TutorialStep? = when (rock to step) {
        COPPER_ROCK to PROSPECT_ROCKS -> PROSPECTED_COPPER
        COPPER_ROCK to PROSPECTED_TIN -> PROSPECTED_COPPER_LAST
        TIN_ROCK to PROSPECT_ROCKS -> PROSPECTED_TIN
        TIN_ROCK to PROSPECTED_COPPER -> PROSPECTED_TIN_LAST
        else -> null
    }

    /** The box naming what a prospected [rock] holds. */
    fun prospectResult(rock: Int): String = if (rock == COPPER_ROCK) PROSPECT_COPPER else PROSPECT_TIN

    /** Mining either ore first; once both are carried, smelting. */
    fun oreProgress(step: TutorialStep, carried: Set<Int>): TutorialStep? {
        val copper = COPPER_ORE in carried
        val tin = TIN_ORE in carried
        return when (step) {
            MINE_ORE -> if (copper && tin) SMELT_BAR else if (copper) MINED_COPPER else MINED_TIN.takeIf { tin }
            MINED_COPPER -> SMELT_BAR.takeIf { tin }
            MINED_TIN -> SMELT_BAR.takeIf { copper }
            else -> null
        }
    }

    /**
     * A click on an island object the tutorial answers itself, with the box to show once the player stands beside it:
     * the rocks before mining is taught, and the furnace's own option, which only explains it.
     */
    fun objectClicked(step: TutorialStep, objectId: Int, firstOption: Boolean): String? = when {
        !firstOption -> null
        objectId in ROCKS && step < MINE_ORE -> MINE_NOT_READY
        objectId == FURNACE -> if (step < SMELT_BAR) FURNACE_NOT_YET else FURNACE_HOW
        else -> null
    }

    /** An item used on the furnace or an anvil before its lesson, with the box saying so. */
    fun itemUsedOn(step: TutorialStep, objectId: Int): String? = when {
        objectId == FURNACE && step < SMELT_BAR -> FURNACE_NOT_YET
        objectId == ANVIL && step < SMELT_BAR -> ANVIL_NOT_YET
        objectId == ANVIL && step < SMITH_DAGGER -> ANVIL_NO_HAMMER
        else -> null
    }

    /** On the island the anvil makes only the bronze dagger. */
    fun maySmith(step: TutorialStep, itemId: Int): Boolean = step == DONE || itemId == BRONZE_DAGGER

    /** How the instructors say a player's name: the login name with each word capitalised. */
    fun spokenName(username: String): String =
        username.split('_', ' ').filter { it.isNotEmpty() }.joinToString(" ") { it[0].uppercaseChar() + it.substring(1) }

    fun talkToChef(step: TutorialStep): Talk = when {
        step <= TALK_TO_CHEF -> Talk(CHEF_WELCOME, Progress(MAKE_DOUGH, listOf(BUCKET_OF_WATER, POT_OF_FLOUR)).takeIf { step == TALK_TO_CHEF })
        step < TALK_ABOUT_SUPPLIES -> Talk(CHEF_BREAD, progress = null)
        step == TALK_ABOUT_SUPPLIES -> Talk(CHEF_SUPPLIES, Progress(BAKE_ON_AUTOPILOT, SUPPLIES))
        step < OPEN_MUSIC -> Talk(CHEF_SUPPLIES_AGAIN, progress = null)
        else -> Talk(CHEF_HELLO, progress = null)
    }

    /**
     * What the chef hands back while the player still has to bake and has nothing left to bake from: the missing flour
     * or water for the first loaf, a new batch of supplies for the autopilot's run.
     */
    fun ingredients(step: TutorialStep, carried: Set<Int>): List<Int> = when {
        BREAD_DOUGH in carried -> emptyList()
        step in MAKE_DOUGH..BAKE_BREAD -> listOf(BUCKET_OF_WATER, POT_OF_FLOUR).filter { it !in carried }
        step in BAKE_ON_AUTOPILOT..WATCH_THE_BAKING && POT_OF_FLOUR !in carried -> SUPPLIES
        else -> emptyList()
    }

    /** The box showing the ingredients just handed back: both together when both were missing. */
    fun ingredientBoxes(given: List<Int>): List<String> = listOfNotNull(
        when {
            given == SUPPLIES -> CHEF_GIVES_SUPPLIES
            BUCKET_OF_WATER in given && POT_OF_FLOUR in given -> CHEF_GIVES_FLOUR_AND_WATER
            POT_OF_FLOUR in given -> CHEF_GIVES_FLOUR
            BUCKET_OF_WATER in given -> CHEF_GIVES_WATER
            else -> null
        },
    )

    /** The tools the Survival Expert makes sure a player still has when talked to; nothing once off the island. */
    fun tools(step: TutorialStep): List<Int> = when {
        step < CUT_TREE || step == DONE -> emptyList()
        step < CATCH_SHRIMP -> listOf(BRONZE_AXE, TINDERBOX)
        else -> listOf(BRONZE_AXE, TINDERBOX, SMALL_FISHING_NET)
    }

    /** The boxes showing the tools just handed back: the axe and tinderbox together when both were missing. */
    fun toolBoxes(given: List<Int>): List<String> = listOfNotNull(
        when {
            BRONZE_AXE in given && TINDERBOX in given -> GIVES_AXE_AND_TINDERBOX
            BRONZE_AXE in given -> GIVES_AXE
            TINDERBOX in given -> GIVES_TINDERBOX
            else -> null
        },
        GIVES_NET.takeIf { SMALL_FISHING_NET in given },
    )

    /** The help box shown while [activity] runs: one of [WOODCUTTING], [FIREMAKING], [FISHING]. */
    fun busyHelp(activity: String): HelpBox = data.busy.getValue(activity)

    /** Chat lines the 2006 island never sent, showing a help box instead. */
    fun quiet(message: String): Boolean = message in data.quietMessages

    /** Clicking the step's flashing tab moves the player on; the inventory is how the Survival Expert's tools arrive. */
    fun tabOpened(step: TutorialStep, tab: TabIndex): Progress? = when {
        data.steps[step]?.flash != tab -> null
        step == OPEN_INVENTORY -> Progress(CUT_TREE, listOf(BRONZE_AXE, TINDERBOX))
        else -> Progress(next(step))
    }

    /** A step with a goal (an idle lesson, an item to carry, run to turn on) moves the player on once it is met. */
    fun goalProgress(step: TutorialStep, progress: PlayerProgress): TutorialStep? =
        data.steps[step]?.goal?.takeIf { it.met(progress) }?.let { next(step) }

    private fun next(step: TutorialStep): TutorialStep = TutorialStep.entries[step.ordinal + 1]

    /** The first log, fire and shrimp each move the player on. */
    fun experienceGained(step: TutorialStep, skill: Int): TutorialStep? =
        FIRST_GAINS[step]?.takeIf { (gainedIn, _) -> gainedIn == skill }?.second

    /** The first shrimp always burns and the second always cooks; the first bread always bakes. */
    fun scriptedCook(step: TutorialStep): ScriptedCook? = when (step) {
        COOK_SHRIMP -> ScriptedCook(RAW_SHRIMPS, Cooking.FIRES, BURNT_FISH, 0.0, SHRIMP_BURNT, FIRE_COOKING, COOK_AGAIN)
        COOK_AGAIN -> ScriptedCook(RAW_SHRIMPS, Cooking.FIRES, SHRIMPS, SHRIMP_EXPERIENCE, SHRIMP_COOKED, FIRE_COOKING, TALK_ABOUT_LOOP)
        BAKE_BREAD -> ScriptedCook(BREAD_DOUGH, Cooking.RANGES, BREAD, BREAD_EXPERIENCE, null, RANGE_COOKING, TALK_ABOUT_SUPPLIES)
        else -> null
    }

    /** Wielding waits until the worn equipment tab exists: an item worn before then could not be taken off again. */
    fun mayWield(step: TutorialStep): Boolean = step == DONE || TabIndex.EQUIPMENT in screen(step).tabs

    fun openDoor(door: Door, step: TutorialStep): PassageOutcome = pass(step, door.opensAt, door.firstPass, door.locked)

    fun climbLadder(ladder: Ladder, step: TutorialStep): PassageOutcome =
        pass(step, ladder.opensAt, ladder.firstPass, ladder.refused)

    private fun pass(step: TutorialStep, opensAt: TutorialStep, firstPass: TutorialStep, refused: String): PassageOutcome =
        if (step < opensAt) {
            PassageOutcome.Locked(refused)
        } else {
            PassageOutcome.Pass(firstPass.takeIf { step == opensAt })
        }

    /** The island's line among the quest journal's stages: yellow like a started quest, green once left behind. */
    fun journalLine(step: TutorialStep): String = (if (step == DONE) "@gre@" else "@yel@") + data.journal

    companion object {
        const val RUNESCAPE_GUIDE = 945
        const val SURVIVAL_EXPERT = 943
        const val MASTER_CHEF = 942
        const val QUEST_GUIDE = 949
        const val MINING_INSTRUCTOR = 948
        const val COPPER_ROCK = 3042
        const val TIN_ROCK = 3043
        val ROCKS = setOf(COPPER_ROCK, TIN_ROCK)
        const val FURNACE = 3044
        const val ANVIL = 2783
        const val BRONZE_PICKAXE = 1265
        const val HAMMER = 2347
        const val COPPER_ORE = 436
        const val TIN_ORE = 438
        const val BRONZE_DAGGER = 1205

        /** The island's line in the quest journal's stages (`idlers.QuestJournal.TUTORIAL_LINE` in `luna-client`). */
        const val JOURNAL_LINE = 30301
        const val BRONZE_AXE = 1351
        const val TINDERBOX = 590
        const val SMALL_FISHING_NET = 303
        const val RAW_SHRIMPS = 317
        const val SHRIMPS = 315
        const val BURNT_FISH = 323
        const val SHRIMP_EXPERIENCE = 30.0
        const val POT_OF_FLOUR = 1933
        const val BUCKET_OF_WATER = 1929
        const val BREAD_DOUGH = 2307
        const val BREAD = 2309
        const val BREAD_EXPERIENCE = 40.0

        /** The flour and water the chef hands over for baking on autopilot. */
        val SUPPLIES: List<Int> = List(4) { POT_OF_FLOUR } + List(4) { BUCKET_OF_WATER }

        const val SHRIMP_BURNT = "shrimp_burnt"
        const val SHRIMP_COOKED = "shrimp_cooked"
        private val FIRE_COOKING = Animations.FIRE_COOKING.id
        private val RANGE_COOKING = Animations.RANGE_COOKING.id

        const val GUIDE_WELCOME = "guide_welcome"
        const val GUIDE_AGAIN = "guide_again"
        const val SURVIVAL_WELCOME = "survival_welcome"
        const val SURVIVAL_INVENTORY = "survival_inventory"
        const val SURVIVAL_FIRE = "survival_fire"
        const val SURVIVAL_SKILLS = "survival_skills"
        const val SURVIVAL_AUTOPILOT = "survival_autopilot"
        const val SURVIVAL_AUTOPILOT_AGAIN = "survival_autopilot_again"
        const val SURVIVAL_FOOD = "survival_food"
        const val SURVIVAL_SHRIMP = "survival_shrimp"
        const val SURVIVAL_LOOP = "survival_loop"
        const val SURVIVAL_LOOP_AGAIN = "survival_loop_again"
        const val SURVIVAL_DONE = "survival_done"
        const val GIVES_AXE_AND_TINDERBOX = "survival_gives_axe_and_tinderbox"
        const val GIVES_AXE = "survival_gives_axe"
        const val GIVES_TINDERBOX = "survival_gives_tinderbox"
        const val GIVES_NET = "survival_gives_net"
        const val CHEF_WELCOME = "chef_welcome"
        const val CHEF_BREAD = "chef_bread"
        const val CHEF_SUPPLIES = "chef_supplies"
        const val CHEF_SUPPLIES_AGAIN = "chef_supplies_again"
        const val CHEF_HELLO = "chef_hello"
        const val CHEF_GIVES_FLOUR_AND_WATER = "chef_gives_flour_and_water"
        const val CHEF_GIVES_FLOUR = "chef_gives_flour"
        const val CHEF_GIVES_WATER = "chef_gives_water"
        const val CHEF_GIVES_SUPPLIES = "chef_gives_supplies"
        const val QUEST_GUIDE_WELCOME = "quest_guide_welcome"
        const val QUEST_GUIDE_OPEN_JOURNAL = "quest_guide_open_journal"
        const val QUEST_GUIDE_JOURNAL = "quest_guide_journal"
        const val QUEST_GUIDE_QUESTS = "quest_guide_quests"
        const val QUEST_GUIDE_STAGES = "quest_guide_stages"
        const val QUEST_GUIDE_CAVES = "quest_guide_caves"

        /** Jagex's lines about quests, ours about stages and resets, then Jagex's pointer to the caves. */
        val QUEST_GUIDE_EXPLAINS = listOf(QUEST_GUIDE_QUESTS, QUEST_GUIDE_STAGES, QUEST_GUIDE_CAVES)

        const val DEZZICK_WELCOME = "dezzick_welcome"
        const val DEZZICK_PROSPECT_AGAIN = "dezzick_prospect_again"
        const val DEZZICK_PROSPECTED = "dezzick_prospected"
        const val DEZZICK_MINING = "dezzick_mining"
        const val DEZZICK_SMELTING = "dezzick_smelting"
        const val DEZZICK_SMITHING = "dezzick_smithing"
        const val DEZZICK_DAGGER = "dezzick_dagger"
        const val DEZZICK_CHAIN = "dezzick_chain"
        const val DEZZICK_CHAIN_AGAIN = "dezzick_chain_again"
        const val DEZZICK_HELLO = "dezzick_hello"
        const val DEZZICK_GIVES_PICKAXE = "dezzick_gives_pickaxe"
        const val DEZZICK_GIVES_HAMMER = "dezzick_gives_hammer"
        const val PROSPECT_COPPER = "prospect_copper"
        const val PROSPECT_TIN = "prospect_tin"
        const val MINE_NOT_READY = "mine_not_ready"
        const val FURNACE_NOT_YET = "furnace_not_yet"
        const val FURNACE_HOW = "furnace_how"
        const val ANVIL_NOT_YET = "anvil_not_yet"
        const val ANVIL_NO_HAMMER = "anvil_no_hammer"
        const val ISLAND_DAGGER_ONLY = "island_dagger_only"

        /** Stands for the player's name in an instructor's lines. */
        const val DISPLAY_NAME = "<displayname>"

        const val WOODCUTTING = "woodcutting"
        const val FIREMAKING = "firemaking"
        const val FISHING = "fishing"
        const val MINING = "mining"
        const val PROSPECTING = "prospecting"

        private val DIALOGUES = listOf(
            GUIDE_WELCOME, GUIDE_AGAIN, SURVIVAL_WELCOME, SURVIVAL_INVENTORY, SURVIVAL_FIRE, SURVIVAL_SKILLS,
            SURVIVAL_AUTOPILOT, SURVIVAL_AUTOPILOT_AGAIN, SURVIVAL_FOOD, SURVIVAL_SHRIMP, SURVIVAL_LOOP, SURVIVAL_LOOP_AGAIN,
            SURVIVAL_DONE, GIVES_AXE_AND_TINDERBOX, GIVES_AXE, GIVES_TINDERBOX, GIVES_NET, CHEF_WELCOME, CHEF_BREAD,
            CHEF_SUPPLIES, CHEF_SUPPLIES_AGAIN, CHEF_HELLO, CHEF_GIVES_FLOUR_AND_WATER, CHEF_GIVES_FLOUR, CHEF_GIVES_WATER,
            CHEF_GIVES_SUPPLIES, QUEST_GUIDE_WELCOME, QUEST_GUIDE_OPEN_JOURNAL, QUEST_GUIDE_JOURNAL, QUEST_GUIDE_QUESTS,
            QUEST_GUIDE_STAGES, QUEST_GUIDE_CAVES, DEZZICK_WELCOME, DEZZICK_PROSPECT_AGAIN, DEZZICK_PROSPECTED, DEZZICK_MINING,
            DEZZICK_SMELTING, DEZZICK_SMITHING, DEZZICK_DAGGER, DEZZICK_CHAIN, DEZZICK_CHAIN_AGAIN, DEZZICK_HELLO,
            DEZZICK_GIVES_PICKAXE, DEZZICK_GIVES_HAMMER, PROSPECT_COPPER, PROSPECT_TIN, MINE_NOT_READY, FURNACE_NOT_YET,
            FURNACE_HOW, ANVIL_NOT_YET, ANVIL_NO_HAMMER, ISLAND_DAGGER_ONLY,
        )

        private val ACTIVITIES = listOf(WOODCUTTING, FIREMAKING, FISHING, MINING, PROSPECTING)

        private val FIRST_GAINS = mapOf(
            CUT_TREE to (Skill.WOODCUTTING to LIGHT_FIRE),
            LIGHT_FIRE to (Skill.FIREMAKING to OPEN_SKILLS),
            CATCH_SHRIMP to (Skill.FISHING to COOK_SHRIMP),
        )
    }
}
