package net.trilleo.mc.plugins.tribingo.items

import net.trilleo.mc.plugins.tribingo.registration.PluginItem
import net.trilleo.mc.plugins.tribingo.utils.PDCEntryUtil
import net.trilleo.mc.plugins.tribingo.utils.itemStack
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin

class MainItem(private val plugin: JavaPlugin) : PluginItem("main-item") {
    override fun buildItem(amount: Int): ItemStack = itemStack(Material.NETHER_STAR) {
        name("<gold><bold>TriBingo Menu")
        lore(
            "<gray>[Right Click] to open"
        )
        pdc(
            PDCEntryUtil.PDCKey(plugin).itemIdentifierKey,
            PersistentDataType.STRING,
            PDCEntryUtil.PDCValue().mainItemIdentifier
        )
    }
}