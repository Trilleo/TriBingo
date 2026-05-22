package net.trilleo.mc.plugins.tribingo.bingo.custom

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.trilleo.mc.plugins.tribingo.bingo.BingoManager
import net.trilleo.mc.plugins.tribingo.bingo.BingoPlayerState
import net.trilleo.mc.plugins.tribingo.bingo.EventBingoObjective
import net.trilleo.mc.plugins.tribingo.bingo.MultiEventBingoObjective
import net.trilleo.mc.plugins.tribingo.bingo.annotation.CustomObjective
import net.trilleo.mc.plugins.tribingo.enums.Difficulty
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.*
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityPickupItemEvent
import org.bukkit.event.inventory.BrewEvent
import org.bukkit.event.inventory.CraftItemEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.player.PlayerBucketEntityEvent
import org.bukkit.event.player.PlayerDeathEvent
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.AxolotlBucketMeta
import org.bukkit.inventory.meta.PotionMeta

private const val SWITCH_MEANS_SUPER_WITCH_REQUIRED = 10
private const val LIBRARIAN_REQUIRED_BOOKS = 10
private const val MURDER_VILLAGER_REQUIRED_KILLS = 30
private const val MISC5_ATTRIBUTION_RADIUS = 6.0

private val misc5PotionMaterials = setOf(Material.POTION, Material.SPLASH_POTION, Material.LINGERING_POTION)
private val misc5RainbowRequiredSteps = setOf(
    Material.WHITE_WOOL.name,
    Material.ORANGE_WOOL.name,
    Material.MAGENTA_WOOL.name,
    Material.LIGHT_BLUE_WOOL.name,
    Material.YELLOW_WOOL.name,
    Material.LIME_WOOL.name,
    Material.PINK_WOOL.name,
    Material.GRAY_WOOL.name,
    Material.LIGHT_GRAY_WOOL.name,
    Material.CYAN_WOOL.name,
    Material.PURPLE_WOOL.name,
    Material.BLUE_WOOL.name,
    Material.BROWN_WOOL.name,
    Material.GREEN_WOOL.name,
    Material.RED_WOOL.name,
    Material.BLACK_WOOL.name
)
private val misc5GameOfSkullsRequiredSteps = setOf(
    Material.ZOMBIE_HEAD.name,
    Material.SKELETON_SKULL.name,
    Material.CREEPER_HEAD.name,
    Material.WITHER_SKELETON_SKULL.name,
    Material.PIGLIN_HEAD.name,
    Material.DRAGON_HEAD.name
)
private val misc5DrunkDriverCauses = setOf(
    EntityDamageEvent.DamageCause.LAVA,
    EntityDamageEvent.DamageCause.FIRE,
    EntityDamageEvent.DamageCause.FIRE_TICK
)

private fun misc5NearestActivePlayer(location: Location, objectiveId: String): Player? =
    location.getNearbyPlayers(MISC5_ATTRIBUTION_RADIUS)
        .sortedBy { it.location.distanceSquared(location) }
        .firstOrNull { BingoManager.getActiveState(it, objectiveId) != null }

private fun misc5PotionSteps(player: Player): Set<String> =
    player.inventory.contents.asSequence()
        .filterNotNull()
        .filter { it.type in misc5PotionMaterials }
        .mapNotNull { it.itemMeta as? PotionMeta }
        .mapNotNull { it.basePotionType?.name }
        .toSet()

private fun misc5MaterialStep(item: ItemStack?, requiredSteps: Set<String>): String? {
    val materialName = item?.type?.name ?: return null
    return materialName.takeIf { it in requiredSteps }
}

private fun misc5TrackInventorySteps(player: Player, state: BingoPlayerState, objectiveId: String, requiredSteps: Set<String>) {
    player.inventory.contents
        .mapNotNull { misc5MaterialStep(it, requiredSteps) }
        .forEach { state.addStep(objectiveId, it) }
}

private fun misc5HeldItemFor(event: PlayerInteractEntityEvent): ItemStack =
    when (event.hand) {
        EquipmentSlot.OFF_HAND -> event.player.inventory.itemInOffHand
        else -> event.player.inventory.itemInMainHand
    }

