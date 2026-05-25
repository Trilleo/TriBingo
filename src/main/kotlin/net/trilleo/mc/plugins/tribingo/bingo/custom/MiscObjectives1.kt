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
import org.bukkit.entity.Ageable
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.entity.*
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.event.player.PlayerItemConsumeEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.inventory.ItemStack

private const val COPPER_STATUE_VARIANTS_REQUIRED = 4
private const val TITANIC_REQUIRED_SECONDS = 300
private const val FORCE_REQUIRED_DEATHS = 10
private const val FORCE_WINDOW_MILLIS = 180_000L
private const val AVENGERS_REQUIRED_GOLEMS = 10
private const val AVENGERS_WINDOW_MILLIS = 180_000L
private const val AVENGERS_TRACK_RADIUS = 8.0
private const val TITANIC_MAX_DELTA_MILLIS = 5_000L

private fun isCopperArmorPiece(material: Material?, suffix: String): Boolean =
    material != null && material != Material.AIR && material.name.startsWith("COPPER_") && material.name.endsWith(suffix)

private fun hasFullCopperArmor(player: Player): Boolean =
    isCopperArmorPiece(player.inventory.helmet?.type, "_HELMET") &&
            isCopperArmorPiece(player.inventory.chestplate?.type, "_CHESTPLATE") &&
            isCopperArmorPiece(player.inventory.leggings?.type, "_LEGGINGS") &&
            isCopperArmorPiece(player.inventory.boots?.type, "_BOOTS")

private fun hasProjectedFullCopperArmor(player: Player, event: InventoryClickEvent): Boolean {
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
        val moved = event.currentItem?.type
        when {
            isCopperArmorPiece(moved, "_HELMET") && helmet == null -> helmet = moved
            isCopperArmorPiece(moved, "_CHESTPLATE") && chestplate == null -> chestplate = moved
            isCopperArmorPiece(moved, "_LEGGINGS") && leggings == null -> leggings = moved
            isCopperArmorPiece(moved, "_BOOTS") && boots == null -> boots = moved
        }
    }

    return isCopperArmorPiece(helmet, "_HELMET") &&
            isCopperArmorPiece(chestplate, "_CHESTPLATE") &&
            isCopperArmorPiece(leggings, "_LEGGINGS") &&
            isCopperArmorPiece(boots, "_BOOTS")
}

private fun heldItemFor(event: PlayerInteractEntityEvent): ItemStack =
    if (event.hand?.name == "OFF_HAND") event.player.inventory.itemInOffHand else event.player.inventory.itemInMainHand

private fun isPotionLike(item: ItemStack?): Boolean =
    item != null && item.type != Material.AIR && item.type.name.contains("POTION")

private fun isCopperGolemStatue(material: Material?): Boolean {
    val name = material?.name ?: return false
    return name.contains("COPPER") && name.contains("GOLEM") && name.contains("STATUE")
}

private fun parseTimestamps(raw: String?): MutableList<Long> =
    raw?.split(',')
        ?.mapNotNull { it.toLongOrNull() }
        ?.toMutableList()
        ?: mutableListOf()

private fun formatProgressSeconds(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

@CustomObjective
class BronzeAgeObjective : MultiEventBingoObjective(
    id = "bronze_age",
    name = Component.text("Bronze Age", difficultyNameColor(Difficulty.EASY)),
    description = Component.text("Wear all 4 copper armor pieces.", NamedTextColor.GRAY),
    difficulty = Difficulty.EASY
) {
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val state = BingoManager.getActiveState(player, id) ?: return
        if (hasFullCopperArmor(player) || hasProjectedFullCopperArmor(player, event)) {
            state.setString(id, "done", "true")
            BingoManager.checkCompletion(player, this)
        }
    }

    override fun isCompletedBy(player: Player, state: BingoPlayerState): Boolean =
        state.getString(id, "done") == "true"

    override fun onReset(player: Player, state: BingoPlayerState) =
        state.removeString(id, "done")
}

