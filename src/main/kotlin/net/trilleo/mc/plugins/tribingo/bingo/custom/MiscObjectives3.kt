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
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Raid
import org.bukkit.entity.*
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.entity.PotionSplashEvent
import org.bukkit.event.inventory.CraftItemEvent
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.event.player.PlayerLeashEntityEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.raid.RaidTriggerEvent
import org.bukkit.event.weather.LightningStrikeEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.LeatherArmorMeta
import org.bukkit.inventory.meta.PotionMeta
import org.bukkit.potion.PotionType
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

private const val TEARS_OF_ISAAC_REQUIRED = 5
private const val KING_SLIME_REQUIRED = 100
private const val TERRARIAN_YOYO_REQUIRED = 10
private const val GODFATHER_RAID_RADIUS = 96.0
private const val PLANTS_VS_ZOMBIES_WINDOW_MILLIS = 5_000L
private const val TEARS_OF_ISAAC_WINDOW_MILLIS = 5_000L
private const val ZEUS_STRIKE_WINDOW_MILLIS = 2_000L
private const val ZEUS_STRIKE_RADIUS = 5.0
private const val WORLD_WAR_PLAYER_RADIUS = 24.0
private const val MELON_KILL_ATTRIBUTION_RADIUS = 16.0
private val BLACK_LEATHER_COLOR: Color = Color.fromRGB(0x1D1D21)

private data class RaidContext(
    val playerId: UUID,
    val center: Location,
    val villagerIds: MutableSet<UUID>,
    var failed: Boolean = false
)

private data class TimedPlayerReference(
    val playerId: UUID,
    val timestamp: Long
)

private data class ChannelingStrikeReference(
    val playerId: UUID,
    val worldName: String,
    val x: Double,
    val y: Double,
    val z: Double,
    val timestamp: Long
)

private fun heldItemFor(event: PlayerInteractEntityEvent): ItemStack =
    when (event.hand) {
        EquipmentSlot.OFF_HAND -> event.player.inventory.itemInOffHand
        else -> event.player.inventory.itemInMainHand
    }

private fun findNearestActivePlayer(location: Location, objectiveId: String, radius: Double): Player? =
    location.getNearbyPlayers(radius)
        .sortedBy { it.location.distanceSquared(location) }
        .firstOrNull { BingoManager.getActiveState(it, objectiveId) != null }

private fun raidKey(raid: Raid): String {
    val center = raid.center
    return listOf(center.world?.name ?: "world", center.blockX, center.blockY, center.blockZ).joinToString(":")
}

private fun isIllager(type: EntityType): Boolean = type in setOf(
    EntityType.PILLAGER,
    EntityType.VINDICATOR,
    EntityType.EVOKER,
    EntityType.ILLUSIONER,
    EntityType.WITCH,
    EntityType.RAVAGER
)

@CustomObjective
class SilentHillObjective : MultiEventBingoObjective(
    id = "silent_hill",
    name = Component.text("Silent Hill"),
    description = Component.text("Find a pale garden biome."),
    difficulty = Difficulty.MEDIUM
) {
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPlayerMove(event: PlayerMoveEvent) {
        if (!event.hasChangedBlock()) return
        val player = event.player
        val state = BingoManager.getActiveState(player, id) ?: return
        if (player.location.block.biome.name != "PALE_GARDEN") return
        state.setString(id, "done", "true")
        BingoManager.checkCompletion(player, this)
    }

    override fun isCompletedBy(player: Player, state: BingoPlayerState): Boolean =
        state.getString(id, "done") == "true"

    override fun onReset(player: Player, state: BingoPlayerState) =
        state.removeString(id, "done")
}