private fun misc5IsBlueAxolotlBucket(item: ItemStack?): Boolean {
    if (item?.type != Material.AXOLOTL_BUCKET) return false
    val meta = item.itemMeta as? AxolotlBucketMeta ?: return false
    return meta.hasVariant() && meta.variant == Axolotl.Variant.BLUE
}

private fun misc5EnchantedBookCount(player: Player): Int =
    player.inventory.contents
        .filterNotNull()
        .filter { it.type == Material.ENCHANTED_BOOK }
        .sumOf { it.amount }

private fun misc5EnchantedBookAmount(item: ItemStack?): Int =
    if (item?.type == Material.ENCHANTED_BOOK) item.amount else 0

@CustomObjective
class SwitchMeansSuperWitchObjective : MultiEventBingoObjective(
    id = "switch_means_super_witch",
    name = Component.text("Switch means super witch"),
    description = Component.text("Have 10 kinds of potions."),
    difficulty = Difficulty.HARD
) {
    private val count = SWITCH_MEANS_SUPER_WITCH_REQUIRED

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onInventoryClose(event: InventoryCloseEvent) {
        val player = event.player as? Player ?: return
        val state = BingoManager.getActiveState(player, id) ?: return
        misc5PotionSteps(player).forEach { state.addStep(id, it) }
        if (state.getSteps(id).size >= count) {
            BingoManager.checkCompletion(player, this)
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onBrew(event: BrewEvent) {
        val player = misc5NearestActivePlayer(event.block.location, id) ?: return
        val state = BingoManager.getActiveState(player, id) ?: return
        misc5PotionSteps(player).forEach { state.addStep(id, it) }
        if (state.getSteps(id).size >= count) {
            BingoManager.checkCompletion(player, this)
        }
    }

    override fun isCompletedBy(player: Player, state: BingoPlayerState): Boolean =
        state.getSteps(id).size >= count

    override fun onReset(player: Player, state: BingoPlayerState) =
        state.clearSteps(id)

    override fun displayItem(player: Player, completed: Boolean): ItemStack {
        val progress = BingoManager.currentGame
            ?.getOrCreateState(player.uniqueId)?.getSteps(id)?.size ?: 0
        return buildProgressItem(
            player,
            completed,
            Component.text("Progress: $progress/$count", NamedTextColor.YELLOW)
        )
    }
}

@CustomObjective
class RainbowObjective : MultiEventBingoObjective(
    id = "rainbow",
    name = Component.text("Rainbow"),
    description = Component.text("Get wool of all colors."),
    difficulty = Difficulty.MEDIUM
) {
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onEntityPickupItem(event: EntityPickupItemEvent) {
        val player = event.entity as? Player ?: return
        val step = misc5MaterialStep(event.item.itemStack, misc5RainbowRequiredSteps) ?: return
        val state = BingoManager.getActiveState(player, id) ?: return
        if (state.addStep(id, step) && state.getSteps(id).containsAll(misc5RainbowRequiredSteps)) {
            BingoManager.checkCompletion(player, this)
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val state = BingoManager.getActiveState(player, id) ?: return
        misc5TrackInventorySteps(player, state, id, misc5RainbowRequiredSteps)
        misc5MaterialStep(event.currentItem, misc5RainbowRequiredSteps)?.let { state.addStep(id, it) }
        misc5MaterialStep(event.cursor, misc5RainbowRequiredSteps)?.let { state.addStep(id, it) }
        if (state.getSteps(id).containsAll(misc5RainbowRequiredSteps)) {
            BingoManager.checkCompletion(player, this)
        }
    }

    override fun isCompletedBy(player: Player, state: BingoPlayerState): Boolean =
        state.getSteps(id).containsAll(misc5RainbowRequiredSteps)

    override fun onReset(player: Player, state: BingoPlayerState) =
        state.clearSteps(id)

    override fun displayItem(player: Player, completed: Boolean): ItemStack {
        val progress = BingoManager.currentGame
            ?.getOrCreateState(player.uniqueId)?.getSteps(id)?.size ?: 0
        return buildProgressItem(
            player,
            completed,
            Component.text("Progress: $progress/${misc5RainbowRequiredSteps.size}", NamedTextColor.YELLOW)
        )
    }
}

@CustomObjective
class InfinityVioletObjective : EventBingoObjective<CraftItemEvent>(
    id = "infinity_violet",
    name = Component.text("Infinity Violet"),
    description = Component.text("Craft an amethyst block."),
    difficulty = Difficulty.EASY,
    eventClass = CraftItemEvent::class.java
) {
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onCraft(event: CraftItemEvent) {
        if (event.recipe.result.type != Material.AMETHYST_BLOCK) return
        val player = event.whoClicked as? Player ?: return
        val state = BingoManager.getActiveState(player, id) ?: return
        onEvent(event, player, state)
    }

    override fun onEvent(event: CraftItemEvent, player: Player, state: BingoPlayerState) {
        state.setString(id, "done", "true")
        BingoManager.checkCompletion(player, this)
    }

    override fun isCompletedBy(player: Player, state: BingoPlayerState): Boolean =
        state.getString(id, "done") == "true"

    override fun onReset(player: Player, state: BingoPlayerState) =
        state.removeString(id, "done")
}

@CustomObjective
class TheChosenOne1Objective : MultiEventBingoObjective(
    id = "the_chosen_one_1",
    name = Component.text("The Chosen One 1"),
    description = Component.text("Get a deepslate coal ore (use Silk Touch)."),
    difficulty = Difficulty.INSANE
) {
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onBlockBreak(event: BlockBreakEvent) {
        if (event.block.type != Material.DEEPSLATE_COAL_ORE) return
        if (!event.player.inventory.itemInMainHand.containsEnchantment(Enchantment.SILK_TOUCH)) return
        val state = BingoManager.getActiveState(event.player, id) ?: return
        state.setString(id, "done", "true")
        BingoManager.checkCompletion(event.player, this)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onEntityPickupItem(event: EntityPickupItemEvent) {
        val player = event.entity as? Player ?: return
        if (event.item.itemStack.type != Material.DEEPSLATE_COAL_ORE) return
        val state = BingoManager.getActiveState(player, id) ?: return
        state.setString(id, "done", "true")
        BingoManager.checkCompletion(player, this)
    }

    override fun isCompletedBy(player: Player, state: BingoPlayerState): Boolean =
        state.getString(id, "done") == "true"

    override fun onReset(player: Player, state: BingoPlayerState) =
        state.removeString(id, "done")
}

@CustomObjective
class TheChosenOne2Objective : MultiEventBingoObjective(
    id = "the_chosen_one_2",
    name = Component.text("The Chosen One 2"),
    description = Component.text("Find a blue axolotl and place it in your bucket."),
    difficulty = Difficulty.INSANE
) {
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPlayerBucketEntity(event: PlayerBucketEntityEvent) {
        val axolotl = event.entity as? Axolotl ?: return
        if (axolotl.variant != Axolotl.Variant.BLUE && !misc5IsBlueAxolotlBucket(event.entityBucket)) return
        val state = BingoManager.getActiveState(event.player, id) ?: return
        state.setString(id, "done", "true")
        BingoManager.checkCompletion(event.player, this)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPlayerInteractEntity(event: PlayerInteractEntityEvent) {
        val axolotl = event.rightClicked as? Axolotl ?: return
        if (axolotl.variant != Axolotl.Variant.BLUE) return
        if (misc5HeldItemFor(event).type != Material.WATER_BUCKET) return
        val state = BingoManager.getActiveState(event.player, id) ?: return
        state.setString(id, "done", "true")
        BingoManager.checkCompletion(event.player, this)
    }

    override fun isCompletedBy(player: Player, state: BingoPlayerState): Boolean =
        state.getString(id, "done") == "true"

    override fun onReset(player: Player, state: BingoPlayerState) =
        state.removeString(id, "done")
}

@CustomObjective
class PandoraGumObjective : EventBingoObjective<CraftItemEvent>(
    id = "pandora_gum",
    name = Component.text("Pandora Gum"),
    description = Component.text("Craft a resin block."),
    difficulty = Difficulty.EASY,
    eventClass = CraftItemEvent::class.java
) {
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onCraft(event: CraftItemEvent) {
        if (event.recipe.result.type.name != "RESIN_BLOCK") return
        val player = event.whoClicked as? Player ?: return
        val state = BingoManager.getActiveState(player, id) ?: return
        onEvent(event, player, state)
    }

    override fun onEvent(event: CraftItemEvent, player: Player, state: BingoPlayerState) {
        state.setString(id, "done", "true")
        BingoManager.checkCompletion(player, this)
    }

    override fun isCompletedBy(player: Player, state: BingoPlayerState): Boolean =
        state.getString(id, "done") == "true"

    override fun onReset(player: Player, state: BingoPlayerState) =
        state.removeString(id, "done")
}

@CustomObjective
class GameOfSkullsObjective : MultiEventBingoObjective(
    id = "game_of_skulls",
    name = Component.text("Game of Skulls"),
    description = Component.text("Collect all kinds of heads (zombie, skeleton, creeper, wither skeleton, piglin, ender dragon)."),
    difficulty = Difficulty.INSANE
) {
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onEntityPickupItem(event: EntityPickupItemEvent) {
        val player = event.entity as? Player ?: return
        val step = misc5MaterialStep(event.item.itemStack, misc5GameOfSkullsRequiredSteps) ?: return
        val state = BingoManager.getActiveState(player, id) ?: return
        if (state.addStep(id, step) && state.getSteps(id).containsAll(misc5GameOfSkullsRequiredSteps)) {
            BingoManager.checkCompletion(player, this)
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val state = BingoManager.getActiveState(player, id) ?: return
        misc5TrackInventorySteps(player, state, id, misc5GameOfSkullsRequiredSteps)
        misc5MaterialStep(event.currentItem, misc5GameOfSkullsRequiredSteps)?.let { state.addStep(id, it) }
        misc5MaterialStep(event.cursor, misc5GameOfSkullsRequiredSteps)?.let { state.addStep(id, it) }
        if (state.getSteps(id).containsAll(misc5GameOfSkullsRequiredSteps)) {
            BingoManager.checkCompletion(player, this)
        }
    }

    override fun isCompletedBy(player: Player, state: BingoPlayerState): Boolean =
        state.getSteps(id).containsAll(misc5GameOfSkullsRequiredSteps)

    override fun onReset(player: Player, state: BingoPlayerState) =
        state.clearSteps(id)

    override fun displayItem(player: Player, completed: Boolean): ItemStack {
        val progress = BingoManager.currentGame
            ?.getOrCreateState(player.uniqueId)?.getSteps(id)?.size ?: 0
        return buildProgressItem(
            player,
            completed,
            Component.text("Progress: $progress/${misc5GameOfSkullsRequiredSteps.size}", NamedTextColor.YELLOW)
        )
    }
}

@CustomObjective
class RockPaperScissorsObjective : MultiEventBingoObjective(
    id = "rock_paper_scissors",
    name = Component.text("Rock, paper, scissors"),
    description = Component.text("Use paper to break a stone."),
    difficulty = Difficulty.EASY
) {
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onBlockBreak(event: BlockBreakEvent) {
        if (event.block.type != Material.STONE) return
        if (event.player.inventory.itemInMainHand.type != Material.PAPER) return
        val state = BingoManager.getActiveState(event.player, id) ?: return
        state.setString(id, "done", "true")
        BingoManager.checkCompletion(event.player, this)
    }

    override fun isCompletedBy(player: Player, state: BingoPlayerState): Boolean =
        state.getString(id, "done") == "true"

    override fun onReset(player: Player, state: BingoPlayerState) =
        state.removeString(id, "done")
}

@CustomObjective
class LibrarianObjective : MultiEventBingoObjective(
    id = "librarian",
    name = Component.text("Librarian"),
    description = Component.text("Collect 10 enchanted books."),
    difficulty = Difficulty.HARD
) {
    private val count = LIBRARIAN_REQUIRED_BOOKS

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onEntityPickupItem(event: EntityPickupItemEvent) {
        val player = event.entity as? Player ?: return
        if (event.item.itemStack.type != Material.ENCHANTED_BOOK) return
        val state = BingoManager.getActiveState(player, id) ?: return
        val progress = maxOf(state.getProgress(id), misc5EnchantedBookCount(player) + event.item.itemStack.amount)
        state.setProgress(id, progress)
        if (progress >= count) {
            BingoManager.checkCompletion(player, this)
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val state = BingoManager.getActiveState(player, id) ?: return
        val progress = maxOf(
            state.getProgress(id),
            misc5EnchantedBookCount(player),
            misc5EnchantedBookCount(player) + maxOf(
                misc5EnchantedBookAmount(event.currentItem),
                misc5EnchantedBookAmount(event.cursor)
            )
        )
        state.setProgress(id, progress)
        if (progress >= count) {
            BingoManager.checkCompletion(player, this)
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onInventoryClose(event: InventoryCloseEvent) {
        val player = event.player as? Player ?: return
        val state = BingoManager.getActiveState(player, id) ?: return
        val progress = maxOf(state.getProgress(id), misc5EnchantedBookCount(player))
        state.setProgress(id, progress)
        if (progress >= count) {
            BingoManager.checkCompletion(player, this)
        }
    }

    override fun isCompletedBy(player: Player, state: BingoPlayerState): Boolean =
        state.getProgress(id) >= count

    override fun onReset(player: Player, state: BingoPlayerState) {
        state.setProgress(id, 0)
    }

    override fun displayItem(player: Player, completed: Boolean): ItemStack {
        val progress = BingoManager.currentGame
            ?.getOrCreateState(player.uniqueId)?.getProgress(id) ?: 0
        return buildProgressItem(
            player,
            completed,
            Component.text("Progress: $progress/$count", NamedTextColor.YELLOW)
        )
    }
}

@CustomObjective
class DrunkDriverObjective : MultiEventBingoObjective(
    id = "drunk_driver",
    name = Component.text("Drunk driver"),
    description = Component.text("Ride your horse into lava and burn to death."),
    difficulty = Difficulty.EASY
) {
    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerDeath(event: PlayerDeathEvent) {
        val player = event.entity
        if (player.vehicle !is Horse) return
        if (player.lastDamageCause?.cause !in misc5DrunkDriverCauses) return
        val state = BingoManager.getActiveState(player, id) ?: return
        state.setString(id, "done", "true")
        BingoManager.checkCompletion(player, this)
    }

    override fun isCompletedBy(player: Player, state: BingoPlayerState): Boolean =
        state.getString(id, "done") == "true"

    override fun onReset(player: Player, state: BingoPlayerState) =
        state.removeString(id, "done")
}

@CustomObjective
class MurderVillagerObjective : EventBingoObjective<EntityDeathEvent>(
    id = "murder_villager",
    name = Component.text("Murder villager"),
    description = Component.text("Kill 30 villagers."),
    difficulty = Difficulty.EASY,
    eventClass = EntityDeathEvent::class.java
) {
    private val count = MURDER_VILLAGER_REQUIRED_KILLS

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onEntityDeath(event: EntityDeathEvent) {
        if (event.entity.type != EntityType.VILLAGER) return
        val player = event.entity.killer ?: return
        val state = BingoManager.getActiveState(player, id) ?: return
        onEvent(event, player, state)
    }

    override fun onEvent(event: EntityDeathEvent, player: Player, state: BingoPlayerState) {
        val progress = state.getProgress(id) + 1
        state.setProgress(id, progress)
        if (progress >= count) {
            BingoManager.checkCompletion(player, this)
        }
    }

    override fun isCompletedBy(player: Player, state: BingoPlayerState): Boolean =
        state.getProgress(id) >= count

    override fun onReset(player: Player, state: BingoPlayerState) {
        state.setProgress(id, 0)
    }

    override fun displayItem(player: Player, completed: Boolean): ItemStack {
        val progress = BingoManager.currentGame
            ?.getOrCreateState(player.uniqueId)?.getProgress(id) ?: 0
        return buildProgressItem(
            player,
            completed,
            Component.text("Progress: $progress/$count", NamedTextColor.YELLOW)
        )
    }
}
