package net.trilleo.mc.plugins.tribingo.bingo.randomizer

import net.trilleo.mc.plugins.tribingo.bingo.BingoBoard
import net.trilleo.mc.plugins.tribingo.bingo.BingoObjective
import net.trilleo.mc.plugins.tribingo.enums.Difficulty

/**
 * Board randomizer for [net.trilleo.mc.plugins.tribingo.enums.GameDifficulty.HARD] games.
 *
 * Distribution targets (approximate, with fallback when pools are insufficient):
 * - ~10% [Difficulty.EASY] objectives
 * - ~25% [Difficulty.MEDIUM] objectives
 * - ~45% [Difficulty.HARD] objectives
 * - ~20% [Difficulty.INSANE] objectives (roughly 5 cells)
 *
 * ### Positional constraints
 * - Each diagonal is guaranteed to contain at least one [Difficulty.INSANE]
 *   objective. The main diagonal (top-left → bottom-right) and the anti-diagonal
 *   (top-right → bottom-left) each have an insane objective placed on them.
 *   The center cell (which belongs to both diagonals) may satisfy both constraints.
 */
class HardBoardRandomizer : BoardRandomizer {

    override fun randomize(available: List<BingoObjective>): List<BingoObjective> {
        val size = BingoBoard.SIZE
        val needed = size * size
        require(available.size >= needed) {
            "Need at least $needed objectives, only ${available.size} available"
        }

        val byDifficulty = available.groupBy { it.difficulty }
        val easy = byDifficulty[Difficulty.EASY]?.shuffled()?.toMutableList() ?: mutableListOf()
        val medium = byDifficulty[Difficulty.MEDIUM]?.shuffled()?.toMutableList() ?: mutableListOf()
        val hard = byDifficulty[Difficulty.HARD]?.shuffled()?.toMutableList() ?: mutableListOf()
        val insane = byDifficulty[Difficulty.INSANE]?.shuffled()?.toMutableList() ?: mutableListOf()

        // Target counts
        val insaneCount = (needed * 0.20).toInt().coerceAtLeast(2) // at least 2 for diagonals
        val easyCount = (needed * 0.10).toInt()    // 2
        val mediumCount = (needed * 0.25).toInt()   // 6
        val hardCount = needed - easyCount - mediumCount - insaneCount

        // Select objectives by pool
        val selectedInsane = take(insane, insaneCount)
        val selectedEasy = take(easy, easyCount)
        val selectedMedium = take(medium, mediumCount)
        val selectedHard = take(hard, hardCount)

        val allSelected = mutableListOf<BingoObjective>()
        allSelected += selectedInsane
        allSelected += selectedEasy
        allSelected += selectedMedium
        allSelected += selectedHard

        // Fill any remaining slots from unused objectives
        if (allSelected.size < needed) {
            val used = allSelected.toSet()
            val fallback = available.filter { it !in used }.shuffled()
            allSelected += fallback.take(needed - allSelected.size)
        }

        // Place objectives on the board with diagonal constraints
        return placeWithDiagonalConstraints(allSelected.take(needed), size)
    }

    /**
     * Arranges objectives into a flat board layout ensuring that each diagonal
     * contains at least one [Difficulty.INSANE] objective.
     */
    private fun placeWithDiagonalConstraints(
        objectives: List<BingoObjective>,
        size: Int
    ): List<BingoObjective> {
        val board = arrayOfNulls<BingoObjective>(size * size)
        val remaining = objectives.toMutableList()

        // Identify insane objectives from our pool
        val insaneObjs = remaining.filter { it.difficulty == Difficulty.INSANE }.toMutableList()
        val nonInsaneObjs = remaining.filter { it.difficulty != Difficulty.INSANE }.toMutableList()

        // Main diagonal indices: (0,0), (1,1), (2,2), (3,3), (4,4)
        val mainDiag = (0 until size).map { it * size + it }
        // Anti-diagonal indices: (0,4), (1,3), (2,2), (3,1), (4,0)
        val antiDiag = (0 until size).map { it * size + (size - 1 - it) }

        // Place one insane objective on the main diagonal
        if (insaneObjs.isNotEmpty()) {
            val mainPos = mainDiag.shuffled().first()
            board[mainPos] = insaneObjs.removeFirst()
        }

        // Place one insane objective on the anti-diagonal (different cell if possible)
        if (insaneObjs.isNotEmpty()) {
            val openAntiDiag = antiDiag.filter { board[it] == null }
            if (openAntiDiag.isNotEmpty()) {
                val antiPos = openAntiDiag.shuffled().first()
                board[antiPos] = insaneObjs.removeFirst()
            }
        }

        // Place any remaining insane objectives in random open cells
        for (obj in insaneObjs) {
            val openCells = board.indices.filter { board[it] == null }
            if (openCells.isNotEmpty()) {
                board[openCells.shuffled().first()] = obj
            }
        }

        // Fill remaining cells with non-insane objectives (shuffled)
        val shuffledNonInsane = nonInsaneObjs.shuffled().toMutableList()
        for (i in board.indices) {
            if (board[i] == null && shuffledNonInsane.isNotEmpty()) {
                board[i] = shuffledNonInsane.removeFirst()
            }
        }

        // Safety: fill any remaining nulls (shouldn't happen with correct counts)
        for (i in board.indices) {
            if (board[i] == null && shuffledNonInsane.isNotEmpty()) {
                board[i] = shuffledNonInsane.removeFirst()
            }
        }

        @Suppress("UNCHECKED_CAST")
        return board.toList() as List<BingoObjective>
    }

    private fun take(pool: MutableList<BingoObjective>, count: Int): List<BingoObjective> {
        val result = pool.take(count)
        repeat(result.size) { pool.removeFirst() }
        return result
    }
}
