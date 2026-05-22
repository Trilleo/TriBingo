package net.trilleo.mc.plugins.tribingo.managers

import net.trilleo.mc.plugins.tribingo.data.ServerDataManager
import net.trilleo.mc.plugins.tribingo.items.MainItem
import net.trilleo.mc.plugins.tribingo.utils.PDCEntryUtil
import net.trilleo.mc.plugins.tribingo.utils.PDCUtil
import org.bukkit.entity.Player
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin

class ItemManager(private val plugin: JavaPlugin) {
    fun clearPluginItems(player: Player) {
        for (item in player.inventory.contents) {
            if (item != null && PDCUtil.get(
                    item,
                    PDCEntryUtil.PDCKey(plugin).itemIdentifierKey,
                    PersistentDataType.STRING
                ) in listOf(
                    PDCEntryUtil.PDCValue().mainItemIdentifier
                )
            ) {
                player.inventory.remove(item)
            }
        }
    }

    fun updatePluginItem(player: Player) {
        val serverData = ServerDataManager.get()

        ItemManager(plugin).clearPluginItems(player)

        if (serverData.getString("bingo_game_state") == "INACTIVE") {
            val mainItem = MainItem(plugin).create()

            if (player.inventory.getItem(8) == null) {
                player.inventory.setItem(8, mainItem)
            } else {
                player.inventory.addItem(mainItem)
            }
        }
    }
}