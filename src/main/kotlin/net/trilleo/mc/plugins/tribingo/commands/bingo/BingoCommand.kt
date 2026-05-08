package net.trilleo.mc.plugins.tribingo.commands.bingo

import net.trilleo.mc.plugins.tribingo.registration.PluginCommand
import net.trilleo.mc.plugins.tribingo.utils.sendPrefixed
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

/**
 * Main command dispatcher for all Bingo game management commands.
 *
 * Registered as `/bingo` and dispatches to the following sub-commands:
 *
 * | Sub-command | Permission              | Description                            |
 * |:------------|:------------------------|:---------------------------------------|
 * | `board`     | (none)                  | Opens the board GUI for the sender     |
 * | `start`     | `tribingo.bingo.manage` | Starts the current game                |
 * | `stop`      | `tribingo.bingo.manage` | Ends the current active game           |
 * | `reset`     | `tribingo.bingo.manage` | Resets all player progress             |
 * | `refresh`   | `tribingo.bingo.manage` | Picks new objectives (INACTIVE only)   |
 * | `status`    | (none)                  | Shows the current game status          |
 *
 * All game-logic is delegated to [BingoActions] so the same operations can
 * be wired to GUI buttons without duplicating code.
 */
class BingoCommand : PluginCommand(
    name = "bingo",
    description = "Manage and view the Bingo game",
    usage = "/bingo <board|start|stop|reset|refresh|status>",
    isMainCommand = true
) {

    override fun execute(sender: CommandSender, args: Array<out String>): Boolean {
        if (args.isEmpty()) {
            showUsage(sender)
            return true
        }

        return when (args[0].lowercase()) {
            "board" -> handleBoard(sender)
            "start" -> handleManage(sender) { BingoActions.startGame() }
            "stop" -> handleManage(sender) { BingoActions.stopGame() }
            "reset" -> handleManage(sender) { BingoActions.resetGame() }
            "refresh" -> handleManage(sender) { BingoActions.refreshBoard() }
            "status" -> handleStatus(sender)
            else -> {
                showUsage(sender); true
            }
        }
    }

    override fun tabComplete(sender: CommandSender, args: Array<out String>): List<String> {
        if (args.size == 1) {
            val subs = listOf("board", "start", "stop", "reset", "refresh", "status")
            return subs.filter { it.startsWith(args[0].lowercase()) }
        }
        return emptyList()
    }

    // ── Sub-command handlers ─────────────────────────────────────────────

    private fun handleBoard(sender: CommandSender): Boolean {
        val player = sender as? Player ?: run {
            sender.sendMessage("Only players can open the board.")
            return true
        }
        val result = BingoActions.openBoard(player)
        if (!result.success) player.sendPrefixed(result.message)
        return true
    }

    private fun handleManage(
        sender: CommandSender,
        action: () -> BingoActions.ActionResult
    ): Boolean {
        if (!sender.hasPermission("tribingo.bingo.manage")) {
            sendMsg(sender, "<red>You don't have permission to use this sub-command.")
            return true
        }
        val result = action()
        sendMsg(sender, result.message)
        return true
    }

    private fun handleStatus(sender: CommandSender): Boolean {
        val result = BingoActions.getStatus()
        result.message.split("\n").forEach { line -> sendMsg(sender, line) }
        return true
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private fun showUsage(sender: CommandSender) {
        sendMsg(sender, "<gray>Usage: $usage")
    }

    private fun sendMsg(sender: CommandSender, message: String) {
        if (sender is Player) sender.sendPrefixed(message)
        else sender.sendMessage(message)
    }
}
