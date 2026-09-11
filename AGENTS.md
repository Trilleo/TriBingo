# TriBingo Agent Guide

## Project snapshot

- **TriBingo** is a Kotlin JVM Paper plugin (`api-version: 1.21`) that implements Minecraft Bingo with a 5×5 board, team
  system, point scoring, and configurable objectives.
- Plugin version: `0.1.0`, set once as `plugin_version` in `gradle.properties` and expanded into `plugin.yml` at build
  time — never hardcode it. Built with Kotlin `2.3.10`, JVM toolchain 25, against
  `io.papermc.paper:paper-api:26.2.build.+`.
- Single declared root command in `plugin.yml`: `/tribingo` (alias `/tb`). The `/bingo` command is registered at runtime
  as a standalone "main command".
- `src/main/kotlin/net/trilleo/mc/plugins/tribingo/Main.kt` is the startup/shutdown hub; its init order matters.
- Paper/Adventure are the main integration points: use `Component` / MiniMessage for player-facing text.

## After every change: keep the changelog and docs in sync

Before finishing any task that changes the plugin, do all of the following:

1. **Update the changelog** — add an entry for the change under `## Unreleased` in `CHANGELOG.md`, in the same commit
   as the change. Every new feature (including every new objective) gets an entry, and so does every improvement and
   fix.
    - Follow the SkyHanni-style format documented in `docs/RELEASING.md`: category (`### New Features` /
      `### Improvements` / `### Fixes` / `### Technical Details` / `### Removed Features`), then a `#### Feature Area`
      heading (`Objectives`, `Board`, `Teams`, `GUI`, `Commands`, `Misc`, …), then `+` bullets.
    - Reuse the category and feature-area headings already under `## Unreleased` instead of repeating them.
    - Write player- and server-owner-facing entries for gameplay changes; put refactors, build, and tooling changes
      under `### Technical Details`.
    - Never edit the section of a version that has already been released.
    - Skip changelog entries only for changes with no effect on the shipped plugin or its workflow (e.g. fixing a typo
      in a doc).

2. **Update the docs** — a change to objectives, board generation, or objective testing updates `docs/BINGO_GUIDE.md`;
   a change to a base class, registrar, or utility updates `docs/DEVELOPER_GUIDE.md` / `docs/UTILITY_GUIDE.md`; a
   change to a documented workflow (e.g. the release process in `docs/RELEASING.md`) updates that doc. Keep this
   file's package layout, command, and permission sections accurate too.

3. **Check the README** — if the change affects anything `README.md` mentions, update it.

## Startup / shutdown order

- `Main.onEnable()` wires systems in this order: `PluginConfig` → `MessageUtil.init(...)` →
  `ServerDataManager.setFactory { BingoServerData() }` → `ServerDataManager.init(...)` → `PlayerDataManager.init(...)` →
  `ItemRegistrar` → `RecipeRegistrar` → `CommandRegistrar` → `PermissionRegistrar` → `ListenerRegistrar` →
  `GUIManager` → `TaskRegistrar` → `TeamManager.initializeTeam()` → `BingoObjectiveRegistry.init(...)` →
  `CodeObjectiveLoader.load(...)` → `YamlObjectiveLoader.load(...)` → `BingoManager.init(...)` →
  `ObjectiveTestManager.init(...)`.
- `Main.onDisable()` does the reverse-style cleanup: `TaskRegistrar.unregisterAll()` →
  `ObjectiveTestManager.shutdown()` → `RecipeRegistrar.unregisterAll()` → `PlayerDataManager.saveAll()` →
  `BingoManager.save()` → `ServerDataManager.save()`.

## Package layout

