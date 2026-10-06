package game.idle.tutorial

import game.idle.tutorial.TutorialStep.CATCH_SHRIMP
import game.idle.tutorial.TutorialStep.COOK_AGAIN
import game.idle.tutorial.TutorialStep.COOK_SHRIMP
import game.idle.tutorial.TutorialStep.CUT_TREE
import game.idle.tutorial.TutorialStep.DESIGN_CHARACTER
import game.idle.tutorial.TutorialStep.DONE
import game.idle.tutorial.TutorialStep.EXTEND_THE_FLOW
import game.idle.tutorial.TutorialStep.FIND_SURVIVAL_EXPERT
import game.idle.tutorial.TutorialStep.LEAVE_SURVIVAL_AREA
import game.idle.tutorial.TutorialStep.LIGHT_FIRE
import game.idle.tutorial.TutorialStep.OPEN_HOUSE_DOOR
import game.idle.tutorial.TutorialStep.OPEN_IDLE_TAB
import game.idle.tutorial.TutorialStep.OPEN_INVENTORY
import game.idle.tutorial.TutorialStep.OPEN_SKILLS
import game.idle.tutorial.TutorialStep.TALK_ABOUT_AUTOPILOT
import game.idle.tutorial.TutorialStep.TALK_ABOUT_FOOD
import game.idle.tutorial.TutorialStep.TALK_ABOUT_LOOP
import game.idle.tutorial.TutorialStep.TALK_TO_GUIDE
import io.luna.game.model.mob.Skill
import io.luna.game.model.mob.overlay.GameTabSet.TabIndex

/** What the player sees at a step: the help box, the arrow, every side tab so far, and the one flashing. */
data class Screen(val help: HelpBox, val arrow: HintTarget, val tabs: Set<TabIndex>, val flash: TabIndex?)

/** Moving on to [step], with [items] handed over. */
data class Progress(val step: TutorialStep, val items: List<Int> = emptyList())

/** A dialogue to open, and the progress it makes once read, if any. */
data class Talk(val dialogue: String, val progress: Progress?)

sealed interface DoorOutcome {
    data class Locked(val dialogue: String) : DoorOutcome

    data class Pass(val advanceTo: TutorialStep?) : DoorOutcome
}

/** The shrimp the Survival Expert has the player cook: the first always burns, the second always cooks. */
data class ScriptedCook(val burnt: Boolean, val advanceTo: TutorialStep)

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

    /** An idle lesson moves the player on once their autopilot reaches the step's goal. */
    fun lessonProgress(step: TutorialStep, progress: FlowProgress): TutorialStep? =
        data.steps[step]?.goal?.takeIf { it.met(progress) }?.let { next(step) }

    private fun next(step: TutorialStep): TutorialStep = TutorialStep.entries[step.ordinal + 1]

    /** The first log, fire and shrimp each move the player on. */
    fun experienceGained(step: TutorialStep, skill: Int): TutorialStep? =
        FIRST_GAINS[step]?.takeIf { (gainedIn, _) -> gainedIn == skill }?.second

    fun cookShrimp(step: TutorialStep): ScriptedCook? = when (step) {
        COOK_SHRIMP -> ScriptedCook(burnt = true, advanceTo = COOK_AGAIN)
        COOK_AGAIN -> ScriptedCook(burnt = false, advanceTo = TALK_ABOUT_LOOP)
        else -> null
    }

    /** Wielding waits until the worn equipment tab exists: an item worn before then could not be taken off again. */
    fun mayWield(step: TutorialStep): Boolean = step == DONE || TabIndex.EQUIPMENT in screen(step).tabs

    fun openDoor(door: Door, step: TutorialStep): DoorOutcome =
        if (step < door.opensAt) {
            DoorOutcome.Locked(door.locked)
        } else {
            DoorOutcome.Pass(door.firstPass.takeIf { step == door.opensAt })
        }

    companion object {
        const val RUNESCAPE_GUIDE = 945
        const val SURVIVAL_EXPERT = 943
        const val BRONZE_AXE = 1351
        const val TINDERBOX = 590
        const val SMALL_FISHING_NET = 303
        const val RAW_SHRIMPS = 317
        const val SHRIMPS = 315
        const val BURNT_FISH = 323
        const val SHRIMP_EXPERIENCE = 30.0

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

        const val WOODCUTTING = "woodcutting"
        const val FIREMAKING = "firemaking"
        const val FISHING = "fishing"

        private val DIALOGUES = listOf(
            GUIDE_WELCOME, GUIDE_AGAIN, SURVIVAL_WELCOME, SURVIVAL_INVENTORY, SURVIVAL_FIRE, SURVIVAL_SKILLS,
            SURVIVAL_AUTOPILOT, SURVIVAL_AUTOPILOT_AGAIN, SURVIVAL_FOOD, SURVIVAL_SHRIMP, SURVIVAL_LOOP, SURVIVAL_LOOP_AGAIN,
            SURVIVAL_DONE, GIVES_AXE_AND_TINDERBOX, GIVES_AXE, GIVES_TINDERBOX, GIVES_NET,
        )

        private val ACTIVITIES = listOf(WOODCUTTING, FIREMAKING, FISHING)

        private val FIRST_GAINS = mapOf(
            CUT_TREE to (Skill.WOODCUTTING to LIGHT_FIRE),
            LIGHT_FIRE to (Skill.FIREMAKING to OPEN_SKILLS),
            CATCH_SHRIMP to (Skill.FISHING to COOK_SHRIMP),
        )
    }
}
