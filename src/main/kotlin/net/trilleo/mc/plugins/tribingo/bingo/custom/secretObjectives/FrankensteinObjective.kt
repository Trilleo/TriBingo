package net.trilleo.mc.plugins.tribingo.bingo.custom.secretObjectives

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.trilleo.mc.plugins.tribingo.bingo.BingoManager
import net.trilleo.mc.plugins.tribingo.bingo.BingoPlayerState
import net.trilleo.mc.plugins.tribingo.bingo.EventBingoObjective
import net.trilleo.mc.plugins.tribingo.bingo.SecretBingoObjective
import net.trilleo.mc.plugins.tribingo.bingo.annotation.CustomObjective
import net.trilleo.mc.plugins.tribingo.bingo.custom.difficultyNameColor
import net.trilleo.mc.plugins.tribingo.enums.Difficulty
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.player.PlayerMoveEvent

private fun armorMaterialFamily(material: Material?): String? {
    val name = material?.name ?: return null
    if (material == Material.AIR) return null

    val suffix = when {
        name.endsWith("_HELMET") -> "_HELMET"
        name.endsWith("_CHESTPLATE") -> "_CHESTPLATE"
        name.endsWith("_LEGGINGS") -> "_LEGGINGS"
        name.endsWith("_BOOTS") -> "_BOOTS"
        else -> return null
    }

    return name.removeSuffix(suffix)
}

private fun hasFourDistinctArmorFamilies(player: Player): Boolean {
    val families = listOf(
        armorMaterialFamily(player.inventory.helmet.type),
        armorMaterialFamily(player.inventory.chestplate.type),
        armorMaterialFamily(player.inventory.leggings.type),
        armorMaterialFamily(player.inventory.boots.type)
    )

    if (families.any { it == null }) return false
    return families.filterNotNull().toSet().size == 4
}

class FrankensteinObjective : EventBingoObjective<PlayerMoveEvent>(
    id = "frankenstein",
    name = Component.text("Frankenstein", difficultyNameColor(Difficulty.EASY)),
    description = Component.text("Wear 4 armor pieces all of different material types simultaneously.", NamedTextColor.GRAY),
    difficulty = Difficulty.EASY,
    eventClass = PlayerMoveEvent::class.java
) {
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPlayerMove(event: PlayerMoveEvent) {
        val player = event.player
        val state = BingoManager.getActiveState(player, id) ?: return
        onEvent(event, player, state)
    }

    override fun onEvent(event: PlayerMoveEvent, player: Player, state: BingoPlayerState) {
        if (!hasFourDistinctArmorFamilies(player)) return
        state.setString(id, "done", "true")
        BingoManager.checkCompletion(player, this)
    }

    override fun isCompletedBy(player: Player, state: BingoPlayerState): Boolean =
        state.getString(id, "done") == "true"

    override fun onReset(player: Player, state: BingoPlayerState) =
        state.removeString(id, "done")
}

@CustomObjective
class SecretFrankenstein : SecretBingoObjective(
    inner = FrankensteinObjective(),
    hints = listOf(
        "A calculated mismatch is key.",
        "You'll need to wear an assortment of items to complete this objective.",
        "The items all need to be different in some way."
    )
)
