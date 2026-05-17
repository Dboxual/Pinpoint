# Changelog

## v1.3.1 — 2026-05-17
### Fixed
- **Hologram flash on player join** — on join, `hideAllFromPlayer` was delayed 5 ticks along with the visibility re-evaluation, meaning the joining player received entity spawn packets for all armor stands during that window. The hide is now applied immediately (synchronous, no delay); only the visibility re-evaluation is deferred 5 ticks so the player's position is fully initialised before showing applicable holograms.

---

## v1.3.0 — 2026-05-17
### Added
- **Hologram privacy — distance and line-of-sight filtering** — holograms are now hidden per-player using `showEntity`/`hideEntity`. A repeating task (once per second) checks every online player against every hologram and reveals or hides it based on two configurable rules:
  - `holograms.view-distance` (default `8`) — maximum distance in blocks at which a hologram is visible.
  - `holograms.require-line-of-sight` (default `true`) — hides the hologram when solid blocks are between the player and the Pinpoint block, preventing holograms from revealing hidden bases through walls.
- Holograms are hidden from players immediately on spawn; players in range with line-of-sight see them appear within one server tick.
- Players who log in have all holograms hidden immediately, then re-evaluated after 5 ticks once their position is fully loaded.

---

## v1.2.9 — 2026-05-17
### Fixed
- **Confirm-delete now returns the Pinpoint item** — deleting a placed Pinpoint through the GUI now gives the Lodestone Pinpoint item back to the owner. If the owner's inventory is full, the item drops naturally at the Pinpoint's block location. The item is only returned after all data (hologram, in-memory state, YAML storage) has been cleaned up, preventing any duplication.

---

## v1.2.8 — 2026-05-16
### Added
- **Hub GUI pagination** — the Pinpoint hub is now a fixed 6-row (54-slot) inventory with 28 Pinpoints per page (rows 1-4, columns 1-7). RED_STAINED_GLASS_PANE (slot 45) navigates to the previous page; GREEN_STAINED_GLASS_PANE (slot 53) to the next. A PAPER item (slot 47) shows the current page number. The focused Pinpoint compass (slot 49) and Close barrier (slot 51) are in the bottom row. The page is remembered when navigating to the manage GUI and back, and reset when explicitly closing.

### Changed
- **All player-facing "waypoint/waystone" text renamed to "Pinpoint/Pinpoints"** — affects messages, GUI labels, item names, lore, and config.yml message values. Database keys, PDC keys, permission nodes, and command aliases are unchanged.

---

## v1.2.7 — 2026-05-15
### Fixed
- **Shift+Pearl now accepts teleport invites immediately** — the Accept/Deny GUI was previously forced open on the invitee's screen the moment an invite arrived. On Bedrock (and any client with an inventory visible), this blocked the `PlayerInteractEvent` path, making shift+right-click appear to do nothing useful. Fix: `sendInvite()` no longer calls `openAcceptDenyGui` on the target. The target receives a chat-only notification instead ("Shift+Pearl to accept, or /wp accept | /wp deny"). Shift+right-click with the Waypoint Pearl now accepts immediately, starts the teleport countdown, and opens no GUI.
- **`processAccept()` closes any open GUI before starting countdown** — if any GUI happens to be open when the player accepts (via command or shift-click), it is cleanly closed before the countdown begins.

### Changed
- **`invite-received` message** — updated to guide players: "Shift+Pearl to accept, or /wp accept | /wp deny."
- The `openAcceptDenyGui` method remains available in code; party travel offer GUI (`openTravelOfferGui`) is unchanged.

---

## v1.2.6 — 2026-05-15
### Fixed
- **Invite Players GUI** — completely rewritten. Previously the GUI only showed already-invited players (empty when none existed, making the button appear broken). It now lists all online players (excluding the waypoint owner), with a player-head icon per player. Green lore = already invited (click to remove access). Yellow lore = not yet invited (click to grant access). The invitee receives a chat notification when added.
- **Teleport fee now pays the waypoint owner** — previously `charge()` withdrew from the teleporter but the money went nowhere. `EconomyManager.depositToOwner(UUID, double)` now uses Vault's `OfflinePlayer` API to credit the owner even when they are offline. If the deposit fails for any reason, the player is refunded and teleport is aborted.

### Changed
- **Hologram default height raised to 2.8** — holograms were clipping into players standing at the block. Existing configs that already set `holograms.height` are unaffected.
- **Block teleport delay default changed to 5s** — was 0 (instant). Block-GUI teleports now show the same action bar countdown as pearl teleports. Set `waypoint-block-teleport-delay-seconds: 0` in config to restore instant behaviour.
- **Action bar countdown for all teleport types** — every teleport now shows a persistent action bar message ("Teleporting in Xs... Don't move.") that ticks down each second using the Adventure API. The action bar is cleared on teleport completion or cancellation. Party-follow teleports show "Following <name>..." on the action bar for the 1-second lead-in. Cancellation messages still also appear in chat.

