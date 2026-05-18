# Pinpoint

A Geyser/Bedrock-compatible craftable waypoint system for Paper 1.21.1. Players craft and place a **Waypoint Block** anywhere in the world to create a physical waypoint, then manage everything through inventory GUIs — no command memorization required.

---

## Features

- **Craftable Waypoint Blocks** — place a Lodestone-based block anywhere; right-click to open the hub GUI
- **Floating holograms** — each placed block displays floating text (name, owner, visibility, fee) that updates in real-time
- **Custom waypoint icons** — owners pick from 10 material icons shown in all hub and selector GUIs
- **Block protection** — only the owner or an admin (`waypoint.admin`) can break a waypoint block
- **Public / private waypoints** — owners control who can see and teleport to each waypoint
- **Teleport fees** — optional per-waypoint fee charged via Vault (gracefully disabled if Vault is absent)
- **Player invites** — right-click another player with a Pinpoint Compass to send a teleport invite via GUI
- **Pinpoint Compass** — Compass item giving access to all accessible waypoints from anywhere
- **Party / social system** — link with other players; party members receive Follow/Stay GUI notifications when someone teleports
- **Dual teleport timing** — block right-clicks teleport instantly; compass teleports use a 10-second countdown (movement or damage cancels it)
- **Safe teleporting** — destination scanner automatically finds the nearest safe landing spot
- **Bedrock / Geyser compatibility** — all flows work through inventory GUIs; shift+right-click compass and `/party follow` provide fallbacks for Bedrock clients

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

**Pinpoint Compass** — opens your waypoints from anywhere:

```
[ C ][   ][ C ]
[   ][ E ][   ]
[ C ][   ][ C ]
```

`C` = Compass &nbsp;&nbsp; `E` = Ender Eye

---

## How Players Use It

**Step 1 — Craft a Waypoint Block**
Use the recipe above (8× Nether Quartz + Ender Eye). You receive a Lodestone-style item.

**Step 2 — Place and name it**
Place the block anywhere. A chat prompt immediately asks for a name (up to 32 characters). Type your name and press Enter — or type `cancel` to abort (the block is removed and returned to your inventory).

**Step 3 — Manage your Waypoint**
Right-click the placed block to open the **Waypoint Hub**. Click your waypoint to open the **Manage** screen, where you can:
- Teleport to the waypoint (instant from the block GUI)
- Toggle public / private
- Set a teleport fee (requires Vault)
- Invite specific players (private waypoints only)
- Change the display icon
- Rename or delete the waypoint
- Get a Pinpoint Compass

**Step 4 — Use a Pinpoint Compass**
Craft a Pinpoint Compass or get one from the Manage screen. With it in hand:

| Action | Result |
|---|---|
| Right-click air or block | Open hub GUI — all accessible waypoints listed |
| Click a waypoint in the hub | Start a 10-second countdown, then teleport |
| Shift+right-click air or block | Accept a pending teleport invite **or** follow a party travel offer |
| Right-click a player | Open waypoint selector to send them a teleport invite |
| Shift+right-click a player | Send a party link request |

---

## Party System

Shift+right-click another player with your Pinpoint Compass to send a **link request**. The target sees an Accept / Deny GUI (or can type `/party accept` / `/party deny`). Accepting merges both players (and any existing parties) into one group — all members are equal, no ownership hierarchy.

When any party member teleports through a Pinpoint, all online party members receive a **Follow / Stay** inventory GUI and a plain-text hint. Clicking **Follow** teleports them to the same waypoint. Clicking **Stay** dismisses the offer. Bedrock players can type `/party follow` with no arguments to follow the most recent offer.

---

## Commands

### Player commands

| Command | Description |
|---|---|
| `/waypoint` or `/wp` | Open the Waypoint Hub GUI |
| `/waypoint list` | List all accessible waypoints in chat |
| `/waypoint accept` | Accept a pending teleport invite |
| `/waypoint deny` | Deny a pending teleport invite |
| `/party` or `/pp` | Open party management GUI |
| `/party leave` | Leave your current party |
| `/party disband` | Disband the whole party |
| `/party remove <player>` | Remove a member from your party |
| `/party accept` / `/party deny` | Respond to a pending link request |
| `/party follow` | Follow the most recent party travel offer |

### Admin commands

| Command | Description |
|---|---|
| `/waypoint give <player> waypoint [amount]` | Give Waypoint Block items |
| `/waypoint give <player> compass [amount]` | Give Pinpoint Compasses |
| `/waypoint reload` | Reload config and data from disk |

---

## Permissions

| Node | Default | Description |
|---|---|---|
| `waypoint.use` | `true` | Open GUI, place/name waypoints, teleport, use compasses |
| `waypoint.list` | `true` | Use `/waypoint list` |
| `waypoint.give` | `op` | Use `/waypoint give` |
| `waypoint.reload` | `op` | Use `/waypoint reload` |
| `waypoint.admin` | `op` | Inherits `waypoint.give` + `waypoint.reload` + break any waypoint block |
| `party.use` | `true` | Use the party / social linking system |

---

## Configuration (`config.yml`)

### `settings:`

| Key | Default | Description |
|---|---|---|
| `default-fee` | `0` | Starting fee for newly created waypoints. `0` = free. |
| `safe-teleport-radius` | `5` | Block radius to scan for a safe landing spot. |
| `waypoint-block-teleport-delay-seconds` | `5` | Countdown for block right-click teleports. `0` = near-instant. |
| `waypoint-pearl-teleport-delay-seconds` | `10` | Countdown for compass-path teleports. Player must stand still. |
| `teleport-cooldown-seconds` | `3` | Reuse cooldown after any successful teleport (all players). |
| `max-waypoints-per-player` | `10` | Max waypoints one player may own. `0` = unlimited. |
| `invite-timeout` | `60` | Seconds before a teleport invite expires. |
| `link-request-timeout` | `60` | Seconds before a party link request expires. |
| `party-travel-offer-timeout` | `30` | Seconds party members have to act on a travel offer. |

### `holograms:`

| Key | Default | Description |
|---|---|---|
| `enabled` | `true` | Show floating text above each placed waypoint block. |
| `height` | `2.8` | Y offset above the block's Y coordinate for the bottom line. |
| `show-owner` | `true` | Include the owner's name in the hologram. |
| `show-fee` | `true` | Include the fee / Free line (only shown when Vault is enabled). |

All messages are configurable under `messages:` in `config.yml`. Use `&` color codes.

---

## Dependencies

| Dependency | Type | Notes |
|---|---|---|
| [Paper 1.21.1](https://papermc.io) | Required | Tested on Paper 1.21.1 |
| [Vault](https://www.spigotmc.org/resources/vault.34315/) | Optional | Required for teleport fees |
| [Geyser-Spigot](https://geysermc.org) | Optional | Required for Bedrock client support |
