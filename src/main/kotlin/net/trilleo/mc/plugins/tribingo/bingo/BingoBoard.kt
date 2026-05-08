package net.trilleo.mc.plugins.tribingo.bingo

/**
 * A 5×5 Bingo board backed by a flat, row-major list of [BingoCell]s.
 *
 * The board is immutable after construction; swapping objectives requires
 * replacing the board via [BingoGame.refresh].
 *
 * ### Coordinate system
 * Cell `(row, col)` maps to `cells[row * SIZE + col]`.
 *
 * @param cells flat list of exactly [SIZE] × [SIZE] cells in row-major order
 */
class BingoBoard(
    val cells: List<BingoCell>
) {

    companion object {
        /** The only supported board side-length. */
        const val SIZE = 5
    }

    /** The side-length of this board, always equal to [SIZE]. */
    val size: Int get() = SIZE

    init {
        require(cells.size == SIZE * SIZE) {
            "Expected ${SIZE * SIZE} cells for a ${SIZE}x${SIZE} board, got ${cells.size}"
        }
    }

    /**
     * Returns the [BingoCell] at the given [row] and [col].
     *
     * @param row zero-based row index (`0` until [SIZE])
     * @param col zero-based column index (`0` until [SIZE])
     */
    fun getCell(row: Int, col: Int): BingoCell {
        require(row in 0 until SIZE) { "Row $row out of range [0, $SIZE)" }
        require(col in 0 until SIZE) { "Col $col out of range [0, $SIZE)" }
        return cells[row * SIZE + col]
    }

    // ── Line-completion helpers ───────────────────────────────────────────

    /**
     * Returns `true` when every cell in [row] has been completed by [state].
     *
     * @param state the per-player state to evaluate
     * @param row   zero-based row index (`0` until [SIZE])
     */
    fun isRowComplete(state: BingoPlayerState, row: Int): Boolean =
        (0 until SIZE).all { col -> state.isCompleted(getCell(row, col).cellIndex) }

    /**
     * Returns `true` when every cell in [col] has been completed by [state].
     *
     * @param state the per-player state to evaluate
     * @param col   zero-based column index (`0` until [SIZE])
     */
    fun isColComplete(state: BingoPlayerState, col: Int): Boolean =
        (0 until SIZE).all { row -> state.isCompleted(getCell(row, col).cellIndex) }

    /**
     * Returns `true` when every cell on the main diagonal
     * (top-left → bottom-right) has been completed by [state].
     *
     * @param state the per-player state to evaluate
     */
    fun isDiagMainComplete(state: BingoPlayerState): Boolean =
        (0 until SIZE).all { i -> state.isCompleted(getCell(i, i).cellIndex) }

    /**
     * Returns `true` when every cell on the anti-diagonal
     * (top-right → bottom-left) has been completed by [state].
     *
     * @param state the per-player state to evaluate
     */
    fun isDiagAntiComplete(state: BingoPlayerState): Boolean =
        (0 until SIZE).all { i -> state.isCompleted(getCell(i, SIZE - 1 - i).cellIndex) }

    /**
     * Returns `true` when every cell on the board has been completed by [state].
     *
     * @param state the per-player state to evaluate
     */
    fun isBoardFull(state: BingoPlayerState): Boolean =
        cells.all { state.isCompleted(it.cellIndex) }
}
