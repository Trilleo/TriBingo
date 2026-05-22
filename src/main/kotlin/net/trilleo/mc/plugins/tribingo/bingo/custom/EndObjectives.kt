package net.trilleo.mc.plugins.tribingo.bingo.custom

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.trilleo.mc.plugins.tribingo.bingo.BingoManager
import net.trilleo.mc.plugins.tribingo.bingo.BingoPlayerState
import net.trilleo.mc.plugins.tribingo.bingo.EventBingoObjective
import net.trilleo.mc.plugins.tribingo.bingo.MultiEventBingoObjective
import net.trilleo.mc.plugins.tribingo.bingo.annotation.CustomObjective
import net.trilleo.mc.plugins.tribingo.enums.Difficulty
import org.bukkit.Material
import org.bukkit.entity.*
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.entity.*
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.inventory.ItemStack
import java.util.*
import java.util.concurrent.ConcurrentHashMap

private const val MY_BAG_IS_HEAVY_REQUIRED = 3
private const val END_FARMER_REQUIRED = 10
private const val I_AM_ENDERMAN_REQUIRED = 64
private const val END_EXTERMINATOR_REQUIRED = 5

@CustomObjective
class DragonKillerObjective : EventBingoObjective<EntityDeathEvent>(
    id = "dragon_killer",
    name = Component.text("Dragon Killer"),
    description = Component.text("Kill the Ender Dragon."),
    difficulty = Difficulty.INSANE,
    eventClass = EntityDeathEvent::class.java
) {
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onEntityDeath(event: EntityDeathEvent) {
        if (event.entity.type != EntityType.ENDER_DRAGON) return
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
class MyBagIsHeavyObjective : MultiEventBingoObjective(
    id = "my_bag_is_heavy",
    name = Component.text("My bag is heavy!"),
    description = Component.text("Get 3 shulker boxes in a chest."),
    difficulty = Difficulty.INSANE
) {
    private val count = MY_BAG_IS_HEAVY_REQUIRED

    @EventHandler(priority = EventPriority.MONITOR)
    fun onInventoryClose(event: InventoryCloseEvent) {
        if (event.inventory.type.name != "CHEST") return
        val player = event.player as? Player ?: return
        val state = BingoManager.getActiveState(player, id) ?: return
        val shulkerCount = event.inventory.contents.count {
            it != null && it.type.name.contains("SHULKER_BOX")
        }
        val progress = maxOf(state.getProgress(id), shulkerCount)
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
            player, completed,
            Component.text("Progress: $progress/$count", NamedTextColor.YELLOW)
        )
    }
}

@CustomObjective
class EndFarmerObjective : EventBingoObjective<BlockPlaceEvent>(
    id = "end_farmer",
    name = Component.text("End Farmer"),
    description = Component.text("Plant chorus flowers 10 times."),
    difficulty = Difficulty.HARD,
    eventClass = BlockPlaceEvent::class.java
) {
    private val material = Material.CHORUS_FLOWER
    private val count = END_FARMER_REQUIRED

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
            player, completed,
            Component.text("Progress: $progress/$count", NamedTextColor.YELLOW)
        )
    }
}

@CustomObjective
class IAmEndermanObjective : MultiEventBingoObjective(
    id = "i_am_enderman",
    name = Component.text("I am enderman"),
    description = Component.text("Get 64 ender pearls."),
    difficulty = Difficulty.MEDIUM
) {
    private val count = I_AM_ENDERMAN_REQUIRED

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onItemPickup(event: EntityPickupItemEvent) {
        if (event.item.itemStack.type != Material.ENDER_PEARL) return
        val player = event.entity as? Player ?: return
        val state = BingoManager.getActiveState(player, id) ?: return
        val pearlCount = player.inventory.contents.filterNotNull()
            .filter { it.type == Material.ENDER_PEARL }
            .sumOf { it.amount }
        val progress = maxOf(state.getProgress(id), pearlCount)
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
            player, completed,
            Component.text("Progress: $progress/$count", NamedTextColor.YELLOW)
        )
    }
}

