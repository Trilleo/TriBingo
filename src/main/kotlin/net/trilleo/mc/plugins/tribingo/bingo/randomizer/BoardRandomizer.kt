package net.trilleo.mc.plugins.tribingo.bingo.randomizer

import net.trilleo.mc.plugins.tribingo.bingo.BingoBoard
import net.trilleo.mc.plugins.tribingo.bingo.BingoObjective

/**
 * Strategy interface for selecting and placing objectives on a [BingoBoard].
 *
 * Each implementation controls the distribution of objectives based on their
 * [net.trilleo.mc.plugins.tribingo.enums.Difficulty]. For example, a "hard"
 * randomizer might bias towards harder objectives and enforce constraints such
 * as always placing an insane objective on each diagonal.
 *
 * ### Adding a new randomizer
 * 1. Create a class implementing this interface.
 * 2. Register it in [BoardRandomizerRegistry] for a corresponding
 *    [net.trilleo.mc.plugins.tribingo.enums.GameDifficulty].
 *
 * @see BoardRandomizerRegistry
 */
interface BoardRandomizer {

    /**
     * Selects and arranges objectives from [available] into a flat list of
     * exactly [BingoBoard.SIZE] × [BingoBoard.SIZE] objectives in row-major
     * order, suitable for constructing a [BingoBoard].
     *
     * Implementations may assume [available] contains enough objectives across
     * all difficulty levels to fill the board. If insufficient objectives of a
     * desired difficulty exist, the implementation should fall back gracefully
     * (e.g. by drawing from adjacent difficulty pools).
     *
     * @param available all registered objectives to choose from
     * @return a list of exactly `SIZE * SIZE` objectives in board cell order
     * @throws IllegalArgumentException if [available] does not have enough
     *         objectives to fill the board
     */
    fun randomize(available: List<BingoObjective>): List<BingoObjective>
}