---

## v1.2.5 — 2026-05-15
### Changed
- **`config.yml`** — removed vestigial keys left over from the pre-v1.0.5 Recall Orb era: `allow-recall-orb-invites`, `require-owner-for-orb-invites`, `recall-orb-created`, `recall-orb-invites-disabled`, `orb-invalid`, `vault-missing`. None of these keys were referenced in any Java source file; deleting them cleans up false documentation.
- **`plugin.yml`** — bumped version to 1.2.5; fixed `waypoint.give` permission description (was "recall orbs", now "waypoint items and pearls"); added `/party stay` to the party command usage block.

---

## v1.2.4 — 2026-05-15
### Fixed
- **`TeleportCancelListener.onPlayerMove`** — changed `ignoreCancelled` from `false` to `true`. Previously the handler fired even when another plugin cancelled the `PlayerMoveEvent` (e.g. an anti-cheat blocking movement). Since the player didn't actually move, this incorrectly cancelled their teleport countdown.
- **`TeleportHelper.partyFollow()`** — now re-validates the waypoint UUID in the 1-second delay task before calling `doTeleport`, consistent with all other teleport paths. Previously, if the waypoint was deleted during the 1-second window, the player would teleport to its old coordinates anyway.

### Changed
- **README** — complete rewrite to reflect the current feature set: holograms, custom icons, dual teleport delay, party system, and updated config reference. The old README referenced removed config keys (`teleport-delay-seconds`) and features by their pre-v1.0.8 names.

---

## v1.2.3 — 2026-05-15
### Added
- **Custom waypoint icons** — owners can choose a display icon for their waypoint from a palette of 10 materials: Grass Block, Diamond, Emerald, Nether Star, Ender Pearl, Compass, Chest, Oak Door, Beacon, Lodestone.
- **Icon Select GUI** — accessible from the Manage GUI via a "Change Icon" button (slot 20, bottom row). Shows the full palette with the currently selected icon highlighted. Only the owner can change the icon.
- `icon` field added to `waypoints.yml` — persisted as a material name string. Existing waypoints without this field default to `LODESTONE` on load.

### Changed
- **Hub GUI** — waypoints now display their chosen icon material instead of lime/orange/blue dyes. Status (Public/Private) and ownership remain visible in lore.
- **Use, Confirm-Delete, and Invite-Select GUIs** — info and option items now use the waypoint's chosen icon instead of fixed Paper/Ender Pearl materials.

---

## v1.2.2 — 2026-05-14
### Added
- **Waypoint holograms** — each placed Lodestone block now displays floating text above it showing the waypoint name, owner, public/private status, and fee.
  - Implemented as stacked invisible marker armor stands (no hitbox, no gravity, protected from interaction).
  - Hologram spawns/updates on: waypoint creation, rename, visibility toggle, fee change, plugin reload/startup.
  - Hologram is removed when: waypoint is deleted (GUI or block break), plugin disables.
  - Chunks that unload and reload have their holograms automatically restored via `ChunkLoadEvent`.
- **New config section `holograms:`**
  - `enabled: true` — toggle all holograms on/off.
  - `height: 1.8` — vertical offset above the block's Y coordinate for the bottom line.
  - `show-owner: true` — include the owner name line.
  - `show-fee: true` — include the fee/free line (only shown when Vault is enabled).

---

## v1.2.1 — 2026-05-14
### Changed
- **Split teleport delay into two independent config keys:**
  - `waypoint-block-teleport-delay-seconds: 0` — right-clicking a placed waypoint block teleports near-instantly (safe-spot and fee checks still run).
  - `waypoint-pearl-teleport-delay-seconds: 10` — Waypoint Pearl teleports keep the full countdown. Players must stand still; movement, damage, or logout cancels it. Item switching does NOT cancel it.
  - The old `teleport-delay-seconds` key is removed; update `config.yml` accordingly.
- **New message `teleport-block`** — shown on instant block teleport ("Teleporting...").
- **Updated `teleport-countdown` message** — now reads "Pearl teleporting in Xs... don't move." to make the pearl context explicit.
- `fromBlock` flag threaded through all `GuiManager` GUI methods so the correct delay is applied regardless of how deeply a player navigates the GUI chain before clicking Teleport.

---

