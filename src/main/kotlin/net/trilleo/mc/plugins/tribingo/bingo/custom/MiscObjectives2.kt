package net.trilleo.mc.plugins.tribingo.bingo.custom

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import net.trilleo.mc.plugins.tribingo.bingo.BingoManager
import net.trilleo.mc.plugins.tribingo.bingo.BingoPlayerState
import net.trilleo.mc.plugins.tribingo.bingo.EventBingoObjective
import net.trilleo.mc.plugins.tribingo.bingo.MultiEventBingoObjective
import net.trilleo.mc.plugins.tribingo.bingo.annotation.CustomObjective
import net.trilleo.mc.plugins.tribingo.enums.Difficulty
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.block.Biome
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.bukkit.entity.Vindicator
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.inventory.CraftItemEvent
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.raid.RaidTriggerEvent
import org.bukkit.generator.structure.Structure
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack

private const val FOSSIL_SCAN_RADIUS = 5
private const val FLIGHT_OF_THE_WIZARD_REQUIRED_SECONDS = 300
private const val FLIGHT_OF_THE_WIZARD_MAX_DELTA_MILLIS = 5_000L
private const val RACCOON_CITY_OUTBREAK_REQUIRED_KILLS = 100
private const val STRUCTURE_DETECTION_PADDING = 6.0

private val plainTextSerializer: PlainTextComponentSerializer = PlainTextComponentSerializer.plainText()

