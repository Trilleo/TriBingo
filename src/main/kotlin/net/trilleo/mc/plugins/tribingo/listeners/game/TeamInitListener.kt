package net.trilleo.mc.plugins.tribingo.listeners.game

import net.trilleo.mc.plugins.tribingo.utils.TeamUtil
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
    }
}