```
net.trilleo.mc.plugins.tribingo
├── Main.kt                        # Plugin entry point
├── bingo/                         # Core bingo system
│   ├── BingoManager.kt            # Singleton facade (game lifecycle, timer, completion checking)
│   ├── BingoGame.kt               # State machine: INACTIVE → ACTIVE → ENDED
│   ├── BingoBoard.kt              # 5×5 board (flat row-major cell list)
│   ├── BingoCell.kt               # (cellIndex, objective) pair
│   ├── BingoPlayerState.kt        # Per-player progress: cells, progressData, stringData, stepData, points
│   ├── BingoObjective.kt          # Abstract base (id, name, description, difficulty)
│   ├── BingoObjectiveFactory.kt   # Companion-object factory interface
│   ├── EventBingoObjective.kt     # Single-event listener base
│   ├── MultiEventBingoObjective.kt # Multi-event listener base
│   ├── SequentialBingoObjective.kt # Ordered-steps base (advanceStep/hasStep)
│   ├── ObjectiveTestManager.kt    # /bingo test <id> isolated test sessions
│   ├── annotation/
│   │   └── CustomObjective.kt     # Annotation for auto-discovered code objectives
│   ├── custom/                    # Plugin-internal code objectives (auto-scanned)
│   ├── objectives/                # Parameterized YAML-instantiated objectives
│   ├── randomizer/                # Board randomizer strategy (Easy/Medium/Hard)
│   └── registry/
│       ├── BingoObjectiveRegistry.kt  # In-memory objective store
│       ├── CodeObjectiveLoader.kt     # JAR scanner for @CustomObjective classes
│       └── YamlObjectiveLoader.kt     # bingo_objectives.yml parser
├── commands/
│   ├── bingo/
│   │   ├── BingoCommand.kt        # /bingo (standalone, isMainCommand=true)
│   │   └── BingoActions.kt        # Shared action logic for commands and GUIs
│   ├── moderation/
│   │   └── ReloadCommand.kt       # /tribingo reload
│   └── info/
│       └── HelpCommand.kt         # /tribingo help
├── config/
│   └── PluginConfig.kt            # Typed wrapper for config.yml
├── data/
│   ├── ServerData.kt              # Generic JSON-backed server data
│   ├── BingoServerData.kt         # Bingo-specific persisted state (board, timer, player states)
│   ├── ServerDataManager.kt       # Server data lifecycle (load/save serverdata.json)
│   ├── PlayerData.kt              # Per-player data model
│   └── PlayerDataManager.kt       # Player data lifecycle (playerdata/*.json)
├── enums/
│   ├── Difficulty.kt              # Objective difficulty: EASY, MEDIUM, HARD, INSANE
│   ├── GameDifficulty.kt          # Game difficulty: EASY, MEDIUM, HARD (board randomizer selection)
│   ├── GameState.kt               # INACTIVE, ACTIVE, ENDED
│   ├── FillMode.kt                # GUI fill: NONE, LIGHT, DARK
│   ├── DisplayLocation.kt         # Display location enum
│   └── PagedGUIMode.kt            # Paged GUI mode enum
├── guis/
│   ├── BingoBoardGUI.kt           # Board display (id="bingo_board")
│   ├── configMenus/
│   │   ├── SettingsGUI.kt         # Game settings (id="settings")
│   │   └── GameRuleGUI.kt         # Game rules config
│   └── mainMenus/
│       ├── MainGUI.kt             # Main menu (id="main")
│       ├── TeamSelectGUI.kt       # Team selection (id="team-select")
│       └── CreditsGUI.kt          # Credits display
├── items/
│   └── MainItem.kt               # Custom plugin item(s)
├── listeners/
│   ├── game/
│   │   ├── GameListener.kt        # Game-wide events
│   │   ├── SignInputListener.kt   # Sign-based timer input (HH:MM:SS)
│   │   └── TeamInitListener.kt   # Team initialization on join
│   └── item/
│       └── MainItemListener.kt    # Custom item interactions
├── managers/
│   ├── ItemManager.kt             # Item management
│   └── TeamManager.kt            # Two-team system (player/spectator)
├── registration/                  # Auto-discovery framework
│   ├── PackageScanner.kt          # JAR/directory class scanner
│   ├── CommandRegistrar.kt        # Scans .commands.** for PluginCommand subclasses
│   ├── ListenerRegistrar.kt       # Scans .listeners.** for Listener impls
│   ├── GUIManager.kt             # Scans .guis.** for PluginGUI subclasses; routes click/close
│   ├── TaskRegistrar.kt           # Scans net.trilleo.mc.plugins.tribingo.tasks for PluginTask
│   ├── ItemRegistrar.kt           # Custom item registration
│   ├── RecipeRegistrar.kt         # Custom recipe registration
│   ├── PermissionRegistrar.kt     # Auto-creates Permission nodes from commands
│   ├── PluginCommand.kt           # Command base class
│   ├── PluginGUI.kt              # GUI base class
│   ├── PagedPluginGUI.kt         # Paginated GUI base
│   ├── PluginTask.kt             # Task base class
│   ├── PluginItem.kt             # Item base class
│   └── PluginRecipe.kt           # Recipe base class
└── utils/
    ├── MessageUtil.kt             # Prefix messaging + Player.sendPrefixed extension
    ├── TeamUtil.kt                # Team queries (isInTeam, etc.)
    ├── CountdownUtil.kt           # Timer formatting
    ├── GameRuleUtil.kt            # Game rule helpers
    ├── ItemStackDSL.kt            # itemStack { } builder DSL
    ├── PDCUtil.kt                 # PersistentDataContainer helpers
    ├── PDCEntryUtil.kt            # PDC entry helpers
    └── TagUtil.kt                 # Tag/metadata utilities
```

