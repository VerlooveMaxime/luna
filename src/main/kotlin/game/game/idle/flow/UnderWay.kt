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
 * opening a door, gate, curtain or trapdoor, or getting past a passage.
 */
sealed interface UnderWay {

    data class Opening(val passage: Passage) : UnderWay

    data class Passing(val passage: Passage) : UnderWay
}