@CustomObjective
class EndExterminatorObjective : EventBingoObjective<EntityDeathEvent>(
    id = "end_exterminator",
    name = Component.text("End Exterminator"),
    description = Component.text("Kill 5 endermites."),
    difficulty = Difficulty.EASY,
    eventClass = EntityDeathEvent::class.java
) {
    private val count = END_EXTERMINATOR_REQUIRED

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onEntityDeath(event: EntityDeathEvent) {
        if (event.entity !is Endermite) return
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
            player, completed,
            Component.text("Progress: $progress/$count", NamedTextColor.YELLOW)
        )
    }
}

@CustomObjective
class WaitWhatObjective : EventBingoObjective<PlayerDeathEvent>(
    id = "wait_what",
    name = Component.text("Wait What?"),
    description = Component.text("Killed by an endermite."),
    difficulty = Difficulty.MEDIUM,
    eventClass = PlayerDeathEvent::class.java
) {
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPlayerDeath(event: PlayerDeathEvent) {
        val player = event.entity
        val lastDamage = player.lastDamageCause as? EntityDamageByEntityEvent ?: return
        if (lastDamage.damager !is Endermite) return
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

@CustomObjective
class AloneObjective : EventBingoObjective<PlayerDeathEvent>(
    id = "alone",
    name = Component.text("Alone"),
    description = Component.text("Fall out of the world."),
    difficulty = Difficulty.MEDIUM,
    eventClass = PlayerDeathEvent::class.java
) {
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPlayerDeath(event: PlayerDeathEvent) {
        val player = event.entity
        val lastDamage = player.lastDamageCause ?: return
        if (lastDamage.cause != EntityDamageEvent.DamageCause.VOID) return
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

@CustomObjective
class ThatsTooFastObjective : EventBingoObjective<PlayerDeathEvent>(
    id = "thats_too_fast",
    name = Component.text("That's too fast!"),
    description = Component.text("Experience kinetic energy."),
    difficulty = Difficulty.HARD,
    eventClass = PlayerDeathEvent::class.java
) {
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPlayerDeath(event: PlayerDeathEvent) {
        val player = event.entity
        val lastDamage = player.lastDamageCause ?: return
        if (lastDamage.cause != EntityDamageEvent.DamageCause.FLY_INTO_WALL) return
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

@CustomObjective
class HereComesThePlaneObjective : MultiEventBingoObjective(
    id = "here_comes_the_plane",
    name = Component.text("Here comes the plane!"),
    description = Component.text("Kill a mob with a trident while flying."),
    difficulty = Difficulty.HARD
) {
    private val trackedTargets = ConcurrentHashMap<UUID, UUID>()

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onEntityDamage(event: EntityDamageByEntityEvent) {
        val trident = event.damager as? Trident ?: return
        val player = trident.shooter as? Player ?: return
        if (!player.isGliding) return
        if (event.entity is Player) return
        trackedTargets[event.entity.uniqueId] = player.uniqueId
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onEntityDeath(event: EntityDeathEvent) {
        val killer = event.entity.killer ?: run {
            trackedTargets.remove(event.entity.uniqueId)
            return
        }
        val trackedPlayer = trackedTargets.remove(event.entity.uniqueId) ?: return
        if (trackedPlayer != killer.uniqueId) return
        val state = BingoManager.getActiveState(killer, id) ?: return
        state.setString(id, "done", "true")
        BingoManager.checkCompletion(killer, this)
    }

    override fun isCompletedBy(player: Player, state: BingoPlayerState): Boolean =
        state.getString(id, "done") == "true"

    override fun onReset(player: Player, state: BingoPlayerState) {
        state.removeString(id, "done")
        trackedTargets.entries.removeIf { it.value == player.uniqueId }
    }
}

@CustomObjective
class BombObjective : MultiEventBingoObjective(
    id = "bomb",
    name = Component.text("Bomb!"),
    description = Component.text("Detonate an end crystal."),
    difficulty = Difficulty.EASY
) {
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onCrystalDamage(event: EntityDamageByEntityEvent) {
        val player = event.damager as? Player ?: return
        if (event.entity !is EnderCrystal) return
        val state = BingoManager.getActiveState(player, id) ?: return
        state.setString(id, "done", "true")
        BingoManager.checkCompletion(player, this)
    }

    override fun isCompletedBy(player: Player, state: BingoPlayerState): Boolean =
        state.getString(id, "done") == "true"

    override fun onReset(player: Player, state: BingoPlayerState) =
        state.removeString(id, "done")
}
