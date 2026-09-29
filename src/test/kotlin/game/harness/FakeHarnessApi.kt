package game.harness

import com.google.gson.JsonElement

/** Records every call and answers with fixed views, so route tests can check what reached the API. */
class FakeHarnessApi : HarnessApi {

    val calls = mutableListOf<String>()
    val actions = mutableListOf<Pair<String, PlayerAction>>()

    val worldView = WorldView(tick = 7, playersOnline = 2, botsOnline = 1, harnessPlayersOnline = 1, uptimeSeconds = 30)

    override fun world(): WorldView {
        calls += "world"
        return worldView
    }

    override fun players(includeBots: Boolean): List<PlayerSummary> {
        calls += "players bots=$includeBots"
        return emptyList()
    }

    override fun login(name: String): PlayerView {
        calls += "login $name"
        return playerView(name)
    }

    override fun logout(name: String): ActionView {
        calls += "logout $name"
        return ActionView(name, "logout", "requested")
    }

    override fun player(name: String): PlayerView {
        calls += "player $name"
        return playerView(name)
    }

    override fun nearby(name: String, radius: Int, includeInert: Boolean): NearbyView {
        calls += "nearby $name radius=$radius all=$includeInert"
        return NearbyView(PositionView(0, 0, 0), radius, emptyList(), emptyList(), emptyList(), emptyList())
    }

    override fun messages(name: String, since: Long, type: String?): MessagesView {
        calls += "messages $name since=$since type=$type"
        return MessagesView(next = 0, messages = emptyList())
    }

    override fun act(name: String, action: PlayerAction): ActionView {
        actions += name to action
        return ActionView(name, "act", "queued")
    }

    private fun playerView(name: String) =
        PlayerView(
            name = name,
            index = 1,
            rights = "PLAYER",
            bot = false,
            harness = true,
            position = PositionView(3200, 3200, 0),
            runEnergy = 100.0,
            running = false,
            walking = false,
            combatLevel = 3,
            skills = emptyList(),
            inventory = emptyList(),
            equipment = emptyList(),
            bankItems = 0,
            actions = emptyList(),
            overlays = emptyMap(),
            attributes = emptyMap<String, JsonElement>(),
        )
}
