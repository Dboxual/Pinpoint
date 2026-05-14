# WaypointSystem

A Geyser/Bedrock-compatible craftable waypoint system for Paper 1.21.1. Players craft and place a **Waypoint Block** in the world, name it, then manage teleportation, fees, and player invites through GUI menus. A **Waypoint Pearl** item lets players access all their waypoints from anywhere — no command memorization required.

---

## Features

- **Craftable Waypoint Blocks** — place a Lodestone-based block anywhere in the world to create a physical waypoint
- **Block protection** — only the owner or an admin can break a waypoint block; breaking it removes the waypoint
- **Public/private waypoints** — owners control who can see and use each waypoint
- **Teleport fees** — optional per-waypoint fee charged via Vault (gracefully disabled without Vault)
- **Player invites** — right-click a player with a Waypoint Pearl to select a waypoint and send a teleport invite
- **Waypoint Pearl** — craftable Ender Pearl item that opens a GUI listing all accessible waypoints; right-click a player to invite them
- **Safe teleporting** — destination scanner finds the nearest safe landing spot automatically
- **Bedrock/Geyser compatibility** — GUI-first design works with Bedrock clients via Geyser
- **Configurable limits and cooldowns** — max waypoints per player, teleport cooldown, invite settings

---

## Crafting Recipes

**Waypoint Block** — place in the world to create a waypoint:

```
[ Q ][ Q ][ Q ]
[ Q ][ E ][ Q ]
[ Q ][ Q ][ Q ]
```

`Q` = Nether Quartz &nbsp;&nbsp; `E` = Ender Eye

---

**Waypoint Pearl** — opens your waypoints from anywhere:

```
[ P ][   ][ P ]
[   ][ E ][   ]
[ P ][   ][ P ]
```

`P` = Ender Pearl &nbsp;&nbsp; `E` = Ender Eye

---

## How Players Use It

**Step 1 — Craft a Waypoint Block**
Use the Waypoint Block recipe (8x Quartz + Ender Eye). You receive a Lodestone-style item named **Waypoint**.

**Step 2 — Place the block and name it**
Place the Waypoint block anywhere in the world. A chat prompt immediately asks for a name. Type a name (up to 32 characters) and press Enter. Type `cancel` to abort (the block is removed and returned to you).

**Step 3 — Manage your Waypoint**
Right-click the placed block to open the **Waypoint Hub** GUI. Click your waypoint to open the **Manage** screen. From there you can:
- Teleport to the waypoint
- Toggle public/private
- Set a teleport fee
- Invite specific players (for private waypoints)
- Rename the waypoint
- Get a Waypoint Pearl
- Delete the waypoint (with confirmation — also removes the block)

**Step 4 — Craft or get a Waypoint Pearl**
Craft a Waypoint Pearl (4x Ender Pearl + Ender Eye) or get one from the Manage screen. The pearl shows **all waypoints you can access**.

**Step 5 — Teleport and invite players**
- **Right-click** a Waypoint Pearl in air to open your accessible waypoints and click one to teleport.
- **Right-click a player** with a Waypoint Pearl to open a waypoint selection screen and send them a teleport invite. They see a GUI to accept or deny; or they can type `/wp accept` / `/wp deny`.

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
| `/waypoint give <player> waypoint [amount]` | Give Waypoint Block items |
| `/waypoint give <player> pearl [amount]` | Give Waypoint Pearls |
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