## Where to put things

- **Commands**: `src/main/kotlin/net/trilleo/mc/plugins/tribingo/commands/**`. Default is a `/tribingo <subcommand>`
  entry; set `isMainCommand = true` for a standalone command. Category is derived from the subpackage name (e.g.
  `commands.bingo` → "Bingo", `commands.moderation` → "Moderation").
- **GUIs**: `.../guis/**`; open them with `GUIManager.open(player, id)`. Base classes: `PluginGUI` (single page) and
  `PagedPluginGUI` (paginated).
- **Listeners**: `.../listeners/**`; `ListenerRegistrar` discovers and registers them automatically.
- **Tasks**: `TaskRegistrar` currently scans `net.trilleo.mc.plugins.tribingo.tasks`. If you add tasks, either place
  them in that package or update the `TASKS_PACKAGE` constant in `TaskRegistrar.kt`.
- **Code objectives**: `.../bingo/custom/**`; annotate concrete classes with `@CustomObjective` so `CodeObjectiveLoader`
  picks them up. No manual registration needed.
- **YAML-backed objectives**: `.../bingo/objectives/**`; these are parameterized classes instantiated by
  `YamlObjectiveLoader` from `bingo_objectives.yml`.

## Objective system architecture

### Base classes (choose one)

| Base class                 | When to use                                   | Listener? |
|:---------------------------|:----------------------------------------------|:----------|
| `BingoObjective`           | Snapshot-based (no events, checked on demand) | No        |
| `EventBingoObjective<T>`   | Listens to exactly one Bukkit event type      | Yes       |
| `MultiEventBingoObjective` | Listens to multiple Bukkit event types        | Yes       |
| `SequentialBingoObjective` | Ordered sequence of steps via `advanceStep()` | Yes       |

### Event objective pattern

```kotlin
@CustomObjective
class MyObjective : EventBingoObjective<SomeEvent>(...) {
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onEvent(event: SomeEvent) {
        val player = /* extract player from event */
        val state = BingoManager.getActiveState(player, id) ?: return
        onEvent(event, player, state)
    }

    override fun onEvent(event: SomeEvent, player: Player, state: BingoPlayerState) {
        val progress = state.getProgress(id) + 1
        state.setProgress(id, progress)
        if (progress >= count) BingoManager.checkCompletion(player, this)
    }

    override fun isCompletedBy(player: Player, state: BingoPlayerState) =
        state.getProgress(id) >= count

    override fun onReset(player: Player, state: BingoPlayerState) {
        state.setProgress(id, 0)
    }
}
```

### BingoPlayerState storage

| Storage                                      | API                                                                              | Purpose                                                   |
|:---------------------------------------------|:---------------------------------------------------------------------------------|:----------------------------------------------------------|
| `progressData` (Map<String, Int>)            | `getProgress(id)`, `setProgress(id, value)`                                      | Integer counters (kill counts, distances)                 |
| `stringData` (Map<String, String>)           | `getString(id, field)`, `setString(id, field, value)`, `removeString(id, field)` | Arbitrary string values (keyed `"objectiveId:fieldName"`) |
| `stepData` (Map<String, MutableSet<String>>) | `getSteps(id)`, `addStep(id, step)`, `hasStep(id, step)`, `clearSteps(id)`       | Ordered step tokens for sequential objectives             |

### BingoObjectiveFactory

For objectives requiring constructor parameters, implement `BingoObjectiveFactory` on the companion object rather than
using a no-arg constructor.

### YAML objective types

Supported `type` values in `bingo_objectives.yml`: `kill_entity`, `mine_block`, `place_block`, `craft_item`,
`fish_item`, `eat_food`, `enchant_item`, `travel_distance`, `breed_mob`, `tame_entity`. Custom types can be registered
via `YamlObjectiveLoader.registerTypeHandler(...)`.

