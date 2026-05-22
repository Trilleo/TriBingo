package net.trilleo.mc.plugins.tribingo.commands.bingo

import net.trilleo.mc.plugins.tribingo.enums.GameDifficulty
import net.trilleo.mc.plugins.tribingo.registration.PluginCommand
import net.trilleo.mc.plugins.tribingo.utils.sendPrefixed
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

/**
 * Main command dispatcher for all Bingo game management commands.
 *
 * Registered as `/bingo` and dispatches to the following sub-commands:
 *
 * | Sub-command              | Permission              | Description                            |
 * |:-------------------------|:------------------------|:---------------------------------------|
 * | `board`                  | (none)                  | Opens the board GUI for the sender     |
 * | `start`                  | `tribingo.bingo.manage` | Starts the current game                |
 * | `stop`                   | `tribingo.bingo.manage` | Ends the current active game           |
 * | `reset`                  | `tribingo.bingo.manage` | Resets all player progress             |
 * | `refresh [difficulty]`   | `tribingo.bingo.manage` | Picks new objectives (INACTIVE only)   |
 * | `time <h> <m> <s>`       | `tribingo.bingo.manage` | Sets the countdown timer               |
 * | `status`                 | (none)                  | Shows the current game status          |
 * | `test <objective_id>`    | `tribingo.bingo.manage` | Tests an objective's completion checker |
 * | `test stop`              | `tribingo.bingo.manage` | Stops the current test session         |
 *
 * All game-logic is delegated to [BingoActions] so the same operations can
 * be wired to GUI buttons without duplicating code.
 */
class BingoCommand : PluginCommand(
    name = "bingo",
    description = "Manage and view the Bingo game",
    usage = "/bingo <board|start|stop|reset|refresh [easy|medium|hard]|time|status|test <objective_id|stop>>",
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
            "refresh" -> handleRefresh(sender, args)
            "time" -> handleTime(sender, args)
            "status" -> handleStatus(sender)
            "test" -> handleTest(sender, args)
            else -> {
                showUsage(sender); true
            }
        }
    }

    override fun tabComplete(sender: CommandSender, args: Array<out String>): List<String> {
        if (args.size == 1) {
            val subs = listOf("board", "start", "stop", "reset", "refresh", "time", "status", "test")
            return subs.filter { it.startsWith(args[0].lowercase()) }
        }
        if (args[0].lowercase() == "refresh" && args.size == 2) {
            return GameDifficulty.entries.map { it.name.lowercase() }
                .filter { it.startsWith(args[1].lowercase()) }
        }
        if (args[0].lowercase() == "time") {
            return when (args.size) {
                2 -> listOf("0", "1").filter { it.startsWith(args[1]) }
                3 -> listOf("0", "30").filter { it.startsWith(args[2]) }
                4 -> listOf("0", "30").filter { it.startsWith(args[3]) }
                else -> emptyList()
            }
        }
        if (args[0].lowercase() == "test" && args.size == 2) {
            val completions = mutableListOf("stop")
            completions.addAll(BingoActions.getObjectiveIds())
            return completions.filter { it.startsWith(args[1].lowercase()) }
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

    private fun handleRefresh(sender: CommandSender, args: Array<out String>): Boolean {
        if (!sender.hasPermission("tribingo.bingo.manage")) {
            sendMsg(sender, "<red>You don't have permission to use this sub-command.")
            return true
        }
        val difficulty = if (args.size >= 2) {
            val parsed = runCatching { GameDifficulty.valueOf(args[1].uppercase()) }.getOrNull()
            if (parsed == null) {
                val valid = GameDifficulty.entries.joinToString(", ") { it.name.lowercase() }
                sendMsg(sender, "<red>Unknown difficulty '${args[1]}'. Valid options: $valid")
                return true
            }
            parsed
        } else {
            null
        }
        val result = BingoActions.refreshBoard(difficulty)
        sendMsg(sender, result.message)
        return true
    }

    private fun handleTime(sender: CommandSender, args: Array<out String>): Boolean {
        if (!sender.hasPermission("tribingo.bingo.manage")) {
            sendMsg(sender, "<red>You don't have permission to use this sub-command.")
            return true
        }
        if (args.size < 4) {
            sendMsg(sender, "<gray>Usage: /bingo time <hours> <minutes> <seconds>")
            return true
        }
        val hours = args[1].toIntOrNull()
        val minutes = args[2].toIntOrNull()
        val seconds = args[3].toIntOrNull()
        if (hours == null || minutes == null || seconds == null) {
            sendMsg(sender, "<red>Invalid values — hours, minutes and seconds must be integers.")
            return true
        }
        val result = BingoActions.setTimer(hours, minutes, seconds)
        sendMsg(sender, result.message)
        return true
    }

    private fun handleStatus(sender: CommandSender): Boolean {
        val result = BingoActions.getStatus()
        result.message.split("\n").forEach { line -> sendMsg(sender, line) }
        return true
    }

    private fun handleTest(sender: CommandSender, args: Array<out String>): Boolean {
        if (!sender.hasPermission("tribingo.bingo.manage")) {
            sendMsg(sender, "<red>You don't have permission to use this sub-command.")
            return true
        }
        val player = sender as? Player ?: run {
            sender.sendMessage("Only players can use /bingo test.")
            return true
        }
        if (args.size < 2) {
            sendMsg(sender, "<gray>Usage: /bingo test <objective_id|stop>")
            sendMsg(sender, "<gray>Starts a test session to verify an objective's completion checker.")
            return true
        }
        if (args[1].lowercase() == "stop") {
            val result = BingoActions.stopTest(player)
            sendMsg(sender, result.message)
            return true
        }
        val result = BingoActions.startTest(player, args[1])
        sendMsg(sender, result.message)
        if (result.success) {
            sendMsg(sender, "<gray>Perform the objective actions now. Progress will show on your action bar.")
            sendMsg(sender, "<gray>Run <white>/bingo test stop<gray> to end the test.")
        }
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
