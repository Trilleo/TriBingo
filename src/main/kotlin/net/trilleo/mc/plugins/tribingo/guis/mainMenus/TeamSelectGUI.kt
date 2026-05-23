package net.trilleo.mc.plugins.tribingo.guis.mainMenus

import net.kyori.adventure.key.Key
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.trilleo.mc.plugins.tribingo.enums.FillMode
import net.trilleo.mc.plugins.tribingo.registration.GUIManager
import net.trilleo.mc.plugins.tribingo.registration.PluginGUI
import net.trilleo.mc.plugins.tribingo.utils.TeamUtil
import net.trilleo.mc.plugins.tribingo.utils.itemStack
import org.bukkit.Material
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemFlag

class TeamSelectGUI : PluginGUI(
    id = "team-select",
    title = Component.text("Team").color(NamedTextColor.DARK_BLUE).decorate(TextDecoration.BOLD),
    rows = 6,
    fillMode = FillMode.LIGHT
) {
    val slotIndex: Map<String, Int> = mapOf(
        "backButtonSlot" to 48,
        "closeButtonSlot" to 49
    )
    val infoIndex: Map<String, Int> = mapOf(
        "infoButtonSlot" to 13
    )
    val teamIndex: Map<String, Int> = mapOf(
        "playerSlot" to 29,
        "spectatorSlot" to 33
    )

    fun refreshInventory(player: Player, inventory: Inventory) {
        val infoButton = itemStack(Material.BOOK) {
            name("<bold><white>Select your team")
            lore(
                "   ",
                "<white>Current Team: ${TeamUtil.getPlayerTeam(player)?.displayName ?: "<dark_gray>None"}"
            )
        }
        val playerButton = itemStack(Material.GREEN_WOOL) {
            name("<bold><dark_green>Player")
            lore(
                "   ",
                if (TeamUtil.isInTeam(player, "player")) "<green>Selected" else "<yellow>Click to select"
            )
            if (TeamUtil.isInTeam(player, "player")) {
                enchant(Enchantment.KNOCKBACK, 1)
                flag(ItemFlag.HIDE_ENCHANTS)
            }
        }
        val spectatorButton = itemStack(Material.GRAY_WOOL) {
            name("<bold><gray>Spectator")
            lore(
                "   ",
                if (TeamUtil.isInTeam(player, "spectator")) "<green>Selected" else "<yellow>Click to select"
            )
            if (TeamUtil.isInTeam(player, "spectator")) {
                enchant(Enchantment.KNOCKBACK, 1)
                flag(ItemFlag.HIDE_ENCHANTS)
            }
        }

        inventory.setItem(infoIndex.getValue("infoButtonSlot"), infoButton)
        inventory.setItem(teamIndex.getValue("playerSlot"), playerButton)
        inventory.setItem(teamIndex.getValue("spectatorSlot"), spectatorButton)
    }

    override fun setup(player: Player, inventory: Inventory) {
        val closeButton = itemStack(Material.BARRIER) {
            name("<bold><red>Close")
        }
        val backButton = itemStack(Material.ARROW) {
            name("<bold><gray>Back")
        }

        inventory.setItem(slotIndex.getValue("backButtonSlot"), backButton)
        inventory.setItem(slotIndex.getValue("closeButtonSlot"), closeButton)

        refreshInventory(player, inventory)
    }

    override fun onClick(event: InventoryClickEvent) {
        event.isCancelled = true
        val player = event.whoClicked as Player
        if (event.slot in slotIndex.values) {
            player.playSound(
                Sound.sound(Key.key("minecraft:ui.button.click"), Sound.Source.UI, 1f, 1f)
            )
        }
        if (event.slot in teamIndex.values) {
            if (event.currentItem?.containsEnchantment(Enchantment.KNOCKBACK) == false) {
                player.playSound(
                    Sound.sound(Key.key("minecraft:entity.experience_orb.pickup"), Sound.Source.UI, 1f, 1f)
                )
            }
        }

        if (event.slot == slotIndex.getValue("closeButtonSlot")) {
            player.closeInventory()
        }
        if (event.slot == slotIndex.getValue("backButtonSlot")) {
            GUIManager.open(player, "main")
        }

        if (event.slot == teamIndex.getValue("playerSlot")) {
            TeamUtil.addPlayer(player, "player")
            refreshInventory(player, event.inventory)
        }
        if (event.slot == teamIndex.getValue("spectatorSlot")) {
            TeamUtil.addPlayer(player, "spectator")
            refreshInventory(player, event.inventory)
        }
    }
}