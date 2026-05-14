# WaypointSystem

A Geyser/Bedrock-compatible craftable waypoint system for Paper 1.21.1. Players craft Waypoint items, name them, and use GUI menus to manage teleportation, fees, and player invites — no command memorization required.

---

## Features

- **Craftable Waypoints** — obtained via the crafting table, no admin commands needed for players
- **Public/private waypoints** — owners control who can see and use each waypoint
- **Teleport fees** — optional per-waypoint fee charged via Vault (gracefully disabled without Vault)
- **Player invites** — owners can invite specific players to private waypoints
- **Recall Orbs** — linkable Ender Pearl items for instant one-click teleportation
- **Recall Orb player invites** — right-click a player with a Recall Orb to send a teleport invite
- **Safe teleporting** — destination scanner finds the nearest safe landing spot automatically
- **Bedrock/Geyser compatibility** — GUI-first design works with Bedrock clients via Geyser
- **Configurable limits and cooldowns** — max waypoints per player, teleport cooldown, invite settings

---

## Crafting Recipe

Craft a **Waypoint** item at any crafting table:

```
[ Q ][ Q ][ Q ]
[ Q ][ E ][ Q ]
[ Q ][ Q ][ Q ]
```

`Q` = Nether Quartz &nbsp;&nbsp; `E` = Ender Eye

---

## How Players Use It

**Step 1 — Craft a Waypoint**
Use the recipe above. The item appears as a Compass named **Waypoint**.

**Step 2 — Name your Waypoint**
Right-click the Waypoint item. A chat prompt appears asking for a name. Type a name (up to 32 characters) and press Enter. Type `cancel` to abort.

**Step 3 — Manage your Waypoint**
Right-click the now-named Waypoint item to open the **Waypoint Hub** GUI. Click your waypoint to open the **Manage** screen. From there you can:
- Teleport to the waypoint
- Toggle public/private
- Set a teleport fee
- Invite specific players
- Rename the waypoint
- Delete the waypoint (with confirmation)

**Step 4 — Create a Recall Orb**
In the Manage screen, click **Create Recall Orb**. The orb is linked directly to that waypoint and can be given to any player.

**Step 5 — Teleport and invite players**
- **Right-click** a Recall Orb in air to teleport instantly.
- **Right-click a player** with a Recall Orb to send them a teleport invite. They see a GUI to accept or deny; or they can type `/wp accept` / `/wp deny`.

---

## Commands

### Player commands

| Command | Description |
|---|---|
| `/waypoint` or `/wp` | Open the Waypoint Hub GUI |
| `/waypoint list` | List all accessible waypoints in chat |
| `/waypoint accept` | Accept a pending teleport invite |
| `/waypoint deny` | Deny a pending teleport invite |

### Admin commands

| Command | Description |
|---|---|
| `/waypoint give <player> waypoint [amount]` | Give unnamed Waypoint items |
| `/waypoint give <player> orb <name\|uuid> [amount]` | Give Recall Orbs for a specific waypoint |
| `/waypoint reload` | Reload config and data from disk |

---

## Permissions

| Node | Default | Description |
|---|---|---|
| `waypoint.use` | `true` | Open GUI, craft/name waypoints, use Recall Orbs |
| `waypoint.list` | `true` | Use `/waypoint list` |
| `waypoint.give` | `op` | Use `/waypoint give` |
| `waypoint.reload` | `op` | Use `/waypoint reload` |
| `waypoint.admin` | `op` | Inherits `waypoint.give` + `waypoint.reload` |

---

## Configuration

All settings live in `config.yml` under the `settings:` key.

| Key | Default | Description |
|---|---|---|
| `default-fee` | `0` | Starting fee for newly created waypoints. `0` = free. |
| `safe-teleport-radius` | `5` | Block radius to scan for a safe landing spot. |
| `teleport-cooldown-seconds` | `3` | Seconds a player must wait between Recall Orb uses. |
| `max-waypoints-per-player` | `10` | Max waypoints one player may own. `0` = unlimited. |
| `allow-recall-orb-invites` | `true` | Whether Recall Orbs can be used to invite other players. |
| `require-owner-for-orb-invites` | `true` | If true, only the waypoint owner can send invites via orb. |
| `invite-timeout` | `60` | Seconds before a pending teleport invite expires. |

All messages are also configurable under `messages:` in `config.yml`. Color codes use the `&` prefix.

---

## Dependencies

| Dependency | Type | Notes |
|---|---|---|
| [Paper 1.21.1](https://papermc.io) | Required | Tested on Paper 1.21.1 |
| [Vault](https://www.spigotmc.org/resources/vault.34315/) | Optional | Required only for teleport fees |
| [Geyser-Spigot](https://geysermc.org) | Optional | Required only for Bedrock client support |
