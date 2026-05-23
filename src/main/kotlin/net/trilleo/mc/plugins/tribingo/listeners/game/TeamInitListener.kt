package net.trilleo.mc.plugins.tribingo.listeners.game

import net.trilleo.mc.plugins.tribingo.bingo.BingoManager
import net.trilleo.mc.plugins.tribingo.enums.GameState
import net.trilleo.mc.plugins.tribingo.utils.TeamUtil
import org.bukkit.GameMode
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.plugin.java.JavaPlugin

class TeamInitListener(private val plugin: JavaPlugin) : Listener {
    // Add player to Spectator Team on default
    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player
        if (TeamUtil.getPlayerTeam(player) == null) {
            TeamUtil.addPlayer(player, "spectator")
        }
        // Apply appropriate game mode if a game is active
        if (BingoManager.currentGame?.state == GameState.ACTIVE) {
            if (TeamUtil.isInTeam(player, "spectator")) {
                player.gameMode = GameMode.SPECTATOR
            } else if (TeamUtil.isInTeam(player, "player")) {
                player.gameMode = GameMode.SURVIVAL
            }
        }
    }
}