@CustomObjective
class PlantsVsZombiesObjective : MultiEventBingoObjective(
    id = "plants_vs_zombies",
    name = Component.text("Plants vs Zombies"),
    description = Component.text("Kill a zombie with a melon block."),
    difficulty = Difficulty.EASY
) {
    private val trackedZombies = ConcurrentHashMap<UUID, TimedPlayerReference>()

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onEntityDamageByEntity(event: EntityDamageByEntityEvent) {
        if (event.entity.type != EntityType.ZOMBIE) return
        val fallingBlock = event.damager as? FallingBlock ?: return
        if (fallingBlock.blockData.material != Material.MELON) return
        val player = findNearestActivePlayer(event.entity.location, id, MELON_KILL_ATTRIBUTION_RADIUS) ?: return
        trackedZombies[event.entity.uniqueId] = TimedPlayerReference(player.uniqueId, System.currentTimeMillis())
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onEntityDeath(event: EntityDeathEvent) {
        if (event.entity.type != EntityType.ZOMBIE) return
        val tracked = trackedZombies.remove(event.entity.uniqueId) ?: return
        if (System.currentTimeMillis() - tracked.timestamp > PLANTS_VS_ZOMBIES_WINDOW_MILLIS) return
        val lastDamage = event.entity.lastDamageCause as? EntityDamageByEntityEvent ?: return
        val fallingBlock = lastDamage.damager as? FallingBlock ?: return
        if (fallingBlock.blockData.material != Material.MELON) return
        val player = Bukkit.getPlayer(tracked.playerId) ?: return
        val state = BingoManager.getActiveState(player, id) ?: return
        state.setString(id, "done", "true")
        BingoManager.checkCompletion(player, this)
    }

    override fun isCompletedBy(player: Player, state: BingoPlayerState): Boolean =
        state.getString(id, "done") == "true"

    override fun onReset(player: Player, state: BingoPlayerState) {
        state.removeString(id, "done")
        trackedZombies.entries.removeIf { it.value.playerId == player.uniqueId }
    }
}

@CustomObjective
class NeverGonnaGiveYouUpObjective : MultiEventBingoObjective(
    id = "never_gonna_give_you_up",
    name = Component.text("Never Gonna Give You Up"),
    description = Component.text("Craft black leather boots."),
    difficulty = Difficulty.EASY
) {
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onCraftItem(event: CraftItemEvent) {
        val player = event.whoClicked as? Player ?: return
        val state = BingoManager.getActiveState(player, id) ?: return
        val result = event.currentItem ?: event.recipe.result
        if (result.type != Material.LEATHER_BOOTS) return
        val meta = result.itemMeta as? LeatherArmorMeta ?: return
        if (meta.color != BLACK_LEATHER_COLOR) return
        state.setString(id, "done", "true")
        BingoManager.checkCompletion(player, this)
    }

    override fun isCompletedBy(player: Player, state: BingoPlayerState): Boolean =
        state.getString(id, "done") == "true"

    override fun onReset(player: Player, state: BingoPlayerState) =
        state.removeString(id, "done")
}

@CustomObjective
class PoorGreenObjective : MultiEventBingoObjective(
    id = "poor_green",
    name = Component.text("Poor Green"),
    description = Component.text("Place a slimeball in an item frame."),
    difficulty = Difficulty.MEDIUM
) {
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPlayerInteractEntity(event: PlayerInteractEntityEvent) {
        if (event.rightClicked.type != EntityType.ITEM_FRAME && event.rightClicked.type != EntityType.GLOW_ITEM_FRAME) return
        if (heldItemFor(event).type != Material.SLIME_BALL) return
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
class GodfatherObjective : MultiEventBingoObjective(
    id = "godfather",
    name = Component.text("You don't even think to call me Godfather."),
    description = Component.text("Start a raid but don't kill any illager until all villagers die."),
    difficulty = Difficulty.INSANE
) {
    private val activeRaids = ConcurrentHashMap<String, RaidContext>()

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onRaidTrigger(event: RaidTriggerEvent) {
        val player = event.player
        val state = BingoManager.getActiveState(player, id) ?: return
        activeRaids.entries.removeIf { it.value.playerId == player.uniqueId }

        val villagers = event.raid.villagers.map { it.uniqueId }.toMutableSet()
        activeRaids[raidKey(event.raid)] = RaidContext(player.uniqueId, event.raid.center, villagers)
        state.setString(id, "in_raid", "true")
        state.removeString(id, "failed")
        state.removeString(id, "done")
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onEntityDeath(event: EntityDeathEvent) {
        if (event.entity.type == EntityType.VILLAGER) {
            val matchingKeys = activeRaids.entries
                .filter { event.entity.uniqueId in it.value.villagerIds }
                .map { it.key }
            for (key in matchingKeys) {
                val context = activeRaids[key] ?: continue
                context.villagerIds.remove(event.entity.uniqueId)
                if (context.failed || context.villagerIds.isNotEmpty()) continue
                val player = Bukkit.getPlayer(context.playerId) ?: continue
                val state = BingoManager.getActiveState(player, id) ?: continue
                state.setString(id, "done", "true")
                state.removeString(id, "failed")
                BingoManager.checkCompletion(player, this)
                activeRaids.remove(key)
            }
            return
        }

        if (!isIllager(event.entity.type)) return
        val killer = event.entity.killer ?: return
        val state = BingoManager.getActiveState(killer, id) ?: return
        val matchingKeys = activeRaids.entries
            .filter { it.value.playerId == killer.uniqueId }
            .filter { it.value.center.world?.uid == event.entity.world.uid }
            .filter { it.value.center.distanceSquared(event.entity.location) <= GODFATHER_RAID_RADIUS * GODFATHER_RAID_RADIUS }
            .map { it.key }
        if (matchingKeys.isEmpty()) return

        state.setString(id, "failed", "true")
        for (key in matchingKeys) {
            activeRaids[key]?.failed = true
        }
    }

    override fun isCompletedBy(player: Player, state: BingoPlayerState): Boolean =
        state.getString(id, "done") == "true"

    override fun onReset(player: Player, state: BingoPlayerState) {
        state.removeString(id, "done")
        state.removeString(id, "failed")
        state.removeString(id, "in_raid")
        activeRaids.entries.removeIf { it.value.playerId == player.uniqueId }
    }
}

@CustomObjective
class TearsOfIsaacObjective : MultiEventBingoObjective(
    id = "tears_of_isaac",
    name = Component.text("Tears of Isaac"),
    description = Component.text("Kill 5 blazes with splash water bottles."),
    difficulty = Difficulty.INSANE
) {
    private val count = TEARS_OF_ISAAC_REQUIRED
    private val trackedBlazes = ConcurrentHashMap<UUID, TimedPlayerReference>()

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPotionSplash(event: PotionSplashEvent) {
        val player = event.potion.shooter as? Player ?: return
        BingoManager.getActiveState(player, id) ?: return
        if (event.potion.item.type != Material.SPLASH_POTION) return
        val meta = event.potion.item.itemMeta as? PotionMeta ?: return
        if (meta.basePotionType != PotionType.WATER) return

        val now = System.currentTimeMillis()
        event.affectedEntities
            .filterIsInstance<Blaze>()
            .filter { event.getIntensity(it) > 0.0 }
            .forEach { trackedBlazes[it.uniqueId] = TimedPlayerReference(player.uniqueId, now) }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onEntityDeath(event: EntityDeathEvent) {
        if (event.entity.type != EntityType.BLAZE) return
        val tracked = trackedBlazes.remove(event.entity.uniqueId) ?: return
        if (System.currentTimeMillis() - tracked.timestamp > TEARS_OF_ISAAC_WINDOW_MILLIS) return
        val player = Bukkit.getPlayer(tracked.playerId) ?: return
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
        trackedBlazes.entries.removeIf { it.value.playerId == player.uniqueId }
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
class KingSlimeObjective : EventBingoObjective<EntityDeathEvent>(
    id = "king_slime",
    name = Component.text("King Slime"),
    description = Component.text("Kill 100 slimes."),
    difficulty = Difficulty.HARD,
    eventClass = EntityDeathEvent::class.java
) {
    private val count = KING_SLIME_REQUIRED

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onEntityDeath(event: EntityDeathEvent) {
        if (event.entity.type != EntityType.SLIME) return
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

@CustomObjective
class TerrarianYoyoObjective : MultiEventBingoObjective(
    id = "terrarian_yoyo",
    name = Component.text("Terrarian Yoyo"),
    description = Component.text("Hold 10 mobs with a leash."),
    difficulty = Difficulty.MEDIUM
) {
    private val count = TERRARIAN_YOYO_REQUIRED

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPlayerLeashEntity(event: PlayerLeashEntityEvent) {
        val state = BingoManager.getActiveState(event.player, id) ?: return
        if (state.addStep(id, event.entity.uniqueId.toString()) && state.getSteps(id).size >= count) {
            BingoManager.checkCompletion(event.player, this)
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
class ZeusWrathObjective : MultiEventBingoObjective(
    id = "zeus_wrath",
    name = Component.text("Zeus's Wrath"),
    description = Component.text("Use a Channeling trident to strike a mob with lightning."),
    difficulty = Difficulty.HARD
) {
    private val trackedStrikes = ConcurrentHashMap<UUID, ChannelingStrikeReference>()

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onLightningStrike(event: LightningStrikeEvent) {
        if (event.cause != LightningStrikeEvent.Cause.TRIDENT) return
        val player = findNearestActivePlayer(event.lightning.location, id, 32.0) ?: return
        trackedStrikes[event.lightning.uniqueId] = ChannelingStrikeReference(
            playerId = player.uniqueId,
            worldName = event.lightning.world.name,
            x = event.lightning.location.x,
            y = event.lightning.location.y,
            z = event.lightning.location.z,
            timestamp = System.currentTimeMillis()
        )
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onEntityDamage(event: EntityDamageEvent) {
        if (event.cause != EntityDamageEvent.DamageCause.LIGHTNING) return
        if (event.entity is Player) return

        val now = System.currentTimeMillis()
        val directLightning = event.damageSource.causingEntity as? LightningStrike
        val tracked = when {
            directLightning != null -> trackedStrikes[directLightning.uniqueId]
            else -> trackedStrikes.values.firstOrNull {
                now - it.timestamp <= ZEUS_STRIKE_WINDOW_MILLIS &&
                    it.worldName == event.entity.world.name &&
                    (event.entity.location.x - it.x) * (event.entity.location.x - it.x) +
                    (event.entity.location.y - it.y) * (event.entity.location.y - it.y) +
                    (event.entity.location.z - it.z) * (event.entity.location.z - it.z) <= ZEUS_STRIKE_RADIUS * ZEUS_STRIKE_RADIUS
            }
        } ?: return

        if (now - tracked.timestamp > ZEUS_STRIKE_WINDOW_MILLIS) return
        val player = Bukkit.getPlayer(tracked.playerId) ?: return
        val state = BingoManager.getActiveState(player, id) ?: return
        state.setString(id, "done", "true")
        BingoManager.checkCompletion(player, this)
    }

    override fun isCompletedBy(player: Player, state: BingoPlayerState): Boolean =
        state.getString(id, "done") == "true"

    override fun onReset(player: Player, state: BingoPlayerState) {
        state.removeString(id, "done")
        trackedStrikes.entries.removeIf { it.value.playerId == player.uniqueId }
    }
}

@CustomObjective
class WorldWarObjective : MultiEventBingoObjective(
    id = "world_war",
    name = Component.text("World War"),
    description = Component.text("Make illagers fight with piglins."),
    difficulty = Difficulty.INSANE
) {
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onEntityDamageByEntity(event: EntityDamageByEntityEvent) {
        val attacker = event.damager.type
        val victim = event.entity.type
        val illagerVsPiglin =
            (isIllager(attacker) && (victim == EntityType.PIGLIN || victim == EntityType.PIGLIN_BRUTE)) ||
                (isIllager(victim) && (attacker == EntityType.PIGLIN || attacker == EntityType.PIGLIN_BRUTE))
        if (!illagerVsPiglin) return

        val player = findNearestActivePlayer(event.entity.location, id, WORLD_WAR_PLAYER_RADIUS) ?: return
        val state = BingoManager.getActiveState(player, id) ?: return
        state.setString(id, "done", "true")
        BingoManager.checkCompletion(player, this)
    }

    override fun isCompletedBy(player: Player, state: BingoPlayerState): Boolean =
        state.getString(id, "done") == "true"

    override fun onReset(player: Player, state: BingoPlayerState) =
        state.removeString(id, "done")
}
