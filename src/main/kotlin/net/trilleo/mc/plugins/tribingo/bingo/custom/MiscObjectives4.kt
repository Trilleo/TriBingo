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
import org.bukkit.block.Biome
import org.bukkit.block.BlockFace
import org.bukkit.entity.Arrow
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.bukkit.entity.TNTPrimed
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.block.BlockRedstoneEvent
import org.bukkit.event.block.TNTPrimeEvent
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.entity.EntityPickupItemEvent
import org.bukkit.event.inventory.CraftItemEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.generator.structure.Structure
import org.bukkit.inventory.ItemStack

private val GREEN_THUMB_REQUIRED_STEPS = setOf(
    Material.SWEET_BERRY_BUSH.name,
    Material.CAVE_VINES.name,
    Material.CARROTS.name,
    Material.POTATOES.name,
    Material.BEETROOTS.name,
    Material.WHEAT.name,
    Material.COCOA.name,
    Material.PUMPKIN_STEM.name,
    Material.MELON_STEM.name,
    Material.TORCHFLOWER_CROP.name,
    Material.PITCHER_CROP.name,
    Material.NETHER_WART.name,
    Material.KELP.name,
    Material.SUGAR_CANE.name,
    Material.BAMBOO.name,
    Material.CHORUS_FLOWER.name
)

private const val ANTI_AIRCRAFT_GUN_REQUIRED_KILLS = 3
private const val REDSTONE_TNT_ATTRIBUTION_RADIUS = 12.0
private const val DESERT_TEMPLE_PADDING = 8.0

private fun misc4NearestActivePlayer(location: Location, objectiveId: String, radius: Double): Player? =
    location.getNearbyPlayers(radius)
        .sortedBy { it.location.distanceSquared(location) }
        .firstOrNull { BingoManager.getActiveState(it, objectiveId) != null }

private fun misc4IsWithinGeneratedStructure(location: Location, structure: Structure, padding: Double = 0.0): Boolean {
    val x = location.x
    val y = location.y
    val z = location.z
    return location.chunk.getStructures().any { generated ->
        if (generated.structure != structure) {
            false
        } else {
            val box = generated.boundingBox
            x >= box.minX - padding && x <= box.maxX + padding &&
                y >= box.minY - padding && y <= box.maxY + padding &&
                z >= box.minZ - padding && z <= box.maxZ + padding
        }
    }
}

private fun misc4HasNearbyBlock(location: Location, material: Material, radius: Int): Boolean {
    val world = location.world
    val minY = maxOf(world.minHeight, location.blockY - radius)
    val maxY = minOf(world.maxHeight - 1, location.blockY + radius)
    for (x in location.blockX - radius..location.blockX + radius) {
        for (y in minY..maxY) {
            for (z in location.blockZ - radius..location.blockZ + radius) {
                if (world.getBlockAt(x, y, z).type == material) {
                    return true
                }
            }
        }
    }
    return false
}

private fun misc4IsLikelyDesertTempleTrap(player: Player, event: EntityDamageEvent): Boolean {
    val location = player.location
    if (misc4IsWithinGeneratedStructure(location, Structure.DESERT_PYRAMID, DESERT_TEMPLE_PADDING)) {
        return true
    }

    val biome = location.block.biome
    val sandyBiome = biome == Biome.DESERT || biome.key.key.contains("badlands", ignoreCase = true)
    if (!sandyBiome) return false

    val tntCausedExplosion = event.damageSource.causingEntity is TNTPrimed
    val nearbyTempleTnt = misc4HasNearbyBlock(location, Material.TNT, 6)
    val highestY = player.world.getHighestBlockYAt(location.blockX, location.blockZ)
    val undergroundEnough = highestY - location.blockY >= 8
    return undergroundEnough && (tntCausedExplosion || nearbyTempleTnt)
}

private fun misc4GreenThumbBlockPlaceStep(material: Material): String? = when (material) {
    Material.SWEET_BERRY_BUSH,
    Material.NETHER_WART,
    Material.KELP,
    Material.SUGAR_CANE,
    Material.BAMBOO,
    Material.CHORUS_FLOWER -> material.name
    else -> null
}

