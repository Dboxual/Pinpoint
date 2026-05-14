# Changelog

## v1.0.5 — 2026-05-14
### Changed (breaking redesign)
- **Waypoint is now a placeable block (Lodestone)** — craft with 8x Quartz + Ender Eye, place in the world; the block IS the waypoint
- **Right-click the placed block** to open the waypoint GUI instead of right-clicking a compass item
- **Block protection** — only the owner or a player with `waypoint.admin` can break a waypoint block; breaking it deletes the waypoint from storage
- **Waypoint Pearl replaces Recall Orb** — an Ender Pearl tagged item (4x Ender Pearl + Ender Eye) that shows ALL accessible waypoints; right-click air/block to open the hub, right-click a player to select a waypoint and send a teleport invite
- Waypoint Pearl invite flow opens a **waypoint selection GUI** instead of linking to one specific waypoint
- Cooldown increased to **10 seconds** (was 3); cooldown now applies only to non-owners and is enforced in `TeleportHelper` rather than the interact listener
- `/waypoint give <player> orb` replaced with `/waypoint give <player> pearl`
- Deleting a waypoint via the Manage GUI now also removes the physical block from the world
- Block restoration on naming cancel/timeout — if the player cancels or times out while naming, the Lodestone block is removed and the item is returned

### Added
- `BlockPlaceListener` — detects waypoint block placement, enforces limits, starts naming flow
- `BlockBreakListener` — protects waypoint blocks, removes waypoint on authorized break
- `WaypointManager.getWaypointAt(Location)` — O(1) block-to-waypoint lookup via location index
- `GuiManager.openInviteSelectGui` — GUI for selecting which waypoint to invite a player to

### Migration
- Existing waypoints in `waypoints.yml` load normally; their location is indexed for block detection
- Old compass waypoint items and old Recall Orbs from prior versions are no longer recognized

---

## v1.0.4 — 2026-05-14
### Added
- Owner can delete a waypoint from Manage GUI — requires a confirmation screen before removal
- Owner can rename a waypoint from Manage GUI — reuses chat input flow with `cancel` support and 60-second timeout; held item updates automatically
- `require-owner-for-orb-invites` config option (default: `true`) — restricts orb invites to the waypoint owner; non-owners can still self-teleport
- New messages: `waypoint-deleted`, `waypoint-renamed`, `rename-prompt`, `orb-invalid`

### Changed
- Waypoint limit now enforced at right-click time (before name prompt), not only at commit
- Invalid Recall Orb PDC data now shows `orb-invalid` instead of silently failing
- Manage GUI expanded: Rename (slot 15) and Delete (slot 16) buttons added for owners

---

## v1.0.3 — 2026-05-14
### Added
- `/waypoint list` — shows all accessible waypoints with name, owner, visibility, and fee
- `/waypoint menu` — explicit subcommand to open the hub GUI
- Recall Orb cooldown — configurable via `teleport-cooldown-seconds`; shows remaining wait on spam
- `max-waypoints-per-player` config option (default: 10)
- `allow-recall-orb-invites` config toggle (default: true)
- Permission-filtered usage message on `/waypoint` with unknown subcommand

### Changed
- Permissions renamed from `waypointsystem.*` to `waypoint.*`
  - `waypoint.use` (default: true), `waypoint.list` (default: true), `waypoint.give` (op), `waypoint.reload` (op), `waypoint.admin` (op)
- `config.yml` restructured with inline comments; `recall-orb-cooldown` renamed to `teleport-cooldown-seconds`

### Fixed
- Recall Orb right-click-player invite now correctly checks `allow-recall-orb-invites` before proceeding

---

## v1.0.2 — 2026-05-14
### Added
- `WaypointManager.shortId(UUID)` — first 4 hex chars of a UUID, uppercase (e.g. `#A1B2`)
- Duplicate-name detection — GUIs show `Home (#A1B2)` when names collide in a player's visible pool
- `/waypoint give orb <name>` now detects multi-match names and lists full UUIDs for disambiguation

### Changed
- All storage, linking, and invite logic uses UUIDs exclusively — names are display-only
- `/waypoint give orb <uuid>` resolves directly by ID, bypassing name lookup

---

## v1.0.1 — 2026-05-14
### Added
- Startup logs: version, Vault status + provider, recipe registration, loaded waypoint count
- `/waypoint give <player> waypoint [amount]` — give unnamed Waypoint items in bulk
- `/waypoint give <player> orb <name|id> [amount]` — give Recall Orbs for any waypoint
- Tab completion for all `give` subcommands
- `cancel` keyword support in naming and fee chat inputs
- 60-second timeout for naming and fee inputs; timeout tasks cancelled early on valid input
- Recall Orb lore shows waypoint name, owner name, and invite capability line
- `EconomyManager.deposit()` for fee refund if teleport is aborted
- `TeleportHelper`: world-loaded check; `findSafe()` returns `null` on failure; fee refunded on abort

---

## v1.0.0 — 2026-05-14
### Initial release
- Craftable Waypoint item (8x Nether Quartz + Ender Eye)
- Chat-based first-time naming with 30-second timeout
- Waypoint Hub GUI showing all accessible waypoints
- Owner Manage GUI: toggle public/private, set fee, invite players, create Recall Orb
- Recall Orb: right-click to teleport, right-click player to send teleport invite
- GUI accept/deny for teleport invites; `/waypoint accept` / `/waypoint deny` chat fallback
- Vault soft-dependency for fee system (gracefully disabled if absent)
- Safe teleport destination finder with configurable spiral search radius
- YAML persistence (`waypoints.yml`)
- Geyser-friendly GUI-first design
