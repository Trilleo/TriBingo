package net.trilleo.mc.plugins.tribingo.listeners.game

import net.trilleo.mc.plugins.tribingo.commands.bingo.BingoActions
import net.trilleo.mc.plugins.tribingo.registration.GUIManager
import net.trilleo.mc.plugins.tribingo.utils.sendPrefixed
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.SignChangeEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.java.JavaPlugin
import java.util.*

/**
 * Listens for sign input to set the Bingo timer duration.
 *
 * When a player requests timer input via the Settings GUI, a sign is placed
 * at the player's location. The player edits the sign with the format
 * `HH:MM:SS` or `MM:SS` on the first line. Upon completing the sign edit,
 * the timer is updated and the sign is removed.
 *
 * ### Format
 * - Line 1: `HH:MM:SS` or `MM:SS` (e.g. `01:30:00` for 1.5 hours, `45:00` for 45 minutes)
 */
class SignInputListener(private val plugin: JavaPlugin) : Listener {

    companion object {
        private val pendingInput = mutableSetOf<UUID>()

        /**
         * Marks [player] as awaiting sign input for timer configuration.
         * A sign is placed at the player's feet for editing.
         */
        fun requestInput(player: Player) {
            pendingInput.add(player.uniqueId)
            val location = player.location.clone()
            location.block.type = Material.OAK_SIGN
            player.sendPrefixed("<gray>Edit the sign with the timer in format <white>HH:MM:SS<gray> or <white>MM:SS<gray>.")
            player.openSign(location.block.state as org.bukkit.block.Sign)
        }

        /**
         * Returns `true` if [player] is currently awaiting sign input.
         */
        fun isPending(player: Player): Boolean = player.uniqueId in pendingInput
    }

    @EventHandler
    fun onSignChange(event: SignChangeEvent) {
        val player = event.player
        if (player.uniqueId !in pendingInput) return

        pendingInput.remove(player.uniqueId)

        // Remove the temporary sign
        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            event.block.type = Material.AIR
        }, 1L)

        val line = event.line(0)?.let {
            net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(it)
        }?.trim() ?: ""

        if (line.isEmpty()) {
            player.sendPrefixed("<red>Timer input cancelled (empty).")
            return
        }

        val parts = line.split(":")
        val result = when (parts.size) {
            2 -> {
                val minutes = parts[0].toIntOrNull()
                val seconds = parts[1].toIntOrNull()
                if (minutes == null || seconds == null) {
                    player.sendPrefixed("<red>Invalid format. Use <white>MM:SS<red> or <white>HH:MM:SS<red>.")
                    return
                }
                BingoActions.setTimer(0, minutes, seconds)
            }
            3 -> {
                val hours = parts[0].toIntOrNull()
                val minutes = parts[1].toIntOrNull()
                val seconds = parts[2].toIntOrNull()
                if (hours == null || minutes == null || seconds == null) {
                    player.sendPrefixed("<red>Invalid format. Use <white>MM:SS<red> or <white>HH:MM:SS<red>.")
                    return
                }
                BingoActions.setTimer(hours, minutes, seconds)
            }
            else -> {
                player.sendPrefixed("<red>Invalid format. Use <white>MM:SS<red> or <white>HH:MM:SS<red>.")
                return
            }
        }

        player.sendPrefixed(result.message)
        if (result.success) {
            // Reopen settings GUI after successful timer set
            Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                GUIManager.open(player, "settings")
            }, 2L)
        }
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        pendingInput.remove(event.player.uniqueId)
    }
}