private fun misc4GreenThumbInteractStep(event: PlayerInteractEvent): String? {
    val clickedBlock = event.clickedBlock ?: return null
    return when (event.item?.type) {
        Material.GLOW_BERRIES -> if (event.blockFace == BlockFace.DOWN) Material.CAVE_VINES.name else null
        Material.CARROT,
        Material.POTATO,
        Material.BEETROOT_SEEDS,
        Material.WHEAT_SEEDS,
        Material.PUMPKIN_SEEDS,
        Material.MELON_SEEDS,
        Material.TORCHFLOWER_SEEDS,
        Material.PITCHER_POD -> if (clickedBlock.type == Material.FARMLAND) {
            when (event.item?.type) {
                Material.CARROT -> Material.CARROTS.name
                Material.POTATO -> Material.POTATOES.name
                Material.BEETROOT_SEEDS -> Material.BEETROOTS.name
                Material.WHEAT_SEEDS -> Material.WHEAT.name
                Material.PUMPKIN_SEEDS -> Material.PUMPKIN_STEM.name
                Material.MELON_SEEDS -> Material.MELON_STEM.name
                Material.TORCHFLOWER_SEEDS -> Material.TORCHFLOWER_CROP.name
                Material.PITCHER_POD -> Material.PITCHER_CROP.name
                else -> null
            }
        } else null
        Material.COCOA_BEANS -> if (
            clickedBlock.type.name.contains("JUNGLE") &&
            (clickedBlock.type.name.endsWith("_LOG") || clickedBlock.type.name.endsWith("_WOOD"))
        ) {
            Material.COCOA.name
        } else null
        else -> null
    }
}

private fun misc4IsChainArmor(material: Material?): Boolean = material in setOf(
    Material.CHAINMAIL_HELMET,
    Material.CHAINMAIL_CHESTPLATE,
    Material.CHAINMAIL_LEGGINGS,
    Material.CHAINMAIL_BOOTS
)

private fun misc4HasAnyChainArmor(player: Player): Boolean =
    misc4IsChainArmor(player.inventory.helmet?.type) ||
        misc4IsChainArmor(player.inventory.chestplate?.type) ||
        misc4IsChainArmor(player.inventory.leggings?.type) ||
        misc4IsChainArmor(player.inventory.boots?.type)

private fun misc4WillEquipChainArmor(player: Player, event: InventoryClickEvent): Boolean {
    if (misc4HasAnyChainArmor(player)) return true

    var helmet = player.inventory.helmet?.type
    var chestplate = player.inventory.chestplate?.type
    var leggings = player.inventory.leggings?.type
    var boots = player.inventory.boots?.type

    fun assignArmor(slot: Int, material: Material?) {
        val applied = material?.takeUnless { it == Material.AIR }
        when (slot) {
            39 -> helmet = applied
            38 -> chestplate = applied
            37 -> leggings = applied
            36 -> boots = applied
        }
    }

    if (event.slotType.name == "ARMOR") {
        assignArmor(event.slot, event.cursor?.type)
    } else if (event.isShiftClick) {
        when (val moved = event.currentItem?.type) {
            Material.CHAINMAIL_HELMET -> if (helmet == null) helmet = moved
            Material.CHAINMAIL_CHESTPLATE -> if (chestplate == null) chestplate = moved
            Material.CHAINMAIL_LEGGINGS -> if (leggings == null) leggings = moved
            Material.CHAINMAIL_BOOTS -> if (boots == null) boots = moved
            else -> Unit
        }
    }

    return misc4IsChainArmor(helmet) || misc4IsChainArmor(chestplate) ||
        misc4IsChainArmor(leggings) || misc4IsChainArmor(boots)
}

