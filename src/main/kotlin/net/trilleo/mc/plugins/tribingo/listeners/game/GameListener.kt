package net.trilleo.mc.plugins.tribingo.listeners.game

import net.trilleo.mc.plugins.tribingo.managers.ItemManager
import net.trilleo.mc.plugins.tribingo.utils.PDCEntryUtil
import net.trilleo.mc.plugins.tribingo.utils.PDCUtil
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerRespawnEvent
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin

class GameListener(private val plugin: JavaPlugin) : Listener {
    // Give Main Item on join
    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        val player = event.player
        ItemManager(plugin).clearPluginItems(player)
        ItemManager(plugin).updatePluginItem(player)
    }

    // Remove plugin items on death
    fun onDeath(event: PlayerDeathEvent) {
        val player = event.player

        for (item in event.drops) {
            if (item != null && PDCUtil.get(
                    item,
                    PDCEntryUtil.PDCKey(plugin).itemIdentifierKey,
                    PersistentDataType.STRING
                ) in listOf(
                    PDCEntryUtil.PDCValue().mainItemIdentifier,
                )
            ) {
                item.amount = 0
            }
        }
    }

    // Update plugin items on respawn
    fun onRespawn(event: PlayerRespawnEvent) {
        val player = event.player
        ItemManager(plugin).clearPluginItems(player)
        ItemManager(plugin).updatePluginItem(player)
    }
}