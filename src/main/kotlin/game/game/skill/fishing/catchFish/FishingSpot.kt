package game.skill.fishing.catchFish

import game.skill.fishing.catchFish.Tool.*

/**
 * An enum representing the kinds of fishing spots, with the [Tool] each of their options fishes with. The click
 * listeners, the fishing bots and the idle fish step all read it.
 *
 * Spots whose fish are not in [Fish] (karambwanji, karambwan, slimy eels, cave eels, frog spawn, lava eels) are left
 * out.
 */
enum class FishingSpot(val ids: Set<Int>,
                       val firstClick: Tool,
                       val secondClick: Tool? = null) {

    /**
     * Net and Bait spots on the coast.
     */
    SEA(ids = setOf(316, 319, 320, 323, 325, 326, 327, 330, 1331),
        firstClick = SMALL_NET,
        secondClick = FISHING_ROD),

    /**
     * Lure and Bait spots in rivers.
     */
    RIVER(ids = setOf(309, 310, 311, 314, 315, 317, 318, 328, 329, 927, 1189, 1190, 3019),
          firstClick = FLY_FISHING_ROD,
          secondClick = FISHING_ROD),

    /**
     * Cage and Harpoon spots.
     */
    CAGE_AND_HARPOON(ids = setOf(312, 321, 324, 1332, 1399, 3804),
                     firstClick = LOBSTER_POT,
                     secondClick = HARPOON),

    /**
     * Net and Harpoon spots: big net fish, and sharks.
     */
    NET_AND_HARPOON(ids = setOf(313, 322, 1191, 1333, 1405, 1406, 3574, 3575),
                    firstClick = BIG_NET,
                    secondClick = SHARK_HARPOON),

    /**
     * The Piscatoris spots: Harpoon first, then Net for monkfish.
     */
    PISCATORIS(ids = setOf(3848),
               firstClick = HARPOON,
               secondClick = MONKFISH_NET),

    /**
     * The fishing contest spots at Hemenster, Bait only.
     */
    CONTEST(ids = setOf(233, 234, 235, 236),
            firstClick = FISHING_ROD),

    /**
     * Tutorial Island's Net spot.
     */
    TUTORIAL(ids = setOf(952),
             firstClick = SMALL_NET);

    companion object {

        /**
         * Retrieves the ids of every spot fished with [tool] on its first option.
         */
        fun firstClickIds(tool: Tool): Set<Int> =
            entries.filter { it.firstClick == tool }.flatMap { it.ids }.toSet()

        /**
         * Retrieves the ids of every spot fished with [tool] on its second option.
         */
        fun secondClickIds(tool: Tool): Set<Int> =
            entries.filter { it.secondClick == tool }.flatMap { it.ids }.toSet()
    }
}
