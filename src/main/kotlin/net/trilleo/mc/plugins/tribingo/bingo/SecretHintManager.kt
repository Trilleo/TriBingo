package net.trilleo.mc.plugins.tribingo.bingo

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.plugin.java.JavaPlugin

/**
 * Singleton that manages the progressive reveal of hints for [SecretBingoObjective]s.
 *
 * Hints are revealed **globally** (all players see the same hints at the same
 * time). The reveal schedule is based on elapsed game time:
 *
 * For an objective with N hints, the reveal thresholds are at fractions
 * `1/(N+1), 2/(N+1), ..., N/(N+1)` of the total timer duration.
 *
 * For example, with 3 hints and a 60-minute timer:
 * - Hint 1 revealed at 25% (15 min elapsed)
 * - Hint 2 revealed at 50% (30 min elapsed)
 * - Hint 3 revealed at 75% (45 min elapsed)
 *
 * ### Chat notification
 * When a new hint is revealed, a brief chat message is broadcast to all online
 * players: `"[Bingo] A new hint is available for a secret objective! Check the board."`
 * Detailed hint text is only shown in the board GUI item lore.
 *
 * ### Lifecycle
 * - Tick via [tick] from [BingoManager]'s countdown each second.
 * - Clear via [reset] when the game ends, is stopped, or the server shuts down.
 */
object SecretHintManager {

    private lateinit var plugin: JavaPlugin

    /**
     * Tracks the number of hints currently revealed for each secret objective,
     * keyed by [BingoObjective.id].
     */
    private val revealedCounts = mutableMapOf<String, Int>()

    /**
     * Stores the plugin reference. Call once during startup.
     */
    fun init(plugin: JavaPlugin) {
        this.plugin = plugin
    }

    /**
     * Called every countdown tick (once per second) to check if any new hints
     * should be revealed based on the elapsed fraction of game time.
     *
     * @param totalSeconds the total timer duration configured for this game
     * @param remaining    the current remaining seconds on the countdown
     */
    fun tick(totalSeconds: Int, remaining: Int) {
        if (totalSeconds <= 0) return

        val elapsed = (totalSeconds - remaining).coerceAtLeast(0)
        val fraction = elapsed.toDouble() / totalSeconds.toDouble()

        val game = BingoManager.currentGame ?: return
        val secrets = game.board.cells
            .map { it.objective }
            .filterIsInstance<SecretBingoObjective>()

        if (secrets.isEmpty()) return

        var anyNewHint = false

        for (secret in secrets) {
            val totalHints = secret.hints.size
            if (totalHints == 0) continue

            // Calculate how many hints should be revealed at this point in time
            var shouldReveal = 0
            for (i in 1..totalHints) {
                val threshold = i.toDouble() / (totalHints + 1).toDouble()
                if (fraction >= threshold) {
                    shouldReveal = i
                }
            }

            val currentRevealed = revealedCounts[secret.id] ?: 0
            if (shouldReveal > currentRevealed) {
                revealedCounts[secret.id] = shouldReveal
                anyNewHint = true
            }
        }

        if (anyNewHint) {
            broadcastHintAvailable()
        }
    }

    /**
     * Returns the list of hints currently revealed for the given secret objective.
     *
     * @param objective the secret objective to query
     * @return a sublist of [SecretBingoObjective.hints] up to the revealed count
     */
    fun getRevealedHints(objective: SecretBingoObjective): List<String> {
        val count = revealedCounts[objective.id] ?: 0
        if (count <= 0) return emptyList()
        return objective.hints.take(count)
    }

    /**
     * Returns the number of hints currently revealed for the given objective ID.
     *
     * @param objectiveId the objective's unique ID
     * @return the number of revealed hints (0 if none or not a secret objective)
     */
    fun getRevealedHintCount(objectiveId: String): Int =
        revealedCounts[objectiveId] ?: 0

    /**
     * Clears all revealed hint state. Called when the game ends, resets, or the
     * server shuts down.
     */
    fun reset() {
        revealedCounts.clear()
    }

    /**
     * Broadcasts a brief chat notification that a new hint is available.
     */
    private fun broadcastHintAvailable() {
        if (!::plugin.isInitialized) return
        val msg = Component.text()
            .append(Component.text("[Bingo] ", NamedTextColor.GOLD))
            .append(
                Component.text(
                    "A new hint is available for a secret objective! Check the board.",
                    NamedTextColor.YELLOW
                )
            )
            .build()
        plugin.server.onlinePlayers.forEach { it.sendMessage(msg) }
    }
}
