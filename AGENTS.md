# TriBingo Agent Guide

## Project snapshot
- `TriBingo` is a Kotlin JVM Paper plugin (`api-version: 1.21`) with one declared root command in `src/main/resources/plugin.yml`: `/tribingo` (`/tb` alias).
- `src/main/kotlin/net/trilleo/mc/plugins/tribingo/Main.kt` is the startup/shutdown hub; its init order matters.
- Paper/Adventure are the main integration points: use `Component` / MiniMessage for player-facing text.

## Startup / shutdown order
- `Main.onEnable()` wires systems in this order: `PluginConfig` → `MessageUtil.init(...)` → `ServerDataManager.setFactory { BingoServerData() }` → `ServerDataManager.init(...)` → `PlayerDataManager.init(...)` → item/recipe registrars → command/permission/listener/GUI/task registrars → `TeamManager.initializeTeam()` → `BingoObjectiveRegistry.init(...)` → `CodeObjectiveLoader.load(...)` → `YamlObjectiveLoader.load(...)` → `BingoManager.init(...)` → `ObjectiveTestManager.init(...)`.
- `Main.onDisable()` does the reverse-style cleanup: `TaskRegistrar.unregisterAll()` → `ObjectiveTestManager.shutdown()` → `RecipeRegistrar.unregisterAll()` → `PlayerDataManager.saveAll()` → `BingoManager.save()` → `ServerDataManager.save()`.

## Where to put things
- Commands: `src/main/kotlin/net/trilleo/mc/plugins/tribingo/commands/**`.
  - Default behavior is a `/tribingo <subcommand>` entry; set `isMainCommand = true` for a standalone command.
  - `BingoCommand` is the main standalone command class.
- GUIs: `.../guis/**`; open them with `GUIManager.open(player, id)`.
- Listeners: `.../listeners/**`; `ListenerRegistrar` discovers and registers them automatically.
- Tasks: `.../tasks/**`; `TaskRegistrar` scans for `PluginTask` subclasses and schedules them.
- Code objectives: `.../bingo/custom/**`; mark concrete classes with `@CustomObjective` so `CodeObjectiveLoader` picks them up.

## TriBingo-specific conventions
- Objective IDs are persistence keys. Do not rename an ID that may already exist in `serverdata.json` or in `bingo_objectives.yml` without handling migration.
- Event objectives usually follow the same pattern: gate on `BingoManager.getActiveState(player, id) ?: return`, update `BingoPlayerState`, then call `BingoManager.checkCompletion(player, this)`.
- `BingoObjectiveRegistry.register(...)` auto-registers any objective that implements Bukkit `Listener`; do not register those listeners manually again.
- Use `sendPrefixed(...)` for player-facing messages and `MessageUtil.init(...)` after config reloads.
- `config.yml` is wrapped by `PluginConfig`; `message-prefix` supports MiniMessage, and `/tribingo reload` re-reads it.

## Persistence and loading
- Player data lives under `<dataFolder>/playerdata/*.json`; server data lives at `<dataFolder>/serverdata.json`.
- `bingo_objectives.yml` is bundled from `src/main/resources/bingo_objectives.yml`, copied on first run, and not overwritten later.
- `YamlObjectiveLoader` parses the YAML objective file; `CodeObjectiveLoader` scans `net.trilleo.mc.plugins.tribingo.bingo.custom` by default.
- `BingoManager.save()` intentionally clears interrupted ACTIVE games during shutdown so the next boot starts clean.

## Workflow notes
- Prefer `./gradlew.bat build` for a full local verification.
- `./gradlew.bat test` is available, but there is currently no `src/test` tree in the repo.
- `./gradlew.bat copyPlugin` copies the jar into `run/plugins`; `./gradlew.bat startServer` depends on that and launches Paper from `run/`.
- Current `TaskRegistrar` scans `net.trilleo.mc.plugins.trihunt.tasks`; if you add or move tasks, keep that package path in mind or update the registrar accordingly.

## Commit structure
- Follow the repo’s commit format: `<tag>: <message>`.
- Approved tags are `Feature`, `Fix`, `Improvement`, `Internal`, `Backend`, and `Update`.
- Keep messages in present tense, specific, and without a trailing period.
- Example: `Fix: Handle missing objective IDs during rehydration`.

