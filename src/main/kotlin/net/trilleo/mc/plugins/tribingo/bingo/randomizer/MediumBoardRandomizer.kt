package net.trilleo.mc.plugins.tribingo.bingo.randomizer

import net.trilleo.mc.plugins.tribingo.bingo.BingoBoard
import net.trilleo.mc.plugins.tribingo.bingo.BingoObjective
import net.trilleo.mc.plugins.tribingo.enums.Difficulty

/**
 * Board randomizer for [net.trilleo.mc.plugins.tribingo.enums.GameDifficulty.MEDIUM] games.
 *
 * Distribution targets (approximate, with fallback when pools are insufficient):
 * - ~30% [Difficulty.EASY] objectives
 * - ~40% [Difficulty.MEDIUM] objectives
 * - ~25% [Difficulty.HARD] objectives
 * - ~5% [Difficulty.INSANE] objectives (roughly 1 cell)
 *
 * No strict positional constraints are enforced; objectives are shuffled
 * randomly after selection.
 */
class MediumBoardRandomizer : BoardRandomizer {

    override fun randomize(available: List<BingoObjective>): List<BingoObjective> {
        val needed = BingoBoard.SIZE * BingoBoard.SIZE
        require(available.size >= needed) {
            "Need at least $needed objectives, only ${available.size} available"
        }

        val byDifficulty = available.groupBy { it.difficulty }
        val easy = byDifficulty[Difficulty.EASY]?.shuffled()?.toMutableList() ?: mutableListOf()
        val medium = byDifficulty[Difficulty.MEDIUM]?.shuffled()?.toMutableList() ?: mutableListOf()
        val hard = byDifficulty[Difficulty.HARD]?.shuffled()?.toMutableList() ?: mutableListOf()
        val insane = byDifficulty[Difficulty.INSANE]?.shuffled()?.toMutableList() ?: mutableListOf()

        // Target counts
        val insaneCount = 1
        val easyCount = (needed * 0.30).toInt()    // 7
        val mediumCount = (needed * 0.40).toInt()   // 10
        val hardCount = needed - easyCount - mediumCount - insaneCount // 7

        val selected = mutableListOf<BingoObjective>()
        selected += take(insane, insaneCount)
        selected += take(easy, easyCount)
        selected += take(medium, mediumCount)
        selected += take(hard, hardCount)

        // Fill any remaining slots from unused objectives
        if (selected.size < needed) {
            val used = selected.toSet()
            val fallback = available.filter { it !in used }.shuffled()
            selected += fallback.take(needed - selected.size)
        }

        return selected.shuffled().take(needed)
    }

    private fun take(pool: MutableList<BingoObjective>, count: Int): List<BingoObjective> {
        val result = pool.take(count)
        repeat(result.size) { pool.removeFirst() }
        return result
    }
}
