# WaypointSystem Changelog

## [1.0.4] - 2026-05-14
### Added
- Owner can delete a waypoint from the Manage GUI — requires confirmation in a new Confirm Delete GUI; deletion removes it from storage and makes any linked Recall Orbs invalid
- Owner can rename a waypoint from the Manage GUI — reuses the chat input flow with `cancel` support and 60-second timeout; held item name tag updated automatically if it matches
- `settings.require-owner-for-orb-invites` config option (default: `true`) — when enabled, only the waypoint owner can use a Recall Orb to invite other players; non-owners can still right-click to self-teleport
- New messages: `waypoint-deleted`, `waypoint-renamed`, `rename-prompt`, `orb-invalid`

### Changed
- Waypoint limit (`max-waypoints-per-player`) is now enforced at right-click time (before the name prompt) instead of only at naming commit; gives a clear message with the current limit
- Invalid Recall Orb PDC data now shows `orb-invalid` message instead of silently failing
- Manage GUI expanded with Rename (Name Tag, slot 15) and Delete (TNT, slot 16) buttons for owners

### Fixed
- Recall Orb invite flow in `onPlayerInteractEntity` now shows `orb-invalid` instead of silently returning when PDC data is missing or malformed

---

## [1.0.3] - 2026-05-14
### Added
- `/waypoint list` — shows all accessible waypoints (owned, public, invited) with name, owner, public/private, and fee
- `/waypoint menu` — explicit command to open the hub GUI (no-args does the same)
- Recall Orb use cooldown — configurable via `settings.teleport-cooldown-seconds`; shows `"Please wait Xs"` message on spam
- `settings.max-waypoints-per-player` config option (default: 10); enforced at naming time with a clear error message
- `settings.allow-recall-orb-invites` config toggle (default: true); disabling blocks right-click-player invite flow
- Helpful `/waypoint` usage message listing all available subcommands (permission-filtered)

### Changed
- Permission nodes renamed from `waypointsystem.*` to `waypoint.*`
  - `waypoint.use` (default: true) — open GUI, use waypoints, use recall orbs, craft/name waypoints
  - `waypoint.list` (default: true) — use `/waypoint list`
  - `waypoint.give` (default: op) — give waypoint items and recall orbs
  - `waypoint.reload` (default: op) — reload config and data
  - `waypoint.admin` (default: op) — inherits give + reload
- `plugin.yml` updated with full command usage and all permissions with descriptions/defaults
- `config.yml` restructured with inline comments on every setting; `recall-orb-cooldown` renamed to `teleport-cooldown-seconds`
- All permission checks in listeners updated to `waypoint.*`
- `/waypoint` no-args and `menu` subcommand now check `waypoint.use` before opening GUI

### Fixed
- Recall Orb right-click-player invite now correctly reads `allow-recall-orb-invites` config before proceeding

---

## [1.0.2] - 2026-05-14
### Added
- `WaypointManager.shortId(UUID)` — returns first 4 hex chars of a UUID uppercased (e.g. `#A1B2`)
- `WaypointManager.getWaypointsByName(String)` — returns all waypoints matching a name (for collision detection)
- `GuiManager.duplicateNames()` / `label()` — appends `(#XXXX)` to any waypoint name that collides within the player's visible pool

### Changed
- Hub GUI, Manage GUI, Use GUI, Invite GUI titles and item names all use disambiguated labels
- `/waypoint give orb <name>`: if multiple waypoints share the name, lists each with owner and full UUID and tells the admin to use the UUID instead
- `/waypoint give orb <uuid>`: UUID input resolves directly by ID, bypassing name lookup

---

## [1.0.1] - 2026-05-14
### Added
- Startup logs: version, Vault status + provider name, recipe registered, waypoint count
- `/waypoint give <player> waypoint [amount]` — give N unnamed waypoint items
- `/waypoint give <player> orb <name|id> [amount]` — give N recall orbs for any waypoint
- Tab completion for all give subcommands
- Chat input `cancel` keyword to abort naming or fee input
- 60-second timeout for both naming and fee inputs (was 30s/none); timeout tasks cancelled early when input arrives
- Recall Orb lore shows waypoint name, owner name, and invite capability line
- `EconomyManager.deposit()` for fee refund on failed teleport
- `TeleportHelper`: explicit world-loaded check; `findSafe()` returns `null` on failure; fee refunded if teleport aborted

---

## [1.0.0] - 2026-05-14
### Initial release
- Craftable Waypoint item (8x Quartz + Ender Eye)
- Chat-based first-time naming with 30-second timeout
- Waypoint Hub GUI showing all accessible waypoints
- Owner Manage GUI: toggle public/private, set fee (chat input), invite players, create Recall Orb
- Recall Orb: right-click to teleport, right-click player to send teleport invite
- GUI-based teleport invite accept/deny with `/waypoint accept` / `/waypoint deny` chat fallback
- Vault soft-dependency for fee system (gracefully disabled if absent)
- Safe teleport destination finder with configurable spiral search radius
- YAML persistence (`waypoints.yml`)
- `/waypoint give`, `/waypoint reload`
- Geyser-friendly GUI-first design
