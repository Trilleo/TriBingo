package net.trilleo.mc.plugins.tribingo.bingo.randomizer

import net.trilleo.mc.plugins.tribingo.bingo.BingoBoard
import net.trilleo.mc.plugins.tribingo.bingo.BingoObjective
import net.trilleo.mc.plugins.tribingo.enums.Difficulty

/**
 * Board randomizer for [net.trilleo.mc.plugins.tribingo.enums.GameDifficulty.EASY] games.
 *
 * Distribution targets (approximate, with fallback when pools are insufficient):
 * - ~60% [Difficulty.EASY] objectives
 * - ~30% [Difficulty.MEDIUM] objectives
 * - ~10% [Difficulty.HARD] objectives
 * - No [Difficulty.INSANE] objectives
 *
 * No positional constraints are enforced; objectives are shuffled randomly
 * after selection.
 */
class EasyBoardRandomizer : BoardRandomizer {

    override fun randomize(available: List<BingoObjective>): List<BingoObjective> {
        val needed = BingoBoard.SIZE * BingoBoard.SIZE
        require(available.size >= needed) {
            "Need at least $needed objectives, only ${available.size} available"
        }

        val byDifficulty = available.groupBy { it.difficulty }
        val easy = byDifficulty[Difficulty.EASY]?.shuffled()?.toMutableList() ?: mutableListOf()
        val medium = byDifficulty[Difficulty.MEDIUM]?.shuffled()?.toMutableList() ?: mutableListOf()
        val hard = byDifficulty[Difficulty.HARD]?.shuffled()?.toMutableList() ?: mutableListOf()

        // Target counts
        val easyCount = (needed * 0.60).toInt()   // 15
        val mediumCount = (needed * 0.30).toInt()  // 7
        val hardCount = needed - easyCount - mediumCount // 3

        val selected = mutableListOf<BingoObjective>()
        selected += take(easy, easyCount)
        selected += take(medium, mediumCount)
        selected += take(hard, hardCount)

        // Fill any remaining slots from unused objectives (excluding insane)
        if (selected.size < needed) {
            val used = selected.toSet()
            val fallback = available
                .filter { it !in used && it.difficulty != Difficulty.INSANE }
                .shuffled()
            selected += fallback.take(needed - selected.size)
        }

        // Final safety: if still not enough, use any available
        if (selected.size < needed) {
            val used = selected.toSet()
            selected += available.filter { it !in used }.shuffled().take(needed - selected.size)
        }

        return selected.shuffled().take(needed)
    }

    private fun take(pool: MutableList<BingoObjective>, count: Int): List<BingoObjective> {
        val result = pool.take(count)
        repeat(result.size) { pool.removeFirst() }
        return result
    }
}