private fun formatProgressSeconds(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

private fun heldItemFor(event: PlayerInteractEntityEvent): ItemStack =
    when (event.hand) {
        EquipmentSlot.OFF_HAND -> event.player.inventory.itemInOffHand
        else -> event.player.inventory.itemInMainHand
    }

private fun nameTagText(item: ItemStack): String? {
    if (item.type != Material.NAME_TAG) return null
    val meta = item.itemMeta ?: return null
    return when {
        meta.hasDisplayName() -> meta.displayName()?.let(plainTextSerializer::serialize)
        meta.hasItemName() -> plainTextSerializer.serialize(meta.itemName())
        else -> null
    }?.trim()
}

private fun isWithinGeneratedStructure(location: org.bukkit.Location, structure: Structure, padding: Double = 0.0): Boolean {
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

private fun isUnderground(player: Player): Boolean =
    player.world.environment == World.Environment.NETHER ||
        player.location.blockY <= player.world.seaLevel

private fun hasNearbyBoneBlock(player: Player): Boolean {
    if (!isUnderground(player)) return false
    val location = player.location
    val world = location.world
    val minY = maxOf(world.minHeight, location.blockY - FOSSIL_SCAN_RADIUS)
    val maxY = minOf(world.maxHeight - 1, location.blockY + FOSSIL_SCAN_RADIUS)

    for (x in location.blockX - FOSSIL_SCAN_RADIUS..location.blockX + FOSSIL_SCAN_RADIUS) {
        for (y in minY..maxY) {
            for (z in location.blockZ - FOSSIL_SCAN_RADIUS..location.blockZ + FOSSIL_SCAN_RADIUS) {
                if (world.getBlockAt(x, y, z).type == Material.BONE_BLOCK) {
                    return true
                }
            }
        }
    }

    return false
}

@CustomObjective
class WelcomeToJurassicParkObjective : MultiEventBingoObjective(
    id = "welcome_to_jurassic_park",
    name = Component.text("Welcome to Jurassic Park!"),
    description = Component.text("Find a fossil."),
    difficulty = Difficulty.MEDIUM
) {
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPlayerMove(event: PlayerMoveEvent) {
        if (!event.hasChangedBlock()) return
        val player = event.player
        val state = BingoManager.getActiveState(player, id) ?: return
        if (!hasNearbyBoneBlock(player)) return
        state.setString(id, "done", "true")
        BingoManager.checkCompletion(player, this)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onBlockBreak(event: BlockBreakEvent) {
        if (event.block.type != Material.BONE_BLOCK) return
        val state = BingoManager.getActiveState(event.player, id) ?: return
        if (!isUnderground(event.player)) return
        state.setString(id, "done", "true")
        BingoManager.checkCompletion(event.player, this)
    }

    override fun isCompletedBy(player: Player, state: BingoPlayerState): Boolean =
        state.getString(id, "done") == "true"

    override fun onReset(player: Player, state: BingoPlayerState) =
        state.removeString(id, "done")
}

@CustomObjective
class ShawshankEscapeObjective : MultiEventBingoObjective(
    id = "shawshank_escape",
    name = Component.text("Shawshank Escape"),
    description = Component.text("Find an igloo basement."),
    difficulty = Difficulty.MEDIUM
) {
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPlayerMove(event: PlayerMoveEvent) {
        if (!event.hasChangedBlock()) return
        val player = event.player
        val state = BingoManager.getActiveState(player, id) ?: return
        if (!isWithinGeneratedStructure(player.location, Structure.IGLOO)) return

        val location = player.location
        val highestY = player.world.getHighestBlockYAt(location.blockX, location.blockZ)
        if (location.blockY > highestY - 4) return

        state.setString(id, "done", "true")
        BingoManager.checkCompletion(player, this)
    }

    override fun isCompletedBy(player: Player, state: BingoPlayerState): Boolean =
        state.getString(id, "done") == "true"

    override fun onReset(player: Player, state: BingoPlayerState) =
        state.removeString(id, "done")
}

@CustomObjective
class FlightOfTheWizardObjective : MultiEventBingoObjective(
    id = "flight_of_the_wizard",
    name = Component.text("Flight of the Wizard"),
    description = Component.text("Keep flying for 5 minutes."),
    difficulty = Difficulty.HARD
) {
    private val count = FLIGHT_OF_THE_WIZARD_REQUIRED_SECONDS

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPlayerMove(event: PlayerMoveEvent) {
        val player = event.player
        val state = BingoManager.getActiveState(player, id) ?: return
        if (!player.isGliding) {
            state.removeString(id, "last_seen")
            return
        }

        val now = System.currentTimeMillis()
        val lastSeen = state.getString(id, "last_seen")?.toLongOrNull()
        if (lastSeen == null) {
            state.setString(id, "last_seen", now.toString())
            return
        }

        val elapsed = (now - lastSeen).coerceAtMost(FLIGHT_OF_THE_WIZARD_MAX_DELTA_MILLIS)
        val wholeSeconds = (elapsed / 1000L).toInt()
        if (wholeSeconds <= 0) return

        val progress = (state.getProgress(id) + wholeSeconds).coerceAtMost(count)
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
class RaccoonCityOutbreakObjective : EventBingoObjective<EntityDeathEvent>(
    id = "raccoon_city_outbreak",
    name = Component.text("Raccoon City Outbreak"),
    description = Component.text("Kill 100 zombies."),
    difficulty = Difficulty.MEDIUM,
    eventClass = EntityDeathEvent::class.java
) {
    private val count = RACCOON_CITY_OUTBREAK_REQUIRED_KILLS

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onEntityDeath(event: EntityDeathEvent) {
        if (event.entity.type != EntityType.ZOMBIE) return
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
class MariosFavoriteObjective : MultiEventBingoObjective(
    id = "marios_favorite",
    name = Component.text("Mario's favorite"),
    description = Component.text("Find a mushroom fields biome."),
    difficulty = Difficulty.HARD
) {
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPlayerMove(event: PlayerMoveEvent) {
        if (!event.hasChangedBlock()) return
        val player = event.player
        val state = BingoManager.getActiveState(player, id) ?: return
        if (player.location.block.biome != Biome.MUSHROOM_FIELDS) return
        state.setString(id, "done", "true")
        BingoManager.checkCompletion(player, this)
    }

    override fun isCompletedBy(player: Player, state: BingoPlayerState): Boolean =
        state.getString(id, "done") == "true"

    override fun onReset(player: Player, state: BingoPlayerState) =
        state.removeString(id, "done")
}

@CustomObjective
class TheLordOfTheMaceObjective : EventBingoObjective<CraftItemEvent>(
    id = "the_lord_of_the_mace",
    name = Component.text("The Lord of the Mace"),
    description = Component.text("Craft a mace."),
    difficulty = Difficulty.HARD,
    eventClass = CraftItemEvent::class.java
) {
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onCraft(event: CraftItemEvent) {
        if (event.recipe.result.type != Material.MACE) return
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
class PraiseTheSunObjective : EventBingoObjective<CraftItemEvent>(
    id = "praise_the_sun",
    name = Component.text("Praise The Sun!"),
    description = Component.text("Craft a daylight detector."),
    difficulty = Difficulty.EASY,
    eventClass = CraftItemEvent::class.java
) {
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onCraft(event: CraftItemEvent) {
        if (event.recipe.result.type != Material.DAYLIGHT_DETECTOR) return
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
class HeresJohnnyObjective : MultiEventBingoObjective(
    id = "heres_johnny",
    name = Component.text("Here's Johnny"),
    description = Component.text("Name a vindicator Johnny."),
    difficulty = Difficulty.MEDIUM
) {
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPlayerInteractEntity(event: PlayerInteractEntityEvent) {
        val target = event.rightClicked as? Vindicator ?: return
        val heldItem = heldItemFor(event)
        if (heldItem.type != Material.NAME_TAG) return
        if (nameTagText(heldItem) != "Johnny") return

        val state = BingoManager.getActiveState(event.player, id) ?: return
        if (target.type != EntityType.VINDICATOR) return
        state.setString(id, "done", "true")
        BingoManager.checkCompletion(event.player, this)
    }

    override fun isCompletedBy(player: Player, state: BingoPlayerState): Boolean =
        state.getString(id, "done") == "true"

    override fun onReset(player: Player, state: BingoPlayerState) =
        state.removeString(id, "done")
}

@CustomObjective
class DeadMenTellNoTalesObjective : MultiEventBingoObjective(
    id = "dead_men_tell_no_tales",
    name = Component.text("Dead Men Tell No Tales"),
    description = Component.text("Find a shipwreck."),
    difficulty = Difficulty.EASY
) {
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPlayerMove(event: PlayerMoveEvent) {
        if (!event.hasChangedBlock()) return
        val player = event.player
        val state = BingoManager.getActiveState(player, id) ?: return
        val inShipwreck = isWithinGeneratedStructure(player.location, Structure.SHIPWRECK, STRUCTURE_DETECTION_PADDING) ||
            isWithinGeneratedStructure(player.location, Structure.SHIPWRECK_BEACHED, STRUCTURE_DETECTION_PADDING)
        if (!inShipwreck) return

        state.setString(id, "done", "true")
        BingoManager.checkCompletion(player, this)
    }

    override fun isCompletedBy(player: Player, state: BingoPlayerState): Boolean =
        state.getString(id, "done") == "true"

    override fun onReset(player: Player, state: BingoPlayerState) =
        state.removeString(id, "done")
}

@CustomObjective
class TwilightOfTheGodsObjective : MultiEventBingoObjective(
    id = "twilight_of_the_gods",
    name = Component.text("Twilight of the Gods"),
    description = Component.text("Start a raid in the End."),
    difficulty = Difficulty.INSANE
) {
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onRaidTrigger(event: RaidTriggerEvent) {
        if (event.player.world.environment != World.Environment.THE_END) return
        val state = BingoManager.getActiveState(event.player, id) ?: return
        state.setString(id, "done", "true")
        BingoManager.checkCompletion(event.player, this)
    }

    override fun isCompletedBy(player: Player, state: BingoPlayerState): Boolean =
        state.getString(id, "done") == "true"

    override fun onReset(player: Player, state: BingoPlayerState) =
        state.removeString(id, "done")
}
