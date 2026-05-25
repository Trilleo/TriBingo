package net.trilleo.mc.plugins.tribingo.guis.mainMenus

import net.kyori.adventure.key.Key
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.trilleo.mc.plugins.tribingo.commands.bingo.BingoActions
import net.trilleo.mc.plugins.tribingo.enums.FillMode
import net.trilleo.mc.plugins.tribingo.registration.GUIManager
import net.trilleo.mc.plugins.tribingo.registration.PluginGUI
import net.trilleo.mc.plugins.tribingo.utils.TeamUtil
import net.trilleo.mc.plugins.tribingo.utils.itemStack
import net.trilleo.mc.plugins.tribingo.utils.sendPrefixed
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.meta.SkullMeta
import org.bukkit.plugin.java.JavaPlugin
import java.util.*

class MainGUI(private val plugin: JavaPlugin) : PluginGUI(
    id = "main",
    title = Component.text("TriBingo Main UI").color(NamedTextColor.GOLD).decorate(TextDecoration.BOLD),
    rows = 6,
    fillMode = FillMode.LIGHT
) {
    val slotIndex: Map<String, Int> = mapOf(
        "startButtonSlot" to 13,
        "creditsButtonSlot" to 15,
        "settingsButtonSlot" to 11,
        "teamSelectButtonSlot" to 31,
        "bingoBoardButtonSlot" to 45,
        "closeButtonSlot" to 49
    )

    val authorUUID = "28468a45-b78c-4968-9782-f4f893216066"

    override fun setup(player: Player, inventory: Inventory) {
        val closeButton = itemStack(Material.BARRIER) {
            name("<bold><red>Close")
        }
        val startButton = itemStack(Material.GREEN_CONCRETE) {
            name("<bold><gradient:green:dark_green>Start</gradient></bold>")
            lore(
                " ",
                "<dark_gray>=====================",
                "<gray>Start the Bingo",
                "<dark_gray>====================="
            )
            enchant(Enchantment.KNOCKBACK, 1)
            flag(ItemFlag.HIDE_ENCHANTS)
        }
        val creditsButton = itemStack(Material.PLAYER_HEAD) {
            name("<bold><light_purple>Credits")
            lore(
                " ",
                "<dark_gray>=====================",
                "<gray>View contributors",
                "<dark_gray>====================="
            )
            meta {
                (this as SkullMeta)
                    .owningPlayer = Bukkit.getPlayer(UUID.fromString(authorUUID))
            }
        }
        val settingsButton = itemStack(Material.COMMAND_BLOCK) {
            name("<bold><dark_gray>Settings")
            lore(
                " ",
                "<dark_gray>=====================",
                "<gray>Configure game settings",
                "<dark_gray>====================="
            )
        }
        val teamSelectButton = itemStack(Material.DIAMOND_SWORD) {
            name("<bold><dark_blue>Team")
            lore(
                " ",
                "<dark_gray>=====================",
                "<gray>Select your team",
                "<dark_gray>=====================",
                "   ",
                "<white>Current team: ${TeamUtil.getPlayerTeam(player)?.displayName ?: "<dark_gray>None"}"
            )
            flag(ItemFlag.HIDE_ATTRIBUTES)
        }
        val bingoBoardButton = itemStack(Material.FILLED_MAP) {
            name("<bold><gold>✦ Bingo Board ✦")
            lore(
                " ",
                "<dark_gray>=====================",
                "<gray>View the Bingo board",
                "<dark_gray>====================="
            )
        }

        inventory.setItem(slotIndex.getValue("startButtonSlot"), startButton)
        inventory.setItem(slotIndex.getValue("creditsButtonSlot"), creditsButton)
        inventory.setItem(slotIndex.getValue("settingsButtonSlot"), settingsButton)
        inventory.setItem(slotIndex.getValue("teamSelectButtonSlot"), teamSelectButton)
        inventory.setItem(slotIndex.getValue("bingoBoardButtonSlot"), bingoBoardButton)
        inventory.setItem(slotIndex.getValue("closeButtonSlot"), closeButton)
    }

    override fun onClick(event: InventoryClickEvent) {
        event.isCancelled = true
        val player = event.whoClicked as Player
        if (event.slot in slotIndex.values) {
            player.playSound(
                Sound.sound(Key.key("minecraft:ui.button.click"), Sound.Source.UI, 1f, 1f)
            )
        }

        if (event.slot == slotIndex.getValue("closeButtonSlot")) {
            player.closeInventory()
        }
        if (event.slot == slotIndex.getValue("settingsButtonSlot")) {
            GUIManager.open(player, "settings")
        }
        if (event.slot == slotIndex.getValue("creditsButtonSlot")) {
            GUIManager.open(player, "credits")
        }
        if (event.slot == slotIndex.getValue("teamSelectButtonSlot")) {
            GUIManager.open(player, "team-select")
        }
        if (event.slot == slotIndex.getValue("bingoBoardButtonSlot")) {
            GUIManager.open(player, "bingo_board")
        }
        if (event.slot == slotIndex.getValue("startButtonSlot")) {
            player.closeInventory()
            if (!player.hasPermission("tribingo.bingo.manage")) {
                player.sendPrefixed("<red>You don't have permission to start the game.")
                return
            }
            val result = BingoActions.startGame()
            player.sendPrefixed(result.message)
        }
    }
}