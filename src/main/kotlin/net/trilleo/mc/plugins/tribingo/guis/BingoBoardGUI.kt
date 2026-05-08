package net.trilleo.mc.plugins.tribingo.guis

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.trilleo.mc.plugins.tribingo.Main
import net.trilleo.mc.plugins.tribingo.bingo.BingoBoard
import net.trilleo.mc.plugins.tribingo.bingo.BingoManager
import net.trilleo.mc.plugins.tribingo.bingo.BingoPlayerState
import net.trilleo.mc.plugins.tribingo.enums.FillMode
import net.trilleo.mc.plugins.tribingo.registration.PluginGUI
import net.trilleo.mc.plugins.tribingo.utils.itemStack
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack

/**
 * Chest-based Bingo board viewer for the fixed 5×5 board.
 *
 * Displays the current [net.trilleo.mc.plugins.tribingo.bingo.BingoGame]'s board
 * in a 6-row (54-slot) double-chest inventory, surrounded on the left, bottom,
 * and right by black/green glass indicator panes (U-shape; no top border).
 *
 * ### Inventory layout (row × column, 0-indexed)
 * ```
 * Row 0: [BG] [R0] [B00] [B01] [B02] [B03] [B04] [BG] [BG]
 * Row 1: [BG] [R1] [B10] [B11] [B12] [B13] [B14] [BG] [BG]
 * Row 2: [BG] [R2] [B20] [B21] [B22] [B23] [B24] [BG] [BG]
 * Row 3: [BG] [R3] [B30] [B31] [B32] [B33] [B34] [BG] [BG]
 * Row 4: [BG] [R4] [B40] [B41] [B42] [B43] [B44] [BG] [BG]
 * Row 5: [BG] [D↘] [C0]  [C1]  [C2]  [C3]  [C4] [D↗] [BG]
 * ```
 * - **BG** – black glass pane filler
 * - **R0–R4** – row indicator panes (col 1, rows 0–4)
 * - **B[r][c]** – board cell at board row r, col c (inventory cols 2–6)
 * - **C0–C4** – column indicator panes (row 5, cols 2–6)
 * - **D↘** – main diagonal indicator (row 5, col 1)
 * - **D↗** – anti-diagonal indicator (row 5, col 7)
 *
 * Indicator panes are **black** when the corresponding line is incomplete and
 * turn **green** when the player has completed it.  The lore shows the bonus
 * points available and the current completion status.
 *
 * ### Cell interaction
 * Clicking a board cell sends the objective's full description and current
 * completion status to the player in chat.
 *
 * ### Live updates
 * Call [refreshFor] with the player whenever a cell is completed; the open
 * inventory is updated in-place without closing it.
 */