## v1.2.0 — 2026-05-14
### Fixed
- **Infinite follow loop** — party follow teleports now pass `suppressFollowPrompt=true` to `doTeleport()`, so when player B follows player A, A is never re-notified. The follow chain stops at one hop.
- **Shift-click pearl no longer opens the GUI** — shift+right-click pearl on air/block now checks for a pending waypoint teleport invite first, then a pending party travel offer, and executes the appropriate accept/follow. If nothing is pending it says so. The hub GUI only opens on a normal (non-shift) right-click.
- **Regular right-click player with pearl** now opens the waypoint invite-select GUI (pick which waypoint to invite that player to), instead of incorrectly opening the hub GUI.
- **Pending invite and travel offer state cleared on logout** — `TeleportCancelListener` now calls `removeInvite` and `clearLastTravelOffer` when a player disconnects.

### Changed
- **Travel offers are now GUI-first** — when a party member teleports, all online party members receive a Follow/Stay inventory GUI instead of only a clickable chat message. A plain-text hint `(Shift+Pearl to follow)` is also sent, usable on Bedrock/Geyser without requiring clickable chat.
- **Stay dismisses the offer state** — clicking Stay (or `/party stay`) now calls `clearLastTravelOffer` so the dismissed offer cannot be accidentally followed later.
- **`/party follow` clears offer state after accept** — prevents the same offer being followed more than once.
- Removed Adventure `ClickEvent`/`HoverEvent` imports from `PartyGuiManager` (no longer used).

---

## v1.1.0 — 2026-05-14
### Added
- **Party system** — persistent social linking between players. Shift+right-click another player with a Waypoint Pearl to send a link request. The receiver gets an Accept/Deny GUI. Accepting merges both players (and any parties they're already in) into one connected group. All members are equal — no ownership hierarchy.
- **Party merging** — A↔B + B↔C automatically creates A↔B↔C. If two separate parties link, all members merge into one party.
- **Travel notifications** — when any party member teleports through a Pinpoint, all online party members receive a clickable chat notification with **[Follow]** (teleports them to the same waypoint) and **[Stay]** buttons. Follow offer expires after `party-travel-offer-timeout` seconds (default 30). Bedrock players can type `/party follow` with no arguments to follow the most recent offer.
- **Party management GUI** — `/party` opens a GUI showing all member heads (click a non-self member to remove them), Leave Party, and Disband Party buttons.
- **`/party` command** (alias `/pp`) — subcommands: `leave`, `disband`, `remove <player>`, `accept`, `deny`, `follow [id]`, `stay`.
- **`parties.yml`** — party data persisted across restarts.
- Two new config keys: `link-request-timeout` (default 60s) and `party-travel-offer-timeout` (default 30s).
- Updated Waypoint Pearl lore to document shift+right-click behaviour.

### Changed
- Shift+right-click a player with Waypoint Pearl now sends a party link request instead of doing nothing.
- Regular right-click player with Waypoint Pearl still opens the waypoint invite GUI (unchanged).

---

## v1.0.8 — 2026-05-14
### Changed
- **Plugin renamed to Pinpoint** — package changed from `com.waypointsystem` to `com.pinpoint`, main class renamed from `WaypointPlugin` to `PinpointPlugin`. All gameplay commands, permissions, and data files are unchanged.

---

## v1.0.7 — 2026-05-14
### Changed
- **Teleport countdown no longer cancels on item switch** — players may freely change their held item during the countdown. Only movement, damage, and logout still cancel it.

### Removed
- `teleport-cancelled-item` message key (no longer fired); remove from any custom `config.yml` if present

---

## v1.0.6 — 2026-05-14
### Added
- **Teleport countdown** — all teleports (pearl, block right-click, invite acceptance) now count down for `teleport-delay-seconds` (default: 10) before firing, showing "Teleporting in Xs... don't move."
- **Teleport cancellation** — moving, taking damage, switching held item, or logging out during the countdown cancels the teleport with a specific message per cause
- `TeleportCancelListener` — new listener handling `PlayerMoveEvent`, `EntityDamageEvent`, `PlayerItemHeldEvent`, `PlayerQuitEvent`
- `teleport-delay-seconds: 10` config option (countdown before teleporting)
- `teleport-cooldown-seconds: 3` config option (reuse cooldown after a successful teleport, reduced from 10 since delay already prevents spam)
- New messages: `teleport-countdown`, `teleport-cancelled-moved`, `teleport-cancelled-damaged`, `teleport-cancelled-item`

### Changed
- **Cooldown applies to all players** including owners (was non-owners only)
- **Management buttons protected server-side** — every owner-only GUI action (toggle visibility, set fee, invite, get pearl, rename, delete) now verifies `wp.isOwner` in the click handler, not just at GUI build time
- **Public/private toggle** replaced glass panes with `EMERALD_BLOCK` ("Visibility: PUBLIC — Anyone can visit") and `REDSTONE_BLOCK` ("Visibility: PRIVATE — Only you and invited players can visit")

---

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
