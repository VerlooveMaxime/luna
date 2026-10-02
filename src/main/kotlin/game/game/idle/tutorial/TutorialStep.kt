package game.idle.tutorial

import game.idle.IdleState

/** Where a player is on Tutorial Island, numbered like the 377 island's progress value (varp 281). */
enum class TutorialStep(val value: Int) {
    DESIGN_CHARACTER(0),
    TALK_TO_GUIDE(1),
    OPEN_HOUSE_DOOR(4),
    FIND_SURVIVAL_EXPERT(10),
    OPEN_INVENTORY(20),
    CUT_TREE(30),
    LIGHT_FIRE(40),
    OPEN_SKILLS(50),
    TALK_ABOUT_FOOD(60),
    CATCH_SHRIMP(70),
    COOK_SHRIMP(80),
    COOK_AGAIN(90),
    LEAVE_SURVIVAL_AREA(120),
    FIND_MASTER_CHEF(130),
    DONE(IdleState.TUTORIAL_DONE),
    ;

    companion object {
        /** The step a saved value stands for: the last one at or below it, so a value whose step is gone still loads. */
        fun of(value: Int): TutorialStep =
            entries.lastOrNull { it.value <= value } ?: throw IllegalArgumentException("No tutorial step below 0: $value")
    }
}
