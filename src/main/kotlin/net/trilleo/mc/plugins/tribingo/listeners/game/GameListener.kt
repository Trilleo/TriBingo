package net.trilleo.mc.plugins.tribingo.listeners.game

import net.trilleo.mc.plugins.tribingo.managers.ItemManager
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.plugin.java.JavaPlugin

class GameListener(private val plugin: JavaPlugin) : Listener {
    // Give Main Item on join
    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        val player = event.player
        ItemManager(plugin).clearPluginItems(player)
        ItemManager(plugin).updatePluginItem(player)
    }
}