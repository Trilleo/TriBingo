package net.trilleo.mc.plugins.tribingo.guis.mainMenus

import net.kyori.adventure.key.Key
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.title.Title
import net.trilleo.mc.plugins.tribingo.enums.FillMode
import net.trilleo.mc.plugins.tribingo.registration.GUIManager
import net.trilleo.mc.plugins.tribingo.registration.PluginGUI
import net.trilleo.mc.plugins.tribingo.utils.itemStack
import net.trilleo.mc.plugins.tribingo.utils.sendPrefixed
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.meta.SkullMeta
import java.util.UUID

class CreditsGUI : PluginGUI(
    id = "credits",
    title = Component.text("Credits").color(NamedTextColor.LIGHT_PURPLE).decorate(TextDecoration.BOLD),
    rows = 5,
    fillMode = FillMode.LIGHT
) {
    val slotIndex: Map<String, Int> = mapOf(
        "backButtonSlot" to 39,
        "closeButtonSlot" to 40,
        "authorButtonSlot" to 4,
        "youtubeButtonSlot" to 21,
        "githubButtonSlot" to 23
    )

    val youtubeLink = "https://www.youtube.com/@TheTrilleo"
    val githubLink = "https://github.com/Trilleo"

    override fun setup(player: Player, inventory: Inventory) {
        val authorUUID = "28468a45-b78c-4968-9782-f4f893216066"

        val closeButton = itemStack(Material.BARRIER) {
            name("<bold><red>Close")
        }
        val backButton = itemStack(Material.ARROW) {
            name("<bold><gray>Back")
        }
        val authorButton = itemStack(Material.PLAYER_HEAD) {
            name("<bold><yellow>Made by <gold>Trilleo")
            meta {
                (this as SkullMeta)
                    .owningPlayer = Bukkit.getPlayer(UUID.fromString(authorUUID))
            }
        }
        val youtubeButton = itemStack(Material.RED_CONCRETE) {
            name("<bold><red>You<white>Tube")
            lore(
                "   ",
                "<gray>Click for my YouTube link"
            )
        }
        val githubButton = itemStack(Material.GRAY_CONCRETE) {
            name("<bold><gray>GitHub")
            lore(
                "   ",
                "<gray>Click for my GitHub link"
            )
        }

        inventory.setItem(slotIndex.getValue("backButtonSlot"), backButton)
        inventory.setItem(slotIndex.getValue("closeButtonSlot"), closeButton)
        inventory.setItem(slotIndex.getValue("authorButtonSlot"), authorButton)
        inventory.setItem(slotIndex.getValue("youtubeButtonSlot"), youtubeButton)
        inventory.setItem(slotIndex.getValue("githubButtonSlot"), githubButton)
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
        if (event.slot == slotIndex.getValue("backButtonSlot")) {
            GUIManager.open(player, "main")
        }
        if (event.slot == slotIndex.getValue("authorButtonSlot")) {
            val title: Title = Title.title(
                Component.text("Trilleo").color(NamedTextColor.GOLD).decorate(TextDecoration.BOLD),
                Component.text("PLUGIN AUTHOR").color(NamedTextColor.GRAY),
                10,
                40,
                10
            )

            player.closeInventory()
            player.playSound(Sound.sound(Key.key("minecraft:entity.player.levelup"), Sound.Source.PLAYER, 1f, 1f))
            player.showTitle(title)
        }
        if (event.slot == slotIndex.getValue("youtubeButtonSlot")) {
            player.closeInventory()
            player.sendPrefixed("<click:open_url:$youtubeLink><gray>[YouTube Link]")
        }
        if (event.slot == slotIndex.getValue("githubButtonSlot")) {
            player.closeInventory()
            player.sendPrefixed("<click:open_url:$githubLink><gray>[GitHub Link]")
        }
    }
}