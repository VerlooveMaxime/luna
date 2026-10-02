package game.idle.tutorial

import game.idle.IdleState

/** Where a player is on Tutorial Island, numbered like the 377 island's progress value (varp 281). */
enum class TutorialStep(val value: Int) {
    DESIGN_CHARACTER(0),
    TALK_TO_GUIDE(1),
    OPEN_HOUSE_DOOR(4),
    FIND_SURVIVAL_EXPERT(10),
    DONE(IdleState.TUTORIAL_DONE),
    ;

    companion object {
        /** The step a saved value stands for: the last one at or below it, so a value whose step is gone still loads. */
        fun of(value: Int): TutorialStep =
            entries.lastOrNull { it.value <= value } ?: throw IllegalArgumentException("No tutorial step below 0: $value")
    }
}
