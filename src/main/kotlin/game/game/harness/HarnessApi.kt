package game.harness

import game.idle.flow.StepSettings

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

    /** Audits the whole world's content: what its spawned npcs and placed objects lack. Holds the game for a moment. */
    fun contentAudit(): ContentAuditView

    /** The player's flow as structured steps, each with how it reads. */
    fun flow(name: String): FlowView

    /** Replaces the player's flow with [steps], checked like the flow builder checks it; refused while it runs. */
    fun replaceFlow(name: String, steps: List<StepSettings>): FlowView
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

    /** A tile picked on the client's world map, as the flow builder asks for one. */
    data class PickTile(val x: Int, val y: Int) : PlayerAction

    /** Rows of the open chatbox search, as the client asks for them when the player types or scrolls. */
    data class SearchPage(val offset: Int, val count: Int, val query: String) : PlayerAction

    /** A row of the open chatbox search clicked, by the index the server gave it. */
    data class SearchPick(val index: Int) : PlayerAction

    /** [text] typed on the open name prompt, then Enter. */
    data class SearchName(val text: String) : PlayerAction

    /** Escape on the open chatbox prompt. */
    data object SearchClose : PlayerAction

    /** [value] typed on the open "Enter amount" prompt, then Enter. */
    data class Amount(val value: Int) : PlayerAction

    /** Tile [from] of the code-defined layer [widget] dragged onto tile [to], as the client sends a dragged tile. */
    data class Arrange(val widget: Int, val from: Int, val to: Int) : PlayerAction

    data object ContinueDialogue : PlayerAction

    data object CloseInterface : PlayerAction
}