@CustomObjective
class NeverGrowUpObjective : MultiEventBingoObjective(
    id = "never_grow_up",
    name = Component.text("Never grow up!", difficultyNameColor(Difficulty.EASY)),
    description = Component.text("Feed a golden dandelion potion to a cub.", NamedTextColor.GRAY),
    difficulty = Difficulty.EASY
) {
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPlayerInteractEntity(event: PlayerInteractEntityEvent) {
        val target = event.rightClicked as? Ageable ?: return
        if (target.isAdult) return
        if (!isPotionLike(heldItemFor(event))) return
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
class CopperArmyObjective : MultiEventBingoObjective(
    id = "copper_army",
    name = Component.text("Copper army!", difficultyNameColor(Difficulty.EASY)),
    description = Component.text("Get all kinds of copper golem statues.", NamedTextColor.GRAY),
    difficulty = Difficulty.EASY
) {
    private val count = COPPER_STATUE_VARIANTS_REQUIRED

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onEntityPickupItem(event: EntityPickupItemEvent) {
        val player = event.entity as? Player ?: return
        if (!isCopperGolemStatue(event.item.itemStack.type)) return
        val state = BingoManager.getActiveState(player, id) ?: return

        state.addStep(id, event.item.itemStack.type.name)

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
class IAmTheGodObjective : EventBingoObjective<PlayerItemConsumeEvent>(
    id = "i_am_the_god",
    name = Component.text("I am the God!", difficultyNameColor(Difficulty.MEDIUM)),
    description = Component.text("Eat an enchanted golden apple.", NamedTextColor.GRAY),
    difficulty = Difficulty.MEDIUM,
    eventClass = PlayerItemConsumeEvent::class.java
) {
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPlayerItemConsume(event: PlayerItemConsumeEvent) {
        if (event.item.type != Material.ENCHANTED_GOLDEN_APPLE) return
        val state = BingoManager.getActiveState(event.player, id) ?: return
        onEvent(event, event.player, state)
    }

    override fun onEvent(event: PlayerItemConsumeEvent, player: Player, state: BingoPlayerState) {
        state.setString(id, "done", "true")
        BingoManager.checkCompletion(player, this)
    }

    override fun isCompletedBy(player: Player, state: BingoPlayerState): Boolean =
        state.getString(id, "done") == "true"

    override fun onReset(player: Player, state: BingoPlayerState) =
        state.removeString(id, "done")
}

@CustomObjective
class LookAtMeObjective : EventBingoObjective<PlayerDeathEvent>(
    id = "look_at_me",
    name = Component.text("Look at me!", difficultyNameColor(Difficulty.MEDIUM)),
    description = Component.text("Killed by a creaking.", NamedTextColor.GRAY),
    difficulty = Difficulty.MEDIUM,
    eventClass = PlayerDeathEvent::class.java
) {
    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerDeath(event: PlayerDeathEvent) {
        val player = event.entity
        val lastDamage = player.lastDamageCause as? EntityDamageByEntityEvent ?: return
        if (lastDamage.damager.type.name != "CREAKING") return
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
class QuietObjective : EventBingoObjective<PlayerDeathEvent>(
    id = "quiet",
    name = Component.text("Quiet!", difficultyNameColor(Difficulty.MEDIUM)),
    description = Component.text("Killed by the Warden.", NamedTextColor.GRAY),
    difficulty = Difficulty.MEDIUM,
    eventClass = PlayerDeathEvent::class.java
) {
    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerDeath(event: PlayerDeathEvent) {
        val player = event.entity
        val lastDamage = player.lastDamageCause as? EntityDamageByEntityEvent ?: return
        if (lastDamage.damager.type != EntityType.WARDEN) return
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
class YouCanSpeakNowObjective : EventBingoObjective<EntityDeathEvent>(
    id = "you_can_speak_now",
    name = Component.text("You can speak now.", difficultyNameColor(Difficulty.INSANE)),
    description = Component.text("Kill the Warden.", NamedTextColor.GRAY),
    difficulty = Difficulty.INSANE,
    eventClass = EntityDeathEvent::class.java
) {
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onEntityDeath(event: EntityDeathEvent) {
        if (event.entity.type != EntityType.WARDEN) return
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
class TitanicObjective : MultiEventBingoObjective(
    id = "titanic",
    name = Component.text("Titanic", difficultyNameColor(Difficulty.EASY)),
    description = Component.text("Keep boating for 5 minutes.", NamedTextColor.GRAY),
    difficulty = Difficulty.EASY
) {
    private val count = TITANIC_REQUIRED_SECONDS

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPlayerMove(event: PlayerMoveEvent) {
        val player = event.player
        val state = BingoManager.getActiveState(player, id) ?: return
        val vehicle = player.vehicle
        if (vehicle == null || (!vehicle.type.name.contains("BOAT") && !vehicle.type.name.contains("RAFT"))) {
            state.removeString(id, "last_seen")
            return
        }

        val now = System.currentTimeMillis()
        val lastSeen = state.getString(id, "last_seen")?.toLongOrNull()
        if (lastSeen == null) {
            state.setString(id, "last_seen", now.toString())
            return
        }

        // Cap each increment so brief lag spikes or delayed move packets do not
        // award large chunks of boating time all at once.
        val elapsed = (now - lastSeen).coerceAtMost(TITANIC_MAX_DELTA_MILLIS)
        val wholeSeconds = (elapsed / 1000L).toInt()
        if (wholeSeconds <= 0) return

        val progress = state.getProgress(id) + wholeSeconds
        state.setProgress(id, progress)
        state.setString(id, "last_seen", (lastSeen + (wholeSeconds * 1000L)).toString())
        if (progress >= count) {
            BingoManager.checkCompletion(player, this)
        }
    }

    override fun isCompletedBy(player: Player, state: BingoPlayerState): Boolean =
        state.getProgress(id) >= count

    override fun onReset(player: Player, state: BingoPlayerState) {
        state.setProgress(id, 0)
        state.removeString(id, "last_seen")
    }

    override fun displayItem(player: Player, completed: Boolean): ItemStack {
        val progress = BingoManager.currentGame
            ?.getOrCreateState(player.uniqueId)?.getProgress(id) ?: 0
        return buildProgressItem(
            player,
            completed,
            Component.text(
                "Progress: ${formatProgressSeconds(progress.coerceAtMost(count))}/${formatProgressSeconds(count)}",
                NamedTextColor.YELLOW
            )
        )
    }
}

@CustomObjective
class MayTheForceBeWithYouObjective : MultiEventBingoObjective(
    id = "may_the_force_be_with_you",
    name = Component.text("May the Force Be With You", difficultyNameColor(Difficulty.EASY)),
    description = Component.text("Die 10 times in 3 minutes.", NamedTextColor.GRAY),
    difficulty = Difficulty.EASY
) {
    private val count = FORCE_REQUIRED_DEATHS
    private val windowMillis = FORCE_WINDOW_MILLIS

    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerDeath(event: PlayerDeathEvent) {
        val player = event.entity
        val state = BingoManager.getActiveState(player, id) ?: return
        val now = System.currentTimeMillis()
        val timestamps = parseTimestamps(state.getString(id, "timestamps"))
            .filter { now - it <= windowMillis }
            .toMutableList()
        timestamps += now

        state.setString(id, "timestamps", timestamps.joinToString(","))
        state.setProgress(id, timestamps.size)
        if (timestamps.size >= count) {
            BingoManager.checkCompletion(player, this)
        }
    }

    override fun isCompletedBy(player: Player, state: BingoPlayerState): Boolean =
        state.getProgress(id) >= count

    override fun onReset(player: Player, state: BingoPlayerState) {
        state.setProgress(id, 0)
        state.removeString(id, "timestamps")
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
class AvengersAssembleObjective : MultiEventBingoObjective(
    id = "avengers_assemble",
    name = Component.text("Avengers Assemble!", difficultyNameColor(Difficulty.HARD)),
    description = Component.text("Make 10 iron golems in 3 minutes.", NamedTextColor.GRAY),
    difficulty = Difficulty.HARD
) {
    private val count = AVENGERS_REQUIRED_GOLEMS
    private val windowMillis = AVENGERS_WINDOW_MILLIS

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onCreatureSpawn(event: CreatureSpawnEvent) {
        if (event.entity.type != EntityType.IRON_GOLEM) return
        val spawnReason = event.spawnReason.name
        if (spawnReason != "BUILD_IRONGOLEM" && spawnReason != "BUILD_IRON_GOLEM") return

        val player = event.location.getNearbyPlayers(AVENGERS_TRACK_RADIUS)
            .sortedBy { it.location.distanceSquared(event.location) }
            .firstOrNull { BingoManager.getActiveState(it, id) != null }
            ?: return
        val state = BingoManager.getActiveState(player, id) ?: return

        val now = System.currentTimeMillis()
        val timestamps = parseTimestamps(state.getString(id, "timestamps"))
            .filter { now - it <= windowMillis }
            .toMutableList()
        timestamps += now

        state.setString(id, "timestamps", timestamps.joinToString(","))
        state.setProgress(id, timestamps.size)
        if (timestamps.size >= count) {
            BingoManager.checkCompletion(player, this)
        }
    }

    override fun isCompletedBy(player: Player, state: BingoPlayerState): Boolean =
        state.getProgress(id) >= count

    override fun onReset(player: Player, state: BingoPlayerState) {
        state.setProgress(id, 0)
        state.removeString(id, "timestamps")
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
