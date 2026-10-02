package io.luna.game.model.mob;

import io.luna.game.model.World;

/**
 * Adjusts the experience a player is about to gain in a skill, after the global experience multiplier. Bots are never
 * adjusted. Plugins install one with {@link World#setExperienceModifier(ExperienceModifier)}.
 */
@FunctionalInterface
public interface ExperienceModifier {

    /**
     * Grants every amount unchanged.
     */
    ExperienceModifier NONE = (player, skill, amount) -> amount;

    /**
     * @param player The player gaining experience.
     * @param skill The skill identifier, see {@link Skill}.
     * @param amount The experience after the global multiplier.
     * @return The experience to grant; nothing is granted unless it is positive.
     */
    double modify(Player player, int skill, double amount);
}
