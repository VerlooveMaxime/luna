package game.idle.flow

/** Which way a trip gets past a [Passage]. */
enum class Way { THROUGH, UP, DOWN, OVER }

/** What a trip passes on its way. */
sealed interface Passage {
    val way: Way
}

/** The web's doors, gates, curtains, ladders, stairs and trapdoors, each worded by its kind. */
enum class Structure(override val way: Way) : Passage {
    DOOR(Way.THROUGH),
    GATE(Way.THROUGH),
    CURTAIN(Way.THROUGH),
    LADDER_UP(Way.UP),
    LADDER_DOWN(Way.DOWN),
    STAIRS_UP(Way.UP),
    STAIRS_DOWN(Way.DOWN),
    TRAPDOOR(Way.DOWN),
}

/**
 * A cave or dungeon entrance in the cache's own words (Maxime, 2026-10-20), never typed: its option's [verb] and the
 * object's [name], both in lower case, and the [way] past it, which the words name only when [wayNamed]: "Crawling
 * through the cart tunnel", but "Entering the cave entrance" and the red "No way through the cave entrance".
 */
data class EntrancePassage(val verb: String, override val way: Way, val wayNamed: Boolean, val name: String) : Passage {

    companion object {
        private const val CLIMB = "climb"

        private val NAMED_WAYS = mapOf("through" to Way.THROUGH, "up" to Way.UP, "down" to Way.DOWN, "over" to Way.OVER)

        private val VERB_WAYS = mapOf("ascend" to Way.UP, "descend" to Way.DOWN)

        /**
         * An entrance whose first option is [option] on an object called [name]. The way is the one the option names
         * ("Climb-down", "Crawl through"), else its verb's ("Ascend"); a plain "Climb" names its way as a ladder does, down
         * when [climbsDown] (Luna's entrance plays the climb-down animation), else up; any other goes through.
         */
        fun of(option: String, name: String, climbsDown: Boolean): EntrancePassage {
            val words = option.lowercase().split('-', ' ', limit = 2)
            val verb = words.first()
            val named = words.getOrNull(1)?.let(NAMED_WAYS::get)
            val way = when {
                named != null -> named
                verb == CLIMB -> if (climbsDown) Way.DOWN else Way.UP
                else -> VERB_WAYS[verb] ?: Way.THROUGH
            }
            return EntrancePassage(verb, way, wayNamed = named != null || verb == CLIMB, name.lowercase())
        }
    }
}

/**
 * What a trip is doing on its way while it lasts, in white on the overlay and the running slot (Q7, Maxime 2026-10-17):
 * opening a door, gate, curtain or trapdoor, getting past a passage, shutting a door behind a flight (web-walker
 * movement Part 5), teleporting, paying the toll or sailing to [Sailing.place] (Part 3c); fetching [Fetching.items] from
 * the bank on the way (Maxime, 2026-10-20).
 */
sealed interface UnderWay {

    data class Opening(val passage: Passage) : UnderWay

    data class Passing(val passage: Passage) : UnderWay

    data class Closing(val passage: Passage) : UnderWay

    data class Teleporting(val destination: TeleportDestination) : UnderWay

    data object PayingToll : UnderWay

    data class Sailing(val place: String) : UnderWay

    data class Fetching(val items: List<Int>) : UnderWay
}
