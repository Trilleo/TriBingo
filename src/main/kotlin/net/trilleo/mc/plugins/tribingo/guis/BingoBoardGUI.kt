package net.trilleo.mc.plugins.tribingo.guis

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import net.trilleo.mc.plugins.tribingo.Main
import net.trilleo.mc.plugins.tribingo.bingo.BingoBoard
import net.trilleo.mc.plugins.tribingo.bingo.BingoGame
import net.trilleo.mc.plugins.tribingo.bingo.BingoManager
import net.trilleo.mc.plugins.tribingo.bingo.BingoPlayerState
import net.trilleo.mc.plugins.tribingo.enums.FillMode
import net.trilleo.mc.plugins.tribingo.registration.PluginGUI
import net.trilleo.mc.plugins.tribingo.utils.TeamUtil
import net.trilleo.mc.plugins.tribingo.utils.itemStack
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import java.util.*

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
 * Row 5: [BG] [D↗] [C0]  [C1]  [C2]  [C3]  [C4] [D↘] [BG]
 * ```
 * - **BG** – black glass pane filler
 * - **R0–R4** – row indicator panes (col 1, rows 0–4)
 * - **B[r, c]** – board cell at board row r, col c (inventory cols 2–6)
 * - **C0–C4** – column indicator panes (row 5, cols 2–6)
 * - **D↗** – anti-diagonal indicator (row 5, col 1)
 * - **D↘** – main diagonal indicator (row 5, col 7)
 * - **Y** – viewer points item (row 5, col 0)
 * - **P** – points leaderboard button (row 5, col 8)
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
 *
 * @param plugin the owning plugin instance, used to read point configuration
 */
class BingoBoardGUI(plugin: JavaPlugin) : PluginGUI(
    id = "bingo_board",
    title = Component.text("✦ Bingo Board ✦").color(NamedTextColor.GOLD)
        .decoration(TextDecoration.BOLD, true),
    rows = 6,
    fillMode = FillMode.NONE
) {

    /** Typed plugin config, resolved once at construction time. */
    private val pluginConfig = (plugin as? Main)?.pluginConfig

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

        val slot = event.rawSlot
        if (slot !in 0 until 54) return

        if (slot == 53) {
            showPointsLeaderboard(player, BingoManager.currentGame)
            return
        }

        val game = BingoManager.currentGame ?: return

        // Board occupies inventory rows 0-4, inventory cols 2-6
        val boardRow = slot / 9
        val boardCol = slot % 9 - 2
        if (boardRow !in 0 until BingoBoard.SIZE || boardCol !in 0 until BingoBoard.SIZE) return

        val cell = game.board.getCell(boardRow, boardCol)

        val header = Component.text("── ", NamedTextColor.DARK_GRAY)
            .append(cell.objective.name)
            .append(Component.text(" ──", NamedTextColor.DARK_GRAY))

        player.sendMessage(header)
        player.sendMessage(cell.objective.description.color(NamedTextColor.GRAY))

        if (TeamUtil.isInTeam(player, "spectator")) {
            val completedCount = game.playerStates.values.count { it.isCompleted(cell.cellIndex) }
            val total = game.playerStates.size
            player.sendMessage(
                Component.text("  ○ Completed by $completedCount/$total players", NamedTextColor.YELLOW)
            )
        } else {
            val state = game.getOrCreateState(player.uniqueId)
            val completed = state.isCompleted(cell.cellIndex)
            val statusLine = if (completed) {
                Component.text("  ✓ Completed!", NamedTextColor.GREEN)
            } else {
                Component.text("  ○ Not yet completed", NamedTextColor.RED)
            }
            player.sendMessage(statusLine)
        }
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
     *
     * Spectators see a combined overview showing how many players have completed
     * each cell across all player-team participants.
     */
    private fun populateBoard(player: Player, inventory: Inventory) {
        val linePoints = pluginConfig?.linePoints ?: 3
        val diagPoints = pluginConfig?.diagonalPoints ?: 5

        val filler = blackGlass()
        for (i in 0 until 54) inventory.setItem(i, filler.clone())

        val game = BingoManager.currentGame
        inventory.setItem(45, viewerPointsItem(player, game))
        inventory.setItem(53, pointsButton(game))
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
        val isSpectator = TeamUtil.isInTeam(player, "spectator")

        if (isSpectator) {
            populateSpectatorBoard(board, game.playerStates, inventory, linePoints, diagPoints)
        } else {
            val state = game.getOrCreateState(player.uniqueId)
            populatePlayerBoard(player, board, state, inventory, linePoints, diagPoints)
        }
    }

    /**
     * Populates the board for a player on the "player" team, showing their own
     * progress and completion status.
     */
    private fun populatePlayerBoard(
        player: Player,
        board: BingoBoard,
        state: BingoPlayerState,
        inventory: Inventory,
        linePoints: Int,
        diagPoints: Int
    ) {
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

        // Anti-diagonal indicator: inventory row 5, col 1 (slot 46)
        inventory.setItem(46, diagPane(main = false, state, board, diagPoints))

        // Main diagonal indicator: inventory row 5, col 7 (slot 52)
        inventory.setItem(52, diagPane(main = true, state, board, diagPoints))
    }

    /**
     * Populates the board for a spectator, showing a combined overview of all
     * players' progress. Each cell shows how many players have completed it.
     */
    private fun populateSpectatorBoard(
        board: BingoBoard,
        playerStates: Map<UUID, BingoPlayerState>,
        inventory: Inventory,
        linePoints: Int,
        diagPoints: Int
    ) {
        val totalPlayers = playerStates.size

        // Board cells: show completion count per cell
        for (boardRow in 0 until BingoBoard.SIZE) {
            for (boardCol in 0 until BingoBoard.SIZE) {
                val cell = board.getCell(boardRow, boardCol)
                val completedCount = playerStates.values.count { it.isCompleted(cell.cellIndex) }
                val slot = boardRow * 9 + (boardCol + 2)
                val material = if (completedCount > 0) Material.LIME_STAINED_GLASS_PANE
                else Material.RED_STAINED_GLASS_PANE
                inventory.setItem(slot, itemStack(material) {
                    name("<white>${PlainTextComponentSerializer.plainText().serialize(cell.objective.name)}")
                    lore(
                        "<gray>Completed by: <white>$completedCount<gray>/$totalPlayers players"
                    )
                    if (completedCount > 0) amount(completedCount.coerceIn(1, 64))
                })
            }
        }

        // Row indicators: show how many players have completed the full row
        for (row in 0 until BingoBoard.SIZE) {
            val completedCount = playerStates.values.count { state ->
                board.isRowComplete(state, row)
            }
            inventory.setItem(
                row * 9 + 1,
                spectatorIndicatorPane("Row ${row + 1}", linePoints, completedCount, totalPlayers)
            )
        }

        // Column indicators
        for (col in 0 until BingoBoard.SIZE) {
            val completedCount = playerStates.values.count { state ->
                board.isColComplete(state, col)
            }
            inventory.setItem(
                47 + col,
                spectatorIndicatorPane("Column ${col + 1}", linePoints, completedCount, totalPlayers)
            )
        }

        // Anti-diagonal indicator
        val antiDiagCount = playerStates.values.count { state -> board.isDiagAntiComplete(state) }
        inventory.setItem(46, spectatorIndicatorPane("Anti Diagonal ↗", diagPoints, antiDiagCount, totalPlayers))

        // Main diagonal indicator
        val mainDiagCount = playerStates.values.count { state -> board.isDiagMainComplete(state) }
        inventory.setItem(52, spectatorIndicatorPane("Main Diagonal ↘", diagPoints, mainDiagCount, totalPlayers))
    }

    /**
     * Builds an indicator pane for spectator view showing how many players
     * completed a line.
     */
    private fun spectatorIndicatorPane(
        label: String,
        bonusPoints: Int,
        completedCount: Int,
        totalPlayers: Int
    ): ItemStack {
        val material = if (completedCount > 0) Material.GREEN_STAINED_GLASS_PANE
        else Material.BLACK_STAINED_GLASS_PANE
        val nameColor = if (completedCount > 0) "<green>" else "<gray>"
        val suffix = if (bonusPoints == 1) "" else "s"
        return itemStack(material) {
            name("$nameColor$label")
            lore(
                "<gray>Bonus: <gold>+$bonusPoints pt$suffix",
                "",
                "<gray>Completed by: <white>$completedCount<gray>/$totalPlayers players"
            )
        }
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

    private fun pointsButton(game: BingoGame?): ItemStack {
        val trackedPlayers = game?.playerStates?.size ?: 0
        return itemStack(Material.OAK_SIGN) {
            name(if (game == null) "<bold><red>Leaderboard" else "<bold><gold>Leaderboard")
            val loreLines = if (game == null) {
                arrayOf(
                    "<gray>No bingo game is currently running.",
                    "<gray>Start a game to view the leaderboard."
                )
            } else {
                arrayOf(
                    "<gray>Click to view all tracked players",
                    "<gray>sorted from <gold>highest<gray> to <gold>lowest<gray>.",
                    "",
                    "<gray>Players tracked: <white>$trackedPlayers"
                )
            }
            lore(*loreLines)
        }
    }

    private fun viewerPointsItem(player: Player, game: BingoGame?): ItemStack {
        val points = game?.playerStates?.get(player.uniqueId)?.points ?: 0
        val hasGame = game != null
        return itemStack(Material.EMERALD) {
            name(if (hasGame) "<bold><green>Your Points" else "<bold><gray>Your Points")
            val loreLines = if (hasGame) {
                arrayOf("<gray>Your current Bingo points are <gold>$points<gray>.")
            } else {
                arrayOf(
                    "<gray>No bingo game is currently running.",
                    "<gray>Your current Bingo points are <gold>$points<gray>."
                )
            }
            lore(*loreLines)
        }
    }

    private fun showPointsLeaderboard(player: Player, game: BingoGame?) {
        if (game == null) {
            player.sendMessage(Component.text("No bingo game is currently running.", NamedTextColor.RED))
            return
        }

        val entries = game.playerStates.entries
            .map { (uuid, state) -> resolvePlayerName(player, uuid) to state.points }
            .sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first.lowercase() })

        player.sendMessage(
            Component.text("── Bingo Points Leaderboard ──", NamedTextColor.GOLD)
                .decoration(TextDecoration.BOLD, true)
        )

        if (entries.isEmpty()) {
            player.sendMessage(Component.text("No player points have been tracked yet.", NamedTextColor.GRAY))
            return
        }

        entries.forEachIndexed { index, (name, points) ->
            player.sendMessage(
                Component.text()
                    .append(Component.text("${index + 1}. ", NamedTextColor.DARK_GRAY))
                    .append(Component.text(name, NamedTextColor.YELLOW))
                    .append(Component.text(" - ", NamedTextColor.GRAY))
                    .append(Component.text(points.toString(), NamedTextColor.GREEN))
                    .append(Component.text(" point${if (points == 1) "" else "s"}", NamedTextColor.GRAY))
                    .build()
            )
        }
    }

    @Suppress("DEPRECATION")
    private fun resolvePlayerName(player: Player, uuid: UUID): String =
        player.server.getOfflinePlayer(uuid).name ?: uuid.toString().take(8)

    private fun blackGlass(): ItemStack = itemStack(Material.BLACK_STAINED_GLASS_PANE) {
        name(" ")
        hideTooltip(true)
    }
}
