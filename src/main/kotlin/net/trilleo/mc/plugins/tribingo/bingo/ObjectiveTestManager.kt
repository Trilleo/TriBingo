package net.trilleo.mc.plugins.tribingo.bingo

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.trilleo.mc.plugins.tribingo.bingo.registry.BingoObjectiveRegistry
import net.trilleo.mc.plugins.tribingo.utils.sendPrefixed
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask
import java.util.*

/**
 * Manages objective test sessions for players.
 *
 * When a player runs `/bingo test <objective_id>`, a test session is created
 * that provides an isolated [BingoPlayerState] for the objective's event
 * handlers to track progress against. The test system reports progress via
 * the action bar and sends a message when the objective's completion checker
 * reports success.
 *
 * Test sessions are completely independent of the Bingo board and game state.
 * They exist solely to verify that an objective's completion logic works as
 * intended.
 *
 * ### Lifecycle
 * Call [init] once during plugin startup (after [BingoObjectiveRegistry] is ready).
 * Call [shutdown] during plugin disable to cancel all active sessions.
 */
object ObjectiveTestManager {

    private lateinit var plugin: JavaPlugin

    /**
     * Represents an active test session for a single player.
     *
     * @property objective the objective being tested
     * @property state     isolated player state for tracking test progress
     */
    data class TestSession(
        val objective: BingoObjective,
        val state: BingoPlayerState
    )

    /** Active test sessions keyed by player UUID. */
    private val sessions = mutableMapOf<UUID, TestSession>()

    /** The repeating task that displays progress on the action bar. */
    private var progressTask: BukkitTask? = null

    /**
     * Initialises the test manager and starts the progress display task.
     *
     * @param plugin the owning plugin instance
     */
    fun init(plugin: JavaPlugin) {
        this.plugin = plugin
        startProgressTask()
    }

    /**
     * Shuts down all active test sessions and cancels the progress task.
     */
    fun shutdown() {
        sessions.clear()
        progressTask?.cancel()
        progressTask = null
    }

    // ── Session management ────────────────────────────────────────────────

    /**
     * Starts a test session for [player] on the objective identified by
     * [objectiveId].
     *
     * If the player already has an active session, it is stopped first.
     *
     * @param player      the player starting the test
     * @param objectiveId the [BingoObjective.id] to test
     * @return `true` if the session was started successfully; `false` if the
     *         objective ID is not registered
     */
    fun startTest(player: Player, objectiveId: String): Boolean {
        val objective = BingoObjectiveRegistry.get(objectiveId) ?: return false
        stopTest(player)
        val state = BingoPlayerState(player.uniqueId)
        sessions[player.uniqueId] = TestSession(objective, state)
        return true
    }

    /**
     * Stops the active test session for [player], if any.
     *
     * @param player the player whose test session to stop
     * @return `true` if a session was active and has been stopped; `false` if
     *         no session existed
     */
    fun stopTest(player: Player): Boolean {
        val session = sessions.remove(player.uniqueId) ?: return false
        // Clear the action bar
        player.sendActionBar(Component.empty())
        return true
    }

    /**
     * Returns `true` if [player] has an active test session.
     */
    fun isTesting(player: Player): Boolean = player.uniqueId in sessions

    /**
     * Returns the active [TestSession] for [player], or `null` if not testing.
     */
    fun getSession(player: Player): TestSession? = sessions[player.uniqueId]

    /**
     * Returns the [BingoPlayerState] for the player's test session if the
     * player is currently testing the objective identified by [objectiveId].
     *
     * This is called by objective event handlers to determine whether to
     * process events for a test session.
     *
     * @param playerUuid  the player's UUID
     * @param objectiveId the objective ID being checked
     * @return the test session's [BingoPlayerState], or `null` if the player
     *         is not testing this objective
     */
    fun getTestState(playerUuid: UUID, objectiveId: String): BingoPlayerState? {
        val session = sessions[playerUuid] ?: return null
        return if (session.objective.id == objectiveId) session.state else null
    }

    /**
     * Called when an objective's completion check passes during a test session.
     *
     * Notifies the player that the objective's checker confirmed completion,
     * and automatically stops the test.
     *
     * @param player    the player who completed the test objective
     * @param objective the objective that was completed
     */
    fun onTestCompleted(player: Player, objective: BingoObjective) {
        val session = sessions[player.uniqueId] ?: return
        if (session.objective.id != objective.id) return

        player.sendPrefixed(
            "<green><bold>✓ TEST PASSED!</bold> <green>Objective <white>${objective.id}" +
                    " <green>completion checker confirmed success."
        )
        // Show final progress info
        val state = session.state
        val progressInfo = state.getProgress(objective.id)
        if (progressInfo > 0) {
            player.sendPrefixed("<gray>Final progress value: <white>$progressInfo")
        }
        val steps = state.getSteps(objective.id)
        if (steps.isNotEmpty()) {
            player.sendPrefixed("<gray>Steps completed: <white>${steps.joinToString(" → ")}")
        }

        sessions.remove(player.uniqueId)
        player.sendActionBar(Component.empty())
    }

    // ── Progress display ──────────────────────────────────────────────────

    /**
     * Starts the repeating task that displays progress on the action bar
     * for all players with active test sessions (every 10 ticks = 0.5s).
     */
    private fun startProgressTask() {
        progressTask?.cancel()
        progressTask = plugin.server.scheduler.runTaskTimer(plugin, Runnable {
            for ((uuid, session) in sessions) {
                val player = plugin.server.getPlayer(uuid) ?: continue
                val bar = buildProgressBar(player, session)
                player.sendActionBar(bar)
            }
        }, 0L, 10L)
    }

    /**
     * Builds the action bar [Component] showing test progress for [session].
     */
    private fun buildProgressBar(player: Player, session: TestSession): Component {
        val objective = session.objective
        val state = session.state

        val builder = Component.text()
            .append(Component.text("⚙ Testing: ", NamedTextColor.GOLD))
            .append(objective.name.color(NamedTextColor.WHITE).decoration(TextDecoration.BOLD, true))
            .append(Component.text(" | ", NamedTextColor.DARK_GRAY))

        // Show progress counter if available
        val progress = state.getProgress(objective.id)
        if (progress > 0) {
            builder.append(Component.text("Progress: $progress", NamedTextColor.YELLOW))
        } else {
            // Show step progress for sequential objectives
            val steps = state.getSteps(objective.id)
            if (steps.isNotEmpty()) {
                builder.append(Component.text("Steps: ${steps.size}", NamedTextColor.YELLOW))
            } else {
                builder.append(Component.text("Waiting...", NamedTextColor.GRAY))
            }
        }

        // Check if completed
        val completed = objective.isCompletedBy(player, state)
        if (completed) {
            builder.append(Component.text(" | ", NamedTextColor.DARK_GRAY))
            builder.append(Component.text("✓ DONE", NamedTextColor.GREEN).decoration(TextDecoration.BOLD, true))
        }

        return builder.build()
    }
}
