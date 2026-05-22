package net.trilleo.mc.plugins.tribingo.bingo.custom

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.trilleo.mc.plugins.tribingo.bingo.BingoManager
import net.trilleo.mc.plugins.tribingo.bingo.BingoPlayerState
import net.trilleo.mc.plugins.tribingo.bingo.EventBingoObjective
import net.trilleo.mc.plugins.tribingo.bingo.MultiEventBingoObjective
import net.trilleo.mc.plugins.tribingo.bingo.annotation.CustomObjective
import net.trilleo.mc.plugins.tribingo.enums.Difficulty
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.entity.EntityPickupItemEvent
import org.bukkit.event.entity.EntityPortalEnterEvent
import org.bukkit.event.entity.EntityTransformEvent
import org.bukkit.event.entity.PiglinBarterEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.inventory.CraftItemEvent
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.spigotmc.event.entity.EntityMountEvent
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

private const val GOLD_ROAD_TRADES_REQUIRED = 320
private const val NETHER_FARMER_PLANTS_REQUIRED = 500
private const val LAVA_SWIMS_REQUIRED = 3
private const val PIGLIN_BARTER_WINDOW_MILLIS = 30_000L
private const val ZOMBIE_PORTAL_WINDOW_MILLIS = 120_000L
private const val ZOMBIE_PORTAL_PROXIMITY_RADIUS = 16.0

@CustomObjective
class NetherHunterObjective : MultiEventBingoObjective(
    id = "nether_hunter",
    name = Component.text("Nether Hunter"),
    description = Component.text("Kill all kinds of naturally generated nether mobs except the Wither."),
    difficulty = Difficulty.HARD
) {
    private val requiredMobSteps = setOf(
        EntityType.BLAZE.name,
        EntityType.GHAST.name,
        EntityType.HOGLIN.name,
        EntityType.MAGMA_CUBE.name,
        EntityType.PIGLIN.name,
        EntityType.PIGLIN_BRUTE.name,
        EntityType.STRIDER.name,
        EntityType.WITHER_SKELETON.name,
        EntityType.ZOMBIFIED_PIGLIN.name,
        EntityType.ENDERMAN.name
    )

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onEntityDeath(event: EntityDeathEvent) {
        val player = event.entity.killer ?: return
        val step = when {
            event.entity.type == EntityType.ENDERMAN && event.entity.world.environment == World.Environment.NETHER -> EntityType.ENDERMAN.name
            event.entity.type.name in requiredMobSteps -> event.entity.type.name
            else -> return
        }
        val state = BingoManager.getActiveState(player, id) ?: return
        if (state.addStep(id, step) && state.getSteps(id).containsAll(requiredMobSteps)) {
            BingoManager.checkCompletion(player, this)
        }
    }

    override fun isCompletedBy(player: Player, state: BingoPlayerState): Boolean =
        state.getSteps(id).containsAll(requiredMobSteps)

    override fun onReset(player: Player, state: BingoPlayerState) =
        state.clearSteps(id)

    override fun displayItem(player: Player, completed: Boolean): ItemStack {
        val progress = BingoManager.currentGame
            ?.getOrCreateState(player.uniqueId)?.getSteps(id)?.size ?: 0
        return buildProgressItem(
            player,
            completed,
            Component.text("Progress: $progress/${requiredMobSteps.size}", NamedTextColor.YELLOW)
        )
    }
}

