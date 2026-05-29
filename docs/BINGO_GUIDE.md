# TriBingo - Bingo System Guide

This guide documents every aspect of the Bingo system: its architecture, game lifecycle, objective model, persistence
layer, configuration, commands, and GUI. It is intended for developers who want to understand, extend, or maintain the
system.

---

## Table of Contents

1. [Architecture Overview](#architecture-overview)
2. [Initialisation Order](#initialisation-order)
3. [Game Lifecycle](#game-lifecycle)
4. [Core Classes](#core-classes)
    - [BingoManager](#bingomanager)
    - [BingoGame](#bingogame)
    - [BingoBoard](#bingoboard)
    - [BingoCell](#bingocell)
    - [BingoPlayerState](#bingoplayerstate)
5. [Objectives](#objectives)
    - [BingoObjective](#bingoobjective)
    - [EventBingoObjective](#eventbingoobjective)
    - [MultiEventBingoObjective](#multieventbingoobjective)
    - [SequentialBingoObjective](#sequentialbingoobjective)
    - [Built-in Objective Types](#built-in-objective-types)
    - [SecretBingoObjective](#secretbingoobjective)
    - [SecretHintManager](#secrethintmanager)
6. [Registry & Loading](#registry--loading)
    - [BingoObjectiveRegistry](#bingoobjectiveregistry)
    - [CodeObjectiveLoader](#codeobjectiveloader)
    - [YamlObjectiveLoader](#yamlobjectiveloader)
    - [bingo_objectives.yml Format](#bingo_objectivesyml-format)
7. [Persistence](#persistence)
    - [BingoServerData](#bingoserverdata)
8. [Configuration](#configuration)
9. [Commands](#commands)
10. [Countdown](#countdown)
11. [Board GUI](#board-gui)
12. [Writing a Custom Objective](#writing-a-custom-objective)
13. [Writing a Code Objective](#writing-a-code-objective)
14. [Writing a Secret Objective](#writing-a-secret-objective)

---

## Architecture Overview

The Bingo system is organised into four layers:

| Layer         | Key Classes                                                        | Responsibility                                 |
|:--------------|:-------------------------------------------------------------------|:-----------------------------------------------|
| **Facade**    | `BingoManager`                                                     | Single entry-point for all gameplay operations |
| **Game**      | `BingoGame`, `BingoBoard`, `BingoCell`, `BingoPlayerState`         | In-memory game state and win-condition logic   |
| **Objective** | `BingoObjective`, `EventBingoObjective`, built-in implementations  | Definition and completion logic for each cell  |
| **Data**      | `BingoObjectiveRegistry`, `YamlObjectiveLoader`, `BingoServerData` | Loading, registering, and persisting game data |

A `BingoBoardGUI` and a `BingoCommand` provide the player-facing interfaces.

```
                          ┌───────────────────────────────────────────────┐
                          │               BingoManager (singleton)        │
                          │  newGame · startGame · stopGame · resetGame   │
                          │  refreshBoard · checkCompletion               │
                          └──────────────────┬────────────────────────────┘
                                             │ owns
                                    ┌────────▼─────────┐
                                    │    BingoGame      │
                                    │  state · board    │
                                    │  playerStates     │
                                    └──┬─────────────┬──┘
                                       │             │
                              ┌────────▼──┐   ┌──────▼─────────────┐
                              │ BingoBoard│   │  BingoPlayerState  │
                              │  size     │   │  completedCells    │
                              │  cells[]  │   │  progressData      │
                              └────┬──────┘   └────────────────────┘
                                   │ N cells
                              ┌────▼──────┐
                              │ BingoCell │
                              │  index    │
                              │  objective│
                              └────┬──────┘
                                   │
                         ┌─────────▼──────────┐
                         │   BingoObjective   │
                         │  (abstract)        │
                         └─────────┬──────────┘
                                   │ extends
                        ┌──────────▼────────────┐
                        │ EventBingoObjective<T>│  (also implements Listener)
                        └───────────────────────┘
```

---

## Initialisation Order

The following sequence must be followed exactly in `Main.onEnable`:

```kotlin
// 1. Set the BingoServerData factory BEFORE ServerDataManager.init
ServerDataManager.setFactory { BingoServerData() }
ServerDataManager.init(plugin)

// 2. Register everything else (commands, listeners, GUIs, tasks) ...

// 3. Initialise the Bingo system LAST, after all other systems are ready
BingoObjectiveRegistry.init(plugin)          // stores plugin reference for listener registration
CodeObjectiveLoader.load(plugin, BingoObjectiveRegistry,
    "net.trilleo.mc.plugins.tribingo.bingo.custom")  // auto-discovers @CustomObjective classes
YamlObjectiveLoader.load(plugin, BingoObjectiveRegistry)  // parses bingo_objectives.yml
SecretHintManager.init(plugin)               // initialises hint reveal system
BingoManager.init(plugin)                    // rehydrates or creates a default game
ObjectiveTestManager.init(plugin)            // initialises objective test sessions
```

And in `Main.onDisable`:

```kotlin
TaskRegistrar.unregisterAll()
ObjectiveTestManager.shutdown()
SecretHintManager.reset()
RecipeRegistrar.unregisterAll()
PlayerDataManager.saveAll()
// Save bingo state BEFORE ServerDataManager.save
BingoManager.save()
ServerDataManager.save()
```

---

## Game Lifecycle

A game moves through three states defined by `GameState`:

```
INACTIVE ──start()──► ACTIVE ──end()──► ENDED
   ▲                                       │
   └──────────────── reset() ─────────────┘
```

| State      | Meaning                                                                             |
|:-----------|:------------------------------------------------------------------------------------|
| `INACTIVE` | Game exists but has not been started; objectives cannot be completed                |
| `ACTIVE`   | Game is running; objectives can be completed; win checks are performed              |
| `ENDED`    | Game has finished (winner found or stopped manually); call `reset()` to start fresh |

`refresh()` can only be called while `INACTIVE`. `reset()` can be called from any state.

---

## Core Classes

### BingoManager

**Package:** `net.trilleo.mc.plugins.tribingo.bingo`

The singleton facade for the entire Bingo system. All gameplay operations flow through this object.

| Method                                                       | Description                                                                                     |
|:-------------------------------------------------------------|:------------------------------------------------------------------------------------------------|
| `init(plugin)`                                               | Stores the plugin reference and rehydrates or creates a default game on startup                 |
| `save()`                                                     | Serialises the current game into `BingoServerData` for disk persistence; clears data if ACTIVE  |
| `newGame(gameDifficulty: GameDifficulty): BingoGame`         | Creates a new game with randomly selected objectives; defaults to `GameDifficulty.MEDIUM`       |
| `startGame()`                                                | Transitions the current game from `INACTIVE` → `ACTIVE`; starts the countdown                   |
| `stopGame()`                                                 | Ends the current `ACTIVE` game without a winner; cancels the countdown                          |
| `resetGame()`                                                | Resets all player progress and returns the game to `INACTIVE`                                   |
| `refreshBoard()`                                             | Picks a new random set of objectives (game must be `INACTIVE`)                                  |
| `getTimerSeconds(): Int`                                     | Returns the configured countdown duration in seconds (default 3 600)                            |
| `setTimerSeconds(seconds: Int)`                              | Persists the countdown duration; must be in `1..86_400`                                         |
| `checkCompletion(player, objective)`                         | Called by event objectives to mark a cell complete and check the win condition                  |
| `isGameActive(): Boolean`                                    | Returns `true` if the current game is in `ACTIVE` state                                         |
| `getActiveState(player, objectiveId): BingoPlayerState?`     | Returns the player's state if they have an active game/test session for the given objective     |
| `applyTeamGameModes()`                                       | Sets players to SURVIVAL and spectators to SPECTATOR mode when a game starts                    |
| `restoreGameModes()`                                         | Restores game modes when a game ends                                                            |
| `currentGame: BingoGame?`                                    | The currently active (or most-recently-created) game; `null` if none exists                     |

**`checkCompletion` flow:**

1. Verifies a game is `ACTIVE` and the cell has not yet been completed for the player.
2. Marks the cell complete in `BingoPlayerState`.
3. Awards objective points and checks for row/column/diagonal line-completion bonuses.
4. Optionally broadcasts a server-wide completion announcement (`bingo.announce-completions`).
5. Refreshes the player's open `BingoBoardGUI` in-place.
6. Checks if the full board is complete and calls `BingoGame.end(player, points)` if met.

---

### BingoGame

**Package:** `net.trilleo.mc.plugins.tribingo.bingo`

The state machine for a single game session. Create via `BingoManager.newGame`; do not instantiate directly.

| Method / Property                           | Description                                                                                          |
|:--------------------------------------------|:-----------------------------------------------------------------------------------------------------|
| `board: BingoBoard`                         | The current board; replaced by `refresh()`                                                           |
| `state: GameState`                          | Current lifecycle state                                                                              |
| `difficulty: GameDifficulty`                | The game difficulty controlling objective distribution on refresh                                     |
| `playerStates: Map<UUID, BingoPlayerState>` | Read-only snapshot of all player states created this session                                         |
| `start()`                                   | `INACTIVE` → `ACTIVE`; broadcasts start message to all online players                                |
| `end(winner, winnerPoints, winnerName?)`    | `ACTIVE` → `ENDED`; broadcasts winner (or "game ended") message with points and optional name        |
| `reset()`                                   | Any state → `INACTIVE`; calls `onReset` on each objective for online players, then clears all states |
| `refresh(objectives)`                       | Rebuilds the board from a new random selection (must be `INACTIVE`)                                  |
| `getOrCreateState(uuid): BingoPlayerState`  | Returns the player's state, creating a fresh one if it does not exist                                |

---

### BingoBoard

**Package:** `net.trilleo.mc.plugins.tribingo.bingo`

A 5×5 grid backed by a flat, row-major list of `BingoCell` objects. Immutable after construction. The board size is
fixed at `5` (defined by `BingoBoard.SIZE`).

**Coordinate system:** cell `(row, col)` maps to `cells[row * SIZE + col]`.

| Method / Property                          | Description                                                         |
|:-------------------------------------------|:--------------------------------------------------------------------|
| `SIZE: Int` (companion constant)           | The only supported board side-length (`5`)                          |
| `size: Int`                                | Side-length of the square board (always `5`)                        |
| `cells: List<BingoCell>`                   | All cells in row-major order (`25` entries)                         |
| `getCell(row, col): BingoCell`             | Returns the cell at zero-based `(row, col)`                         |
| `isRowComplete(state, row): Boolean`       | `true` if the player has completed every cell in the given row      |
| `isColComplete(state, col): Boolean`       | `true` if the player has completed every cell in the given column   |
| `isDiagMainComplete(state): Boolean`       | `true` if the player has completed the main diagonal (↘)            |
| `isDiagAntiComplete(state): Boolean`       | `true` if the player has completed the anti-diagonal (↗)            |
| `isBoardFull(state): Boolean`              | `true` if the player has completed every cell                       |

Win-condition check performed by `BingoManager.checkCompletion`:

- The win condition is **full board** — `isBoardFull(state)` must return `true`.
- Line completions (rows, columns, diagonals) award **bonus points** but do not trigger a win.

---

### BingoCell

**Package:** `net.trilleo.mc.plugins.tribingo.bingo`

Immutable value type representing a single cell on the board. Completion state lives in `BingoPlayerState`.

| Property    | Type             | Description                                                                 |
|:------------|:-----------------|:----------------------------------------------------------------------------|
| `cellIndex` | `Int`            | Zero-based flat index (row-major); key in `BingoPlayerState.completedCells` |
| `objective` | `BingoObjective` | The objective that must be fulfilled to complete this cell                  |

---

### BingoPlayerState

**Package:** `net.trilleo.mc.plugins.tribingo.bingo`

Per-player mutable state for a single game session. Keyed by player `UUID`.

| Property / Method                       | Description                                                                |
|:----------------------------------------|:---------------------------------------------------------------------------|
| `uuid: UUID`                            | The player this state belongs to                                           |
| `completedCells: MutableSet<Int>`       | Set of completed `cellIndex` values                                        |
| `progressData: MutableMap<String, Int>` | Per-objective progress counters, keyed by `BingoObjective.id`              |
| `stringData: MutableMap<String, String>`| Arbitrary string values, keyed by `"objectiveId:fieldName"`                |
| `stepData: MutableMap<String, MutableSet<String>>` | Ordered step tokens for sequential objectives, keyed by objective ID |
| `completedLines: MutableSet<String>`    | Line keys that have received bonus points (`"row_N"`, `"col_N"`, `"diag_main"`, `"diag_anti"`) |
| `points: Int`                           | Accumulated point total for this session                                   |
| `isCompleted(cellIndex): Boolean`       | Returns `true` when the cell has been marked complete                      |
| `markCompleted(cellIndex)`              | Adds `cellIndex` to `completedCells`                                       |
| `getProgress(objectiveId): Int`         | Returns the current progress counter (0 if absent)                         |
| `setProgress(objectiveId, value)`       | Sets the progress counter for the given objective ID                       |
| `getString(objectiveId, field): String?`| Returns a stored string value, or `null` if absent                         |
| `setString(objectiveId, field, value)`  | Stores a string value under `"objectiveId:field"`                          |
| `removeString(objectiveId, field)`      | Removes a stored string value                                              |
| `getSteps(objectiveId): MutableSet<String>` | Returns the live step set (creates an empty one on first call)         |
| `addStep(objectiveId, step): Boolean`   | Adds a step; returns `true` if new                                         |
| `hasStep(objectiveId, step): Boolean`   | Returns `true` when the step has been recorded                             |
| `clearSteps(objectiveId)`               | Removes all steps for the objective                                        |
| `reset()`                               | Clears all completion, progress, string, step, line, and point data        |

---

## Objectives

### BingoObjective

**Package:** `net.trilleo.mc.plugins.tribingo.bingo`

Abstract base class for every bingo objective. Extend this class when the objective does not rely on a Bukkit event
(e.g. a snapshot check). For event-driven objectives, extend `EventBingoObjective` instead.

**Constructor parameters:**

| Parameter     | Type         | Description                                                 |
|:--------------|:-------------|:------------------------------------------------------------|
| `id`          | `String`     | Unique identifier; used for persistence and YAML lookups    |
| `name`        | `Component`  | Display name shown in GUIs and completion announcements     |
| `description` | `Component`  | Flavour text shown when a player clicks the cell in the GUI |
| `difficulty`  | `Difficulty` | `EASY`, `MEDIUM`, `HARD`, or `INSANE`                       |

**Methods to implement / override:**

| Method                                      | Required | Description                                                                              |
|:--------------------------------------------|:---------|:-----------------------------------------------------------------------------------------|
| `isCompletedBy(player, state): Boolean`     | Yes      | Returns `true` when the objective has been fulfilled                                     |
| `onReset(player, state)`                    | No       | Called on board reset; override to clear intermediate counters in `progressData`         |
| `displayItem(player, completed): ItemStack` | No       | Inventory GUI representation; default uses coloured stained-glass / lime concrete blocks |

**Default `displayItem` colour mapping:**

| State     | Material               |
|:----------|:-----------------------|
| Completed | `LIME_CONCRETE`        |
| `EASY`    | `GREEN_STAINED_GLASS`  |
| `MEDIUM`  | `YELLOW_STAINED_GLASS` |
| `HARD`    | `RED_STAINED_GLASS`    |
| `INSANE`  | `PURPLE_STAINED_GLASS` |

---

### EventBingoObjective

**Package:** `net.trilleo.mc.plugins.tribingo.bingo`

Abstract subclass of `BingoObjective` that also implements Bukkit's `Listener`. Use this for any objective that tracks a
Bukkit event (entity deaths, block breaks, crafting, etc.).

**Constructor parameters** (in addition to those inherited from `BingoObjective`):

| Parameter    | Type       | Description                                      |
|:-------------|:-----------|:-------------------------------------------------|
| `eventClass` | `Class<T>` | The Bukkit event class this objective listens to |

`BingoObjectiveRegistry.register` automatically calls `registerEvents` for every `EventBingoObjective`, so no manual
listener registration is needed.

**Abstract method:**

| Method                                                       | Description                                                                            |
|:-------------------------------------------------------------|:---------------------------------------------------------------------------------------|
| `onEvent(event: T, player: Player, state: BingoPlayerState)` | Called from the concrete `@EventHandler` after the player and state have been resolved |

**Typical implementation pattern:**

```kotlin
class MyObjective : EventBingoObjective<SomeBukkitEvent>(
    id = "my_objective",
    name = Component.text("My Objective"),
    description = Component.text("Do the thing."),
    difficulty = Difficulty.MEDIUM,
    eventClass = SomeBukkitEvent::class.java
) {
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onSomeEvent(event: SomeBukkitEvent) {
        val player = /* extract player from event */ ?: return
        val state = BingoManager.getActiveState(player, id) ?: return
        onEvent(event, player, state)
    }

    override fun onEvent(event: SomeBukkitEvent, player: Player, state: BingoPlayerState) {
        val progress = state.getProgress(id) + 1
        state.setProgress(id, progress)
        if (progress >= REQUIRED_COUNT) {
            BingoManager.checkCompletion(player, this)
        }
    }

    override fun isCompletedBy(player: Player, state: BingoPlayerState) =
        state.getProgress(id) >= REQUIRED_COUNT

    override fun onReset(player: Player, state: BingoPlayerState) {
        state.setProgress(id, 0)
    }
}
```

---

### MultiEventBingoObjective

**Package:** `net.trilleo.mc.plugins.tribingo.bingo`

Abstract subclass of `BingoObjective` that also implements Bukkit's `Listener`. Use this when an objective must listen
to **more than one Bukkit event type** simultaneously (e.g. tracking which weapon last hit an entity on
`EntityDamageByEntityEvent`, then detecting the kill on `EntityDeathEvent`).

Unlike `EventBingoObjective<T>`, this class has no generic parameter — add as many `@EventHandler`-annotated methods as
needed. The registry registers every `MultiEventBingoObjective` as a Bukkit event listener automatically.

```kotlin
@CustomObjective
class MyMultiEventObjective : MultiEventBingoObjective(
    id = "my_multi", name = ..., description = ..., difficulty = ...
) {
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onFirstEvent(event: FirstEvent) {
        ...
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onSecondEvent(event: SecondEvent) {
        ...
    }

    override fun isCompletedBy(player: Player, state: BingoPlayerState): Boolean = ...
}
```

---

### SequentialBingoObjective

**Package:** `net.trilleo.mc.plugins.tribingo.bingo`

A helper base class for objectives that require a fixed sequence of named steps to be completed **in order**. Extends
`BingoObjective` and implements `Listener`.

Declare the ordered step tokens in the `steps` constructor parameter. From within `@EventHandler` methods, call
`advanceStep(state, step)` — the method records the step only if it is the next expected one and returns `true` when the
final step is reached (signal to call `BingoManager.checkCompletion`). Call
`hasStep(state, step)` to guard handlers that should only fire after a prior step is complete.

`isCompletedBy` and `onReset` are already implemented by this class; do not override them unless you need additional
behaviour.

| Method / Property                       | Description                                                                              |
|:----------------------------------------|:-----------------------------------------------------------------------------------------|
| `steps: List<String>`                   | Ordered list of step tokens that must be completed in sequence                           |
| `advanceStep(state, step): Boolean`     | Records `step` if it is the expected next step; returns `true` when sequence is complete |
| `hasStep(state, step): Boolean`         | Returns `true` when `step` has already been recorded                                     |
| `isCompletedBy(player, state): Boolean` | Implemented; returns `true` when all steps are recorded                                  |
| `onReset(player, state)`                | Implemented; clears the step set from `state.stepData`                                   |

---

### Built-in Objective Types

All built-in implementations live in `net.trilleo.mc.plugins.tribingo.bingo.objectives`.

| Class                     | YAML `type`       | Bukkit Event             | Tracked field(s)                                                             |
|:--------------------------|:------------------|:-------------------------|:-----------------------------------------------------------------------------|
| `KillEntityObjective`     | `kill_entity`     | `EntityDeathEvent`       | Kills of a specific `EntityType`                                             |
| `MineBlockObjective`      | `mine_block`      | `BlockBreakEvent`        | Blocks of a specific `Material` mined                                        |
| `PlaceBlockObjective`     | `place_block`     | `BlockPlaceEvent`        | Blocks of a specific `Material` placed                                       |
| `CraftItemObjective`      | `craft_item`      | `CraftItemEvent`         | Craft operations (optionally filtered by `Material`)                         |
| `FishItemObjective`       | `fish_item`       | `PlayerFishEvent`        | Fish caught (or all rod catches if `countAll = true`)                        |
| `EatFoodObjective`        | `eat_food`        | `PlayerItemConsumeEvent` | Food items consumed (optionally filtered by `Material`)                      |
| `EnchantItemObjective`    | `enchant_item`    | `EnchantItemEvent`       | Enchanting operations (optionally filtered by enchantment)                   |
| `TravelDistanceObjective` | `travel_distance` | `PlayerMoveEvent`        | Block-boundary crossings (in any direction)                                  |
| `BreedMobObjective`       | `breed_mob`       | `EntityBreedEvent`       | Breeding events triggered by the player (optionally filtered by entity type) |
| `TameEntityObjective`     | `tame_entity`     | `EntityTameEvent`        | Taming events (optionally filtered by entity type)                           |

All built-in objectives listen at `EventPriority.MONITOR` with `ignoreCancelled = true`.

---

### SecretBingoObjective

**Package:** `net.trilleo.mc.plugins.tribingo.bingo`

A **decorator / wrapper** that wraps any existing `BingoObjective` subclass to mark it as a "secret" objective. Secret
objectives hide their description from players until they personally complete the objective. Hints are revealed
progressively to all players as the game timer elapses.

**Constructor parameters:**

| Parameter | Type             | Description                                                         |
|:----------|:-----------------|:--------------------------------------------------------------------|
| `inner`   | `BingoObjective` | The real objective being wrapped (any subclass)                     |
| `hints`   | `List<String>`   | Ordered list of hint strings revealed progressively during the game |

**Design:**

- Extends `BingoObjective` with `id`, `name`, `description`, and `difficulty` all delegated to the inner objective
- `isCompletedBy(player, state)` and `onReset(player, state)` delegate to the inner objective
- The wrapper itself is **not** a Bukkit `Listener` — the inner objective retains its event listener registration
- `BingoObjectiveRegistry` checks if the inner objective `is Listener` and registers it accordingly

**Display behaviour (`displayItem`):**

| State                  | Material                |
|:-----------------------|:------------------------|
| Completed              | `LIME_CONCRETE`         |
| Not completed (secret) | `MAGENTA_STAINED_GLASS` |

**Lore layout:**

```
Difficulty: <colour>Easy/Medium/Hard/Insane
⚡ Secret

[If completed: real description]
[If not completed: "*****"]

[Revealed hints, if any:]
  Hint 1: <text>
  Hint 2: <text>

✓ Completed / ○ Not yet completed
```

**Helper check:**

```kotlin
if (objective is SecretBingoObjective) {
    ...
}
```

---

### SecretHintManager

**Package:** `net.trilleo.mc.plugins.tribingo.bingo`

Singleton that manages the progressive reveal of hints for all `SecretBingoObjective` instances on the current board.
Hints are revealed **globally** — all players see the same hints at the same time.

**Reveal schedule:**

For an objective with N hints, the reveal thresholds are at fractions `1/(N+1), 2/(N+1), ..., N/(N+1)` of the total
timer duration.

| Hints | Reveal thresholds  | Example (60-min timer)         |
|:------|:-------------------|:-------------------------------|
| 1     | 50%                | 30 min elapsed                 |
| 2     | 33%, 67%           | 20 min, 40 min                 |
| 3     | 25%, 50%, 75%      | 15 min, 30 min, 45 min         |
| 4     | 20%, 40%, 60%, 80% | 12 min, 24 min, 36 min, 48 min |

**Key methods:**

| Method                                      | Description                                                          |
|:--------------------------------------------|:---------------------------------------------------------------------|
| `init(plugin)`                              | Stores the plugin reference; call once during startup                |
| `tick(totalSeconds, remaining)`             | Called every countdown tick; checks if new hints should be revealed  |
| `getRevealedHints(objective): List<String>` | Returns the subset of hints currently visible for a secret objective |
| `getRevealedHintCount(objectiveId): Int`    | Returns the count of revealed hints for a given objective ID         |
| `reset()`                                   | Clears all revealed hint state (called on game end/reset/shutdown)   |

**Chat notification:**

When a new hint is revealed, all online players receive:

```
[Bingo] A new hint is available for a secret objective! Check the board.
```

**Lifecycle:**

- Initialised in `Main.onEnable` after objective loading: `SecretHintManager.init(this)`
- Ticked from `BingoManager`'s countdown each second
- Reset on game stop, game reset, timer expiry, board-full win, and server shutdown

**Edge cases:**

- **No timer / timer is 0**: No hints are revealed (elapsed fraction stays at 0)
- **Player joins mid-game**: They see whatever hints have been globally revealed when they open the board
- **Objective completed before all hints revealed**: That player sees the full description; others still see only
  revealed hints
- **Server restart mid-game**: Hint state is cleared; game resets to INACTIVE on rehydration anyway

---

## Registry & Loading

### BingoObjectiveRegistry

**Package:** `net.trilleo.mc.plugins.tribingo.bingo.registry`

In-memory registry of all available `BingoObjective` instances. Objectives are stored in insertion order.

| Method                                              | Description                                                                                                                                     |
|:----------------------------------------------------|:------------------------------------------------------------------------------------------------------------------------------------------------|
| `init(plugin)`                                      | Stores the plugin reference; must be called before `register`                                                                                   |
| `register(objective)`                               | Adds the objective to the registry; auto-registers any `Listener` implementation as a Bukkit listener; duplicate IDs are skipped with a warning |
| `unregister(id)`                                    | Removes an objective by ID (listener remains active but is a no-op while the game is inactive)                                                  |
| `getAll(): List<BingoObjective>`                    | Returns all registered objectives in insertion order                                                                                            |
| `getByDifficulty(difficulty): List<BingoObjective>` | Returns objectives filtered by `Difficulty`                                                                                                     |
| `get(id): BingoObjective?`                          | Returns the objective with the given ID, or `null`                                                                                              |
| `clear()`                                           | Removes all objectives; intended for tests or full plugin reloads                                                                               |

---

### CodeObjectiveLoader

**Package:** `net.trilleo.mc.plugins.tribingo.bingo.registry`

Discovers and registers code objectives — concrete `BingoObjective` subclasses annotated with `@CustomObjective` — from
one or more designated packages.

| Method                                    | Description                                                                                  |
|:------------------------------------------|:---------------------------------------------------------------------------------------------|
| `load(plugin, registry, vararg packages)` | Scans the given packages, instantiates annotated classes, and registers them with `registry` |

The default package scanned by `Main.onEnable` is
`net.trilleo.mc.plugins.tribingo.bingo.custom`. For each class the loader tries a companion `BingoObjectiveFactory`
first, then a no-arg constructor. Classes that satisfy neither are skipped with a warning.

See [Writing a Code Objective](#writing-a-code-objective) for full usage documentation and worked examples.

---

### YamlObjectiveLoader

**Package:** `net.trilleo.mc.plugins.tribingo.bingo.registry`

Loads user-defined objectives from `bingo_objectives.yml` in the plugin's data folder and registers them with
`BingoObjectiveRegistry`.

| Method                               | Description                                                                                 |
|:-------------------------------------|:--------------------------------------------------------------------------------------------|
| `load(plugin, registry)`             | Saves the default `bingo_objectives.yml` if absent, parses it, and registers all objectives |
| `registerTypeHandler(type, handler)` | Adds a custom type handler; must be called **before** `load`                                |

**Custom type handler example:**

```kotlin
YamlObjectiveLoader.registerTypeHandler("my_type") { entry ->
    MyObjective(
        id = entry["id"].toString(),
        name = MiniMessage.miniMessage().deserialize(entry["name"].toString()),
        description = MiniMessage.miniMessage().deserialize(entry["description"].toString()),
        difficulty = Difficulty.valueOf((entry["difficulty"] as? String ?: "EASY").uppercase()),
        myParam = entry["my_param"].toString()
    )
}
```

---

### bingo_objectives.yml Format

Located at `<server>/plugins/TriBingo/bingo_objectives.yml`. The file is created from the bundled default on the first
run and is **not** overwritten on subsequent starts.

**Top-level structure:**

```yaml
objectives:
  - id: <string>
    type: <string>
    difficulty: EASY | MEDIUM | HARD | INSANE
    name: "<MiniMessage string>"
    description: "<MiniMessage string>"
    # type-specific parameters ...
```

**Parameters per type:**

| `type`            | Required parameters    | Optional parameters                               |
|:------------------|:-----------------------|:--------------------------------------------------|
| `kill_entity`     | `entity_type`, `count` | —                                                 |
| `mine_block`      | `material`, `count`    | —                                                 |
| `place_block`     | `material`, `count`    | —                                                 |
| `craft_item`      | `count`                | `material` (omit to match any crafted item)       |
| `fish_item`       | `count`                | `count_all: true` (count fish + reeled entities)  |
| `eat_food`        | `count`                | `material` (omit to match any food)               |
| `enchant_item`    | `count`                | `enchantment` (Minecraft key, e.g. `sharpness`)   |
| `travel_distance` | `blocks`               | —                                                 |
| `breed_mob`       | `count`                | `entity_type` (omit to match any breedable mob)   |
| `tame_entity`     | `count`                | `entity_type` (omit to match any tameable entity) |

**Field details:**

- `id` — Unique string. **Do not change after a game has been persisted** — IDs are stored in `serverdata.json` and used
  to rehydrate the board.
- `name` / `description` — Parsed with [MiniMessage](https://docs.advntr.dev/minimessage/format.html). Tags like`<red>`,
  `<bold>`, `<gradient:gold:yellow>` are fully supported.
- `entity_type` — Must match a valid `org.bukkit.entity.EntityType` name (e.g. `CREEPER`, `ZOMBIE`).
- `material` — Must match a valid `org.bukkit.Material` name (e.g. `IRON_ORE`, `BREAD`).
- `enchantment` — Must be a valid Minecraft namespaced key without the `minecraft:` prefix (e.g. `sharpness`,`mending`).

**Example entry:**

```yaml
objectives:
  - id: kill_creeper
    type: kill_entity
    difficulty: EASY
    name: "<green>Creeper Killer"
    description: "<gray>Kill a Creeper."
    entity_type: CREEPER
    count: 1
```

---

## Persistence

### BingoServerData

**Package:** `net.trilleo.mc.plugins.tribingo.data`

Extends `ServerData` to persist the active game to `serverdata.json` via `ServerDataManager`.

| Property / Method                          | JSON key                | Type         | Description                                         |
|:-------------------------------------------|:------------------------|:-------------|:----------------------------------------------------|
| `boardSize`                                | `bingo_board_size`      | `Int`        | Side-length of the persisted board; `0` = no game   |
| `gameStateName`                            | `bingo_game_state`      | `String`     | Serialised `GameState` name                         |
| `gameDifficultyName`                       | `bingo_game_difficulty` | `String`     | Serialised `GameDifficulty` name (default `MEDIUM`) |
| `boardLayout`                              | `bingo_board_layout`    | `JsonArray`  | Ordered list of objective IDs (size × size entries) |
| `timerSeconds`                             | `bingo_timer_seconds`   | `Int`        | Countdown duration in seconds; default `3600`       |
| `savePlayerStates(states)`                 | `bingo_player_states`   | `JsonObject` | Serialises all player states                        |
| `loadPlayerStates(): Map<UUID, PersistedPlayerState>` | `bingo_player_states` | `JsonObject` | Deserialises previously saved player states  |
| `clearGameData()`                          | —                       | —            | Removes all bingo keys from the backing JSON        |

**Player-state JSON structure** (stored per UUID):

```json
{
  "c": [0, 3, 7],
  "p": {
    "kill_creeper": 1,
    "mine_dirt": 14
  },
  "l": ["row_0", "col_3"],
  "pts": 12,
  "sd": {
    "my_objective:last_weapon": "DIAMOND_SWORD"
  },
  "ssd": {
    "sequential_obj": ["step_1", "step_2"]
  }
}
```

| Key   | Description                                                        |
|:------|:-------------------------------------------------------------------|
| `c`   | Completed cell indices                                             |
| `p`   | Progress counters keyed by objective ID                            |
| `l`   | Completed line keys that have received bonus points                |
| `pts` | Accumulated point total                                            |
| `sd`  | String data for code objectives (optional, omitted when empty)     |
| `ssd` | Step data for sequential objectives (optional, omitted when empty) |

**Rehydration:** On `BingoManager.init`, the system reads `boardSize` and `boardLayout`, looks up each objective ID in
the registry, reconstructs the `BingoBoard` and `BingoGame`, then loads player states. If any objective ID is missing
from the registry, rehydration is aborted and a warning is logged.

---

## Configuration

All bingo settings live in `config.yml` under the `bingo` section. Reload at runtime with `/tb reload`.

| YAML key                     | Config property                    | Type      | Default | Description                                                                            |
|:-----------------------------|:-----------------------------------|:----------|:--------|:---------------------------------------------------------------------------------------|
| `bingo.announce-completions` | `PluginConfig.announceCompletions` | `Boolean` | `true`  | Broadcasts a message to all players when any player completes a cell                   |
| `bingo.points.objective`     | `PluginConfig.objectivePoints`     | `Int`     | `1`     | Points awarded per cell completion                                                     |
| `bingo.points.line`          | `PluginConfig.linePoints`          | `Int`     | `3`     | Bonus points for completing a row or column                                            |
| `bingo.points.diagonal`      | `PluginConfig.diagonalPoints`      | `Int`     | `5`     | Bonus points for completing a diagonal                                                 |

> **Note:** The board size is fixed at `5×5` (defined by `BingoBoard.SIZE`). The `PluginConfig.boardDefaultSize` property
> always returns `5` regardless of configuration.

**Example `config.yml` section:**

```yaml
bingo:
  announce-completions: true
  points:
    objective: 1
    line: 3
    diagonal: 5
```

---

## Board Randomizer

The board randomizer system controls how objectives are selected and placed on the board based on the game's difficulty.
Each `GameDifficulty` (defined in `net.trilleo.mc.plugins.tribingo.enums.GameDifficulty`)
maps to a `BoardRandomizer` implementation via the `BoardRandomizerRegistry`.

### Game Difficulty vs Objective Difficulty

- **`GameDifficulty`** (`EASY`, `MEDIUM`, `HARD`): Set per-game; determines which randomizer strategy is used.
- **`Difficulty`** (`EASY`, `MEDIUM`, `HARD`, `INSANE`): Set per-objective; describes how challenging a single objective
  is.

### Built-in Randomizers

| Game Difficulty | Randomizer Class        | Distribution (approx.)                                | Constraints                                  |
|:----------------|:------------------------|:------------------------------------------------------|:---------------------------------------------|
| `EASY`          | `EasyBoardRandomizer`   | 60% easy, 30% medium, 10% hard, 0% insane             | None                                         |
| `MEDIUM`        | `MediumBoardRandomizer` | 30% easy, 40% medium, 25% hard, 5% insane (~1 cell)   | None                                         |
| `HARD`          | `HardBoardRandomizer`   | 10% easy, 25% medium, 45% hard, 20% insane (~5 cells) | Each diagonal guaranteed ≥1 insane objective |

All randomizers fall back gracefully when insufficient objectives of a target difficulty exist — they draw from adjacent
difficulty pools rather than failing.

### Adding a New Game Difficulty

1. Add a new entry to the `GameDifficulty` enum.
2. Create a class implementing `BoardRandomizer`.
3. Register it in `BoardRandomizerRegistry`:
   ```kotlin
   BoardRandomizerRegistry.register(GameDifficulty.MY_DIFFICULTY, MyRandomizer())
   ```

### Changing Randomizer Logic

To change the randomization behaviour for an existing difficulty, either:

- Replace the registered randomizer: `BoardRandomizerRegistry.register(GameDifficulty.HARD, MyCustomHardRandomizer())`
- Or modify the existing randomizer class directly.

### Usage

Players with `tribingo.bingo.manage` permission can specify difficulty when refreshing:

```
/bingo refresh easy
/bingo refresh medium
/bingo refresh hard
```

If no difficulty is specified, the current game's difficulty is preserved (defaults to `MEDIUM` for new games).

---

## Commands

Bingo commands are available as the standalone `/bingo` command, dispatched through `BingoCommand`.

### `/bingo <sub-command>`

| Sub-command            | Permission              | Who can use | Description                                                                                                           |
|:-----------------------|:------------------------|:------------|:----------------------------------------------------------------------------------------------------------------------|
| `board`                | *(none)*                | Players     | Opens the `BingoBoardGUI` for the sender                                                                              |
| `start`                | `tribingo.bingo.manage` | Any sender  | Starts the current game (must be `INACTIVE`); also starts the countdown                                               |
| `stop`                 | `tribingo.bingo.manage` | Any sender  | Ends the current game without a winner (must be `ACTIVE`); cancels the countdown                                      |
| `reset`                | `tribingo.bingo.manage` | Any sender  | Resets all player progress; transitions game back to `INACTIVE`                                                       |
| `refresh [difficulty]` | `tribingo.bingo.manage` | Any sender  | Picks new random objectives for the current board (must be `INACTIVE`). Optional difficulty: `easy`, `medium`, `hard` |
| `time <h> <m> <s>`     | `tribingo.bingo.manage` | Any sender  | Sets the countdown duration (hours, minutes, seconds); stored in server data                                          |
| `status`               | *(none)*                | Any sender  | Shows board size, game state, number of objectives, active players, and timer setting                                 |

Tab-completion is supported: typing `/bingo ` shows all sub-commands; typing `/bingo time ` shows example values.

---

## Countdown

When a game starts (via `/bingo start`), a server-wide countdown begins. The remaining time is sent to **every online
player** as an action bar message once per second in the format `⏱ Bingo: MM:SS` (or
`HH:MM:SS` when hours remain). Players who join mid-game will see the countdown on the next tick.

### Timer setting

The countdown duration is stored in `serverdata.json` under the key `bingo_timer_seconds` and defaults to **3 600
seconds (1 hour)**. Change it with:

```
/bingo time <hours> <minutes> <seconds>
```

For example, `/bingo time 0 30 0` sets the timer to 30 minutes. The setting persists across server restarts and cannot
be changed while a game is active.

### Timer expiry

When the countdown reaches zero, `BingoManager.onTimerExpired` is called:

1. The player with the **highest point total** across all recorded `BingoPlayerState`s is declared winner.
2. If multiple players have the same top score, the first one returned by the state map is used.
3. If no player has accumulated any points, the game ends without a winner.

The winner broadcast uses `BingoGame.end(winner, winnerPoints, winnerName)`. The `winnerName` fallback ensures the
announcement works even when the top scorer is offline at the moment the timer expires.

### Server stop during active game

If the server is stopped while a game is `ACTIVE`, `BingoManager.save()` clears the game data rather than persisting the
interrupted state. On the next server start, `BingoManager.init` creates a fresh `INACTIVE`
game instead of rehydrating the stale one.

---

## Board GUI

**Class:** `BingoBoardGUI` — `net.trilleo.mc.plugins.tribingo.guis`
**GUI ID:** `bingo_board`

Opened with `/bingo board` (players only) or via `GUIManager.open(player, "bingo_board")`.

### Layout

The board is centred within a 6-row (54-slot) double-chest inventory. All unoccupied slots are filled with dark glass
panes.

```
vertPad  = (6 - SIZE) / 2    // rows above the board (always 0 for 5×5)
horizPad = (9 - SIZE) / 2    // columns to the left of the board (always 2 for 5×5)
slot(row, col) = (vertPad + row) * 9 + (horizPad + col)
```

The 5×5 board (`vertPad = 0`, `horizPad = 2`) occupies slots 2–6, 11–15, 20–24, 29–33, 38–42.

Indicator panes use the remaining U-shape around the board:

- Row indicators: slots `1, 10, 19, 28, 37`
- Column indicators: slots `47-51`
- Anti diagonal (`↗`): slot `46` (bottom-left indicator)
- Main diagonal (`↘`): slot `52` (bottom-right indicator)

### Cell Appearance

Each cell is rendered by `BingoObjective.displayItem(player, completed)`:

- **Uncompleted:** coloured stained-glass block whose colour reflects difficulty (green/yellow/red/purple).
- **Completed:** lime concrete block.
- Lore shows: difficulty, description, progress counter (for count-based objectives), and completion status.

### Interaction

Clicking a cell sends the objective's name, description, and completion status to the player in chat. All inventory
clicks are cancelled to prevent item theft.

### Live Updates

`BingoBoardGUI.refreshFor(player)` repopulates the inventory in-place. It is called automatically by
`BingoManager.checkCompletion` whenever a player completes a cell.

---

## Writing a Custom Objective

### Step 1 — Create the class

Place the class anywhere in the project (it does **not** need to be in a specific package for auto-registration;
objectives are registered explicitly via `BingoObjectiveRegistry.register`).

```kotlin
import net.kyori.adventure.text.Component
import net.trilleo.mc.plugins.tribingo.bingo.BingoManager
import net.trilleo.mc.plugins.tribingo.bingo.BingoPlayerState
import net.trilleo.mc.plugins.tribingo.bingo.EventBingoObjective
import net.trilleo.mc.plugins.tribingo.enums.Difficulty
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.player.PlayerBedEnterEvent

class SleepObjective : EventBingoObjective<PlayerBedEnterEvent>(
    id = "sleep_in_bed",
    name = Component.text("Good Night"),
    description = Component.text("Sleep in a bed."),
    difficulty = Difficulty.EASY,
    eventClass = PlayerBedEnterEvent::class.java
) {

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onBedEnter(event: PlayerBedEnterEvent) {
        if (event.bedEnterResult != PlayerBedEnterEvent.BedEnterResult.OK) return
        val state = BingoManager.getActiveState(event.player, id) ?: return
        onEvent(event, event.player, state)
    }

    override fun onEvent(event: PlayerBedEnterEvent, player: Player, state: BingoPlayerState) {
        BingoManager.checkCompletion(player, this)
    }

    override fun isCompletedBy(player: Player, state: BingoPlayerState): Boolean =
        state.isCompleted(
            BingoManager.currentGame?.board?.cells?.find { it.objective.id == id }?.cellIndex ?: return false
        )
}
```

### Step 2 — Register it

Call `BingoObjectiveRegistry.register` **after** `BingoObjectiveRegistry.init` and **before**
`YamlObjectiveLoader.load` (or anywhere before `BingoManager.init`) so it is available for board generation:

```kotlin
BingoObjectiveRegistry.init(plugin)
BingoObjectiveRegistry.register(SleepObjective())   // ← register your custom objective
YamlObjectiveLoader.load(plugin, BingoObjectiveRegistry)
BingoManager.init(plugin)
```

### Step 3 — (Optional) Add a YAML type handler

If you want server operators to configure multiple variants of your objective from `bingo_objectives.yml`, register a
type handler **before** `YamlObjectiveLoader.load`:

```kotlin
YamlObjectiveLoader.registerTypeHandler("sleep_in_bed") { entry ->
    SleepObjective(
        id = entry["id"].toString(),
        name = MiniMessage.miniMessage().deserialize(entry["name"]?.toString() ?: "Sleep"),
        description = MiniMessage.miniMessage().deserialize(entry["description"]?.toString() ?: "Sleep in a bed."),
        difficulty = Difficulty.valueOf((entry["difficulty"] as? String ?: "EASY").uppercase())
    )
}
```

---

## Writing a Code Objective

Code objectives are Kotlin classes that live inside the plugin JAR and are discovered automatically at startup. They
behave identically to YAML-loaded objectives from the perspective of the board, the GUI, and the persistence layer — the
only difference is that their completion logic is written in Kotlin rather than configured in `bingo_objectives.yml`.

Use code objectives when you need behaviour that YAML types cannot express:
tracking which weapon was used, enforcing an ordered sequence of actions, listening to multiple event types at once, or
maintaining complex per-player state.

---

### Quick-start

1. Choose the right base class (see the table in the next section).
2. Annotate the class with `@CustomObjective`.
3. Place the class in `net.trilleo.mc.plugins.tribingo.bingo.custom` (or any additional package you pass to
   `CodeObjectiveLoader.load`).
4. Provide a no-arg constructor **or** a companion object that implements
   `BingoObjectiveFactory`.

That's it — `CodeObjectiveLoader` registers the objective automatically, including Bukkit event listener registration if
applicable.

---

### Choosing a Base Class

| Base class                 | When to use                                                                            |
|:---------------------------|:---------------------------------------------------------------------------------------|
| `BingoObjective`           | Snapshot-based check (e.g. "player currently has ≥ 20 hearts"). No events needed.      |
| `EventBingoObjective<T>`   | Reacts to exactly one Bukkit event type. The generic parameter `T` is the event class. |
| `MultiEventBingoObjective` | Reacts to multiple Bukkit event types. Add as many `@EventHandler` methods as needed.  |
| `SequentialBingoObjective` | Must be completed by performing a fixed sequence of named steps in the correct order.  |

All event-driven classes (`EventBingoObjective`, `MultiEventBingoObjective`,
`SequentialBingoObjective`) implement Bukkit's `Listener`. The registry registers them as event listeners automatically.

---

### The `@CustomObjective` Annotation

`@CustomObjective` (in `net.trilleo.mc.plugins.tribingo.bingo.annotation`)
marks a concrete `BingoObjective` subclass for auto-discovery by
`CodeObjectiveLoader`. Classes without this annotation are ignored even if they are in the scanned package.

```kotlin
import net.trilleo.mc.plugins.tribingo.bingo.annotation.CustomObjective

@CustomObjective
class MySleepObjective : EventBingoObjective<PlayerBedEnterEvent>(...) { ... }
```

---

### Construction Strategies

`CodeObjectiveLoader` instantiates each annotated class using one of two strategies (companion factory is tried first):

#### 1 — No-arg constructor (recommended for simple objectives)

Hardcode all parameters in the class body:

```kotlin
@CustomObjective
class SleepObjective : EventBingoObjective<PlayerBedEnterEvent>(
    id = "sleep_in_bed",
    name = Component.text("Good Night"),
    description = Component.text("Sleep in a bed."),
    difficulty = Difficulty.EASY,
    eventClass = PlayerBedEnterEvent::class.java
) {
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onBedEnter(event: PlayerBedEnterEvent) {
        if (event.bedEnterResult != PlayerBedEnterEvent.BedEnterResult.OK) return
        val state = BingoManager.getActiveState(event.player, id) ?: return
        onEvent(event, event.player, state)
    }

    override fun onEvent(event: PlayerBedEnterEvent, player: Player, state: BingoPlayerState) {
        BingoManager.checkCompletion(player, this)
    }

    override fun isCompletedBy(player: Player, state: BingoPlayerState): Boolean =
        state.isCompleted(
            BingoManager.currentGame?.board?.cells?.find { it.objective.id == id }?.cellIndex ?: return false
        )
}
```

#### 2 — Companion factory (`BingoObjectiveFactory`)

Use a companion object implementing `BingoObjectiveFactory` when the objective needs construction-time parameters that
cannot be hardcoded (e.g. a `Material` constant, a count from a config value, or a reference to another service):

```kotlin
@CustomObjective
class KillWithDiamondSwordObjective(
    private val requiredMaterial: Material
) : MultiEventBingoObjective(
    id = "kill_zombie_diamond_sword",
    name = Component.text("Diamond Slayer"),
    description = Component.text("Kill a zombie with a diamond sword."),
    difficulty = Difficulty.MEDIUM
) {
    companion object : BingoObjectiveFactory {
        override fun create() = KillWithDiamondSwordObjective(Material.DIAMOND_SWORD)
    }
    // ... @EventHandler methods
}
```

---

### Extended Per-Player State

Beyond the integer `progressData` map inherited from `BingoObjective`,
`BingoPlayerState` exposes two additional state stores for code objectives:

#### `stringData` — arbitrary string values

Keyed by a compound `"objectiveId:fieldName"` string. Use the typed accessors rather than accessing the map directly.

| Method                                   | Description                                   |
|:-----------------------------------------|:----------------------------------------------|
| `getString(objectiveId, field): String?` | Returns the stored value, or `null` if absent |
| `setString(objectiveId, field, value)`   | Stores a string value                         |
| `removeString(objectiveId, field)`       | Removes a stored value                        |

#### `stepData` — ordered sets of completed step tokens

Keyed by `objectiveId`. Each set is a `LinkedHashSet` that preserves insertion order, making it suitable for sequential
objectives.

| Method                                      | Description                                                           |
|:--------------------------------------------|:----------------------------------------------------------------------|
| `getSteps(objectiveId): MutableSet<String>` | Returns the live step set (creates an empty one on first call)        |
| `addStep(objectiveId, step): Boolean`       | Adds a step; returns `true` if it was new, `false` if already present |
| `hasStep(objectiveId, step): Boolean`       | Returns `true` when the step has already been recorded                |
| `clearSteps(objectiveId)`                   | Removes all steps for the objective (call from `onReset`)             |

All three stores (`progressData`, `stringData`, `stepData`) are serialised to `serverdata.json` and rehydrated on server
restart, so progress survives restarts.

---

### Worked Examples

#### Example 1 — Kill a zombie with a sword (`MultiEventBingoObjective`)

This objective must track two events: a damage event (to record which weapon hit the zombie last) and a death event (to
check whether that weapon was a sword).

```kotlin
package net.trilleo.mc.plugins.tribingo.bingo.custom

import net.kyori.adventure.text.Component
import net.trilleo.mc.plugins.tribingo.bingo.BingoManager
import net.trilleo.mc.plugins.tribingo.bingo.BingoPlayerState
import net.trilleo.mc.plugins.tribingo.bingo.MultiEventBingoObjective
import net.trilleo.mc.plugins.tribingo.bingo.annotation.CustomObjective
import net.trilleo.mc.plugins.tribingo.enums.Difficulty
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDeathEvent
import java.util.UUID

@CustomObjective
class KillZombieWithSwordObjective : MultiEventBingoObjective(
    id = "kill_zombie_with_sword",
    name = Component.text("Undead Swordsman"),
    description = Component.text("Kill a zombie using any sword."),
    difficulty = Difficulty.MEDIUM
) {
    // Tracks whether the last hit on a given entity was with a sword
    private val lastHitWithSword = mutableSetOf<UUID>()

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onDamage(event: EntityDamageByEntityEvent) {
        val player = event.damager as? Player ?: return
        val entityId = event.entity.uniqueId
        val weapon = player.inventory.itemInMainHand.type
        if (weapon.name.endsWith("_SWORD")) {
            lastHitWithSword.add(entityId)
        } else {
            lastHitWithSword.remove(entityId)
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onDeath(event: EntityDeathEvent) {
        if (event.entity.type != EntityType.ZOMBIE) return
        val player = event.entity.killer ?: return
        if (!lastHitWithSword.remove(event.entity.uniqueId)) return
        val state = BingoManager.getActiveState(player, id) ?: return
        state.setString(id, "done", "true")
        BingoManager.checkCompletion(player, this)
    }

    override fun isCompletedBy(player: Player, state: BingoPlayerState): Boolean =
        state.getString(id, "done") == "true"

    override fun onReset(player: Player, state: BingoPlayerState) =
        state.removeString(id, "done")
}
```

---

#### Example 2 — Deliver a pig to the Nether (`MultiEventBingoObjective`)

This objective flags completion when a pig entity enters the Nether dimension.

```kotlin
package net.trilleo.mc.plugins.tribingo.bingo.custom

import net.kyori.adventure.text.Component
import net.trilleo.mc.plugins.tribingo.bingo.BingoManager
import net.trilleo.mc.plugins.tribingo.bingo.BingoPlayerState
import net.trilleo.mc.plugins.tribingo.bingo.MultiEventBingoObjective
import net.trilleo.mc.plugins.tribingo.bingo.annotation.CustomObjective
import net.trilleo.mc.plugins.tribingo.enums.Difficulty
import org.bukkit.World
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.entity.EntityPortalEnterEvent

@CustomObjective
class DeliverPigToNetherObjective : MultiEventBingoObjective(
    id = "deliver_pig_nether",
    name = Component.text("Pork Express"),
    description = Component.text("Push a pig through a Nether portal."),
    difficulty = Difficulty.HARD
) {
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPortalEnter(event: EntityPortalEnterEvent) {
        if (event.entity.type != EntityType.PIG) return
        // The event fires while the pig is still in its source dimension.
        // Skip if the pig is already in the Nether (it would be returning to the
        // Overworld, not going TO the Nether).
        if (event.entity.world.environment == World.Environment.NETHER) return
        // Award the nearest online player within 10 blocks who is in the same world
        val pig = event.entity
        val nearbyPlayer = pig.world.getNearbyPlayers(pig.location, 10.0).firstOrNull() ?: return
        val state = BingoManager.getActiveState(nearbyPlayer, id) ?: return
        state.setString(id, "done", "true")
        BingoManager.checkCompletion(nearbyPlayer, this)
    }

    override fun isCompletedBy(player: Player, state: BingoPlayerState): Boolean =
        state.getString(id, "done") == "true"

    override fun onReset(player: Player, state: BingoPlayerState) =
        state.removeString(id, "done")
}
```

---

#### Example 3 — Craft a table, place it, craft again in order (`SequentialBingoObjective`)

`SequentialBingoObjective` automatically handles the step-tracking and
`isCompletedBy`/`onReset` implementations. You only need to add
`@EventHandler` methods and call `advanceStep`.

```kotlin
package net.trilleo.mc.plugins.tribingo.bingo.custom

import net.kyori.adventure.text.Component
import net.trilleo.mc.plugins.tribingo.bingo.BingoManager
import net.trilleo.mc.plugins.tribingo.bingo.BingoPlayerState
import net.trilleo.mc.plugins.tribingo.bingo.SequentialBingoObjective
import net.trilleo.mc.plugins.tribingo.bingo.annotation.CustomObjective
import net.trilleo.mc.plugins.tribingo.enums.Difficulty
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.inventory.CraftItemEvent

@CustomObjective
class CraftPlaceCraftObjective : SequentialBingoObjective(
    id = "craft_place_craft",
    name = Component.text("Crafty Crafter"),
    description = Component.text("Craft a crafting table, place it, then craft something on it."),
    difficulty = Difficulty.MEDIUM,
    steps = listOf("crafted_table", "placed_table", "crafted_on_table")
) {
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onCraft(event: CraftItemEvent) {
        val player = event.whoClicked as? Player ?: return
        val state = BingoManager.getActiveState(player, id) ?: return

        when {
            // Step 1: craft the table (only if not yet done)
            event.recipe.result.type == Material.CRAFTING_TABLE && !hasStep(state, "crafted_table") ->
                advanceStep(state, "crafted_table")

            // Step 3: craft anything after placing the table
            hasStep(state, "placed_table") ->
                if (advanceStep(state, "crafted_on_table"))
                    BingoManager.checkCompletion(player, this)
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPlace(event: BlockPlaceEvent) {
        if (event.blockPlaced.type != Material.CRAFTING_TABLE) return
        val state = BingoManager.getActiveState(event.player, id) ?: return
        // Step 2: place the crafting table
        advanceStep(state, "placed_table")
    }
}
```

---

### Displaying Progress (`buildProgressItem`)

Override `displayItem` and call `buildProgressItem` to add custom lore lines without duplicating the standard
difficulty/description/completion layout:

```kotlin
override fun displayItem(player: Player, completed: Boolean): ItemStack {
    val progress = BingoManager.currentGame
        ?.getOrCreateState(player.uniqueId)?.getProgress(id) ?: 0
    return buildProgressItem(
        player, completed,
        Component.text("Progress: $progress/$count", NamedTextColor.YELLOW)
    )
}
```

The helper renders:

```
Difficulty: <difficulty>

<description>

<progressLines>   ← shown only when not completed; falls back to "○ Not yet completed" when empty
```

When `completed` is `true` the progress lines are omitted and `"✓ Completed"` is shown instead.

---

### Scanning Additional Packages

`CodeObjectiveLoader.load` accepts a `vararg packageNames` parameter. Specify additional packages in `Main.onEnable` if
your objectives are spread across multiple locations:

```kotlin
CodeObjectiveLoader.load(
    this, BingoObjectiveRegistry,
    "net.trilleo.mc.plugins.tribingo.bingo.custom",
    "com.example.myplugin.objectives"
)
```

---

## Writing a Secret Objective

Secret objectives use the `SecretBingoObjective` wrapper to hide an objective's description and reveal hints over time.
Any existing `BingoObjective` subclass can be made secret by wrapping it.

---

### Quick-start

1. Create your inner objective (any `BingoObjective` subclass — event-driven, sequential, etc.).
2. Create a class that extends `SecretBingoObjective`, passing the inner objective and a list of hints.
3. Annotate the **wrapper** class with `@CustomObjective` (not the inner class).
4. Place the file in `net.trilleo.mc.plugins.tribingo.bingo.custom`.

That's it — the registry handles listener registration for the inner objective automatically.

---

### Basic Example: Wrapping an EventBingoObjective

```kotlin
package net.trilleo.mc.plugins.tribingo.bingo.custom

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.trilleo.mc.plugins.tribingo.bingo.*
import net.trilleo.mc.plugins.tribingo.bingo.annotation.CustomObjective
import net.trilleo.mc.plugins.tribingo.enums.Difficulty
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.entity.EntityDeathEvent

/**
 * Inner objective: kill 3 Withers.
 * This class is NOT annotated with @CustomObjective — only the wrapper is.
 */
class KillWithersObjective : EventBingoObjective<EntityDeathEvent>(
    id = "secret_kill_withers",
    name = Component.text("???", NamedTextColor.LIGHT_PURPLE),
    description = Component.text("Kill 3 Withers."),
    difficulty = Difficulty.INSANE,
    eventClass = EntityDeathEvent::class.java
) {
    private val required = 3

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onEntityDeath(event: EntityDeathEvent) {
        if (event.entity.type != EntityType.WITHER) return
        val killer = event.entity.killer ?: return
        val state = BingoManager.getActiveState(killer, id) ?: return
        onEvent(event, killer, state)
    }

    override fun onEvent(event: EntityDeathEvent, player: Player, state: BingoPlayerState) {
        val progress = state.getProgress(id) + 1
        state.setProgress(id, progress)
        if (progress >= required) BingoManager.checkCompletion(player, this)
    }

    override fun isCompletedBy(player: Player, state: BingoPlayerState) =
        state.getProgress(id) >= required

    override fun onReset(player: Player, state: BingoPlayerState) {
        state.setProgress(id, 0)
    }
}

/**
 * The secret wrapper — this is what gets registered and placed on the board.
 */
@CustomObjective
class SecretKillWithers : SecretBingoObjective(
    inner = KillWithersObjective(),
    hints = listOf(
        "This involves a boss mob",
        "You need to summon it yourself",
        "Three skulls, soul sand, and patience"
    )
)
```

**What happens:**

- `SecretKillWithers` is discovered by `CodeObjectiveLoader` and registered
- The registry sees it's a `SecretBingoObjective` and checks the `inner` for `Listener` → registers
  `KillWithersObjective` as a Bukkit event listener
- On the board, the cell shows `MAGENTA_STAINED_GLASS` with a `⚡ Secret` tag
- Players see `*****` instead of "Kill 3 Withers." until they complete it
- Hints are revealed progressively as the game timer elapses

---

### Using a Companion Factory

When the inner objective requires dynamic construction or parameters:

```kotlin
@CustomObjective
class SecretNetherExplorer private constructor() : SecretBingoObjective(
    inner = TravelDistanceInNetherObjective(),
    hints = listOf(
        "This involves travelling",
        "You need to visit a specific dimension",
        "Cover a large distance there"
    )
) {
    companion object : BingoObjectiveFactory {
        override fun create() = SecretNetherExplorer()
    }
}
```

---

### Wrapping a MultiEventBingoObjective

Secret objectives can wrap any objective type, including multi-event objectives:

```kotlin
class BrewAndDrinkObjective : MultiEventBingoObjective(
    id = "secret_brew_drink",
    name = Component.text("???", NamedTextColor.LIGHT_PURPLE),
    description = Component.text("Brew and drink a Strength potion."),
    difficulty = Difficulty.MEDIUM
) {
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onBrew(event: BrewEvent) { /* ... track brewing ... */ }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onDrink(event: PlayerItemConsumeEvent) { /* ... track drinking ... */ }

    // ... isCompletedBy, onReset ...
}

@CustomObjective
class SecretBrewAndDrink : SecretBingoObjective(
    inner = BrewAndDrinkObjective(),
    hints = listOf(
        "This involves alchemy",
        "Drink your own creation"
    )
)
```

---

### Display Behaviour

The `SecretBingoObjective.displayItem()` rendering works as follows:

| Player state      | What they see                                                                    |
|:------------------|:---------------------------------------------------------------------------------|
| Has NOT completed | Magenta glass, name, difficulty, `⚡ Secret` tag, `*****`, revealed hints, status |
| Has completed     | Lime concrete, name, difficulty, `⚡ Secret` tag, **real description**, status    |
| Spectator         | Board shows `*****` + revealed hints (spectators cannot complete objectives)     |

Hints appear in the lore as:

```
Hint 1: This involves a boss mob
Hint 2: You need to summon it yourself
```

Only hints that have been globally revealed (based on elapsed time) are shown. Unrevealed hints are not displayed at
all.

---

### Board GUI Chat Output

When a player clicks a secret objective cell in the board GUI:

- **If completed by that player**: the full description is shown in chat (same as regular objectives)
- **If not completed**: `*****` is shown, followed by any currently revealed hints

---

### Important Notes

1. **Only annotate the wrapper** with `@CustomObjective`, not the inner objective class
2. **The inner objective's `id`** is what's used for persistence — the wrapper delegates its `id` to the inner
3. **Hints are revealed globally** — all players see the same hints at the same time
4. **No timer = no hints** — if the countdown timer is 0 or not set, hints won't reveal
5. **Test sessions** (`/bingo test <id>`) work normally with secret objectives — the test system uses the objective's
   `id` which delegates to the inner
6. **Board randomizers** can filter secret objectives using `objective is SecretBingoObjective`
7. **The `difficulty` property** is preserved from the inner objective, so randomizers can still group by difficulty

---
