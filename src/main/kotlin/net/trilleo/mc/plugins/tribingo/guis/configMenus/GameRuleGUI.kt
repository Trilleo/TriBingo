package net.trilleo.mc.plugins.tribingo.guis.configMenus

import net.kyori.adventure.key.Key
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.trilleo.mc.plugins.tribingo.enums.FillMode
import net.trilleo.mc.plugins.tribingo.registration.GUIManager
import net.trilleo.mc.plugins.tribingo.registration.PluginGUI
import net.trilleo.mc.plugins.tribingo.utils.GameRuleUtil
import net.trilleo.mc.plugins.tribingo.utils.itemStack
import org.bukkit.GameRules
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemFlag
import org.bukkit.plugin.java.JavaPlugin

class GameRuleGUI(private val plugin: JavaPlugin) : PluginGUI(
    id = "gamerule",
    title = Component.text("GameRule Modifier").color(NamedTextColor.DARK_AQUA).decorate(TextDecoration.BOLD),
    rows = 6,
    fillMode = FillMode.LIGHT
) {
    val slotIndex: Map<String, Int> = mapOf(
        "backButtonSlot" to 48,
        "closeButtonSlot" to 49
    )
    val ruleIndex: Map<String, Int> = mapOf(
        "advanceTimeSlot" to 10,
        "advanceWeatherSlot" to 12,
        "locatorBarSlot" to 14,
        "keepInventorySlot" to 16,
        "immediateRespawnSlot" to 20,
        "showAdvancementMessagesSlot" to 22,
        "fallDamageSlot" to 24,
        "fireDamageSlot" to 28,
        "freezeDamageSlot" to 30,
        "limitedCraftingSlot" to 32,
        "tntExplodesSlot" to 34
    )

    fun refreshRules(player: Player, inventory: Inventory) {
        val advanceTimeButton = itemStack(Material.CLOCK) {
            name("<bold><white>Advance Time")
            lore(
                "   ",
                "<gray>Whether to do daylight cycle",
                "   ",
                "<white>Value: <yellow>${GameRuleUtil.get(player.world, GameRules.ADVANCE_TIME)}",
                "   ",
                "<gray>[Click] <dark_gray>to toggle"
            )
        }
        val advanceWeatherButton = itemStack(Material.WATER_BUCKET) {
            name("<bold><white>Advance Weather")
            lore(
                "   ",
                "<gray>Whether to do weather cycle",
                "   ",
                "<white>Value: <yellow>${GameRuleUtil.get(player.world, GameRules.ADVANCE_WEATHER)}",
                "   ",
                "<gray>[Click] <dark_gray>to toggle"
            )
        }
        val locatorBarButton = itemStack(Material.COMPASS) {
            name("<bold><white>Locator Bar")
            lore(
                "   ",
                "<gray>Whether to show locator bar",
                "   ",
                "<white>Value: <yellow>${GameRuleUtil.get(player.world, GameRules.LOCATOR_BAR)}",
                "   ",
                "<gray>[Click] <dark_gray>to toggle"
            )
        }
        val keepInventoryButton = itemStack(Material.BOOK) {
            name("<bold><white>Keep Inventory")
            lore(
                "   ",
                "<gray>Whether to keep inventory on death",
                "   ",
                "<white>Value: <yellow>${GameRuleUtil.get(player.world, GameRules.KEEP_INVENTORY)}",
                "   ",
                "<gray>[Click] <dark_gray>to toggle"
            )
        }
        val immediateRespawnButton = itemStack(Material.RED_BED) {
            name("<bold><white>Immediate Respawn")
            lore(
                "   ",
                "<gray>Whether to respawn immediately after death",
                "   ",
                "<white>Value: <yellow>${GameRuleUtil.get(player.world, GameRules.IMMEDIATE_RESPAWN)}",
                "   ",
                "<gray>[Click] <dark_gray>to toggle"
            )
        }
        val showAdvancementMessagesButton = itemStack(Material.NETHER_STAR) {
            name("<bold><white>Show Advancement Messages")
            lore(
                "   ",
                "<gray>Whether to show advancement messages",
                "   ",
                "<white>Value: <yellow>${GameRuleUtil.get(player.world, GameRules.SHOW_ADVANCEMENT_MESSAGES)}",
                "   ",
                "<gray>[Click] <dark_gray>to toggle"
            )
        }
        val fallDamageButton = itemStack(Material.IRON_BOOTS) {
            name("<bold><white>Fall Damage")
            lore(
                "   ",
                "<gray>Whether to do fall damage",
                "   ",
                "<white>Value: <yellow>${GameRuleUtil.get(player.world, GameRules.FALL_DAMAGE)}",
                "   ",
                "<gray>[Click] <dark_gray>to toggle"
            )
            flag(ItemFlag.HIDE_ATTRIBUTES)
        }
        val fireDamageButton = itemStack(Material.LAVA_BUCKET) {
            name("<bold><white>Fire Damage")
            lore(
                "   ",
                "<gray>Whether to do fire damage",
                "   ",
                "<white>Value: <yellow>${GameRuleUtil.get(player.world, GameRules.FIRE_DAMAGE)}",
                "   ",
                "<gray>[Click] <dark_gray>to toggle"
            )
        }
        val freezeDamageButton = itemStack(Material.BLUE_ICE) {
            name("<bold><white>Freeze Damage")
            lore(
                "   ",
                "<gray>Whether to do freeze damage",
                "   ",
                "<white>Value: <yellow>${GameRuleUtil.get(player.world, GameRules.FREEZE_DAMAGE)}",
                "   ",
                "<gray>[Click] <dark_gray>to toggle"
            )
        }
        val limitedCraftingButton = itemStack(Material.CRAFTING_TABLE) {
            name("<bold><white>Limited Crafting")
            lore(
                "   ",
                "<gray>Whether to limit crafting to recipes that player have unlocked",
                "   ",
                "<white>Value: <yellow>${GameRuleUtil.get(player.world, GameRules.LIMITED_CRAFTING)}",
                "   ",
                "<gray>[Click] <dark_gray>to toggle"
            )
        }
        val tntExplodesButton = itemStack(Material.TNT) {
            name("<bold><white>TNT Explodes")
            lore(
                "   ",
                "<gray>Whether to explode TNT",
                "   ",
                "<white>Value: <yellow>${GameRuleUtil.get(player.world, GameRules.TNT_EXPLODES)}",
                "   ",
                "<gray>[Click] <dark_gray>to toggle"
            )
        }

        inventory.setItem(ruleIndex.getValue("advanceTimeSlot"), advanceTimeButton)
        inventory.setItem(ruleIndex.getValue("advanceWeatherSlot"), advanceWeatherButton)
        inventory.setItem(ruleIndex.getValue("locatorBarSlot"), locatorBarButton)
        inventory.setItem(ruleIndex.getValue("keepInventorySlot"), keepInventoryButton)
        inventory.setItem(ruleIndex.getValue("immediateRespawnSlot"), immediateRespawnButton)
        inventory.setItem(ruleIndex.getValue("showAdvancementMessagesSlot"), showAdvancementMessagesButton)
        inventory.setItem(ruleIndex.getValue("fallDamageSlot"), fallDamageButton)
        inventory.setItem(ruleIndex.getValue("fireDamageSlot"), fireDamageButton)
        inventory.setItem(ruleIndex.getValue("freezeDamageSlot"), freezeDamageButton)
        inventory.setItem(ruleIndex.getValue("limitedCraftingSlot"), limitedCraftingButton)
        inventory.setItem(ruleIndex.getValue("tntExplodesSlot"), tntExplodesButton)
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

        refreshRules(player, inventory)
    }

    override fun onClick(event: InventoryClickEvent) {
        event.isCancelled = true
        val player = event.whoClicked as Player
        if (event.slot in slotIndex.values) {
            player.playSound(
                Sound.sound(Key.key("minecraft:ui.button.click"), Sound.Source.UI, 1f, 1f)
            )
        }
        if (event.slot in ruleIndex.values) {
            player.playSound(
                Sound.sound(Key.key("minecraft:entity.experience_orb.pickup"), Sound.Source.UI, 1f, 1f)
            )
        }

        if (event.slot == slotIndex.getValue("closeButtonSlot")) {
            player.closeInventory()
        }
        if (event.slot == slotIndex.getValue("backButtonSlot")) {
            GUIManager.open(player, "settings")
        }

        if (event.slot == ruleIndex.getValue("advanceTimeSlot")) {
            for (world in player.server.worlds) {
                GameRuleUtil.toggle(world, GameRules.ADVANCE_TIME)
            }
            refreshRules(player, event.inventory)
        }
        if (event.slot == ruleIndex.getValue("advanceWeatherSlot")) {
            for (world in player.server.worlds) {
                GameRuleUtil.toggle(world, GameRules.ADVANCE_WEATHER)
            }
            refreshRules(player, event.inventory)
        }
        if (event.slot == ruleIndex.getValue("locatorBarSlot")) {
            for (world in player.server.worlds) {
                GameRuleUtil.toggle(world, GameRules.LOCATOR_BAR)
            }
            refreshRules(player, event.inventory)
        }
        if (event.slot == ruleIndex.getValue("keepInventorySlot")) {
            for (world in player.server.worlds) {
                GameRuleUtil.toggle(world, GameRules.KEEP_INVENTORY)
            }
            refreshRules(player, event.inventory)
        }
        if (event.slot == ruleIndex.getValue("immediateRespawnSlot")) {
            for (world in player.server.worlds) {
                GameRuleUtil.toggle(world, GameRules.IMMEDIATE_RESPAWN)
            }
            refreshRules(player, event.inventory)
        }
        if (event.slot == ruleIndex.getValue("showAdvancementMessagesSlot")) {
            for (world in player.server.worlds) {
                GameRuleUtil.toggle(world, GameRules.SHOW_ADVANCEMENT_MESSAGES)
            }
            refreshRules(player, event.inventory)
        }
        if (event.slot == ruleIndex.getValue("fallDamageSlot")) {
            for (world in player.server.worlds) {
                GameRuleUtil.toggle(world, GameRules.FALL_DAMAGE)
            }
            refreshRules(player, event.inventory)
        }
        if (event.slot == ruleIndex.getValue("fireDamageSlot")) {
            for (world in player.server.worlds) {
                GameRuleUtil.toggle(world, GameRules.FIRE_DAMAGE)
            }
            refreshRules(player, event.inventory)
        }
        if (event.slot == ruleIndex.getValue("freezeDamageSlot")) {
            for (world in player.server.worlds) {
                GameRuleUtil.toggle(world, GameRules.FREEZE_DAMAGE)
            }
            refreshRules(player, event.inventory)
        }
        if (event.slot == ruleIndex.getValue("limitedCraftingSlot")) {
            for (world in player.server.worlds) {
                GameRuleUtil.toggle(world, GameRules.LIMITED_CRAFTING)
            }
            refreshRules(player, event.inventory)
        }
        if (event.slot == ruleIndex.getValue("tntExplodesSlot")) {
            for (world in player.server.worlds) {
                GameRuleUtil.toggle(world, GameRules.TNT_EXPLODES)
            }
            refreshRules(player, event.inventory)
        }
    }
}