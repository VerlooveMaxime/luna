package game.idle.experience

import game.idle.idleState
import io.luna.game.model.mob.ExperienceModifier
import io.luna.game.model.mob.Player
import io.luna.util.GsonUtils
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/** The share of real experience granted, from `data/idle/experience.jsonc`. Fields default for Gson's no-arg constructor. */
data class ExperienceRates(
    val idleRate: Double = 0.10,
    val activeRate: Double = 0.30,
) {

    /** The rate while the player's autopilot runs ([idle]) or while they play themselves. */
    fun rate(idle: Boolean): Double = if (idle) idleRate else activeRate

    fun validated(): ExperienceRates {
        require(idleRate > 0) { "idle_rate must be positive, got $idleRate" }
        require(activeRate > 0) { "active_rate must be positive, got $activeRate" }
        return this
    }

    companion object {
        val PATH: Path = Paths.get("data", "idle", "experience.jsonc")

        fun parse(jsonc: String): ExperienceRates = GsonUtils.GSON.fromJson(jsonc, ExperienceRates::class.java)

        fun load(path: Path): ExperienceRates = parse(Files.readString(path)).validated()
    }
}

/**
 * The world's experience modifier: every gain scaled by the player's rate (idle while their autopilot runs), then
 * handed to [next] (the island's level cap).
 */
class RatedExperience(private val rates: ExperienceRates, private val next: ExperienceModifier) : ExperienceModifier {

    override fun modify(player: Player, skill: Int, amount: Double): Double =
        next.modify(player, skill, amount * rates.rate(idle = player.idleState.running))
}