## Game lifecycle

```
INACTIVE ──start()──► ACTIVE ──end()──► ENDED
   ▲                                       │
   └───────────────reset()─────────────────┘
```

- `BingoManager.newGame()` creates a game in INACTIVE state.
- `BingoGame.refresh(objectives)` shuffles objectives onto the board (must be INACTIVE).
- `BingoGame.start()` transitions to ACTIVE, broadcasts start message.
- `BingoGame.end(winner?, points, name?)` transitions to ENDED, broadcasts results.
- `BingoGame.reset()` calls `onReset()` on each objective for online players, clears all state, returns to INACTIVE.
- `BingoManager.save()` clears data for ACTIVE games on shutdown (no mid-game persistence across restarts).

## Team system

- Two teams: `"player"` (active participants) and `"spectator"` (viewers in SPECTATOR mode during active games).
- `TeamUtil.isInTeam(player, "player")` gates objective tracking — spectators cannot progress.
- `TeamSelectGUI` prevents team switching while the game is ACTIVE.
- `TeamManager.initializeTeam()` sets up teams on startup.

## GUI system

- All GUIs extend `PluginGUI` (id, title, rows, fillMode) and are auto-discovered from
  `net.trilleo.mc.plugins.tribingo.guis`.
- `GUIManager.open(player, id)` creates an inventory, applies fill mode, calls `setup(player, inventory)`, and opens it.
- `GUIManager` routes `InventoryClickEvent` and `InventoryCloseEvent` to the correct GUI instance.
- Known GUIs: `main`, `bingo_board`, `settings`, `team-select`, `game-rule`, `credits`.
- The `team-select` GUI is blocked from opening while a game is active.

## Objective test system

- `ObjectiveTestManager` provides isolated test sessions for verifying objectives.
- `/bingo test <id>` starts a test session; `/bingo test stop` ends it.
- One session per player at a time. Game cannot start while test sessions are active.
- Objectives use `BingoManager.getActiveState(player, id)` which checks both game state and test sessions.
- Action bar displays live test progress (every 10 ticks).
- On completion, `ObjectiveTestManager.onTestCompleted(player, objective)` is called automatically.

## Board randomizer system

- `BoardRandomizer` interface: `randomize(available: List<BingoObjective>): List<BingoObjective>` returns exactly 25
  objectives in row-major order.
- `BoardRandomizerRegistry` maps `GameDifficulty` → `BoardRandomizer` implementation.
- Implementations: `EasyBoardRandomizer`, `MediumBoardRandomizer`, `HardBoardRandomizer`.
- Randomizers control the distribution of objective difficulties based on game difficulty.

## Point system (from config.yml)

- `bingo.points.objective` (default 1): Points per cell completed.
- `bingo.points.line` (default 3): Bonus points for completing a row or column.
- `bingo.points.diagonal` (default 5): Bonus points for completing a diagonal.
- Line bonus tracking uses `BingoPlayerState.completedLines` with keys: `"row_N"`, `"col_N"`, `"diag_main"`,
  `"diag_anti"`.

## TriBingo-specific conventions

- Objective IDs are persistence keys. Do not rename an ID that may already exist in `serverdata.json` or in
  `bingo_objectives.yml` without handling migration.
- Event objectives gate on `BingoManager.getActiveState(player, id) ?: return`, update `BingoPlayerState`, then call
  `BingoManager.checkCompletion(player, this)`.
- `BingoObjectiveRegistry.register(...)` auto-registers any objective that implements Bukkit `Listener`; do not register
  those listeners manually.
- Use `player.sendPrefixed(...)` (extension function) for player-facing messages. It uses MiniMessage formatting.
- `config.yml` is wrapped by `PluginConfig`; `message-prefix` supports MiniMessage, and `/tribingo reload` re-reads it.
- `BingoActions` encapsulates game management logic so commands and GUIs share the same code paths.
- Registration framework uses `PackageScanner` to find concrete classes via JAR/directory scanning; constructors must be
  no-arg or accept a single `JavaPlugin` parameter.

## Persistence and loading

- Player data lives under `<dataFolder>/playerdata/*.json`; server data lives at `<dataFolder>/serverdata.json`.
- `bingo_objectives.yml` is bundled from `src/main/resources/bingo_objectives.yml`, copied on first run (via
  `saveResource`), and not overwritten later.
