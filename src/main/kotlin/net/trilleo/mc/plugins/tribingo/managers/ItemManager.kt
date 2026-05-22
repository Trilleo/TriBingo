package net.trilleo.mc.plugins.tribingo.managers

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
}