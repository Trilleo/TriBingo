package net.trilleo.mc.plugins.tribingo.guis.mainMenus

import net.kyori.adventure.key.Key
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.trilleo.mc.plugins.tribingo.bingo.BingoManager
import net.trilleo.mc.plugins.tribingo.enums.FillMode
import net.trilleo.mc.plugins.tribingo.enums.GameState
import net.trilleo.mc.plugins.tribingo.registration.GUIManager
import net.trilleo.mc.plugins.tribingo.registration.PluginGUI
import net.trilleo.mc.plugins.tribingo.utils.TeamUtil
import net.trilleo.mc.plugins.tribingo.utils.itemStack
import net.trilleo.mc.plugins.tribingo.utils.sendPrefixed
import org.bukkit.Material
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemFlag

/**
 * GUI for selecting between the "player" and "spectator" teams.
 *
 * Team switching is only allowed when the game is in [GameState.INACTIVE]
 * state (i.e. before the game starts or after it has been reset).
 */
class TeamSelectGUI : PluginGUI(
    id = "team-select",
    title = Component.text("Team Selection").color(NamedTextColor.DARK_BLUE).decorate(TextDecoration.BOLD),
    rows = 4,
    fillMode = FillMode.LIGHT
) {
    val slotIndex: Map<String, Int> = mapOf(
        "playerTeamSlot" to 12,
        "spectatorTeamSlot" to 14,
        "backButtonSlot" to 30,
        "closeButtonSlot" to 31
    )

    override fun setup(player: Player, inventory: Inventory) {
        populateItems(player, inventory)
    }

    override fun onClick(event: InventoryClickEvent) {
        event.isCancelled = true
        val player = event.whoClicked as Player
        if (event.slot in slotIndex.values) {
            player.playSound(
                Sound.sound(Key.key("minecraft:ui.button.click"), Sound.Source.UI, 1f, 1f)
            )
        }

        when (event.slot) {
            slotIndex.getValue("closeButtonSlot") -> player.closeInventory()
            slotIndex.getValue("backButtonSlot") -> GUIManager.open(player, "main")
            slotIndex.getValue("playerTeamSlot") -> handleTeamSwitch(player, "player", event.inventory)
            slotIndex.getValue("spectatorTeamSlot") -> handleTeamSwitch(player, "spectator", event.inventory)
        }
    }

    private fun handleTeamSwitch(player: Player, teamName: String, inventory: Inventory) {
        val gameState = BingoManager.currentGame?.state
        if (gameState != null && gameState != GameState.INACTIVE) {
            player.sendPrefixed("<red>You cannot switch teams until the game is reset.")
            player.playSound(
                Sound.sound(Key.key("minecraft:entity.villager.no"), Sound.Source.UI, 1f, 1f)
            )
            return
        }
        if (TeamUtil.isInTeam(player, teamName)) return
        TeamUtil.addPlayer(player, teamName)
        player.playSound(
            Sound.sound(Key.key("minecraft:entity.experience_orb.pickup"), Sound.Source.UI, 1f, 1f)
        )
        populateItems(player, inventory)
    }

    private fun populateItems(player: Player, inventory: Inventory) {
        val currentTeam = TeamUtil.getPlayerTeam(player)?.name
        val playerTeam = TeamUtil.getTeam("player")
        val spectatorTeam = TeamUtil.getTeam("spectator")
        val gameState = BingoManager.currentGame?.state
        val locked = gameState != null && gameState != GameState.INACTIVE

        val playerTeamItem = itemStack(Material.LIME_CONCRETE) {
            name("<bold><green>Player")
            if (locked) {
                lore(
                    " ",
                    "<dark_gray>=====================",
                    "<gray>Join the game as a player",
                    "<dark_gray>=====================",
                    "   ",
                    "<gray>Members: <white>${playerTeam?.memberCount ?: 0}",
                    "   ",
                    "<red>Locked until game is reset"
                )
            } else {
                lore(
                    " ",
                    "<dark_gray>=====================",
                    "<gray>Join the game as a player",
                    "<dark_gray>=====================",
                    "   ",
                    "<gray>Members: <white>${playerTeam?.memberCount ?: 0}"
                )
            }
            if (currentTeam == "player") {
                enchant(Enchantment.KNOCKBACK, 1)
                flag(ItemFlag.HIDE_ENCHANTS)
            }
        }

        val spectatorTeamItem = itemStack(Material.GRAY_CONCRETE) {
            name("<bold><gray>Spectator")
            if (locked) {
                lore(
                    " ",
                    "<dark_gray>=====================",
                    "<gray>Watch the game as a spectator",
                    "<dark_gray>=====================",
                    "   ",
                    "<gray>Members: <white>${spectatorTeam?.memberCount ?: 0}",
                    "   ",
                    "<red>Locked until game is reset"
                )
            } else {
                lore(
                    " ",
                    "<dark_gray>=====================",
                    "<gray>Watch the game as a spectator",
                    "<dark_gray>=====================",
                    "   ",
                    "<gray>Members: <white>${spectatorTeam?.memberCount ?: 0}"
                )
            }
            if (currentTeam == "spectator") {
                enchant(Enchantment.KNOCKBACK, 1)
                flag(ItemFlag.HIDE_ENCHANTS)
            }
        }

        val backButton = itemStack(Material.ARROW) {
            name("<bold><gray>Back")
        }
        val closeButton = itemStack(Material.BARRIER) {
            name("<bold><red>Close")
        }

        inventory.setItem(slotIndex.getValue("playerTeamSlot"), playerTeamItem)
        inventory.setItem(slotIndex.getValue("spectatorTeamSlot"), spectatorTeamItem)
        inventory.setItem(slotIndex.getValue("backButtonSlot"), backButton)
        inventory.setItem(slotIndex.getValue("closeButtonSlot"), closeButton)
    }
}
