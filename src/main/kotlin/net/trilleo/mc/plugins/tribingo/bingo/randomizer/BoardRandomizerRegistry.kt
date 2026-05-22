package net.trilleo.mc.plugins.tribingo.bingo.randomizer

import net.trilleo.mc.plugins.tribingo.enums.GameDifficulty

/**
 * Registry that maps each [GameDifficulty] to a [BoardRandomizer] implementation.
 *
 * By default three randomizers are registered (easy, medium, hard). To add a
 * new game difficulty, register a custom [BoardRandomizer] via [register].
 *
 * ### Example: adding a custom difficulty
 * ```kotlin
 * BoardRandomizerRegistry.register(GameDifficulty.MY_NEW_DIFFICULTY, MyRandomizer())
 * ```
 */
object BoardRandomizerRegistry {

    private val randomizers = mutableMapOf<GameDifficulty, BoardRandomizer>()

    init {
        register(GameDifficulty.EASY, EasyBoardRandomizer())
        register(GameDifficulty.MEDIUM, MediumBoardRandomizer())
        register(GameDifficulty.HARD, HardBoardRandomizer())
    }

    /**
     * Registers a [randomizer] for the given [gameDifficulty], replacing any
     * previously registered randomizer for that difficulty.
     *
     * @param gameDifficulty the game difficulty to associate
     * @param randomizer     the randomizer implementation
     */
    fun register(gameDifficulty: GameDifficulty, randomizer: BoardRandomizer) {
        randomizers[gameDifficulty] = randomizer
    }

    /**
     * Returns the [BoardRandomizer] registered for [gameDifficulty], or `null`
     * if none is registered.
     *
     * @param gameDifficulty the game difficulty to look up
     */
    fun get(gameDifficulty: GameDifficulty): BoardRandomizer? = randomizers[gameDifficulty]
}
