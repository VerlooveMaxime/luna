package game.idle.autopilot.reflex

import engine.combat.prayer.CombatPrayer
import game.idle.flow.Prayers
import game.idle.flow.option.OptionContext
import game.idle.flow.option.OptionIcon
import game.idle.flow.option.OptionSource
import game.idle.flow.option.StepOption
import io.luna.game.model.mob.Skill

/**
 * Luna's prayers, one row each by its tab button, with the tab's lit picture and the level it needs, greyed below the
 * player's base Prayer level whichever levels the builder judges on: points left are the run's business, not the
 * list's (S13 part 3, Maxime 2026-10-16).
 */
object PrayerOptions : OptionSource {

    override fun options(context: OptionContext): List<StepOption> =
        CombatPrayer.entries.map { prayer ->
            StepOption(
                value = Prayers.key(prayer).toString(),
                label = Prayers.named(prayer),
                icon = OptionIcon.of(Prayers.picture(prayer)),
                note = "Prayer ${prayer.level}",
                blocked = context.facts.lacksBase(Skill.PRAYER, prayer.level),
                level = prayer.level,
            )
        }
}