class BingoBoardGUI : PluginGUI(
    id = "bingo_board",
    title = Component.text("✦ Bingo Board ✦").color(NamedTextColor.GOLD)
        .decoration(TextDecoration.BOLD, true),
    rows = 6,
    fillMode = FillMode.NONE
) {

    /** Inventories currently open, keyed by player. */
    private val openInventories = mutableMapOf<Player, Inventory>()

    // ── PluginGUI overrides ───────────────────────────────────────────────

    override fun setup(player: Player, inventory: Inventory) {
        openInventories[player] = inventory
        populateBoard(player, inventory)
    }

    override fun onClose(event: InventoryCloseEvent) {
        openInventories.remove(event.player as? Player)
    }

    override fun onClick(event: InventoryClickEvent) {
        event.isCancelled = true
        val player = event.whoClicked as? Player ?: return
        val game = BingoManager.currentGame ?: return

        val slot = event.rawSlot
        if (slot !in 0 until 54) return

        // Board occupies inventory rows 0-4, inventory cols 2-6
        val boardRow = slot / 9
        val boardCol = slot % 9 - 2
        if (boardRow !in 0 until BingoBoard.SIZE || boardCol !in 0 until BingoBoard.SIZE) return

        val cell = game.board.getCell(boardRow, boardCol)
        val state = game.getOrCreateState(player.uniqueId)
        val completed = state.isCompleted(cell.cellIndex)

        val header = Component.text("── ", NamedTextColor.DARK_GRAY)
            .append(cell.objective.name)
            .append(Component.text(" ──", NamedTextColor.DARK_GRAY))
        val statusLine = if (completed) {
            Component.text("  ✓ Completed!", NamedTextColor.GREEN)
        } else {
            Component.text("  ○ Not yet completed", NamedTextColor.RED)
        }

        player.sendMessage(header)
        player.sendMessage(cell.objective.description.color(NamedTextColor.GRAY))
        player.sendMessage(statusLine)
    }

    // ── Public API ────────────────────────────────────────────────────────

    /**
     * Re-renders the board in the inventory the player currently has open.
     *
     * Does nothing if the player does not have this GUI open.
     *
     * @param player the player whose open board should be refreshed
     */
    fun refreshFor(player: Player) {
        val inventory = openInventories[player] ?: return
        populateBoard(player, inventory)
    }

    // ── Internal ──────────────────────────────────────────────────────────

    /**
     * Populates [inventory] with board cells and indicator panes for [player],
     * or a "no game" placeholder if there is no current game.
     */
    private fun populateBoard(player: Player, inventory: Inventory) {
        val pluginConfig = (player.server.pluginManager.getPlugin("TriBingo") as? Main)?.pluginConfig
        val linePoints = pluginConfig?.linePoints ?: 3
        val diagPoints = pluginConfig?.diagonalPoints ?: 5

        val filler = blackGlass()
        for (i in 0 until 54) inventory.setItem(i, filler.clone())

        val game = BingoManager.currentGame
        if (game == null) {
            inventory.setItem(
                22,
                itemStack(Material.BARRIER) {
                    name("<red>No Bingo Game")
                    lore(
                        "<gray>No game has been set up yet.",
                        "<gray>Ask an admin to run <white>/bingo refresh<gray>."
                    )
                }
            )
            return
        }

        val board = game.board
        val state = game.getOrCreateState(player.uniqueId)

        // Board cells: inventory rows 0-4, inventory cols 2-6
        for (boardRow in 0 until BingoBoard.SIZE) {
            for (boardCol in 0 until BingoBoard.SIZE) {
                val cell = board.getCell(boardRow, boardCol)
                val completed = state.isCompleted(cell.cellIndex)
                val slot = boardRow * 9 + (boardCol + 2)
                inventory.setItem(slot, cell.objective.displayItem(player, completed))
            }
        }

        // Row indicator panes: inventory col 1, rows 0-4 (slots 1, 10, 19, 28, 37)
        for (row in 0 until BingoBoard.SIZE) {
            inventory.setItem(row * 9 + 1, rowPane(row, state, board, linePoints))
        }

        // Column indicator panes: inventory row 5, cols 2-6 (slots 47-51)
        for (col in 0 until BingoBoard.SIZE) {
            inventory.setItem(47 + col, colPane(col, state, board, linePoints))
        }

        // Main diagonal indicator: inventory row 5, col 1 (slot 46)
        inventory.setItem(46, diagPane(main = true, state, board, diagPoints))

        // Anti-diagonal indicator: inventory row 5, col 7 (slot 52)
        inventory.setItem(52, diagPane(main = false, state, board, diagPoints))
    }

    // ── Indicator pane builders ───────────────────────────────────────────

    private fun rowPane(
        row: Int,
        state: BingoPlayerState,
        board: BingoBoard,
        bonusPoints: Int
    ): ItemStack {
        val complete = board.isRowComplete(state, row)
        val filled = (0 until BingoBoard.SIZE).count { col ->
            state.isCompleted(board.getCell(row, col).cellIndex)
        }
        return indicatorPane("Row ${row + 1}", bonusPoints, filled, complete)
    }

    private fun colPane(
        col: Int,
        state: BingoPlayerState,
        board: BingoBoard,
        bonusPoints: Int
    ): ItemStack {
        val complete = board.isColComplete(state, col)
        val filled = (0 until BingoBoard.SIZE).count { row ->
            state.isCompleted(board.getCell(row, col).cellIndex)
        }
        return indicatorPane("Column ${col + 1}", bonusPoints, filled, complete)
    }

    private fun diagPane(
        main: Boolean,
        state: BingoPlayerState,
        board: BingoBoard,
        bonusPoints: Int
    ): ItemStack {
        val complete = if (main) board.isDiagMainComplete(state) else board.isDiagAntiComplete(state)
        val filled = if (main) {
            (0 until BingoBoard.SIZE).count { i -> state.isCompleted(board.getCell(i, i).cellIndex) }
        } else {
            (0 until BingoBoard.SIZE).count { i ->
                state.isCompleted(board.getCell(i, BingoBoard.SIZE - 1 - i).cellIndex)
            }
        }
        val label = if (main) "Main Diagonal ↘" else "Anti Diagonal ↗"
        return indicatorPane(label, bonusPoints, filled, complete)
    }

    /**
     * Builds a glass-pane indicator [ItemStack].
     *
     * @param label       display name shown in the item tooltip
     * @param bonusPoints extra points awarded when the line is completed
     * @param filled      number of cells already completed in this line
     * @param complete    whether all cells in the line are completed
     */
    private fun indicatorPane(
        label: String,
        bonusPoints: Int,
        filled: Int,
        complete: Boolean
    ): ItemStack {
        val material = if (complete) Material.GREEN_STAINED_GLASS_PANE else Material.BLACK_STAINED_GLASS_PANE
        val nameColor = if (complete) "<green>" else "<gray>"
        val prefix = if (complete) "✓ " else ""
        val statusLine = if (complete) {
            "<green>✓ Completed!"
        } else {
            "<gray>○ <white>$filled<gray>/${BingoBoard.SIZE} cells"
        }
        val suffix = if (bonusPoints == 1) "" else "s"
        return itemStack(material) {
            name("$nameColor$prefix$label")
            lore(
                "<gray>Completion Bonus: <gold>+$bonusPoints pt$suffix",
                "",
                statusLine
            )
        }
    }

    private fun blackGlass(): ItemStack = itemStack(Material.BLACK_STAINED_GLASS_PANE) {
        name(" ")
        hideTooltip(true)
    }
}
