package game.harness

/** What the HTTP endpoints can ask of the game. [LunaHarnessApi] is the real one; tests use fakes. */
interface HarnessApi {

    fun world(): WorldView

    fun players(includeBots: Boolean): List<PlayerSummary>

    /** Logs a headless player in, loading their save if one exists. */
    fun login(name: String): PlayerView

    /** Requests a forced logout of a headless player; the save completes asynchronously. */
    fun logout(name: String): ActionView

    fun player(name: String): PlayerView

    fun nearby(name: String, radius: Int, includeInert: Boolean): NearbyView

    fun messages(name: String, since: Long, type: String?): MessagesView

    fun act(name: String, action: PlayerAction): ActionView
}

/** Something an agent asks a player to do, carried out by the same client packet a real click would send. */
sealed interface PlayerAction {

    data class ClickObject(val x: Int, val y: Int, val id: Int, val option: Int) : PlayerAction

    data class ClickNpc(val index: Int, val option: Int) : PlayerAction

    /** [id] is optional: when present it must match the item in [slot]. */
    data class ClickItem(val slot: Int, val id: Int?, val option: Int) : PlayerAction

    data class ClickGroundItem(val x: Int, val y: Int, val id: Int, val option: Int) : PlayerAction

    data class Walk(val x: Int, val y: Int) : PlayerAction

    data class Command(val text: String) : PlayerAction

    data class Chat(val text: String) : PlayerAction

    data class Button(val id: Int) : PlayerAction

    data object ContinueDialogue : PlayerAction

    data object CloseInterface : PlayerAction
}
