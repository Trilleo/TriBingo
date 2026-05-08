package net.trilleo.mc.plugins.tribingo.bingo

import java.util.*

/**
 * Per-player mutable state for a single [BingoGame] session.
 *
 * Tracks which cells have been completed, stores intermediate progress
 * counters for objectives that require multiple steps (e.g. "kill 5 zombies"),
 * records which lines (rows/columns/diagonals) have been completed, and
 * accumulates the player's point total.
 *
 * @param uuid the unique identifier of the player this state belongs to
 */
class BingoPlayerState(val uuid: UUID) {

    /**
     * Set of completed [BingoCell.cellIndex] values for this player.
     *
     * Populated by [markCompleted] and cleared by [reset].
     */
    val completedCells: MutableSet<Int> = mutableSetOf()

    /**
     * Per-objective intermediate progress counters, keyed by
     * [BingoObjective.id].
     *
     * For example, a "kill 5 zombies" objective stores the current kill count
     * here. Cleared by [reset].
     */
    val progressData: MutableMap<String, Int> = mutableMapOf()

    /**
     * Set of line keys that have already been awarded their bonus points.
     *
     * Keys follow the pattern `"row_N"`, `"col_N"`, `"diag_main"`, or
     * `"diag_anti"`.  Used to avoid awarding the same line bonus twice.
     * Cleared by [reset].
     */
    val completedLines: MutableSet<String> = mutableSetOf()

    /**
     * The player's current point total for this game session.
     *
     * Incremented by [BingoManager.checkCompletion] when objectives and lines
     * are completed.  Reset to `0` by [reset].
     */
    var points: Int = 0

    // ── Completion helpers ───────────────────────────────────────────────

    /** Returns `true` when [cellIndex] has been marked complete. */
    fun isCompleted(cellIndex: Int): Boolean = cellIndex in completedCells

    /** Marks [cellIndex] as complete. */
    fun markCompleted(cellIndex: Int) {
        completedCells.add(cellIndex)
    }

    // ── Progress helpers ─────────────────────────────────────────────────

    /**
     * Returns the current progress counter for the objective identified by
     * [objectiveId], or `0` if no counter has been recorded yet.
     */
    fun getProgress(objectiveId: String): Int =
        progressData.getOrDefault(objectiveId, 0)

    /**
     * Sets the progress counter for [objectiveId] to [value].
     */
    fun setProgress(objectiveId: String, value: Int) {
        progressData[objectiveId] = value
    }

    // ── Lifecycle ────────────────────────────────────────────────────────

    /**
     * Clears all completion, progress, line-bonus, and points data.
     *
     * Called by [BingoGame.reset] to wipe per-player state for a new game.
     */
    fun reset() {
        completedCells.clear()
        progressData.clear()
        completedLines.clear()
        points = 0
    }
}
