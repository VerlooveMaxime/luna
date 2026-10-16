package game.idle.autopilot.reflex

import game.idle.flow.TeleportDestination
import game.idle.flow.Teleports
import game.idle.flow.option.GameNames
import game.idle.flow.option.OptionContext
import game.idle.flow.option.OptionIcon
import game.idle.flow.option.OptionSource
import game.idle.flow.option.StepOption
import io.luna.game.model.mob.Skill

/**
 * Where a reflex can teleport (S13 part 5, Maxime 2026-10-16): Luna's teleport spells outside the wilderness, with the
 * spellbook's icon, the runes they cost and the Magic level they need, greyed below the player's base level whichever
 * levels the builder judges on; then the jewellery's destinations, with the jewellery's picture and how many charged
 * pieces the bank holds.
 */
class TeleportOptions(private val names: GameNames) : OptionSource {

    override fun options(context: OptionContext): List<StepOption> =
        Teleports.all.map { destination ->
            when (destination) {
                is TeleportDestination.Spell -> StepOption(
                    value = Teleports.key(destination),
                    label = Teleports.label(destination, names),
                    icon = OptionIcon.of(Teleports.picture(destination)),
                    note = Teleports.cost(destination.spell, names),
                    blocked = context.facts.lacksBase(Skill.MAGIC, destination.spell.level),
                    level = destination.spell.level,
                )
                is TeleportDestination.Jewellery -> {
                    val banked = destination.jewellery.items.sumOf { context.facts.banked(it) }
                    StepOption(
                        value = Teleports.key(destination),
                        label = Teleports.label(destination, names),
                        icon = OptionIcon.of(Teleports.picture(destination)),
                        note = if (banked == 0) "none in bank" else "$banked in bank",
                        banked = banked,
                        group = JEWELLERY,
                    )
                }
            }
        }

    private companion object {
        /** The jewellery's rows come after the spells', whatever the bank holds. */
        const val JEWELLERY = 1
    }
}
