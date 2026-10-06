package game.idle.tutorial

import game.idle.IdleState

/**
 * Where a player is on Tutorial Island, numbered like the 377 island's progress value (varp 281). The idle lessons
 * are ours and use values between LostCity's (51-55, 100-104, 162-165).
 */
enum class TutorialStep(val value: Int) {
    DESIGN_CHARACTER(0),
    TALK_TO_GUIDE(1),
    OPEN_HOUSE_DOOR(4),
    FIND_SURVIVAL_EXPERT(10),
    OPEN_INVENTORY(20),
    CUT_TREE(30),
    LIGHT_FIRE(40),
    OPEN_SKILLS(50),
    TALK_ABOUT_AUTOPILOT(51),
    OPEN_IDLE_TAB(52),
    BUILD_FIRST_FLOW(53),
    WATCH_THE_AUTOPILOT(54),
    STOP_THE_AUTOPILOT(55),
    TALK_ABOUT_FOOD(60),
    CATCH_SHRIMP(70),
    COOK_SHRIMP(80),
    COOK_AGAIN(90),
    TALK_ABOUT_LOOP(100),
    EXTEND_THE_FLOW(102),
    WATCH_FULL_LOOP(103),
    STOP_FULL_LOOP(104),
    LEAVE_SURVIVAL_AREA(120),
    FIND_MASTER_CHEF(130),
    TALK_TO_CHEF(140),
    MAKE_DOUGH(150),
    BAKE_BREAD(160),
    TALK_ABOUT_SUPPLIES(162),
    BAKE_ON_AUTOPILOT(163),
    WATCH_THE_BAKING(164),
    STOP_THE_BAKING(165),
    OPEN_MUSIC(170),
    LEAVE_CHEF(180),
    OPEN_PLAYER_CONTROLS(190),
    TURN_RUN_ON(195),
    FIND_QUEST_GUIDE(200),
    TALK_TO_QUEST_GUIDE(220),
    OPEN_QUEST_JOURNAL(230),
    TALK_ABOUT_QUESTS(240),
    ENTER_MINE(250),
    TALK_TO_MINING_INSTRUCTOR(260),
    DONE(IdleState.TUTORIAL_DONE),
    ;

    companion object {
        /** The step a saved value stands for: the last one at or below it, so a value whose step is gone still loads. */
        fun of(value: Int): TutorialStep =
            entries.lastOrNull { it.value <= value } ?: throw IllegalArgumentException("No tutorial step below 0: $value")
    }
}
