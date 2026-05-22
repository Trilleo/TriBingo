package net.trilleo.mc.plugins.tribingo.enums

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor

/**
 * The difficulty level of a Bingo game, which controls how objectives are
 * distributed across the board via a [net.trilleo.mc.plugins.tribingo.bingo.randomizer.BoardRandomizer].
 *
 * This is distinct from [Difficulty], which describes the difficulty of an
 * individual objective. [GameDifficulty] determines the overall challenge
 * of the game by influencing which objective difficulties appear and where.
 *
 * New values can be added freely; pair each new entry with a corresponding
 * [net.trilleo.mc.plugins.tribingo.bingo.randomizer.BoardRandomizer] implementation
 * registered in [net.trilleo.mc.plugins.tribingo.bingo.randomizer.BoardRandomizerRegistry].
 */
enum class GameDifficulty {
    EASY,
    MEDIUM,
    HARD;

    /**
     * Returns a coloured [Component] representation of this game difficulty
     * suitable for use in chat messages or GUIs.
     */
    fun displayName(): Component = when (this) {
        EASY -> Component.text("Easy", NamedTextColor.GREEN)
        MEDIUM -> Component.text("Medium", NamedTextColor.YELLOW)
        HARD -> Component.text("Hard", NamedTextColor.RED)
    }
}
