package game.idle.flow

/** Which way a trip gets past a [Passage]. */
enum class Way { THROUGH, UP, DOWN }

/** What a trip passes on its way. */
enum class Passage(val way: Way) {
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
 * What a trip is doing on its way while it lasts, in white on the overlay and the running slot (Q7, Maxime 2026-10-17):
 * opening a door, gate, curtain or trapdoor, getting past a passage, shutting a door behind a flight (web-walker
 * movement Part 5), teleporting, paying the toll or sailing to [Sailing.place] (Part 3c).
 */
sealed interface UnderWay {

    data class Opening(val passage: Passage) : UnderWay

    data class Passing(val passage: Passage) : UnderWay

    data class Closing(val passage: Passage) : UnderWay

    data class Teleporting(val destination: TeleportDestination) : UnderWay

    data object PayingToll : UnderWay

    data class Sailing(val place: String) : UnderWay
}
