# Pinpoint

A craftable waypoint and teleportation system. Set permanent locations around the world, share them with friends, and travel between them with ease.

---

## Features

- Craft placeable **Pinpoint Blocks** to mark locations
- Craft a **Pinpoint Pearl** for quick access to all your Pinpoints
- Set Pinpoints as **public** or **private**
- **Invite specific players** to access your private Pinpoints
- Charge a **fee** for others to teleport to your Pinpoint
- **Party system** — link up with a friend and travel together
- **Full pagination** in the hub — scroll through any number of Pinpoints

---

## How It Works

### Placing a Pinpoint

1. Craft a **Pinpoint Block** (see recipe below)
2. Place it anywhere in the world
3. Type a **name** in chat when prompted — that's it

The block is now your Pinpoint. Right-click it to manage or teleport.

### Traveling

- Craft a **Pinpoint Pearl** and right-click to open the hub
- Right-click any Pinpoint block you have access to
- Select a destination and stand still while the countdown runs

> Movement or taking damage cancels the teleport.

### Hub Navigation

The hub shows all Pinpoints you can access — yours, public ones, and any you've been invited to.

Use the **green pane** to go to the next page and the **red pane** to go back.

---

## Recipes

**Pinpoint Block**
```
Q Q Q
Q E Q     Q = Quartz  |  E = Ender Eye
Q Q Q
```

**Pinpoint Pearl**
```
P   P
  E       P = Ender Pearl  |  E = Ender Eye
P   P
```

---

## Managing Your Pinpoint

Right-click your Pinpoint block (or click the compass icon in the hub) to open the manage menu.

From there you can:

- **Teleport** to it
- **Toggle** public/private visibility
- **Set a fee** (players pay to teleport there; you receive the money)
- **Invite players** by name
- **Rename** it
- **Change its icon** in the hub
- **Delete** it

---

## Party System

Link up with a friend using your **Pinpoint Pearl**:

1. Hold the Pearl in your main hand
2. **Shift+right-click** a player to send them a link request
3. They accept in the pop-up

Once linked, when you teleport, your party members get a notification and can follow with one click.

To manage your party: `/party`

---

## Commands

| Command | Description |
|---|---|
| `/wp` or `/wp menu` | Open the Pinpoint hub |
| `/wp list` | List all Pinpoints you can access |
| `/wp accept` | Accept a teleport invite |
| `/wp deny` | Deny a teleport invite |
| `/party` | Open party management |
| `/party leave` | Leave your current party |
| `/party follow` | Follow your party's latest travel |

---

## Tips

- **Free Pinpoints** with a fee of 0 are accessible to anyone (if public)
- Pinpoints **remember their state** — they persist through server restarts
- You can have **multiple Pinpoints** in different worlds
- **Private Pinpoints** only show to you and players you invite
- The hub page **remembers where you left off** when you go into the manage menu and come back

---

## Changelog Summary

**v1.2.8** — Hub now supports unlimited Pinpoints with full pagination. All in-game text renamed from "waypoint" to "Pinpoint."

**v1.2.7** — Shift+Pearl now accepts teleport invites immediately without opening a GUI (better Bedrock support).

**v1.2.6** — Invite Players GUI rebuilt. Teleport fees now correctly pay the Pinpoint owner.

**v1.2.5 and earlier** — Core system: craftable blocks/pearls, public/private visibility, fees, party linking, party travel offers, hologram display above blocks.
