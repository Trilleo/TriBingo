package net.trilleo.mc.plugins.tribingo.bingo

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.trilleo.mc.plugins.tribingo.enums.Difficulty
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

/**
 * A decorator that wraps any [BingoObjective] to mark it as a "secret" objective.
 *
 * Secret objectives hide their description behind `"*****"` until the viewing
 * player has personally completed them. Hints are progressively revealed to all
 * players as the game timer elapses (managed by [SecretHintManager]).
 *
 * ### Display behaviour
 * - **Name**: always shown (the wrapped objective's name)
 * - **Difficulty**: always shown
 * - **Secret tag**: a light-purple `"⚡ Secret"` label
 * - **Description**: `"*****"` until completed by the viewer, then the real description
 * - **Hints**: globally revealed over time; shown in the item lore
 *
 * ### Event listener registration
 * The wrapper itself is **not** a Bukkit [org.bukkit.event.Listener]. The inner
 * objective retains its listener registration (handled by [BingoObjectiveRegistry]).
 *
 * ### Usage
 * ```kotlin
 * @CustomObjective
 * class MySecretObj : SecretBingoObjective(
 *     inner = ActualObjective(),
 *     hints = listOf("Hint about the Nether", "Involves a fortress", "Kill a specific mob")
 * )
 * ```
 *
 * @param inner the real objective being wrapped
 * @param hints ordered list of hint strings revealed progressively during the game
 */
open class SecretBingoObjective(
    val inner: BingoObjective,
    val hints: List<String>
) : BingoObjective(
    id = inner.id,
    name = inner.name,
    description = inner.description,
    difficulty = inner.difficulty
) {

    override fun isCompletedBy(player: Player, state: BingoPlayerState): Boolean =
        inner.isCompletedBy(player, state)

    override fun onReset(player: Player, state: BingoPlayerState) =
        inner.onReset(player, state)

    /**
     * Renders the board cell item with secret-aware display logic.
     *
     * - Uses [Material.MAGENTA_STAINED_GLASS] for incomplete secret cells
     *   (distinct from normal difficulty colours) and [Material.LIME_CONCRETE]
     *   when completed.
     * - Shows the secret tag, difficulty, masked/real description (player-aware),
     *   and any currently revealed hints.
     */
    override fun displayItem(player: Player, completed: Boolean): ItemStack {
        val material = if (completed) Material.LIME_CONCRETE else Material.MAGENTA_STAINED_GLASS

        val item = ItemStack(material)
        val meta = item.itemMeta ?: return item

        meta.displayName(name.decoration(TextDecoration.ITALIC, false))

        val lore = mutableListOf<Component>()

        // Difficulty line
        lore += Component.text("Difficulty: ", NamedTextColor.GRAY)
            .decoration(TextDecoration.ITALIC, false)
            .append(difficulty.displayName().decoration(TextDecoration.ITALIC, false))

        // Secret tag
        lore += Component.text("⚡ Secret", NamedTextColor.LIGHT_PURPLE)
            .decoration(TextDecoration.ITALIC, false)

        lore += Component.empty()

        // Description: real if completed by this player, masked otherwise
        if (completed) {
            lore += description.decoration(TextDecoration.ITALIC, false)
        } else {
            lore += Component.text("*****", NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, false)
        }

        // Revealed hints (only when not completed — completed players see description)
        if (!completed) {
            val revealedHints = SecretHintManager.getRevealedHints(this)
            if (revealedHints.isNotEmpty()) {
                lore += Component.empty()
                revealedHints.forEachIndexed { index, hint ->
                    lore += Component.text("Hint ${index + 1}: ", NamedTextColor.GOLD)
                        .decoration(TextDecoration.ITALIC, false)
                        .append(
                            Component.text(hint, NamedTextColor.YELLOW)
                                .decoration(TextDecoration.ITALIC, false)
                        )
                }
            }
        }

        lore += Component.empty()

        // Completion status
        lore += if (completed) {
            Component.text("✓ Completed", NamedTextColor.GREEN)
                .decoration(TextDecoration.ITALIC, false)
        } else {
            Component.text("○ Not yet completed", NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false)
        }

        meta.lore(lore)
        item.itemMeta = meta
        return item
    }
}
