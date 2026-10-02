package game.idle.tutorial

import io.luna.game.model.mob.ExperienceModifier
import io.luna.game.model.mob.Player

/**
 * On Tutorial Island a skill stops gaining experience once it is level [MAX_LEVEL], as on the 2006 island: the gain
 * that reaches the level may pass it, nothing comes after. Off the island experience is untouched.
 */
class TutorialExperience : ExperienceModifier {

    override fun modify(player: Player, skill: Int, amount: Double): Double =
        if (capped(player.tutorialStep, player.skills.getSkill(skill).staticLevel)) 0.0 else amount

    companion object {
        const val MAX_LEVEL = 3

        fun capped(step: TutorialStep, staticLevel: Int): Boolean = step != TutorialStep.DONE && staticLevel >= MAX_LEVEL
    }
}