@CustomObjective
class NetheriteForgerObjective : EventBingoObjective<CraftItemEvent>(
    id = "netherite_forger",
    name = Component.text("Netherite Forger"),
    description = Component.text("Craft a netherite ingot."),
    difficulty = Difficulty.HARD,
    eventClass = CraftItemEvent::class.java
) {
    private val material = Material.NETHERITE_INGOT

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onCraft(event: CraftItemEvent) {
        if (event.recipe.result.type != material) return
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
class HappyGhastObjective : MultiEventBingoObjective(
    id = "happy_ghast",
    name = Component.text("Happy Ghast!"),
    description = Component.text("Fly on a happy ghast."),
    difficulty = Difficulty.EASY
) {
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onMount(event: EntityMountEvent) {
        val player = event.entity as? Player ?: return
        if (event.mount.type.name != "HAPPY_GHAST") return
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
class WitherEnderObjective : EventBingoObjective<EntityDeathEvent>(
    id = "wither_ender",
    name = Component.text("Wither Ender"),
    description = Component.text("Kill the Wither."),
    difficulty = Difficulty.INSANE,
    eventClass = EntityDeathEvent::class.java
) {
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onEntityDeath(event: EntityDeathEvent) {
        if (event.entity.type != EntityType.WITHER) return
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

@CustomObjective
class GoldRoadObjective : MultiEventBingoObjective(
    id = "gold_road",
    name = Component.text("Gold Road"),
    description = Component.text("Trade with piglins $GOLD_ROAD_TRADES_REQUIRED times."),
    difficulty = Difficulty.MEDIUM
) {
    private val count = GOLD_ROAD_TRADES_REQUIRED
    private val barterWindowMillis = PIGLIN_BARTER_WINDOW_MILLIS
    private val piglinTraders = ConcurrentHashMap<UUID, Pair<UUID, Long>>()

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onInteract(event: PlayerInteractEntityEvent) {
        if (event.rightClicked.type != EntityType.PIGLIN) return
        val item = when (event.hand) {
            EquipmentSlot.HAND -> event.player.inventory.itemInMainHand
            EquipmentSlot.OFF_HAND -> event.player.inventory.itemInOffHand
            else -> return
        }
        if (item.type != Material.GOLD_INGOT) return
        val now = System.currentTimeMillis()
        purgeExpiredPiglinTraders(now)
        piglinTraders[event.rightClicked.uniqueId] = event.player.uniqueId to now
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPiglinBarter(event: PiglinBarterEvent) {
        val now = System.currentTimeMillis()
        val trader = piglinTraders.remove(event.entity.uniqueId) ?: return
        if (now - trader.second > barterWindowMillis) return
        val player = Bukkit.getPlayer(trader.first) ?: return
        val state = BingoManager.getActiveState(player, id) ?: return
        val progress = state.getProgress(id) + 1
        state.setProgress(id, progress)
        if (progress >= count) {
            BingoManager.checkCompletion(player, this)
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPiglinDeath(event: EntityDeathEvent) {
        piglinTraders.remove(event.entity.uniqueId)
    }

    override fun isCompletedBy(player: Player, state: BingoPlayerState): Boolean =
        state.getProgress(id) >= count

    override fun onReset(player: Player, state: BingoPlayerState) {
        state.setProgress(id, 0)
        piglinTraders.entries.removeIf { it.value.first == player.uniqueId }
    }

    private fun purgeExpiredPiglinTraders(now: Long) {
        piglinTraders.entries.removeIf { now - it.value.second > barterWindowMillis }
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
class NetherFarmerObjective : EventBingoObjective<BlockPlaceEvent>(
    id = "nether_farmer",
    name = Component.text("Nether Farmer"),
    description = Component.text("Plant nether wart $NETHER_FARMER_PLANTS_REQUIRED times."),
    difficulty = Difficulty.MEDIUM,
    eventClass = BlockPlaceEvent::class.java
) {
    private val material = Material.NETHER_WART
    private val count = NETHER_FARMER_PLANTS_REQUIRED

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onBlockPlace(event: BlockPlaceEvent) {
        if (event.block.type != material) return
        val state = BingoManager.getActiveState(event.player, id) ?: return
        onEvent(event, event.player, state)
    }

    override fun onEvent(event: BlockPlaceEvent, player: Player, state: BingoPlayerState) {
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
class LaLaLaLavaObjective : EventBingoObjective<EntityDamageEvent>(
    id = "la_la_la_lava",
    name = Component.text("La-La-La-Lava!"),
    description = Component.text("Try to swim in lava $LAVA_SWIMS_REQUIRED times."),
    difficulty = Difficulty.EASY,
    eventClass = EntityDamageEvent::class.java
) {
    private val count = LAVA_SWIMS_REQUIRED
    private val cooldownMillis = 3000L

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onEntityDamage(event: EntityDamageEvent) {
        if (event.cause != EntityDamageEvent.DamageCause.LAVA) return
        val player = event.entity as? Player ?: return
        val state = BingoManager.getActiveState(player, id) ?: return
        val now = System.currentTimeMillis()
        val lastSeen = state.getString(id, "last_lava_time")?.toLongOrNull() ?: 0L
        if (now - lastSeen < cooldownMillis) return
        state.setString(id, "last_lava_time", now.toString())
        onEvent(event, player, state)
    }

    override fun onEvent(event: EntityDamageEvent, player: Player, state: BingoPlayerState) {
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
        state.removeString(id, "last_lava_time")
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
class ZombieConversionObjective : MultiEventBingoObjective(
    id = "zombie_conversion",
    name = Component.text("Zombie!"),
    description = Component.text("Make a piglin and piglin brute become zombified piglins, and a hoglin become a zoglin."),
    difficulty = Difficulty.HARD
) {
    private val requiredSteps = setOf(
        "piglin_zombified",
        "piglin_brute_zombified",
        "hoglin_zoglin"
    )
    private val portalWindowMillis = ZOMBIE_PORTAL_WINDOW_MILLIS
    private val portalProximityRadius = ZOMBIE_PORTAL_PROXIMITY_RADIUS
    private val portalOwners = ConcurrentHashMap<UUID, Pair<UUID, Long>>()

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onEntityPortalEnter(event: EntityPortalEnterEvent) {
        if (event.entity.type != EntityType.PIGLIN &&
            event.entity.type != EntityType.PIGLIN_BRUTE &&
            event.entity.type != EntityType.HOGLIN
        ) return

        val player = event.entity.location.getNearbyPlayers(portalProximityRadius)
            .minByOrNull { it.location.distanceSquared(event.entity.location) } ?: return
        val now = System.currentTimeMillis()
        purgeExpiredPortalOwners(now)
        portalOwners[event.entity.uniqueId] = player.uniqueId to now
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onEntityTransform(event: EntityTransformEvent) {
        val step = when {
            event.entity.type == EntityType.PIGLIN && event.transformedEntity.type == EntityType.ZOMBIFIED_PIGLIN -> "piglin_zombified"
            event.entity.type == EntityType.PIGLIN_BRUTE && event.transformedEntity.type == EntityType.ZOMBIFIED_PIGLIN -> "piglin_brute_zombified"
            event.entity.type == EntityType.HOGLIN && event.transformedEntity.type == EntityType.ZOGLIN -> "hoglin_zoglin"
            else -> return
        }

        val now = System.currentTimeMillis()
        val owner = portalOwners.remove(event.entity.uniqueId) ?: return
        if (now - owner.second > portalWindowMillis) return
        val player = Bukkit.getPlayer(owner.first) ?: return
        val state = BingoManager.getActiveState(player, id) ?: return
        if (state.addStep(id, step) && state.getSteps(id).containsAll(requiredSteps)) {
            BingoManager.checkCompletion(player, this)
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onTrackedMobDeath(event: EntityDeathEvent) {
        portalOwners.remove(event.entity.uniqueId)
    }

    override fun isCompletedBy(player: Player, state: BingoPlayerState): Boolean =
        state.getSteps(id).containsAll(requiredSteps)

    override fun onReset(player: Player, state: BingoPlayerState) {
        state.clearSteps(id)
        portalOwners.entries.removeIf { it.value.first == player.uniqueId }
    }

    private fun purgeExpiredPortalOwners(now: Long) {
        portalOwners.entries.removeIf { now - it.value.second > portalWindowMillis }
    }

    override fun displayItem(player: Player, completed: Boolean): ItemStack {
        val progress = BingoManager.currentGame
            ?.getOrCreateState(player.uniqueId)?.getSteps(id)?.size ?: 0
        return buildProgressItem(
            player,
            completed,
            Component.text("Progress: $progress/${requiredSteps.size}", NamedTextColor.YELLOW)
        )
    }
}

@CustomObjective
class IAmPiglinObjective : EventBingoObjective<EntityPickupItemEvent>(
    id = "i_am_piglin",
    name = Component.text("I Am Piglin"),
    description = Component.text("Get a piglin head."),
    difficulty = Difficulty.HARD,
    eventClass = EntityPickupItemEvent::class.java
) {
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onItemPickup(event: EntityPickupItemEvent) {
        val player = event.entity as? Player ?: return
        if (event.item.itemStack.type != Material.PIGLIN_HEAD) return
        val state = BingoManager.getActiveState(player, id) ?: return
        onEvent(event, player, state)
    }

    override fun onEvent(event: EntityPickupItemEvent, player: Player, state: BingoPlayerState) {
        state.setString(id, "done", "true")
        BingoManager.checkCompletion(player, this)
    }

    override fun isCompletedBy(player: Player, state: BingoPlayerState): Boolean =
        state.getString(id, "done") == "true"

    override fun onReset(player: Player, state: BingoPlayerState) =
        state.removeString(id, "done")
}

@CustomObjective
class ImSoTiredObjective : EventBingoObjective<PlayerDeathEvent>(
    id = "im_so_tired",
    name = Component.text("I'm so tired!"),
    description = Component.text("Get killed by a bed explosion in the Nether or the End."),
    difficulty = Difficulty.EASY,
    eventClass = PlayerDeathEvent::class.java
) {
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPlayerDeath(event: PlayerDeathEvent) {
        val player = event.entity
        val lastDamage = player.lastDamageCause ?: return
        if (lastDamage.cause != EntityDamageEvent.DamageCause.BLOCK_EXPLOSION) return
        val environment = player.world.environment
        if (environment !in setOf(World.Environment.NETHER, World.Environment.THE_END)) return
        val state = BingoManager.getActiveState(player, id) ?: return
        onEvent(event, player, state)
    }

    override fun onEvent(event: PlayerDeathEvent, player: Player, state: BingoPlayerState) {
        state.setString(id, "done", "true")
        BingoManager.checkCompletion(player, this)
    }

    override fun isCompletedBy(player: Player, state: BingoPlayerState): Boolean =
        state.getString(id, "done") == "true"

    override fun onReset(player: Player, state: BingoPlayerState) =
        state.removeString(id, "done")
}
