package net.trilleo.mc.plugins.tribingo.guis.configMenus

import net.kyori.adventure.key.Key
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.trilleo.mc.plugins.tribingo.bingo.BingoManager
import net.trilleo.mc.plugins.tribingo.commands.bingo.BingoActions
import net.trilleo.mc.plugins.tribingo.enums.FillMode
import net.trilleo.mc.plugins.tribingo.enums.GameDifficulty
import net.trilleo.mc.plugins.tribingo.enums.GameState
import net.trilleo.mc.plugins.tribingo.listeners.game.SignInputListener
import net.trilleo.mc.plugins.tribingo.registration.GUIManager
import net.trilleo.mc.plugins.tribingo.registration.PluginGUI
import net.trilleo.mc.plugins.tribingo.utils.itemStack
import net.trilleo.mc.plugins.tribingo.utils.sendPrefixed
import org.bukkit.Material
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemFlag

/**
 * Settings GUI for configuring the Bingo game.
 *
 * All players can view this menu, but only players with the
 * `tribingo.bingo.manage` permission can interact with management buttons.
 */
class SettingsGUI : PluginGUI(
    id = "settings",
    title = Component.text("Settings").color(NamedTextColor.DARK_GRAY).decorate(TextDecoration.BOLD),
    rows = 6,
    fillMode = FillMode.LIGHT
) {
    val slotIndex: Map<String, Int> = mapOf(
        "difficultySlot" to 11,
        "timerSlot" to 13,
        "statusSlot" to 15,
        "startSlot" to 29,
        "stopSlot" to 31,
        "resetSlot" to 33,
        "refreshSlot" to 40,
        "backButtonSlot" to 48,
        "closeButtonSlot" to 49
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
            slotIndex.getValue("difficultySlot") -> handleDifficulty(player, event.inventory)
            slotIndex.getValue("timerSlot") -> handleTimer(player)
            slotIndex.getValue("startSlot") -> handleAction(player, event.inventory) { BingoActions.startGame() }
            slotIndex.getValue("stopSlot") -> handleAction(player, event.inventory) { BingoActions.stopGame() }
            slotIndex.getValue("resetSlot") -> handleAction(player, event.inventory) { BingoActions.resetGame() }
            slotIndex.getValue("refreshSlot") -> handleAction(player, event.inventory) { BingoActions.refreshBoard() }
        }
    }

    private fun handleDifficulty(player: Player, inventory: Inventory) {
        if (!player.hasPermission("tribingo.bingo.manage")) {
            player.sendPrefixed("<red>You don't have permission to change settings.")
            player.playSound(
                Sound.sound(Key.key("minecraft:entity.villager.no"), Sound.Source.UI, 1f, 1f)
            )
            return
        }
        val game = BingoManager.currentGame
        if (game == null || game.state != GameState.INACTIVE) {
            player.sendPrefixed("<red>Difficulty can only be changed when the game is inactive.")
            return
        }
        val current = game.difficulty
        val next = when (current) {
            GameDifficulty.EASY -> GameDifficulty.MEDIUM
            GameDifficulty.MEDIUM -> GameDifficulty.HARD
            GameDifficulty.HARD -> GameDifficulty.EASY
        }
        game.difficulty = next
        player.playSound(
            Sound.sound(Key.key("minecraft:entity.experience_orb.pickup"), Sound.Source.UI, 1f, 1f)
        )
        populateItems(player, inventory)
    }

    private fun handleTimer(player: Player) {
        if (!player.hasPermission("tribingo.bingo.manage")) {
            player.sendPrefixed("<red>You don't have permission to change settings.")
            player.playSound(
                Sound.sound(Key.key("minecraft:entity.villager.no"), Sound.Source.UI, 1f, 1f)
            )
            return
        }
        if (BingoManager.isGameActive()) {
            player.sendPrefixed("<red>Cannot change the timer while a game is active.")
            return
        }
        player.closeInventory()
        SignInputListener.requestInput(player)
    }

    private fun handleAction(
        player: Player,
        inventory: Inventory,
        action: () -> BingoActions.ActionResult
    ) {
        if (!player.hasPermission("tribingo.bingo.manage")) {
            player.sendPrefixed("<red>You don't have permission to manage the game.")
            player.playSound(
                Sound.sound(Key.key("minecraft:entity.villager.no"), Sound.Source.UI, 1f, 1f)
            )
            return
        }
        val result = action()
        player.sendPrefixed(result.message)
        if (result.success) {
            player.playSound(
                Sound.sound(Key.key("minecraft:entity.experience_orb.pickup"), Sound.Source.UI, 1f, 1f)
            )
        }
        populateItems(player, inventory)
    }

    private fun populateItems(player: Player, inventory: Inventory) {
        val game = BingoManager.currentGame
        val state = game?.state ?: GameState.INACTIVE
        val difficulty = game?.difficulty ?: GameDifficulty.MEDIUM
        val hasPermission = player.hasPermission("tribingo.bingo.manage")
        val timerSeconds = BingoManager.getTimerSeconds()

        // Difficulty button
        val diffMaterial = when (difficulty) {
            GameDifficulty.EASY -> Material.LIME_DYE
            GameDifficulty.MEDIUM -> Material.ORANGE_DYE
            GameDifficulty.HARD -> Material.RED_DYE
        }
        val difficultyItem = itemStack(diffMaterial) {
            name("<bold><yellow>Difficulty")
            if (hasPermission) {
                lore(
                    " ",
                    "<dark_gray>=====================",
                    "<gray>Current: <white>${difficulty.name}",
                    "<dark_gray>=====================",
                    "   ",
                    "<yellow>Click to cycle"
                )
            } else {
                lore(
                    " ",
                    "<dark_gray>=====================",
                    "<gray>Current: <white>${difficulty.name}",
                    "<dark_gray>====================="
                )
            }
        }

        // Timer button
        val timerItem = itemStack(Material.CLOCK) {
            name("<bold><aqua>Timer")
            if (hasPermission) {
                lore(
                    " ",
                    "<dark_gray>=====================",
                    "<gray>Current: <white>${formatSeconds(timerSeconds)}",
                    "<dark_gray>=====================",
                    "   ",
                    "<yellow>Click to set via sign"
                )
            } else {
                lore(
                    " ",
                    "<dark_gray>=====================",
                    "<gray>Current: <white>${formatSeconds(timerSeconds)}",
                    "<dark_gray>====================="
                )
            }
        }

        // Status display
        val stateMaterial = when (state) {
            GameState.INACTIVE -> Material.GRAY_CONCRETE
            GameState.ACTIVE -> Material.GREEN_CONCRETE
            GameState.ENDED -> Material.RED_CONCRETE
        }
        val statusItem = itemStack(stateMaterial) {
            name("<bold><white>Status")
            lore(
                " ",
                "<dark_gray>=====================",
                "<gray>State: <white>${state.name}",
                "<gray>Difficulty: <white>${difficulty.name}",
                "<gray>Timer: <white>${formatSeconds(timerSeconds)}",
                "<gray>Players: <white>${game?.playerStates?.size ?: 0}",
                "<dark_gray>====================="
            )
        }

        // Start button
        val startItem = if (hasPermission && state == GameState.INACTIVE) {
            itemStack(Material.GREEN_CONCRETE) {
                name("<bold><green>Start Game")
                lore(
                    " ",
                    "<dark_gray>=====================",
                    "<gray>Start the Bingo game",
                    "<dark_gray>====================="
                )
                enchant(Enchantment.KNOCKBACK, 1)
                flag(ItemFlag.HIDE_ENCHANTS)
            }
        } else {
            itemStack(Material.GREEN_CONCRETE) {
                name("<bold><green>Start Game")
                lore(
                    " ",
                    "<dark_gray>=====================",
                    if (!hasPermission) "<red>No permission"
                    else "<red>Not available in state: $state",
                    "<dark_gray>====================="
                )
            }
        }

        // Stop button
        val stopItem = if (hasPermission && state == GameState.ACTIVE) {
            itemStack(Material.RED_CONCRETE) {
                name("<bold><red>Stop Game")
                lore(
                    " ",
                    "<dark_gray>=====================",
                    "<gray>Stop the current game",
                    "<dark_gray>====================="
                )
                enchant(Enchantment.KNOCKBACK, 1)
                flag(ItemFlag.HIDE_ENCHANTS)
            }
        } else {
            itemStack(Material.RED_CONCRETE) {
                name("<bold><red>Stop Game")
                lore(
                    " ",
                    "<dark_gray>=====================",
                    if (!hasPermission) "<red>No permission"
                    else "<red>No active game to stop",
                    "<dark_gray>====================="
                )
            }
        }

        // Reset button
        val resetItem = if (hasPermission && game != null && state != GameState.ACTIVE) {
            itemStack(Material.YELLOW_CONCRETE) {
                name("<bold><yellow>Reset Game")
                lore(
                    " ",
                    "<dark_gray>=====================",
                    "<gray>Reset all player progress",
                    "<dark_gray>====================="
                )
                enchant(Enchantment.KNOCKBACK, 1)
                flag(ItemFlag.HIDE_ENCHANTS)
            }
        } else {
            itemStack(Material.YELLOW_CONCRETE) {
                name("<bold><yellow>Reset Game")
                lore(
                    " ",
                    "<dark_gray>=====================",
                    if (!hasPermission) "<red>No permission"
                    else if (game == null) "<red>No game exists"
                    else "<red>Cannot reset during active game",
                    "<dark_gray>====================="
                )
            }
        }

        // Refresh button
        val refreshItem = if (hasPermission && (game == null || state == GameState.INACTIVE)) {
            itemStack(Material.LIGHT_BLUE_CONCRETE) {
                name("<bold><aqua>Refresh Board")
                lore(
                    " ",
                    "<dark_gray>=====================",
                    "<gray>Pick new random objectives",
                    "<dark_gray>====================="
                )
                enchant(Enchantment.KNOCKBACK, 1)
                flag(ItemFlag.HIDE_ENCHANTS)
            }
        } else {
            itemStack(Material.LIGHT_BLUE_CONCRETE) {
                name("<bold><aqua>Refresh Board")
                lore(
                    " ",
                    "<dark_gray>=====================",
                    if (!hasPermission) "<red>No permission"
                    else "<red>Game must be INACTIVE to refresh",
                    "<dark_gray>====================="
                )
            }
        }

        val backButton = itemStack(Material.ARROW) {
            name("<bold><gray>Back")
        }
        val closeButton = itemStack(Material.BARRIER) {
            name("<bold><red>Close")
        }

        inventory.setItem(slotIndex.getValue("difficultySlot"), difficultyItem)
        inventory.setItem(slotIndex.getValue("timerSlot"), timerItem)
        inventory.setItem(slotIndex.getValue("statusSlot"), statusItem)
        inventory.setItem(slotIndex.getValue("startSlot"), startItem)
        inventory.setItem(slotIndex.getValue("stopSlot"), stopItem)
        inventory.setItem(slotIndex.getValue("resetSlot"), resetItem)
        inventory.setItem(slotIndex.getValue("refreshSlot"), refreshItem)
        inventory.setItem(slotIndex.getValue("backButtonSlot"), backButton)
        inventory.setItem(slotIndex.getValue("closeButtonSlot"), closeButton)
    }

    private fun formatSeconds(totalSeconds: Int): String {
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        return if (h > 0) String.format("%02d:%02d:%02d", h, m, s)
        else String.format("%02d:%02d", m, s)
    }
}
