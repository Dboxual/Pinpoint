# Pinpoint — Developer Guide

## Project purpose

Pinpoint is a Paper 1.21.1 plugin that gives players craftable teleportation waypoints managed entirely through inventory GUIs. The goal is a zero-command player experience that works identically on Java and Bedrock (via Geyser). Admins use commands only for giving items and reloading; players interact exclusively through GUIs and right-clicks.

---

## Development rules

- **Target platform:** Paper 1.21.1, `api-version: '1.21'`
- **Java version:** Source and target compatibility set to Java 21 (`--release 21`)
- **Geyser/Bedrock friendly:** All player-facing flows must work through inventory GUIs. Avoid chat-only interactions; chat input is only acceptable as a supplementary fallback (e.g., naming a waypoint after right-clicking)
- **GUI-first design:** Every action a player needs to take should be reachable by right-clicking an item and navigating GUIs
- **PDC-tag all custom items:** Custom items must carry their identity in `PersistentDataContainer` tags, never in display name or lore strings
- **UUIDs internally:** All waypoint identity, orb linking, invite tracking, and storage keys use `UUID`. Names are display-only — never rely on them for logic
- **No database:** Data is persisted to `waypoints.yml` via Bukkit `YamlConfiguration`. Keep the storage layer in `WaypointStorage`
- **Vault is optional:** Always null-check `EconomyManager.isEnabled()` before touching economy code
- **Keep player experience simple:** Minimize required player knowledge. Lore text on items should explain how to use them

---

## Architecture overview

```
PinpointPlugin             — main class, wires all managers together
  ItemManager              — PDC keys, recipe registration, item factories
  WaypointManager          — in-memory waypoint state, location index, pending-input maps
  WaypointStorage          — YAML read/write/delete
  GuiManager               — inventory GUI builder + click handler dispatcher
  EconomyManager           — Vault wrapper (null-safe)
  TeleportHelper           — safe-spot finder, fee charge/refund, cooldown, teleport
  ChatInputListener        — AsyncPlayerChatEvent handler for naming, fee, rename flows
  BlockPlaceListener       — waypoint block placement → starts naming flow
  BlockBreakListener       — protects waypoint blocks; authorized break deletes waypoint
  WaypointInteractListener — block right-click (open GUI) + compass right-click (open hub / invite)
  TeleportCancelListener   — cancels pending teleports on move / damage / item-switch / logout
  WaypointCommand          — /waypoint subcommands + tab completion
```

**Block-based waypoints:** The placed Lodestone block IS the waypoint. `WaypointManager` maintains a `Map<String, UUID> locationIndex` keyed by `"world,x,y,z"` strings for O(1) block-to-waypoint lookups. The index is populated on load and kept in sync on create/delete.

**Pinpoint Compass:** A PDC-tagged Compass that gives access to all accessible waypoints. Right-click air/block → Hub GUI. Right-click player → `openInviteSelectGui` → waypoint selection → invite sent. The PDC key is still `waypoint_pearl` for backwards compatibility — old ENDER_PEARL tagged items with that key are still recognised by `isWaypointPearl()`.

**Pending-input pattern:** Any chat input flow (naming, fee, rename) stores state in `WaypointManager` maps keyed by player UUID. `ChatInputListener.onChat` checks each map in priority order (rename → naming → fee). Each flow has a companion timeout task whose ID is stored so it can be cancelled early. Naming cancel/timeout also restores the physical block and refunds the item.

**Teleport countdown:** All teleports go through `TeleportHelper.teleport()`, which schedules a delayed task (`teleport-delay-seconds`, default 10). A `PendingTeleport` record (player ID, waypoint ID, start location, task ID) is stored in `WaypointManager`. `TeleportCancelListener` cancels the task on block-position move, damage, or logout (item-switch does NOT cancel). When the task fires it re-validates the waypoint ID before calling `doTeleport()`. After a successful teleport the reuse cooldown (`teleport-cooldown-seconds`) is set for all players including owners.

---

## Build rules

Build with Gradle (Gradle 8.x, Java 21):

```bash
bash gradlew jar
```

The compiled jar is placed in `build/libs/Pinpoint-<version>.jar`.

`build.sh` exists but requires Windows-compatible classpath separators; prefer Gradle.

---

## Workflow rules

Before ending any work session:

1. **Increment the version** in `plugin.yml`, `build.sh` (jar filename), and `build.gradle.kts`
2. **Run `bash gradlew jar`** and confirm the jar is produced with no errors
3. **Verify the jar exists** at `build/libs/Pinpoint-<version>.jar`
4. **Update `CHANGELOG.md`** with a dated entry summarising what changed
5. **Commit** all modified source files and the new jar together

Documentation changes (README, CHANGELOG, CLAUDE.md) do not require a version bump on their own, but must be included in the nearest commit.