- `YamlObjectiveLoader` parses the YAML objective file; `CodeObjectiveLoader` scans
  `net.trilleo.mc.plugins.tribingo.bingo.custom` by default.
- `BingoManager.save()` intentionally clears interrupted ACTIVE games during shutdown so the next boot starts clean.
- `BingoServerData` stores: board size, game state, game difficulty, board layout (objective IDs), player states (cells,
  progress, strings, steps, lines, points), and timer seconds.

## Permissions

- `tribingo.bingo.manage` — required for game management commands (start, stop, reset, refresh, time, test).
- Permissions default to OP. `PermissionRegistrar` auto-creates `Permission` nodes from command definitions after
  `CommandRegistrar` runs.
- The settings GUI is visible to all but only editable with `tribingo.bingo.manage`.

## Commands

| Command                       | Type              | Permission              | Description                                      |
|:------------------------------|:------------------|:------------------------|:-------------------------------------------------|
| `/tribingo <sub>`             | Root (plugin.yml) | —                       | Parent dispatcher for sub-commands               |
| `/tribingo reload`            | Sub-command       | `tribingo.reload`       | Reloads config.yml                               |
| `/tribingo help`              | Sub-command       | —                       | Shows command help by category                   |
| `/bingo`                      | Standalone        | —                       | Opens main GUI (player) or shows usage (console) |
| `/bingo board`                | Sub-action        | —                       | Opens bingo board GUI                            |
| `/bingo start`                | Sub-action        | `tribingo.bingo.manage` | Starts the game                                  |
| `/bingo stop`                 | Sub-action        | `tribingo.bingo.manage` | Ends the active game                             |
| `/bingo reset`                | Sub-action        | `tribingo.bingo.manage` | Resets all player progress                       |
| `/bingo refresh [difficulty]` | Sub-action        | `tribingo.bingo.manage` | Picks new objectives (INACTIVE only)             |
| `/bingo time <h> <m> <s>`     | Sub-action        | `tribingo.bingo.manage` | Sets countdown timer                             |
| `/bingo status`               | Sub-action        | —                       | Shows current game status                        |
| `/bingo test <id\|stop>`      | Sub-action        | `tribingo.bingo.manage` | Tests objective completion logic                 |

## Workflow notes

- Prefer `./gradlew.bat build` (Windows) or `./gradlew build` (Linux/macOS) for a full local verification.
- `./gradlew test` is available (JUnit 5 platform), but there is currently no `src/test` tree in the repo.
- `./gradlew copyPlugin` copies the jar into `run/plugins`; `./gradlew startServer` depends on that and launches Paper
  from `run/`.
- Current `TaskRegistrar` scans `net.trilleo.mc.plugins.tribingo.tasks`; if you add or move tasks, keep that package
  path in mind or update the `TASKS_PACKAGE` constant.
- The fat-JAR (`tasks.jar`) includes all `runtimeClasspath` dependencies and sets `paperweight-mappings-namespace` to
  `"spigot"` in the manifest.
- CI: `.github/workflows/build.yml` builds every push and pull request, and `.github/workflows/release.yml` publishes a
  GitHub Release with the jar when a `vX.Y.Z` tag is pushed (see `docs/RELEASING.md`). Never tag or push tags unless
  explicitly asked — pushing a tag publishes a release.

## Documentation

- `docs/BINGO_GUIDE.md` — comprehensive guide to the bingo system (objectives, testing, board generation).
- `docs/DEVELOPER_GUIDE.md` — developer guide for extending the plugin.
- `docs/UTILITY_GUIDE.md` — utility classes documentation.
- `docs/COMMIT_STRUCTURE.md` — commit message format specification.
- `docs/RELEASING.md` — changelog format and the release process.
- `CHANGELOG.md` — player-facing change log; new entries go under `## Unreleased`.

## Commit structure

- Follow the repo's commit format: `<tag>: <message>`.
- Approved tags: `Feature`, `Fix`, `Improvement`, `Internal`, `Backend`, `Update`.
- Keep messages in present tense, specific, and without a trailing period.
- Example: `Fix: Handle missing objective IDs during rehydration`.
- Tags map to changelog categories: `Feature` → `### New Features`, `Improvement` → `### Improvements`, `Fix` →
  `### Fixes`, `Backend` / `Internal` → `### Technical Details`, `Update` → usually no entry. A `Feature`,
  `Improvement`, or `Fix` commit carries its own changelog entry.

