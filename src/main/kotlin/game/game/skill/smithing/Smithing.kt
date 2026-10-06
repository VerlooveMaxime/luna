package game.skill.smithing

import io.luna.game.model.def.GameObjectDefinition

/**
 * Holds important constants and utility functions related to the Smithing skill.
 *
 * @author lare96
 */
object Smithing {

    /**
     * The hammer item ID.
     */
    const val HAMMER = 2347

    /**
     * All anvil object IDs.
     */
    val ANVIL_OBJECTS = setOf(2782, 2783, 4306, 6150)

    /**
     * Tutorial Island's furnace, which only has a "Use" action: ore is used on it.
     */
    const val TUTORIAL_FURNACE = 3044

    /**
     * Retrieves all object IDs with the interaction action "Smelt," and Tutorial Island's furnace.
     */
    val FURNACE_OBJECTS =
        GameObjectDefinition.ALL.filter { it.actions.contains("Smelt") }.map { it.id() }.toSet() + TUTORIAL_FURNACE
}