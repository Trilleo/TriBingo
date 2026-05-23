package net.trilleo.mc.plugins.tribingo.utils

import org.bukkit.NamespacedKey
import org.bukkit.plugin.java.JavaPlugin

class PDCEntryUtil {
    // Namespaced Keys
    class PDCKey(private val plugin: JavaPlugin) {
        // Key for identifying plugin items
        val itemIdentifierKey = NamespacedKey(plugin, "itemIdentifier")
    }

    // Values
    class PDCValue {
        // itemIdentifier - Main Item
        val mainItemIdentifier = "main-item"
    }
}