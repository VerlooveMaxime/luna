package game.idle.tutorial

import io.luna.game.model.mob.overlay.GameTabSet.TabIndex

/** What the player sees at a step: the help box, the arrow, and every side tab that has appeared so far. */
data class Screen(val help: HelpBox, val arrow: HintTarget, val tabs: Set<TabIndex>)

/** A dialogue to open, and the step it moves the player on to once read, if any. */
data class Talk(val dialogue: String, val advanceTo: TutorialStep?)

sealed interface DoorOutcome {
    data class Locked(val dialogue: String) : DoorOutcome

    data class Pass(val advanceTo: TutorialStep?) : DoorOutcome
}

/** The island's rules: what each step shows and what moves a player from one step to the next. */
class TutorialScript(private val data: TutorialData) {

    init {
        val missing = listOf(GUIDE_WELCOME, GUIDE_AGAIN).filter { it !in data.dialogues }
        require(missing.isEmpty()) { "The tutorial needs dialogues that are missing: $missing" }
    }

    fun screen(step: TutorialStep): Screen {
        val screen = data.steps.getValue(step)
        val tabs = data.steps.filterKeys { it <= step }.values.flatMap { it.tabs }.toSet()
        return Screen(screen.help, screen.arrow, tabs)
    }

    /** Closing the character designer, by accepting or by walking away, starts the tutorial proper. */
    fun designerClosed(step: TutorialStep): TutorialStep? =
        TutorialStep.TALK_TO_GUIDE.takeIf { step == TutorialStep.DESIGN_CHARACTER }

    fun talkToGuide(step: TutorialStep): Talk =
        if (step < TutorialStep.OPEN_HOUSE_DOOR) {
            Talk(GUIDE_WELCOME, advanceTo = TutorialStep.OPEN_HOUSE_DOOR)
        } else {
            Talk(GUIDE_AGAIN, advanceTo = null)
        }

    fun openDoor(door: Door, step: TutorialStep): DoorOutcome =
        if (step < door.opensAt) {
            DoorOutcome.Locked(door.locked)
        } else {
            DoorOutcome.Pass(door.firstPass.takeIf { step == door.opensAt })
        }

    companion object {
        const val RUNESCAPE_GUIDE = 945
        const val GUIDE_WELCOME = "guide_welcome"
        const val GUIDE_AGAIN = "guide_again"
    }
}