@CustomObjective
class GreenThumbObjective : MultiEventBingoObjective(
    id = "green_thumb",
    name = Component.text("Green thumb"),
    description = Component.text(
        "Plant all kinds of crops (including sweet berries, glow berries, carrot, potato, beetroot, wheat, cocoa beans, pumpkin, melon, torchflower, pitcher, nether wart, kelp, sugar cane, bamboo, chorus flower)."
    ),
    difficulty = Difficulty.HARD
) {
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onBlockPlace(event: BlockPlaceEvent) {
        val step = misc4GreenThumbBlockPlaceStep(event.blockPlaced.type) ?: return
        val state = BingoManager.getActiveState(event.player, id) ?: return
        if (state.addStep(id, step) && state.getSteps(id).containsAll(GREEN_THUMB_REQUIRED_STEPS)) {
            BingoManager.checkCompletion(event.player, this)
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPlayerInteract(event: PlayerInteractEvent) {
        if (event.action != Action.RIGHT_CLICK_BLOCK) return
        val step = misc4GreenThumbInteractStep(event) ?: return
        val state = BingoManager.getActiveState(event.player, id) ?: return
        if (state.addStep(id, step) && state.getSteps(id).containsAll(GREEN_THUMB_REQUIRED_STEPS)) {
            BingoManager.checkCompletion(event.player, this)
        }
    }

    override fun isCompletedBy(player: Player, state: BingoPlayerState): Boolean =
        state.getSteps(id).containsAll(GREEN_THUMB_REQUIRED_STEPS)

    override fun onReset(player: Player, state: BingoPlayerState) =
        state.clearSteps(id)

    override fun displayItem(player: Player, completed: Boolean): ItemStack {
        val progress = BingoManager.currentGame
            ?.getOrCreateState(player.uniqueId)?.getSteps(id)?.size ?: 0
        return buildProgressItem(
            player,
            completed,
            Component.text("Progress: $progress/${GREEN_THUMB_REQUIRED_STEPS.size}", NamedTextColor.YELLOW)
        )
    }
}

@CustomObjective
class TheMummysCurseObjective : MultiEventBingoObjective(
    id = "the_mummys_curse",
    name = Component.text("The Mummy's Curse"),
    description = Component.text("Trigger the explosion trap of a desert pyramid."),
    difficulty = Difficulty.EASY
) {
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onEntityDamage(event: EntityDamageEvent) {
        if (event.cause != EntityDamageEvent.DamageCause.BLOCK_EXPLOSION) return
        val player = event.entity as? Player ?: return
        val state = BingoManager.getActiveState(player, id) ?: return
        if (!misc4IsLikelyDesertTempleTrap(player, event)) return
        state.setString(id, "done", "true")
        BingoManager.checkCompletion(player, this)
    }

    override fun isCompletedBy(player: Player, state: BingoPlayerState): Boolean =
        state.getString(id, "done") == "true"

    override fun onReset(player: Player, state: BingoPlayerState) =
        state.removeString(id, "done")
}

@CustomObjective
class TempleRunObjective : MultiEventBingoObjective(
    id = "temple_run",
    name = Component.text("Temple Run"),
    description = Component.text("Get into a jungle pyramid."),
    difficulty = Difficulty.EASY
) {
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPlayerMove(event: PlayerMoveEvent) {
        if (!event.hasChangedBlock()) return
        val player = event.player
        val state = BingoManager.getActiveState(player, id) ?: return
        if (!misc4IsWithinGeneratedStructure(player.location, Structure.JUNGLE_PYRAMID, 2.0)) return
        state.setString(id, "done", "true")
        BingoManager.checkCompletion(player, this)
    }

    override fun isCompletedBy(player: Player, state: BingoPlayerState): Boolean =
        state.getString(id, "done") == "true"

    override fun onReset(player: Player, state: BingoPlayerState) =
        state.removeString(id, "done")
}

@CustomObjective
class StarSpangledWardObjective : EventBingoObjective<CraftItemEvent>(
    id = "star_spangled_ward",
    name = Component.text("Star-Spangled Ward"),
    description = Component.text("Craft a shield."),
    difficulty = Difficulty.EASY,
    eventClass = CraftItemEvent::class.java
) {
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onCraft(event: CraftItemEvent) {
        if (event.recipe.result.type != Material.SHIELD) return
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
class HornOfGondorObjective : MultiEventBingoObjective(
    id = "horn_of_gondor",
    name = Component.text("Horn of Gondor"),
    description = Component.text("Get a goat horn."),
    difficulty = Difficulty.MEDIUM
) {
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onEntityPickupItem(event: EntityPickupItemEvent) {
        val player = event.entity as? Player ?: return
        if (event.item.itemStack.type != Material.GOAT_HORN) return
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
class VikingChainwardObjective : MultiEventBingoObjective(
    id = "viking_chainward",
    name = Component.text("Viking Chainward"),
    description = Component.text("Wear any chain armor."),
    difficulty = Difficulty.HARD
) {
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val state = BingoManager.getActiveState(player, id) ?: return
        if (!misc4WillEquipChainArmor(player, event)) return
        state.setString(id, "done", "true")
        BingoManager.checkCompletion(player, this)
    }

    override fun isCompletedBy(player: Player, state: BingoPlayerState): Boolean =
        state.getString(id, "done") == "true"

    override fun onReset(player: Player, state: BingoPlayerState) =
        state.removeString(id, "done")
}

@CustomObjective
class RedstoneAgeObjective : MultiEventBingoObjective(
    id = "redstone_age",
    name = Component.text("Redstone Age"),
    description = Component.text("Use redstone to light TNT."),
    difficulty = Difficulty.EASY
) {
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onTntPrime(event: TNTPrimeEvent) {
        if (event.cause != TNTPrimeEvent.PrimeCause.REDSTONE) return
        val player = misc4NearestActivePlayer(event.block.location, id, REDSTONE_TNT_ATTRIBUTION_RADIUS) ?: return
        val state = BingoManager.getActiveState(player, id) ?: return
        state.setString(id, "done", "true")
        BingoManager.checkCompletion(player, this)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onBlockRedstone(event: BlockRedstoneEvent) {
        if (event.block.type != Material.TNT) return
        if (event.oldCurrent >= event.newCurrent || event.newCurrent <= 0) return
        val player = misc4NearestActivePlayer(event.block.location, id, REDSTONE_TNT_ATTRIBUTION_RADIUS) ?: return
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
class FenrirsPlateObjective : EventBingoObjective<CraftItemEvent>(
    id = "fenrirs_plate",
    name = Component.text("Fenrir's Plate"),
    description = Component.text("Craft wolf armor."),
    difficulty = Difficulty.MEDIUM,
    eventClass = CraftItemEvent::class.java
) {
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onCraft(event: CraftItemEvent) {
        if (event.recipe.result.type != Material.WOLF_ARMOR) return
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
class AntiAircraftGunObjective : MultiEventBingoObjective(
    id = "anti_aircraft_gun",
    name = Component.text("Anti-aircraft gun"),
    description = Component.text("Kill 3 phantoms with arrows."),
    difficulty = Difficulty.MEDIUM
) {
    private val count = ANTI_AIRCRAFT_GUN_REQUIRED_KILLS

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onEntityDeath(event: EntityDeathEvent) {
        if (event.entity.type != EntityType.PHANTOM) return
        val damage = event.entity.lastDamageCause as? EntityDamageByEntityEvent ?: return
        if (damage.cause != EntityDamageEvent.DamageCause.PROJECTILE) return
        val arrow = damage.damager as? Arrow ?: return
        val player = arrow.shooter as? Player ?: return
        val state = BingoManager.getActiveState(player, id) ?: return
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

@CustomObjective
class RedDeadRedemptionObjective : EventBingoObjective<EntityDeathEvent>(
    id = "red_dead_redemption",
    name = Component.text("Red Dead Redemption"),
    description = Component.text("Kill a wandering trader."),
    difficulty = Difficulty.MEDIUM,
    eventClass = EntityDeathEvent::class.java
) {
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onEntityDeath(event: EntityDeathEvent) {
        if (event.entity.type != EntityType.WANDERING_TRADER) return
        val player = event.entity.killer ?: return
        val state = BingoManager.getActiveState(player, id) ?: return
        onEvent(event, player, state)
    }

    override fun onEvent(event: EntityDeathEvent, player: Player, state: BingoPlayerState) {
        state.setString(id, "done", "true")
        BingoManager.checkCompletion(player, this)
    }

    override fun isCompletedBy(player: Player, state: BingoPlayerState): Boolean =
        state.getString(id, "done") == "true"

    override fun onReset(player: Player, state: BingoPlayerState) =
        state.removeString(id, "done")
}
